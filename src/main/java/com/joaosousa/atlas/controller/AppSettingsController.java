package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.service.AppSettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    public AppSettingsController(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @GetMapping
    public AppSettings get() {
        return appSettingsService.get();
    }

    @PutMapping
    public AppSettings update(@RequestBody AppSettings appSettings) {
        return appSettingsService.update(appSettings);
    }
}