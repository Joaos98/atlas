package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "workout_types")
public class WorkoutType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "color_hex")
    private String colorHex;

    /**
     * Set when sync created this type on its own, so the dashboard can announce it.
     *
     * <p>Auto-creation has to be visible the day it starts rather than discovered months later
     * as an unexplained extra slice on a chart: a grouping type like "Cardio" cannot know it is
     * a grouping, so the first activity that falls outside it silently splits the history.
     * Cleared by merge, rename, ignore, or dismiss — action-based, not time-based.
     *
     * <p>Declared as {@link Boolean}, not {@code boolean}, and read through
     * {@link #isPendingReview()}. {@code ddl-auto=update} adds a new column as NULL to every
     * row that already exists, and Hibernate refuses to assign NULL to a primitive — which
     * took out {@code /api/workout-types} and, because every log fetches its type,
     * {@code /api/workout-logs} with it. A fresh install never sees the state, so only an
     * upgrade exposes it.
     */
    @Column(name = "pending_review")
    private Boolean pendingReview;

    /** NULL means "existed before this column did", which is not awaiting review. */
    public boolean isPendingReview() {
        return Boolean.TRUE.equals(pendingReview);
    }
}
