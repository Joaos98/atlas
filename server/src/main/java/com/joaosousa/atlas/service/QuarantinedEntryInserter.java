package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.QuarantinedEntry;
import com.joaosousa.atlas.repository.QuarantinedEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors {@link WorkoutLogInserter}, and exists for the same reason: the constraint violation
 * has to escape the transaction that caused it.
 *
 * <p>Catching it <i>inside</i> the transactional method does not work — the persistence context
 * is already marked rollback-only by then, so returning normally fails the commit with
 * {@code UnexpectedRollbackException}. The catch therefore has to live one level out, in a
 * caller that is not itself transactional.
 */
@Component
public class QuarantinedEntryInserter {

    private final QuarantinedEntryRepository repository;

    public QuarantinedEntryInserter(QuarantinedEntryRepository repository) {
        this.repository = repository;
    }

    /** Throws DataIntegrityViolationException if this entry is already quarantined. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(QuarantinedEntry entry) {
        repository.saveAndFlush(entry);
    }
}
