package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {
}
