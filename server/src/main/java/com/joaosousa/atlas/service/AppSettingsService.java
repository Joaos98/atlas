package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.AppSettingsUpdateRequest;
import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.entity.UnitSystem;
import com.joaosousa.atlas.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingsService {

    public static final int DEFAULT_TARGET_WORKOUTS_PER_WEEK = 3;

    /** Gemini's OpenAI-compatible endpoint. The provider is a setting, not a code path. */
    public static final String DEFAULT_INSIGHT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    public static final String DEFAULT_INSIGHT_MODEL = "gemini-3.5-flash";

    /** Metric by default, so an existing install renders exactly as it did before. */
    public static final UnitSystem DEFAULT_UNIT_SYSTEM = UnitSystem.METRIC;

    private final AppSettingsRepository appSettingsRepository;

    public AppSettingsService(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    /**
     * Converges the singleton settings row to a complete one, whether it is missing entirely
     * or merely missing columns that {@code ddl-auto=update} has just added. Both halves are
     * needed: a fresh install has no row, while an existing install has one that the insert
     * would skip — leaving every new column NULL on precisely the databases that hold real
     * data. Idempotent, so it runs on every startup via {@link AppSettingsSeeder}.
     */
    @Transactional
    public void ensureSeeded() {
        if (!appSettingsRepository.existsById(AppSettings.SETTINGS_ID)) {
            appSettingsRepository.insertWithId(AppSettings.SETTINGS_ID, DEFAULT_TARGET_WORKOUTS_PER_WEEK);
        }
        appSettingsRepository.backfillDefaults(
                AppSettings.SETTINGS_ID, DEFAULT_INSIGHT_BASE_URL, DEFAULT_INSIGHT_MODEL,
                DEFAULT_UNIT_SYSTEM.name());
    }

    public AppSettings get() {
        return appSettingsRepository.findById(AppSettings.SETTINGS_ID).orElseThrow();
    }

    /**
     * Applies only the fields the request actually carried. See
     * {@link AppSettingsUpdateRequest} for why absent means unchanged.
     */
    @Transactional
    public AppSettings update(AppSettingsUpdateRequest request) {
        AppSettings existing = appSettingsRepository.findById(AppSettings.SETTINGS_ID).orElseThrow();

        if (request.getTargetWorkoutsPerWeek() != null) {
            existing.setTargetWorkoutsPerWeek(request.getTargetWorkoutsPerWeek());
        }
        if (request.getInsightBaseUrl() != null) {
            existing.setInsightBaseUrl(request.getInsightBaseUrl().trim());
        }
        if (request.getInsightModel() != null) {
            existing.setInsightModel(request.getInsightModel().trim());
        }
        if (request.getUnitSystem() != null) {
            existing.setUnitSystem(request.getUnitSystem());
        }

        if (request.isClearInsightApiKey()) {
            existing.setInsightApiKey("");
        } else if (request.getInsightApiKey() != null && !request.getInsightApiKey().isBlank()) {
            // Trimmed because a pasted key routinely arrives with whitespace attached.
            existing.setInsightApiKey(request.getInsightApiKey().trim());
        }

        return appSettingsRepository.save(existing);
    }
}
