package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "body_metrics")
public class BodyMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "measured_on")
    private LocalDate measuredOn;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "muscle_mass_kg")
    private Double muscleMassKg;

    @Column(name = "water_liters")
    private Double waterLiters;

    @Column(name = "body_fat_kg")
    private Double bodyFatKg;

    @Column(name = "body_fat_pct")
    private Double bodyFatPct;

    @Column(name = "insight_text", columnDefinition = "TEXT")
    private String insightText;

    @Column(name = "insight_generated_at")
    private LocalDateTime insightGeneratedAt;
}
