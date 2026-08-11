package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.WorkoutType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutTypeRepository extends JpaRepository<WorkoutType, Long> {
}
