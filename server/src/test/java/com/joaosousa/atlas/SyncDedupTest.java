package com.joaosousa.atlas;

import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import com.joaosousa.atlas.service.WorkoutLogInserter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers sync-source-allowlist-spec.md §7 cases 1, 2 and 7.
 *
 * <p><b>Not covered: the concurrent double-fire</b> — the original bug from
 * webhook-sync-deduplication-spec.md §8 case 2. {@code hikari.maximum-pool-size=1} serializes
 * SQLite writes, so two simultaneous requests cannot race on this harness. Protection there is
 * structural — {@code REQUIRES_NEW} plus the database constraint — and a Testcontainers
 * Postgres was rejected as a CI dependency. The gap is stated rather than merely absent.
 */
@AutoConfigureMockMvc
class SyncDedupTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("atlas-sync-dedup.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-sync-dedup.db");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkoutLogInserter workoutLogInserter;

    @Autowired
    private WorkoutLogRepository workoutLogRepository;

    @Autowired
    private WorkoutTypeRepository workoutTypeRepository;

    @BeforeEach
    void reset() {
        // Raw DELETE rather than deleteAll(): the latter reads every row first, and dates are
        // stored as epoch millis, so a badly written row would fail here instead of where it
        // was created.
        jdbc.update("DELETE FROM workout_logs");
    }

    /**
     * Case 1, the regression guard for §1.3. Asserted against the real schema rather than the
     * annotation, because {@code ddl-auto=update} logs and swallows a failed index creation —
     * so a missing constraint would otherwise look exactly like a working one.
     */
    @Test
    void theSyncSignatureUniqueIndexExistsOnAFreshInstall() {
        List<String> indexes = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'workout_logs'",
                String.class);

        assertTrue(indexes.stream().anyMatch(n -> n != null && n.contains("ux_workout_logs_sync_signature")),
                "ux_workout_logs_sync_signature is missing; cross-sync dedup is silently dead. Found: " + indexes);
    }

    /** Case 2: the same entry arriving in two separate requests, which is the real double-fire. */
    @Test
    void theSameSignatureCannotBeInsertedTwice() {
        WorkoutType type = newType("Run");
        String signature = "1755000000000|79";

        insertSigned(type, signature);
        assertThrows(DataIntegrityViolationException.class, () -> insertSigned(type, signature),
                "the second insert should have violated the unique index");

        assertEquals(1, workoutLogRepository.count());
    }

    /**
     * Case 7, carried over from webhook-sync-deduplication-spec.md §8 case 3: two genuinely
     * different workouts of the same type on the same day must both survive. This is the
     * regression guard against ever "fixing" dedup with a rounded-time signature.
     */
    @Test
    void twoDistinctWorkoutsOfTheSameTypeOnOneDayBothSurvive() {
        WorkoutType type = newType("Run");

        insertSigned(type, "1755000000000|79");
        insertSigned(type, "1755020000000|79");

        assertEquals(2, workoutLogRepository.count());
    }

    /**
     * Manual workouts carry no signature and must never collide, however many there are —
     * SQLite treats NULLs as distinct in a unique index, as Postgres did.
     */
    @Test
    void nullSignaturesDoNotCollide() {
        WorkoutType type = newType("Manual");

        insertSigned(type, null);
        insertSigned(type, null);
        insertSigned(type, null);

        assertEquals(3, workoutLogRepository.count());
    }

    private void insertSigned(WorkoutType type, String signature) {
        WorkoutLog logEntry = new WorkoutLog();
        logEntry.setWorkoutType(type);
        logEntry.setLogDate(LocalDate.of(2026, 8, 12));
        logEntry.setDurationMinutes(30);
        logEntry.setSyncSignature(signature);
        workoutLogInserter.insert(logEntry);
    }

    private WorkoutType newType(String name) {
        WorkoutType type = new WorkoutType();
        type.setName(name);
        type.setColorHex("#4F8DFF");
        return workoutTypeRepository.save(type);
    }
}
