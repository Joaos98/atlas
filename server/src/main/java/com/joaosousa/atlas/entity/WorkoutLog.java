package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * <b>The unique index on {@code sync_signature} is created by {@link
 * com.joaosousa.atlas.service.SyncSignatureIndex}, not by an annotation here.</b>
 *
 * <p>{@code @Table(uniqueConstraints = ...)} was tried first and does nothing on SQLite:
 * Hibernate emits it as {@code ALTER TABLE ... ADD CONSTRAINT}, which SQLite does not support,
 * and {@code ddl-auto=update} logs the failure and carries on — leaving a schema that looks
 * annotated but has no constraint. See the "Sources and quarantine" design note on the
 * Saturn docs hub.
 */
@Entity
@Getter
@Setter
@Table(name = "workout_logs")
public class WorkoutLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_type_id")
    private WorkoutType workoutType;

    @Column(name = "log_date")
    private LocalDate logDate;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "sync_signature", length = 100)
    private String syncSignature;
}
