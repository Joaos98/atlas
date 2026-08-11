package com.joaosousa.atlas.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WorkoutStatsDto {
    private long totalWorkoutsThisMonth;
    private long totalWorkoutsThisYear;
    private Integer longestSessionEver;
    private Integer longestSessionThisMonth;
    private Double averageDurationThisMonth;
    private Double averageDurationThisYear;
    private String mostFrequentTypeThisMonth;
    private String mostFrequentTypeThisYear;
    private long totalMinutesThisMonth;
    private long totalMinutesThisYear;
}
