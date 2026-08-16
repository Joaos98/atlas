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
     */
    @Column(name = "pending_review")
    private boolean pendingReview;
}
