package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.StatsDto;
import com.joaosousa.atlas.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;
    private final Clock clock;

    public StatsController(StatsService statsService, Clock clock) {
        this.statsService = statsService;
        this.clock = clock;
    }

    @GetMapping
    public StatsDto getStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        int resolvedYear = year != null ? year : LocalDate.now(clock).getYear();
        int resolvedMonth = month != null ? month : LocalDate.now(clock).getMonthValue();

        return statsService.getStats(resolvedYear, resolvedMonth);
    }
}
