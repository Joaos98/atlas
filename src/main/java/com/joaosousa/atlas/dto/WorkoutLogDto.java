package com.joaosousa.atlas.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WorkoutLogDto {
    private String type;
    private String colorHex;
    private Integer durationMinutes;
}
