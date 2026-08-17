package com.joaosousa.atlas;

import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.ExerciseTypeMappingRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import com.joaosousa.atlas.service.WorkoutTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers merging a type into another, and rejecting a merge of a type into itself.
 *
 * <p>The orphan assertion is the important one. This schema has <b>no foreign keys at all</b> —
 * {@code workout_logs.workout_type_id} is a bare bigint — so a merge that misses rows produces
 * silently dangling references rather than an integrity error. Nothing but this test would
 * notice.
 */
@AutoConfigureMockMvc
class WorkoutTypeMergeTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("atlas-merge.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-merge.db");
    }

    @Autowired private WorkoutTypeService workoutTypeService;
    @Autowired private WorkoutTypeRepository workoutTypeRepository;
    @Autowired private WorkoutLogRepository workoutLogRepository;
    @Autowired private ExerciseTypeMappingRepository mappingRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM workout_logs");
        jdbc.update("DELETE FROM exercise_type_mapping");
        jdbc.update("DELETE FROM workout_types");
    }

    /** Case 6. */
    @Test
    void mergeReassignsLogsAndMappingsAndLeavesNoOrphans() {
        WorkoutType running = newType("Running", true);
        WorkoutType run = newType("Run", false);

        logWorkout(running, "2026-08-10");
        logWorkout(running, "2026-08-11");
        logWorkout(run, "2026-08-12");
        map(56, running);
        map(57, run);

        WorkoutType target = workoutTypeService.merge(running.getId(), run.getId());

        assertEquals(run.getId(), target.getId());
        assertTrue(workoutTypeRepository.findById(running.getId()).isEmpty(), "the source type should be gone");

        assertEquals(3, workoutLogRepository.count());
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM workout_logs WHERE workout_type_id = ?", Integer.class, run.getId()));
        assertEquals(2, mappingRepository.count());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM exercise_type_mapping WHERE workout_type_id = ?", Integer.class, run.getId()));

        assertNoOrphans();
    }

    /** Case 7. */
    @Test
    void mergingATypeIntoItselfIsRejected() {
        WorkoutType type = newType("Run", false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> workoutTypeService.merge(type.getId(), type.getId()));

        assertEquals(400, error.getStatusCode().value());
        assertTrue(workoutTypeRepository.findById(type.getId()).isPresent());
    }

    @Test
    void mergingIntoAnUnknownTypeChangesNothing() {
        WorkoutType source = newType("Run", false);
        logWorkout(source, "2026-08-10");

        assertThrows(ResponseStatusException.class, () -> workoutTypeService.merge(source.getId(), 9999L));

        assertEquals(1, workoutLogRepository.count());
        assertTrue(workoutTypeRepository.findById(source.getId()).isPresent());
        assertNoOrphans();
    }

    /** Merging is a review, so the surviving type stops asking to be reviewed. */
    @Test
    void mergeClearsPendingReviewOnTheTarget() {
        WorkoutType source = newType("Elliptical", true);
        WorkoutType target = newType("Cardio", true);

        workoutTypeService.merge(source.getId(), target.getId());

        assertFalse(workoutTypeRepository.findById(target.getId()).orElseThrow().isPendingReview());
        assertEquals(0, workoutTypeService.findPendingReview().size());
    }

    @Test
    void renamingAlsoCountsAsAReview() {
        WorkoutType type = newType("Activity 84", true);

        WorkoutType renamed = workoutTypeService.rename(type.getId(), "Padel");

        assertEquals("Padel", renamed.getName());
        assertFalse(renamed.isPendingReview());
    }

    private void assertNoOrphans() {
        Integer orphans = jdbc.queryForObject("""
                SELECT COUNT(*) FROM workout_logs
                 WHERE workout_type_id IS NOT NULL
                   AND workout_type_id NOT IN (SELECT id FROM workout_types)
                """, Integer.class);
        assertEquals(0, orphans, "workout_logs point at a workout type that no longer exists");

        Integer orphanMappings = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exercise_type_mapping
                 WHERE workout_type_id IS NOT NULL
                   AND workout_type_id NOT IN (SELECT id FROM workout_types)
                """, Integer.class);
        assertEquals(0, orphanMappings, "mappings point at a workout type that no longer exists");
    }

    private WorkoutType newType(String name, boolean pendingReview) {
        WorkoutType type = new WorkoutType();
        type.setName(name);
        type.setColorHex("#4F8DFF");
        type.setPendingReview(pendingReview);
        return workoutTypeRepository.save(type);
    }

    private void logWorkout(WorkoutType type, String date) {
        WorkoutLog entry = new WorkoutLog();
        entry.setWorkoutType(type);
        entry.setLogDate(LocalDate.parse(date));
        entry.setDurationMinutes(30);
        workoutLogRepository.save(entry);
    }

    private void map(int healthConnectType, WorkoutType type) {
        ExerciseTypeMapping mapping = new ExerciseTypeMapping();
        mapping.setHealthConnectType(healthConnectType);
        mapping.setWorkoutType(type);
        mappingRepository.save(mapping);
    }
}
