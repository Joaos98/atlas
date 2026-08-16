package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.entity.QuarantinedEntry;
import com.joaosousa.atlas.repository.QuarantinedEntryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Stores an entry Atlas refused, so enabling its source later can still recover it.
 *
 * <p>Deliberately <b>not</b> transactional: the insert runs in its own transaction inside
 * {@link QuarantinedEntryInserter}, and a duplicate has to be caught out here, after that
 * transaction has rolled back. The webhook double-fires most days, so the same refused entry
 * arrives twice and must leave one row, not two.
 */
@Component
public class QuarantineWriter {

    private final QuarantinedEntryRepository repository;
    private final QuarantinedEntryInserter inserter;

    public QuarantineWriter(QuarantinedEntryRepository repository, QuarantinedEntryInserter inserter) {
        this.repository = repository;
        this.inserter = inserter;
    }

    /** @return true if a row was stored, false if this entry was already quarantined */
    public boolean quarantine(SyncRequest.ExerciseEntry entry, String dataOrigin, String recordingMethod,
                              LocalDateTime now) {
        // Checked first so the ordinary double-fire never reaches the constraint at all. The
        // catch below is the backstop for a genuine race, not the main path.
        if (repository.existsByDataOriginAndRecordingMethodAndTypeAndStartTime(
                dataOrigin, recordingMethod, entry.getType(), entry.getStart_time())) {
            return false;
        }

        QuarantinedEntry quarantined = new QuarantinedEntry();
        quarantined.setDataOrigin(dataOrigin);
        quarantined.setRecordingMethod(recordingMethod);
        quarantined.setType(entry.getType());
        quarantined.setStartTime(entry.getStart_time());
        quarantined.setDurationSeconds(entry.getDuration_seconds());
        quarantined.setReceivedAt(now);
        quarantined.setReason(QuarantinedEntry.SOURCE_NOT_ALLOWED);

        try {
            inserter.insert(quarantined);
            return true;
        } catch (DataAccessException e) {
            if (isUniqueViolation(e)) {
                return false;
            }
            throw e;
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                if ("23505".equals(sql.getSQLState())) return true;
                String message = sql.getMessage();
                if (message != null && message.contains("UNIQUE constraint failed")) return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }
}
