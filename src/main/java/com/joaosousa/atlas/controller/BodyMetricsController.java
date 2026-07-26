package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.service.BodyMetricsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/body-metrics")
public class BodyMetricsController {

    private final BodyMetricsService bodyMetricsService;

    public BodyMetricsController(BodyMetricsService bodyMetricsService) {
        this.bodyMetricsService = bodyMetricsService;
    }

    @GetMapping
    public Page<BodyMetrics> findAll(
            @PageableDefault(size = Integer.MAX_VALUE, sort = "measuredOn", direction = Sort.Direction.ASC) Pageable pageable) {
        return bodyMetricsService.findAll(pageable);
    }

    @PostMapping
    public BodyMetrics save(@RequestBody BodyMetrics bodyMetrics) {
        return bodyMetricsService.save(bodyMetrics);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        bodyMetricsService.delete(id);
    }

    @PutMapping("/{id}")
    public BodyMetrics updateBodyMetrics(@PathVariable Long id, @RequestBody BodyMetrics updated) {
        return bodyMetricsService.updateBodyMetrics(id, updated);
    }
}