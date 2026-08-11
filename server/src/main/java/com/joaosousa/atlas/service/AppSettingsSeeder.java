package com.joaosousa.atlas.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsSeeder implements ApplicationRunner {

    private final AppSettingsService appSettingsService;

    public AppSettingsSeeder(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        appSettingsService.ensureSeeded();
    }
}
