package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.WorkoutType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutTypeRepository extends JpaRepository<WorkoutType, Long> {

    Optional<WorkoutType> findByNameIgnoreCase(String name);

    List<WorkoutType> findByPendingReviewTrue();
}
