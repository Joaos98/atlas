package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.AppSettingsDto;
import com.joaosousa.atlas.dto.AppSettingsUpdateRequest;
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
    public AppSettingsDto get() {
        return AppSettingsDto.from(appSettingsService.get());
    }

    @PutMapping
    public AppSettingsDto update(@RequestBody AppSettingsUpdateRequest request) {
        return AppSettingsDto.from(appSettingsService.update(request));
    }
}
