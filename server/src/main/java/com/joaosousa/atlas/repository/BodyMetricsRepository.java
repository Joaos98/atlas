package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.BodyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BodyMetricsRepository extends JpaRepository<BodyMetrics, Long> {
    List<BodyMetrics> findAllByOrderByMeasuredOnAsc();
}
