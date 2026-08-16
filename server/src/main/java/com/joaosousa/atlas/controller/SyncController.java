package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.dto.SyncResponse;
import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.service.SyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final String syncApiKey;

    public SyncController(SyncService syncService,
                          @Value("${app.sync.api-key}") String syncApiKey) {
        this.syncService = syncService;
        this.syncApiKey = syncApiKey;
    }

    @PostMapping
    public SyncResponse sync(@RequestBody SyncRequest request,
                             @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (apiKey == null || !MessageDigest.isEqual(syncApiKey.getBytes(UTF_8), apiKey.getBytes(UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
        }
        return syncService.sync(request);
    }

    @GetMapping("/mappings")
    public List<ExerciseTypeMapping> getMappings() {
        return syncService.getMappings();
    }

    @PostMapping("/mappings")
    public ExerciseTypeMapping addMapping(@RequestBody Map<String, Object> body) {
        int healthConnectType = ((Number) body.get("healthConnectType")).intValue();
        long workoutTypeId = ((Number) body.get("workoutTypeId")).longValue();
        return syncService.addMapping(healthConnectType, workoutTypeId);
    }

    @DeleteMapping("/mappings/{healthConnectType}")
    public void deleteMapping(@PathVariable int healthConnectType) {
        syncService.deleteMapping(healthConnectType);
    }
}
