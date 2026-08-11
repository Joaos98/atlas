package com.joaosousa.atlas.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class GoalProgressDto {

    private final Long id;
    private final String metricType;
    private final Double targetValue;
    private final LocalDate targetDate;
    private final String status;
    private final LocalDateTime createdAt;
    private final Double startValue;

    // Computed
    private final Double currentValue;
    private final Double progressPercent;
    private final String eta;
    private final String paceStatus;

    public GoalProgressDto(Long id, String metricType, Double targetValue, LocalDate targetDate,
                           String status, LocalDateTime createdAt, Double startValue,
                           Double currentValue, Double progressPercent, String eta, String paceStatus) {
        this.id = id;
        this.metricType = metricType;
        this.targetValue = targetValue;
        this.targetDate = targetDate;
        this.status = status;
        this.createdAt = createdAt;
        this.startValue = startValue;
        this.currentValue = currentValue;
        this.progressPercent = progressPercent;
        this.eta = eta;
        this.paceStatus = paceStatus;
    }

    public Long getId() { return id; }
    public String getMetricType() { return metricType; }
    public Double getTargetValue() { return targetValue; }
    public LocalDate getTargetDate() { return targetDate; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Double getStartValue() { return startValue; }
    public Double getCurrentValue() { return currentValue; }
    public Double getProgressPercent() { return progressPercent; }
    public String getEta() { return eta; }
    public String getPaceStatus() { return paceStatus; }
}
