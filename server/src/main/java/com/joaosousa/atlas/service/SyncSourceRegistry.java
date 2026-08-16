package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.SyncSource;
import com.joaosousa.atlas.repository.SyncSourceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records that a source was seen, and answers whether it may log workouts.
 *
 * <p>Its own transaction, like every other write during a sync: {@code SyncService.sync()} is
 * deliberately not transactional so one bad entry cannot poison the batch.
 */
@Component
public class SyncSourceRegistry {

    private final SyncSourceRepository repository;

    public SyncSourceRegistry(SyncSourceRepository repository) {
        this.repository = repository;
    }

    /**
     * Missing metadata becomes the literal {@code (none)}.
     *
     * <p>It must not become {@code unknown}: that is a real observed {@code recording_method} on
     * mirror records, and using it as a sentinel would merge a genuine source with the malformed
     * case. Normalised entries then flow through the ordinary path — recorded, rejected by
     * default, enablable if some future sender legitimately omits metadata.
     */
    public static String normalize(String value) {
        return (value == null || value.isBlank()) ? SyncSource.NONE : value;
    }

    /**
     * Upserts the source and reports whether it is allowed. New sources are always recorded as
     * <b>not</b> allowed — the app learns what exists from real payloads and asks, rather than
     * shipping a vendor as a product default.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean seenAndAllowed(String dataOrigin, String recordingMethod, LocalDateTime now) {
        SyncSource source = repository.findById(new SyncSource.Key(dataOrigin, recordingMethod))
                .orElseGet(() -> {
                    SyncSource created = new SyncSource();
                    created.setDataOrigin(dataOrigin);
                    created.setRecordingMethod(recordingMethod);
                    created.setAllowed(false);
                    created.setFirstSeen(now);
                    return created;
                });

        source.setLastSeen(now);
        repository.save(source);
        return source.isAllowed();
    }
}
