package com.joaosousa.atlas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Creates every unique index the app depends on.
 *
 * <p>These cannot be JPA annotations. Hibernate emits {@code @Table(uniqueConstraints = ...)}
 * as {@code ALTER TABLE ... ADD CONSTRAINT}, which SQLite does not support; the statement
 * fails, {@code ddl-auto=update} logs and continues, and the result is a schema that looks
 * annotated but has no constraint — indistinguishable from a working one until duplicates
 * appear. {@code CREATE UNIQUE INDEX IF NOT EXISTS} is supported, applies to an existing
 * table, and is idempotent.
 *
 * <p>Runs before {@link AppSettingsSeeder} and anything else that writes.
 */
@Component
@Order(1)
public class SqliteIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqliteIndexes.class);

    static final String SYNC_SIGNATURE_INDEX = "ux_workout_logs_sync_signature";
    static final String QUARANTINE_INDEX = "ux_quarantined_entries_identity";

    private final JdbcTemplate jdbc;

    public SqliteIndexes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillNewColumns();
        createSyncSignatureIndex();

        // No pre-check needed: this table is introduced with the index, so it cannot already
        // hold rows that would violate it.
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + QUARANTINE_INDEX
                + " ON quarantined_entries (data_origin, recording_method, type, start_time)");
    }

    /**
     * Gives pre-existing rows a value for columns added later.
     *
     * <p>{@code ddl-auto=update} adds a column with {@code ALTER TABLE ADD COLUMN}, which leaves
     * NULL everywhere. That is fine for a nullable field and fatal for anything read as a
     * primitive — {@code workout_types.pending_review} took out {@code /api/workout-types} and
     * {@code /api/workout-logs} on the first upgraded install it met, while every fresh-install
     * test stayed green because a fresh install creates the column and its rows together.
     *
     * <p>Runs before anything reads. Any future column added to an existing table belongs here.
     */
    private void backfillNewColumns() {
        int rows = jdbc.update("UPDATE workout_types SET pending_review = 0 WHERE pending_review IS NULL");
        if (rows > 0) {
            log.info("Backfilled pending_review on {} pre-existing workout type(s)", rows);
        }
    }

    /**
     * Without this index, {@link WorkoutLogInserter}'s documented contract cannot fire and only
     * the in-request {@code seen} set survives — which catches the webhook's double-fire solely
     * when both copies arrive in the same POST. They arrive about five seconds apart as two
     * separate requests.
     */
    private void createSyncSignatureIndex() {
        List<String> duplicates = jdbc.queryForList("""
                SELECT sync_signature FROM workout_logs
                 WHERE sync_signature IS NOT NULL
                 GROUP BY sync_signature HAVING COUNT(*) > 1
                """, String.class);

        // Creating the index would fail outright on existing duplicates, and a swallowed
        // failure is precisely the silent no-op this class exists to prevent. Say so loudly and
        // leave the app running: duplicates can only pre-date the index, never follow it.
        if (!duplicates.isEmpty()) {
            log.error("""
                    Cannot create {}: workout_logs already holds {} duplicated sync signature(s), \
                    so cross-sync deduplication stays disabled. Those rows were inserted while no \
                    index existed. Delete the extra copies, keeping the lowest id per signature, \
                    then restart. Affected: {}""",
                    SYNC_SIGNATURE_INDEX, duplicates.size(), duplicates);
            return;
        }

        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + SYNC_SIGNATURE_INDEX
                + " ON workout_logs (sync_signature)");
    }
}
