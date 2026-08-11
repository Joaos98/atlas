package com.joaosousa.atlas.repository;

import com.joaosousa.atlas.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {

    @Modifying
    @Query(value = "INSERT INTO app_settings (id, target_workouts_per_week) VALUES (:id, :target)", nativeQuery = true)
    void insertWithId(@Param("id") long id, @Param("target") int target);
}
