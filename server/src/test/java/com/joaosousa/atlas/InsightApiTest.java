package com.joaosousa.atlas;

import com.joaosousa.atlas.dto.AppSettingsUpdateRequest;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers insight-provider-spec.md §10 verifications 1 and 7.
 */
@AutoConfigureMockMvc
class InsightApiTest extends AbstractSqliteIntegrationTest {

    private static final String GOOD_INSIGHT = "VERDICT: Strong progress\nINSIGHT: Fat is dropping steadily.";

    static {
        resetDb("atlas-insights.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-insights.db");
    }

    @Autowired
    private BodyMetricsRepository bodyMetricsRepository;

    @Autowired
    private AppSettingsService appSettingsService;

    @BeforeEach
    void resetState() {
        bodyMetricsRepository.deleteAll();

        BodyMetrics measurement = new BodyMetrics();
        measurement.setMeasuredOn(LocalDate.of(2026, 8, 1));
        measurement.setWeightKg(80.0);
        measurement.setMuscleMassKg(40.0);
        measurement.setWaterLiters(40.0);
        measurement.setBodyFatKg(15.0);
        measurement.setBodyFatPct(18.0);
        bodyMetricsRepository.save(measurement);

        AppSettingsUpdateRequest clearKey = new AppSettingsUpdateRequest();
        clearKey.setClearInsightApiKey(true);
        appSettingsService.update(clearKey);
    }

    /**
     * Verification 1. This path used to render as a crash on a fresh install — the one state
     * every new self-hoster sees first.
     */
    @Test
    void aFreshInstallWithNoKeyIsAStateNotAnError() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.text").value(com.joaosousa.atlas.service.InsightService.NOT_CONFIGURED_MESSAGE));
    }

    @Test
    void regeneratingWithNoKeyReportsNotConfiguredAndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"));

        assertEquals(null, latest().getInsightText());
    }

    /**
     * Verification 7, the defect from §8. A failed generation used to be written into
     * {@code insight_text}, destroying the last good insight — and {@code GET} then re-served
     * the error prose as though it were an insight, so the loss was invisible.
     */
    @Test
    void aFailedRegenerationLeavesThePreviousInsightIntact() throws Exception {
        storeGoodInsight();
        pointAtSomethingThatIsNotListening();

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNREACHABLE"));

        assertEquals(GOOD_INSIGHT, latest().getInsightText(), "the stored insight was overwritten by a failure");

        // And the good insight is still what GET serves.
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.verdict").value("Strong progress"))
                .andExpect(jsonPath("$.text").value("Fat is dropping steadily."));
    }

    /**
     * The §8 defect had two sites the spec never named, and this is the worse one: creating a
     * measurement generates an insight for it, and used to store the failure text unconditionally
     * — so on an install with no provider configured, every new measurement was permanently
     * stamped with "Insights are off" as its insight, and GET served it as a real one.
     */
    @Test
    void creatingAMeasurementWithNoProviderStoresNoInsightText() throws Exception {
        mockMvc.perform(post("/api/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"measuredOn":"2026-08-09","weightKg":81.0,"muscleMassKg":40.0,
                                 "waterLiters":40.0,"bodyFatKg":15.0,"bodyFatPct":18.0}"""))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.insightText").doesNotExist());
    }

    /** The URL is named back so a typo in it is self-evident. */
    @Test
    void anUnreachableProviderNamesTheUrlItTried() throws Exception {
        String baseUrl = pointAtSomethingThatIsNotListening();

        mockMvc.perform(post("/api/insights/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString(baseUrl)));
    }

    private void storeGoodInsight() {
        BodyMetrics latest = latest();
        latest.setInsightText(GOOD_INSIGHT);
        latest.setInsightGeneratedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        bodyMetricsRepository.save(latest);
    }

    private String pointAtSomethingThatIsNotListening() {
        String baseUrl = "http://localhost:" + closedPort() + "/v1";
        AppSettingsUpdateRequest request = new AppSettingsUpdateRequest();
        request.setInsightApiKey("sk-test-key");
        request.setInsightBaseUrl(baseUrl);
        appSettingsService.update(request);
        return baseUrl;
    }

    /** Bound then released, so the port is real but nothing answers on it. */
    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("could not reserve a port", e);
        }
    }

    private BodyMetrics latest() {
        List<BodyMetrics> all = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn"));
        return all.get(0);
    }
}
