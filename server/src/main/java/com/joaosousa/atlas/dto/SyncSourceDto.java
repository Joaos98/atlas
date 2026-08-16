package com.joaosousa.atlas.dto;

import java.time.LocalDateTime;

/**
 * A device or app Atlas has received workouts from, as shown in Settings.
 *
 * <p>{@code quarantinedCount} is derived from the quarantine table rather than stored on the
 * source, so there is one source of truth. A count that climbs on every sync is the visible
 * signature of a source whose timestamps drift — which is exactly what the allow-list exists
 * to keep out.
 */
public record SyncSourceDto(
        String dataOrigin,
        String recordingMethod,
        boolean allowed,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        long quarantinedCount) {
}
