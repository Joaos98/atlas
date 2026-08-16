package com.joaosousa.atlas.dto;

/**
 * A workout type sync created on its own, with how much has landed in it.
 *
 * <p>The count is what makes the dashboard notice actionable: "New activity type added:
 * Elliptical (3 workouts)" tells you whether a grouping has quietly started fragmenting, where
 * a bare name would not.
 */
public record PendingReviewTypeDto(Long id, String name, String colorHex, long logCount) {
}
