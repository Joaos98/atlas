package com.joaosousa.atlas;

import com.joaosousa.atlas.entity.SyncSource;
import com.joaosousa.atlas.repository.SyncSourceRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the {@code POST /api/sync} contract published in README.md.
 *
 * <p>That section tells strangers they can write their own sender, so it is an interface, not
 * prose — and an interface nobody tests is a promise that quietly stops being true. Every
 * assertion here corresponds to a claim the README makes in writing. If one fails, the README
 * is now wrong and needs changing alongside the code.
 */
@AutoConfigureMockMvc
class SyncContractTest extends AbstractSqliteIntegrationTest {

    private static final String KEY = "spike-key";
    private static final String ORIGIN = "com.example.wearable";
    private static final String METHOD = "automatically_recorded";

    static {
        resetDb("atlas-sync-contract.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-sync-contract.db");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkoutLogRepository workoutLogRepository;
    @Autowired private SyncSourceRepository sourceRepository;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM workout_logs");
        jdbc.update("DELETE FROM quarantined_entries");
        jdbc.update("DELETE FROM sync_sources");
        jdbc.update("DELETE FROM exercise_type_mapping");
        jdbc.update("DELETE FROM workout_types");
        allowSource();
    }

    /** README: "Health Connect exercise type as a numeric code, string or number". */
    @Test
    void typeIsAcceptedAsAJsonStringOrANumber() throws Exception {
        sync("""
             {"exercise":[
               {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                "metadata":{"data_origin":"%s","recording_method":"%s"}},
               {"type":56,"start_time":"2024-01-16T08:30:00.000Z","duration_seconds":1800,
                "metadata":{"data_origin":"%s","recording_method":"%s"}}
             ]}""".formatted(ORIGIN, METHOD, ORIGIN, METHOD))
                .andExpect(jsonPath("$.created").value(2));
    }

    /** README: "Fractional seconds optional — both forms accepted". */
    @Test
    void startTimeIsAcceptedWithAndWithoutFractionalSeconds() throws Exception {
        sync("""
             {"exercise":[
               {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                "metadata":{"data_origin":"%s","recording_method":"%s"}},
               {"type":"79","start_time":"2024-01-16T08:30:00Z","duration_seconds":1800,
                "metadata":{"data_origin":"%s","recording_method":"%s"}}
             ]}""".formatted(ORIGIN, METHOD, ORIGIN, METHOD))
                .andExpect(jsonPath("$.created").value(2));
    }

    /**
     * README: "Anything else in the payload is ignored, including fields HC Webhook sends that
     * Atlas does not model". A stricter parser here would reject the only known working sender.
     */
    @Test
    void unmodelledFieldsAreIgnoredRatherThanRejected() throws Exception {
        sync("""
             {"timestamp":"2024-01-15T09:00:00Z","app_version":"9.9.9",
              "exercise":[
                {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                 "end_time":"2024-01-15T09:00:00.000Z","distance_meters":4200,"steps":5100,
                 "avg_cadence_spm":150,"stride_length_m":0.9,
                 "metadata":{"data_origin":"%s","recording_method":"%s",
                             "device":{"manufacturer":"example","type":"WATCH"}}}
              ]}""".formatted(ORIGIN, METHOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }

    /** README: "The guarantee: re-send freely." The headline promise of the whole contract. */
    @Test
    void resendingTheSamePayloadIsSafe() throws Exception {
        String payload = """
                {"exercise":[
                  {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                   "metadata":{"data_origin":"%s","recording_method":"%s"}}
                ]}""".formatted(ORIGIN, METHOD);

        sync(payload).andExpect(jsonPath("$.created").value(1));
        sync(payload).andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.skipped").value(1));
        sync(payload).andExpect(jsonPath("$.created").value(0));

        assertEquals(1, workoutLogRepository.count(), "re-sending must never duplicate a workout");
    }

    /** README documents exactly these four counters plus rejectedSources. */
    @Test
    void theResponseCarriesEveryDocumentedField() throws Exception {
        sync("""
             {"exercise":[
               {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                "metadata":{"data_origin":"%s","recording_method":"%s"}}
             ]}""".formatted(ORIGIN, METHOD))
                .andExpect(jsonPath("$.created").exists())
                .andExpect(jsonPath("$.skipped").exists())
                .andExpect(jsonPath("$.rejected").exists())
                .andExpect(jsonPath("$.ignored").exists())
                .andExpect(jsonPath("$.rejectedSources").isArray());
    }

    /** README: "absent becomes (none)" — and the first-run signal it describes. */
    @Test
    void aPayloadWithoutMetadataIsHeldUnderTheNoneSource() throws Exception {
        sync("""
             {"exercise":[
               {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800}
             ]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.rejectedSources[0].origin").value(SyncSource.NONE));
    }

    /** README: a new install trusts no device, so the first sync is 200 with nothing created. */
    @Test
    void anUnknownDeviceGetsOkAndNothingLogged() throws Exception {
        sync("""
             {"exercise":[
               {"type":"79","start_time":"2024-01-15T08:30:00.000Z","duration_seconds":1800,
                "metadata":{"data_origin":"com.example.unknown","recording_method":"%s"}}
             ]}""".formatted(METHOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rejected").value(1));

        assertEquals(0, workoutLogRepository.count());
    }

    /** The one thing the endpoint does refuse. */
    @Test
    void aWrongOrMissingApiKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/sync").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exercise\":[]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sync").header("X-API-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exercise\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions sync(String payload) throws Exception {
        return mockMvc.perform(post("/api/sync")
                        .header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void allowSource() {
        SyncSource source = new SyncSource();
        source.setDataOrigin(ORIGIN);
        source.setRecordingMethod(METHOD);
        source.setAllowed(true);
        source.setFirstSeen(LocalDateTime.now());
        source.setLastSeen(LocalDateTime.now());
        sourceRepository.save(source);
    }
}
