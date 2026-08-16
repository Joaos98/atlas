package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Component
public class WorkoutLogInserter {

    private final WorkoutLogRepository repository;

    public WorkoutLogInserter(WorkoutLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists in its own transaction so a unique-constraint violation cannot poison the
     * caller's. Throws DataIntegrityViolationException if the signature already exists —
     * the caller is responsible for catching it. Do not catch it here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(WorkoutLog workoutLog) {
        try {
            repository.saveAndFlush(workoutLog);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (DataAccessException e) {
            // Hibernate's SQLite dialect does not classify a unique violation, so it arrives
            // as an uncategorised JpaSystemException — which SyncService's catch, written
            // against Postgres, does not match. Without this the duplicate would propagate out
            // of sync() and fail the whole request. Translated here so the contract above stays
            // true whatever the dialect underneath does.
            if (isUniqueViolation(e)) {
                throw new DataIntegrityViolationException("Duplicate sync signature", e);
            }
            throw e;
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                if ("23505".equals(sql.getSQLState())) return true;   // Postgres
                String message = sql.getMessage();
                if (message != null && message.contains("UNIQUE constraint failed")) return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }
}
