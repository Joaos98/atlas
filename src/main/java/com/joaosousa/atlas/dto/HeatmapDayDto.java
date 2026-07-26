package com.joaosousa.atlas.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class HeatmapDayDto {
    private LocalDate date;
    private List<WorkoutLogDto> workouts;
}