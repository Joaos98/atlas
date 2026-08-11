package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.GoalProgressDto;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.entity.Goal;
import com.joaosousa.atlas.entity.GoalStatus;
import com.joaosousa.atlas.entity.MetricType;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.repository.GoalRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class GoalService {
    private final GoalRepository goalRepository;
    private final BodyMetricsRepository bodyMetricsRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, BodyMetricsRepository bodyMetricsRepository, Clock clock) {
        this.goalRepository = goalRepository;
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.clock = clock;
    }

    public Goal save(Goal goal) {
        goal.setCreatedAt(LocalDateTime.now(clock));

        List<BodyMetrics> metrics = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn"));
        if (!metrics.isEmpty()) {
            BodyMetrics latest = metrics.get(0);
            goal.setStartValue(getMetricValue(latest, goal.getMetricType()));
        }

        return goalRepository.save(goal);
    }

    public List<Goal> findAll() {
        return goalRepository.findAll();
    }

    public Goal updateStatus(Long id, GoalStatus status) {
        Goal goal = goalRepository.findById(id).orElseThrow();
        goal.setStatus(status);
        return goalRepository.save(goal);
    }

    public void deleteGoal(Long id) {
        goalRepository.deleteById(id);
    }

    public List<GoalProgressDto> findAllWithProgress() {
        List<Goal> goals = goalRepository.findAll();
        List<BodyMetrics> metrics = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.ASC, "measuredOn"));

        // Build latest value per metric type
        Map<MetricType, Double> latestValues = new HashMap<>();
        if (!metrics.isEmpty()) {
            BodyMetrics latest = metrics.get(metrics.size() - 1);
            for (MetricType type : MetricType.values()) {
                latestValues.put(type, getMetricValue(latest, type));
            }
        }

        List<GoalProgressDto> result = new ArrayList<>();
        for (Goal g : goals) {
            Double current = latestValues.getOrDefault(g.getMetricType(), null);

            Double progressPercent = null;
            String eta = null;
            String paceStatus = null;

            if ("ACTIVE".equals(g.getStatus().name()) && g.getStartValue() != null && current != null) {
                double start = g.getStartValue();
                double target = g.getTargetValue();
                boolean isDown = isDownGoal(g.getMetricType(), start, target);

                // Progress %
                if (Math.abs(target - start) > 0.001) {
                    double pct = ((current - start) / (target - start)) * 100;
                    progressPercent = Math.max(0, Math.min(100, pct));
                }

                // Already reached?
                boolean reached = Math.abs(current - target) < 0.05
                        || (isDown ? current <= target : current >= target);

                if (!reached) {
                    // ETA (only for goals without a target date)
                    if (g.getTargetDate() == null) {
                        eta = computeEta(g.getCreatedAt(), start, current, target);
                    } else {
                        // Pace check for dated goals
                        paceStatus = computePace(g.getTargetDate(), g.getCreatedAt(), start, current, target);
                    }
                }
            }

            result.add(new GoalProgressDto(
                    g.getId(),
                    g.getMetricType().name(),
                    g.getTargetValue(),
                    g.getTargetDate(),
                    g.getStatus().name(),
                    g.getCreatedAt(),
                    g.getStartValue(),
                    current,
                    progressPercent,
                    eta,
                    paceStatus
            ));
        }
        return result;
    }

    // --- helpers ---

    private Double getMetricValue(BodyMetrics bm, MetricType type) {
        return switch (type) {
            case WEIGHT -> bm.getWeightKg();
            case MUSCLE_MASS -> bm.getMuscleMassKg();
            case WATER -> bm.getWaterLiters();
            case BODY_FAT_KG -> bm.getBodyFatKg();
            case BODY_FAT_PCT -> bm.getBodyFatPct();
        };
    }

    private boolean isDownGoal(MetricType type, double start, double target) {
        return switch (type) {
            case BODY_FAT_KG, BODY_FAT_PCT -> true;
            case MUSCLE_MASS -> false;
            case WEIGHT, WATER -> target < start;
        };
    }

    private String computeEta(LocalDateTime createdAt, double start, double current, double target) {
        if (createdAt == null) return null;
        long daysElapsed = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now(clock));
        if (daysElapsed < 1) return null;

        double ratePerDay = (current - start) / daysElapsed;
        double remaining = target - current;

        if (Math.abs(ratePerDay) < 0.0001) return null;
        if (Math.signum(ratePerDay) != Math.signum(remaining)) return null;

        long daysRemaining = Math.round(remaining / ratePerDay);
        if (daysRemaining <= 0 || daysRemaining > 365 * 5) return null;

        return LocalDate.now(clock).plusDays(daysRemaining).toString();
    }

    private String computePace(LocalDate targetDate, LocalDateTime createdAt,
                               double start, double current, double target) {
        if (createdAt == null || targetDate == null) return null;
        long daysElapsed = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now(clock));
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(clock), targetDate);

        if (daysElapsed < 1 || daysRemaining < 1) return null;
        if (Math.abs(target - current) < 0.05) return null;

        double requiredRatePerDay = (target - current) / daysRemaining;
        double actualRatePerDay = (current - start) / daysElapsed;
        double neededDirection = Math.signum(target - start);

        if (Math.abs(actualRatePerDay) < 0.0001) return "behind";
        boolean onTrack = Math.abs(actualRatePerDay) >= Math.abs(requiredRatePerDay)
                && Math.signum(actualRatePerDay) == neededDirection;

        return onTrack ? "on_track" : "behind";
    }
}
