package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        repository.saveAndFlush(workoutLog);
    }
}
