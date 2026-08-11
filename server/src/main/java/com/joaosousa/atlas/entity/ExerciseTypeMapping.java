package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "exercise_type_mapping")
public class ExerciseTypeMapping {

    @Id
    @Column(name = "health_connect_type")
    private Integer healthConnectType;

    @ManyToOne
    @JoinColumn(name = "workout_type_id")
    private WorkoutType workoutType;
}
