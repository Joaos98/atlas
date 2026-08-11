package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingsService {

    public static final long SETTINGS_ID = 1L;
    public static final int DEFAULT_TARGET_WORKOUTS_PER_WEEK = 3;

    private final AppSettingsRepository appSettingsRepository;

    public AppSettingsService(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @Transactional
    public void ensureSeeded() {
        if (appSettingsRepository.existsById(SETTINGS_ID)) {
            return;
        }
        appSettingsRepository.insertWithId(SETTINGS_ID, DEFAULT_TARGET_WORKOUTS_PER_WEEK);
    }

    public AppSettings get() {
        return appSettingsRepository.findById(SETTINGS_ID).orElseThrow();
    }

    public AppSettings update(AppSettings appSettings) {
        AppSettings existing = appSettingsRepository.findById(SETTINGS_ID).orElseThrow();
        existing.setTargetWorkoutsPerWeek(appSettings.getTargetWorkoutsPerWeek());
        return appSettingsRepository.save(existing);
    }
}
