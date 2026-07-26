package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.InsightResponse;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.service.InsightService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final BodyMetricsRepository bodyMetricsRepository;
    private final InsightService insightService;

    public InsightController(BodyMetricsRepository bodyMetricsRepository,
                             InsightService insightService) {
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.insightService = insightService;
    }

    @GetMapping
    public InsightResponse getLatest() {
        List<BodyMetrics> all = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn"));
        if (all.isEmpty()) {
            return null;
        }
        BodyMetrics latest = all.get(0);
        InsightService.ParsedInsight parsed = InsightService.parseRawText(latest.getInsightText());
        return new InsightResponse(
            parsed.verdict(),
            parsed.text(),
            latest.getInsightGeneratedAt(),
            false
        );
    }

    @PostMapping("/regenerate")
    public InsightResponse regenerate() {
        List<BodyMetrics> all = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn"));
        if (all.isEmpty()) {
            return null;
        }

        BodyMetrics entry = all.get(0);
        InsightService.InsightResult result = insightService.generateInsight(entry);

        String storedText = result.verdict() != null
                ? "VERDICT:" + result.verdict() + "\nINSIGHT:" + result.text()
                : result.text();
        entry.setInsightText(storedText);
        entry.setInsightGeneratedAt(result.generatedAt());
        bodyMetricsRepository.save(entry);

        return new InsightResponse(result.verdict(), result.text(), result.generatedAt(), result.fallback());
    }
}
