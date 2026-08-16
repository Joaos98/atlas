package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    boolean existsByWorkoutType(WorkoutType workoutType);

    long countByWorkoutTypeId(Long workoutTypeId);

    /**
     * Part of a merge. There is no foreign key on {@code workout_type_id}, so if this misses
     * rows nothing will complain — they simply point at a type that no longer exists.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorkoutLog wl SET wl.workoutType.id = :targetId WHERE wl.workoutType.id = :sourceId")
    int reassignWorkoutType(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    @Query("SELECT wl FROM WorkoutLog wl JOIN FETCH wl.workoutType WHERE wl.logDate BETWEEN :startDate AND :endDate ORDER BY wl.logDate")
    List<WorkoutLog> findByDateRangeFetched(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT DISTINCT wl.logDate FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    List<LocalDate> findDistinctWorkoutDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(DISTINCT wl.logDate) FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    int countDistinctWorkoutDates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(wl) FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    long countWorkouts(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT MAX(wl.durationMinutes) FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    Integer maxDuration(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT AVG(wl.durationMinutes) FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    Double averageDuration(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(wl.durationMinutes) FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate")
    Long sumDuration(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT wl.workoutType.name FROM WorkoutLog wl WHERE wl.logDate BETWEEN :startDate AND :endDate GROUP BY wl.workoutType.name ORDER BY COUNT(wl) DESC LIMIT 1")
    String mostFrequentType(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
