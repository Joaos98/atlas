package com.joaosousa.atlas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joaosousa.atlas.dto.AppSettingsUpdateRequest;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.service.AppSettingsService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The happy path, against a stub that speaks the OpenAI chat-completions format.
 *
 * <p>Everything else in the suite exercises failure: no key, nothing listening, parser input
 * handed over directly. That left the one path the whole spec exists for — a real request going
 * out and a real reply coming back — with no coverage at all, so a mistake in the request shape
 * or the response path would surface only when a user first pasted in a key.
 *
 * <p>What this cannot do is prove a given vendor accepts the request; that is §10 verification 9
 * and needs a real endpoint. It proves Atlas holds up its end of the format.
 */
@AutoConfigureMockMvc
class InsightProviderClientTest extends AbstractSqliteIntegrationTest {

    private static final String KEY = "sk-stub-abcdef123456";

    record RecordedRequest(String method, String path, String authorization, String body) {}

    private static HttpServer server;
    private static int port;
    private static final List<RecordedRequest> received = new ArrayList<>();
    private static final Deque<String[]> queuedResponses = new ArrayDeque<>();

    static {
        resetDb("atlas-provider.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-provider.db");
    }

    @Autowired
    private BodyMetricsRepository bodyMetricsRepository;

    @Autowired
    private AppSettingsService appSettingsService;

    @BeforeAll
    static void startStubProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
            received.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body));

            String[] response = queuedResponses.isEmpty()
                    ? new String[]{"200", chatCompletion("VERDICT: Fallback\nINSIGHT: Default stub reply.")}
                    : queuedResponses.poll();

            byte[] out = response[1].getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(Integer.parseInt(response[0]), out.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopStubProvider() {
        server.stop(0);
    }

    @BeforeEach
    void resetState() {
        received.clear();
        queuedResponses.clear();

        bodyMetricsRepository.deleteAll();
        BodyMetrics measurement = new BodyMetrics();
        measurement.setMeasuredOn(LocalDate.of(2026, 8, 1));
        measurement.setWeightKg(80.0);
        measurement.setMuscleMassKg(40.0);
        measurement.setWaterLiters(40.0);
        measurement.setBodyFatKg(15.0);
        measurement.setBodyFatPct(18.0);
        bodyMetricsRepository.save(measurement);

        AppSettingsUpdateRequest settings = new AppSettingsUpdateRequest();
        settings.setInsightApiKey(KEY);
        // Trailing slash on purpose: it is easy to paste in, and must not produce "//chat".
        settings.setInsightBaseUrl("http://localhost:" + port + "/v1/");
        settings.setInsightModel("stub-model");
        appSettingsService.update(settings);
    }

    @Test
    void aSuccessfulGenerationSendsAWellFormedRequest() throws Exception {
        queuedResponses.add(new String[]{"200", chatCompletion("VERDICT: Strong progress\nINSIGHT: Fat is dropping.")});

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.verdict").value("Strong progress"))
                .andExpect(jsonPath("$.text").value("Fat is dropping."));

        assertEquals(1, received.size());
        RecordedRequest request = received.get(0);

        assertEquals("POST", request.method());
        assertEquals("/v1/chat/completions", request.path());
        assertEquals("Bearer " + KEY, request.authorization());

        JsonNode body = objectMapper.readTree(request.body());
        assertEquals("stub-model", body.get("model").asText());
        assertEquals(0.7, body.get("temperature").asDouble(), 0.0001);
        assertEquals(2000, body.get("max_tokens").asInt());

        JsonNode messages = body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertTrue(messages.get(0).get("content").asText().startsWith("You are a personal fitness coach"));
        assertEquals("user", messages.get(1).get("role").asText());
        assertTrue(messages.get(1).get("content").asText().contains("Weight"),
                "the built prompt should carry the measurement");
    }

    @Test
    void aSuccessfulGenerationIsStoredAndServedBack() throws Exception {
        queuedResponses.add(new String[]{"200", chatCompletion("VERDICT: Holding steady\nINSIGHT: Nothing has moved much.")});

        mockMvc.perform(post("/api/insights/regenerate")).andExpect(status().isOk());

        BodyMetrics stored = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn")).get(0);
        assertEquals("VERDICT:Holding steady\nINSIGHT:Nothing has moved much.", stored.getInsightText());
        assertEquals(LocalDate.of(2026, 8, 1).atStartOfDay(), stored.getInsightGeneratedAt());
    }

    /** Markdown-wrapped labels off the wire, not handed to the parser directly. */
    @Test
    void aModelThatWrapsItsLabelsInMarkdownStillParses() throws Exception {
        queuedResponses.add(new String[]{"200", chatCompletion("**VERDICT:** Plateau\n\n**INSIGHT:** Flat for weeks.")});

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.verdict").value("Plateau"))
                .andExpect(jsonPath("$.text").value("Flat for weeks."));
    }

    @Test
    void a503IsRetriedExactlyOnce() throws Exception {
        queuedResponses.add(new String[]{"503", "{\"error\":{\"message\":\"overloaded\"}}"});
        queuedResponses.add(new String[]{"200", chatCompletion("VERDICT: Recovered\nINSIGHT: Second attempt worked.")});

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.verdict").value("Recovered"));

        assertEquals(2, received.size(), "a 503 should be retried once, and only once");
    }

    @Test
    void theProvidersOwnErrorMessageIsSurfaced() throws Exception {
        queuedResponses.add(new String[]{"404", "{\"error\":{\"message\":\"model 'llama3.2' not found\"}}"});

        String text = regenerateText();
        assertTrue(text.contains("404"), text);
        assertTrue(text.contains("model 'llama3.2' not found"), text);
    }

    @Test
    void anOverlongProviderErrorIsTruncated() throws Exception {
        String longMessage = "x".repeat(400);
        queuedResponses.add(new String[]{"400", "{\"error\":{\"message\":\"" + longMessage + "\"}}"});

        String text = regenerateText();
        assertTrue(text.contains("…"), "expected a truncation marker: " + text);
        assertFalse(text.contains(longMessage), "the full 400-character body was passed through");
        assertTrue(text.length() < 400 + 120, "message grew beyond the cap: " + text.length());
    }

    /** A 200 from something that is not a chat-completions endpoint. */
    @Test
    void aReplyWithNoChoicesFailsInsteadOfThrowingNpe() throws Exception {
        queuedResponses.add(new String[]{"200", "{\"unexpected\":true}"});

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROVIDER_ERROR"));
    }

    /** Failures still must not overwrite what is stored, on this path too. */
    @Test
    void aProviderErrorLeavesTheStoredInsightAlone() throws Exception {
        queuedResponses.add(new String[]{"200", chatCompletion("VERDICT: Good\nINSIGHT: Keep going.")});
        mockMvc.perform(post("/api/insights/regenerate")).andExpect(jsonPath("$.state").value("OK"));

        queuedResponses.add(new String[]{"401", "{\"error\":{\"message\":\"invalid key\"}}"});
        mockMvc.perform(post("/api/insights/regenerate")).andExpect(jsonPath("$.state").value("PROVIDER_ERROR"));

        BodyMetrics stored = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn")).get(0);
        assertEquals("VERDICT:Good\nINSIGHT:Keep going.", stored.getInsightText());
    }

    private String regenerateText() throws Exception {
        String body = mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("text").asText();
    }

    /** Built rather than hand-written so newlines in the content are escaped properly. */
    private static String chatCompletion(String content) {
        try {
            ObjectNode root = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            root.putArray("choices").addObject()
                    .putObject("message")
                    .put("role", "assistant")
                    .put("content", content);
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
