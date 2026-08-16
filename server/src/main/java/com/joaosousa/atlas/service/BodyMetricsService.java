package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BodyMetricsService {
    private final BodyMetricsRepository bodyMetricsRepository;
    private final InsightService insightService;

    public BodyMetricsService(BodyMetricsRepository bodyMetricsRepository, InsightService insightService) {
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.insightService = insightService;
    }

    public BodyMetrics save(BodyMetrics bodyMetrics) {
        BodyMetrics saved = bodyMetricsRepository.save(bodyMetrics);

        InsightService.InsightResult result = insightService.generateInsight(saved);
        if (InsightService.applyIfGenerated(saved, result)) {
            saved = bodyMetricsRepository.save(saved);
        }
        return saved;
    }

    /**
     * Unreferenced as of 2026-08-16 — no controller exposes it and nothing else calls it. The
     * frontend's regenerate goes to {@code POST /api/insights/regenerate} instead. Kept only
     * because a per-measurement regenerate is a plausible feature; delete it if that never comes.
     */
    public BodyMetrics regenerateInsight(Long id) {
        BodyMetrics entry = bodyMetricsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        InsightService.InsightResult result = insightService.generateInsight(entry);
        InsightService.applyIfGenerated(entry, result);

        return bodyMetricsRepository.save(entry);
    }

    public Page<BodyMetrics> findAll(Pageable pageable) {
        return bodyMetricsRepository.findAll(pageable);
    }

    public BodyMetrics updateBodyMetrics(Long id, BodyMetrics updated) {
        BodyMetrics existing = bodyMetricsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        existing.setMeasuredOn(updated.getMeasuredOn());
        existing.setWeightKg(updated.getWeightKg());
        existing.setMuscleMassKg(updated.getMuscleMassKg());
        existing.setWaterLiters(updated.getWaterLiters());
        existing.setBodyFatKg(updated.getBodyFatKg());
        existing.setBodyFatPct(updated.getBodyFatPct());

        return bodyMetricsRepository.save(existing);
    }

    public void delete(Long id) {
        bodyMetricsRepository.deleteById(id);
    }
}
