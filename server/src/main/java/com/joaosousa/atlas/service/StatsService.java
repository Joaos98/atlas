package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.BodyCompositionStatsDto;
import com.joaosousa.atlas.dto.StatsDto;
import com.joaosousa.atlas.dto.StreakDto;
import com.joaosousa.atlas.dto.WorkoutStatsDto;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatsService {

    private final WorkoutLogRepository workoutLogRepository;
    private final BodyMetricsRepository bodyMetricsRepository;
    private final WorkoutLogService workoutLogService;

    public StatsService(WorkoutLogRepository workoutLogRepository,
                        BodyMetricsRepository bodyMetricsRepository,
                        WorkoutLogService workoutLogService) {
        this.workoutLogRepository = workoutLogRepository;
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.workoutLogService = workoutLogService;
    }

    public StatsDto getStats(int year, int month) {
        // date ranges
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        // workout stats
        WorkoutStatsDto workoutStats = new WorkoutStatsDto(
                workoutLogRepository.countWorkouts(monthStart, monthEnd),
                workoutLogRepository.countWorkouts(yearStart, yearEnd),
                workoutLogRepository.maxDuration(yearStart, yearEnd),
                workoutLogRepository.maxDuration(monthStart, monthEnd),
                workoutLogRepository.averageDuration(monthStart, monthEnd),
                workoutLogRepository.averageDuration(yearStart, yearEnd),
                workoutLogRepository.mostFrequentType(monthStart, monthEnd),
                workoutLogRepository.mostFrequentType(yearStart, yearEnd),
                workoutLogRepository.sumDuration(monthStart, monthEnd) != null
                        ? workoutLogRepository.sumDuration(monthStart, monthEnd) : 0L,
                workoutLogRepository.sumDuration(yearStart, yearEnd) != null
                        ? workoutLogRepository.sumDuration(yearStart, yearEnd) : 0L
        );

        // body composition stats
        List<BodyMetrics> metrics = bodyMetricsRepository.findAllByOrderByMeasuredOnAsc();
        BodyCompositionStatsDto bodyStats = getBodyStats(metrics);

        // streak stats — reuse existing logic
        StreakDto streakStats = workoutLogService.calculateStreaks();

        return new StatsDto(workoutStats, bodyStats, streakStats);
    }

    private static @NonNull BodyCompositionStatsDto getBodyStats(List<BodyMetrics> metrics) {
        BodyCompositionStatsDto bodyStats;
        if (metrics.size() < 2) {
            bodyStats = new BodyCompositionStatsDto(null, null, null, metrics.size());
        } else {
            BodyMetrics first = metrics.getFirst();
            BodyMetrics latest = metrics.getLast();
            bodyStats = new BodyCompositionStatsDto(
                    latest.getWeightKg() - first.getWeightKg(),
                    latest.getMuscleMassKg() - first.getMuscleMassKg(),
                    latest.getBodyFatPct() - first.getBodyFatPct(),
                    metrics.size()
            );
        }
        return bodyStats;
    }
}