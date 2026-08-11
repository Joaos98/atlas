package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "app_settings")
public class AppSettings {
    public static final long SETTINGS_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_workouts_per_week")
    private int targetWorkoutsPerWeek;
}
