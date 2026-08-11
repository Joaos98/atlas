package com.joaosousa.atlas.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StreakDto {
    private int currentStreak;
    private int longestStreak;
}