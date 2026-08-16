package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseTypeMappingRepository extends JpaRepository<ExerciseTypeMapping, Integer> {

    /** Part of a merge. See WorkoutTypeService.merge — nothing else enforces this. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ExerciseTypeMapping m SET m.workoutType.id = :targetId WHERE m.workoutType.id = :sourceId")
    int reassignWorkoutType(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
