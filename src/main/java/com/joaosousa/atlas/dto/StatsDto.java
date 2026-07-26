package com.joaosousa.atlas.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatsDto {
    private WorkoutStatsDto workoutStats;
    private BodyCompositionStatsDto bodyCompositionStats;
    private StreakDto streakStats;
}
