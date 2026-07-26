package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "workout_logs")
public class WorkoutLog {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workout_type_id")
    private WorkoutType workoutType;

    @Column(name = "log_date")
    private LocalDate logDate;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
