package com.joaosousa.atlas;

import com.fasterxml.jackson.databind.JsonNode;
import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.service.AppSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers insight-provider-spec.md §10 verifications 2–4, plus the seeding gap the spec
 * assumed away in §3.1.
 */
@AutoConfigureMockMvc
class AppSettingsApiTest extends AbstractSqliteIntegrationTest {

    private static final String KEY = "sk-test-abcdef123456789";

    static {
        resetDb("atlas-settings.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-settings.db");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppSettingsService appSettingsService;

    @BeforeEach
    void storeAKey() throws Exception {
        putSettings("{\"insightApiKey\":\"" + KEY + "\"}");
    }

    /**
     * Verification 2. Asserted against the raw body rather than named fields: the leak this
     * guards against is a future entity field serialised under a name nobody predicted.
     */
    @Test
    void theApiKeyIsNeverServedOverHttp() throws Exception {
        String body = mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(KEY), "GET /api/settings served the API key: " + body);

        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.get("insightApiKeyConfigured").asBoolean());
        assertEquals("6789", json.get("insightApiKeyLast4").asText());
    }

    /** Verification 3 — the rule that stops unrelated settings saves from wiping the key. */
    @Test
    void savingOtherSettingsLeavesTheKeyIntact() throws Exception {
        JsonNode response = putSettings("{\"targetWorkoutsPerWeek\":5}");

        assertEquals(5, response.get("targetWorkoutsPerWeek").asInt());
        assertTrue(response.get("insightApiKeyConfigured").asBoolean());
        assertEquals(KEY, storedKey());
    }

    /** An explicitly blank key means unchanged too, which is why clearing needs its own flag. */
    @Test
    void aBlankKeyMeansUnchangedRatherThanCleared() throws Exception {
        JsonNode response = putSettings("{\"insightApiKey\":\"   \"}");

        assertTrue(response.get("insightApiKeyConfigured").asBoolean());
        assertEquals(KEY, storedKey());
    }

    /** Verification 4. */
    @Test
    void theClearFlagRemovesTheKey() throws Exception {
        JsonNode response = putSettings("{\"clearInsightApiKey\":true}");

        assertFalse(response.get("insightApiKeyConfigured").asBoolean());
        assertTrue(response.get("insightApiKeyLast4").isNull());
        assertEquals("", storedKey());
    }

    /**
     * The case spec §3.1 got wrong: it expected {@code ddl-auto=update} to supply defaults for
     * the new columns, but ALTER TABLE ADD COLUMN leaves existing rows NULL — and the settings
     * row already exists on every install that holds real data, so the insert never runs there.
     * Without the backfill, upgrading would silently disable insights on exactly those installs.
     */
    @Test
    void seedingBackfillsColumnsAddedToAnAlreadyExistingRow() {
        jdbc.update("""
                UPDATE app_settings
                   SET insight_base_url = NULL, insight_model = NULL,
                       insight_api_key = NULL, unit_system = NULL
                 WHERE id = ?""", AppSettings.SETTINGS_ID);

        appSettingsService.ensureSeeded();

        AppSettings settings = appSettingsService.get();
        assertEquals(AppSettingsService.DEFAULT_INSIGHT_BASE_URL, settings.getInsightBaseUrl());
        assertEquals(AppSettingsService.DEFAULT_INSIGHT_MODEL, settings.getInsightModel());
        assertEquals("", settings.getInsightApiKey());
        assertEquals(AppSettingsService.DEFAULT_UNIT_SYSTEM, settings.getUnitSystem());
    }

    /** The default must leave an existing install rendering exactly as it did before. */
    @Test
    void unitSystemIsSeededToMetricAndIsRoundTrippable() throws Exception {
        assertEquals("METRIC", getJson("/api/settings").get("unitSystem").asText());

        JsonNode response = putSettings("{\"unitSystem\":\"IMPERIAL\"}");
        assertEquals("IMPERIAL", response.get("unitSystem").asText());
        assertEquals("IMPERIAL", getJson("/api/settings").get("unitSystem").asText());

        // Still absent-means-unchanged, like every other field.
        assertEquals("IMPERIAL", putSettings("{\"targetWorkoutsPerWeek\":3}").get("unitSystem").asText());

        putSettings("{\"unitSystem\":\"METRIC\"}");
    }

    /** Re-seeding must not walk over a configured install — COALESCE only fills NULLs. */
    @Test
    void seedingLeavesConfiguredValuesAlone() throws Exception {
        putSettings("{\"insightBaseUrl\":\"http://localhost:11434/v1\",\"insightModel\":\"llama3.2\"}");

        appSettingsService.ensureSeeded();

        AppSettings settings = appSettingsService.get();
        assertEquals("http://localhost:11434/v1", settings.getInsightBaseUrl());
        assertEquals("llama3.2", settings.getInsightModel());
        assertEquals(KEY, settings.getInsightApiKey());
    }

    private JsonNode putSettings(String json) throws Exception {
        String body = mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String storedKey() {
        return jdbc.queryForObject(
                "SELECT insight_api_key FROM app_settings WHERE id = ?", String.class, AppSettings.SETTINGS_ID);
    }
}
