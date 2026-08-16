package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.InsightResponse;
import com.joaosousa.atlas.dto.InsightState;
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

        // Nothing generated yet and nothing to generate with: a fresh install's normal state.
        if (latest.getInsightText() == null && !insightService.isProviderConfigured()) {
            return new InsightResponse(null, InsightService.NOT_CONFIGURED_MESSAGE, null, InsightState.NOT_CONFIGURED);
        }

        InsightService.ParsedInsight parsed = InsightService.parseRawText(latest.getInsightText());
        // Only successful generations are ever stored (see below), so what is here is genuine.
        return new InsightResponse(
            parsed.verdict(),
            parsed.text(),
            latest.getInsightGeneratedAt(),
            InsightState.OK
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

        if (InsightService.applyIfGenerated(entry, result)) {
            bodyMetricsRepository.save(entry);
        }

        return new InsightResponse(result.verdict(), result.text(), result.generatedAt(), result.state());
    }
}
