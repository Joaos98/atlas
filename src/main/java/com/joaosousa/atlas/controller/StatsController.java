package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.StatsDto;
import com.joaosousa.atlas.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsDto getStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        int resolvedYear = year != null ? year : LocalDate.now().getYear();
        int resolvedMonth = month != null ? month : LocalDate.now().getMonthValue();

        return statsService.getStats(resolvedYear, resolvedMonth);
    }
}
