package com.joaosousa.atlas;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.dto.SyncResponse;
import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.entity.SyncSource;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.*;
import com.joaosousa.atlas.dto.SyncSourceDto;
import com.joaosousa.atlas.service.SyncService;
import com.joaosousa.atlas.service.SyncSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers sync-source-allowlist-spec.md §7 cases 3, 6 and 8, and
 * exercise-type-vocabulary-spec.md §7 cases 1–5.
 */
@AutoConfigureMockMvc
class SyncServiceTest extends AbstractSqliteIntegrationTest {

    private static final String WATCH = "com.example.wearable";
    private static final String AUTO = "automatically_recorded";

    static {
        resetDb("atlas-sync-service.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-sync-service.db");
    }

    @Autowired private SyncService syncService;
    @Autowired private SyncSourceService sourceService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkoutLogRepository workoutLogRepository;
    @Autowired private WorkoutTypeRepository workoutTypeRepository;
    @Autowired private ExerciseTypeMappingRepository mappingRepository;
    @Autowired private SyncSourceRepository sourceRepository;
    @Autowired private QuarantinedEntryRepository quarantineRepository;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM workout_logs");
        jdbc.update("DELETE FROM quarantined_entries");
        jdbc.update("DELETE FROM sync_sources");
        jdbc.update("DELETE FROM exercise_type_mapping");
        jdbc.update("DELETE FROM workout_types");
    }

    // --- allow-list ---

    /** Case 3. The whole point of the to-do: an unrecognised device is visible, not silent. */
    @Test
    void anUnknownSourceIsRecordedAndQuarantinedRatherThanDropped() {
        SyncResponse response = syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));

        assertEquals(0, response.getCreated());
        assertEquals(1, response.getRejected());
        assertEquals(0, workoutLogRepository.count());
        assertEquals(1, quarantineRepository.count());

        SyncSource source = sourceRepository.findById(new SyncSource.Key(WATCH, AUTO)).orElseThrow();
        assertFalse(source.isAllowed(), "a newly discovered source must never be allowed by default");
        assertNotNull(source.getFirstSeen());
        assertNotNull(source.getLastSeen());

        assertEquals(List.of(new SyncResponse.RejectedSource(WATCH, AUTO, 1)), response.getRejectedSources());
    }

    /**
     * Case 6. The sentinel must not be "unknown" — that is a real observed recording_method, and
     * reusing it would merge a genuine source with the malformed case.
     */
    @Test
    void missingMetadataBecomesTheNoneSentinelAndIsStillQuarantined() {
        SyncResponse response = syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, null, null)));

        assertEquals(1, response.getRejected());
        assertTrue(sourceRepository.findById(new SyncSource.Key(SyncSource.NONE, SyncSource.NONE)).isPresent());
        assertEquals(1, quarantineRepository.count());
    }

    /** Case 8: the webhook double-fires, so the same refused entry must not stack up. */
    @Test
    void theSameRejectedEntryIsQuarantinedOnlyOnce() {
        SyncRequest.ExerciseEntry entry = entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO);

        SyncResponse first = syncService.sync(request(entry));
        SyncResponse second = syncService.sync(request(entry));

        assertEquals(1, first.getRejected());
        assertEquals(0, second.getRejected(), "the repeat was already quarantined, so nothing new was refused");
        assertEquals(1, quarantineRepository.count());
    }

    @Test
    void lastSeenAdvancesWhileFirstSeenStays() {
        syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));
        LocalDateTime firstSeen = sourceRepository.findById(new SyncSource.Key(WATCH, AUTO)).orElseThrow().getFirstSeen();

        syncService.sync(request(entry("79", "2026-08-13T10:00:00Z", 1800, WATCH, AUTO)));
        SyncSource source = sourceRepository.findById(new SyncSource.Key(WATCH, AUTO)).orElseThrow();

        assertEquals(firstSeen, source.getFirstSeen());
        assertTrue(source.getLastSeen().compareTo(firstSeen) >= 0);
    }

    // --- exercise type vocabulary ---

    /** Case 1: "unmapped" stops being a reason to drop anything. */
    @Test
    void anUnmappedTypeCreatesItsTypeMappingAndWorkout() {
        allow(WATCH, AUTO);

        SyncResponse response = syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));

        assertEquals(1, response.getCreated());
        assertEquals(1, workoutLogRepository.count());

        WorkoutType created = workoutTypeRepository.findByNameIgnoreCase("Walking").orElseThrow();
        assertTrue(created.isPendingReview(), "auto-created types are announced, not silent");
        assertEquals(created.getId(), mappingRepository.findById(79).orElseThrow().getWorkoutType().getId());
    }

    /** Case 2. */
    @Test
    void aSecondWorkoutOfTheSameTypeReusesTheMapping() {
        allow(WATCH, AUTO);

        syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));
        syncService.sync(request(entry("79", "2026-08-13T10:00:00Z", 1800, WATCH, AUTO)));

        assertEquals(1, workoutTypeRepository.count(), "a second type would have split the history");
        assertEquals(2, workoutLogRepository.count());
    }

    /** Case 3: a hand-chosen label must never be overwritten by the catalog. */
    @Test
    void anExplicitMappingWinsOverTheCatalog() {
        allow(WATCH, AUTO);
        WorkoutType gym = newType("Crossfit");
        map(0, gym);

        syncService.sync(request(entry("0", "2026-08-12T10:00:00Z", 3600, WATCH, AUTO)));

        assertEquals(1, workoutTypeRepository.count());
        assertTrue(workoutTypeRepository.findByNameIgnoreCase("Other workout").isEmpty(),
                "the catalog name must not appear when the user has already named this code");
        assertEquals("Crossfit", workoutLogRepository.findAll().get(0).getWorkoutType().getName());
    }

    /** Case 4: a mapping to nothing means "never log this", and suppresses auto-create too. */
    @Test
    void aNullMappingIgnoresTheActivityWithoutCreatingAnything() {
        allow(WATCH, AUTO);
        map(79, null);

        SyncResponse response = syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));

        assertEquals(1, response.getIgnored());
        assertEquals(0, response.getCreated());
        assertEquals(0, workoutLogRepository.count());
        assertEquals(0, workoutTypeRepository.count(), "an ignored activity must not auto-create a type");
        assertEquals(0, quarantineRepository.count(), "ignoring is a decision already made, so nothing is held");
    }

    /** Case 5: a future Health Connect release still logs, under a generic name. */
    @Test
    void aCodeMissingFromTheCatalogStillLogs() {
        allow(WATCH, AUTO);

        SyncResponse response = syncService.sync(request(entry("9999", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));

        assertEquals(1, response.getCreated());
        assertTrue(workoutTypeRepository.findByNameIgnoreCase("Activity 9999").isPresent());
    }

    // --- replay ---

    /** Case 4: the payoff for quarantining rather than dropping. */
    @Test
    void enablingASourceReplaysWhatWasHeldForIt() {
        syncService.sync(request(
                entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO),
                entry("70", "2026-08-12T18:00:00Z", 3600, WATCH, AUTO)));
        assertEquals(2, quarantineRepository.count());
        assertEquals(0, workoutLogRepository.count());

        SyncSourceService.ReplayResult result = sourceService.setAllowed(WATCH, AUTO, true);

        assertEquals(2, result.created());
        assertEquals(2, workoutLogRepository.count(), "held entries should have become real workouts");
        assertEquals(0, quarantineRepository.count(), "quarantine is emptied once its entries are resolved");
        assertTrue(workoutTypeRepository.findByNameIgnoreCase("Walking").isPresent(),
                "replay runs the same path as a live sync, so auto-create applies");
    }

    /** Case 5: the signature constraint makes replay safe to repeat. */
    @Test
    void replayIsIdempotent() {
        syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));
        sourceService.setAllowed(WATCH, AUTO, true);

        SyncSourceService.ReplayResult second = sourceService.setAllowed(WATCH, AUTO, true);

        assertEquals(0, second.created());
        assertEquals(1, workoutLogRepository.count(), "a second enable must not duplicate anything");
    }

    /** An entry that can never be replayed usefully is discarded, not held forever. */
    @Test
    void unusableQuarantinedEntriesAreDiscardedOnReplay() {
        syncService.sync(request(entry("not-a-number", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));
        assertEquals(1, quarantineRepository.count(), "a bad entry is still quarantined — the source was the reason");

        SyncSourceService.ReplayResult result = sourceService.setAllowed(WATCH, AUTO, true);

        assertEquals(1, result.unusable());
        assertEquals(0, workoutLogRepository.count());
        assertEquals(0, quarantineRepository.count());
    }

    /** Disabling is not a statement that the history was wrong. */
    @Test
    void disablingASourceKeepsItsExistingWorkouts() {
        allow(WATCH, AUTO);
        syncService.sync(request(entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO)));
        assertEquals(1, workoutLogRepository.count());

        sourceService.setAllowed(WATCH, AUTO, false);

        assertEquals(1, workoutLogRepository.count(), "disabling must never delete logged workouts");
        syncService.sync(request(entry("79", "2026-08-13T10:00:00Z", 1800, WATCH, AUTO)));
        assertEquals(1, workoutLogRepository.count(), "but nothing new is logged while it is off");
    }

    @Test
    void listingSourcesReportsQuarantineCounts() {
        syncService.sync(request(
                entry("79", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO),
                entry("70", "2026-08-12T18:00:00Z", 3600, WATCH, AUTO)));

        List<SyncSourceDto> sources = sourceService.list();

        assertEquals(1, sources.size());
        assertEquals(WATCH, sources.get(0).dataOrigin());
        assertFalse(sources.get(0).allowed());
        assertEquals(2, sources.get(0).quarantinedCount());
    }

    @Test
    void malformedEntriesAreSkippedNotRejected() {
        allow(WATCH, AUTO);

        SyncResponse response = syncService.sync(request(
                entry("not-a-number", "2026-08-12T10:00:00Z", 1800, WATCH, AUTO),
                entry("79", "definitely-not-a-timestamp", 1800, WATCH, AUTO)));

        assertEquals(2, response.getSkipped());
        assertEquals(0, response.getRejected());
        assertEquals(0, workoutLogRepository.count());
    }

    // --- helpers ---

    private void allow(String origin, String method) {
        SyncSource source = new SyncSource();
        source.setDataOrigin(origin);
        source.setRecordingMethod(method);
        source.setAllowed(true);
        source.setFirstSeen(LocalDateTime.now());
        source.setLastSeen(LocalDateTime.now());
        sourceRepository.save(source);
    }

    private WorkoutType newType(String name) {
        WorkoutType type = new WorkoutType();
        type.setName(name);
        type.setColorHex("#4F8DFF");
        return workoutTypeRepository.save(type);
    }

    private void map(int healthConnectType, WorkoutType type) {
        ExerciseTypeMapping mapping = new ExerciseTypeMapping();
        mapping.setHealthConnectType(healthConnectType);
        mapping.setWorkoutType(type);
        mappingRepository.save(mapping);
    }

    private static SyncRequest request(SyncRequest.ExerciseEntry... entries) {
        SyncRequest request = new SyncRequest();
        request.setExercise(List.of(entries));
        return request;
    }

    private static SyncRequest.ExerciseEntry entry(String type, String startTime, int seconds,
                                                   String origin, String method) {
        SyncRequest.ExerciseEntry entry = new SyncRequest.ExerciseEntry();
        entry.setType(type);
        entry.setStart_time(startTime);
        entry.setDuration_seconds(seconds);
        if (origin != null || method != null) {
            SyncRequest.MetadataEntry metadata = new SyncRequest.MetadataEntry();
            metadata.setData_origin(origin);
            metadata.setRecording_method(method);
            entry.setMetadata(metadata);
        }
        return entry;
    }
}
