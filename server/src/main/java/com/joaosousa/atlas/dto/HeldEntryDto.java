package com.joaosousa.atlas.dto;

import java.time.LocalDateTime;

/**
 * A quarantined workout, named rather than coded, for the "what exactly is being held?" list.
 *
 * <p>A count alone asks the user to take Atlas's word for it before enabling a source. Showing
 * the workouts makes the decision an informed one — and makes a source whose timestamps drift
 * obvious, because the same session appears repeatedly at slightly different times.
 *
 * <p>{@code activity} is resolved through the catalog; {@code type} is kept alongside it because
 * an entry held from a future Health Connect release has a code and no name.
 */
public record HeldEntryDto(
        Long id,
        String activity,
        String type,
        String startTime,
        Integer durationMinutes,
        LocalDateTime receivedAt) {
}
