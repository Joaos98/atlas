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

    /**
     * Fills columns that are NULL because {@code ddl-auto=update} had just added them, leaving
     * every pre-existing row without a value. COALESCE is what makes this safe to run on every
     * startup: it only ever writes where nothing was written before, so a user's own edits and
     * a deliberately blank key both survive it.
     *
     * <p>This is the seeding path that matters for an install that already has the settings row
     * — the insert above never runs there.
     */
    @Modifying
    @Query(value = """
            UPDATE app_settings
               SET insight_base_url = COALESCE(insight_base_url, :baseUrl),
                   insight_model    = COALESCE(insight_model, :model),
                   insight_api_key  = COALESCE(insight_api_key, '')
             WHERE id = :id
            """, nativeQuery = true)
    void backfillDefaults(@Param("id") long id,
                          @Param("baseUrl") String baseUrl,
                          @Param("model") String model);
}
