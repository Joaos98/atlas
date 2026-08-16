package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.dto.SyncResponse;
import com.joaosousa.atlas.dto.SyncSourceDto;
import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.service.ExerciseTypeCatalog;
import com.joaosousa.atlas.service.SyncService;
import com.joaosousa.atlas.service.SyncSourceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final SyncSourceService syncSourceService;
    private final String syncApiKey;

    public SyncController(SyncService syncService,
                          SyncSourceService syncSourceService,
                          @Value("${app.sync.api-key}") String syncApiKey) {
        this.syncService = syncService;
        this.syncSourceService = syncSourceService;
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
        // A null workoutTypeId is meaningful: it maps the activity to nothing, which means
        // "never log this". It is not a missing field.
        Number workoutTypeId = (Number) body.get("workoutTypeId");
        return syncService.addMapping(healthConnectType,
                workoutTypeId == null ? null : workoutTypeId.longValue());
    }

    @DeleteMapping("/mappings/{healthConnectType}")
    public void deleteMapping(@PathVariable int healthConnectType) {
        syncService.deleteMapping(healthConnectType);
    }

    /** The static Health Connect vocabulary, so Settings can offer names instead of raw codes. */
    @GetMapping("/exercise-types")
    public List<Map<String, Object>> exerciseTypes() {
        return ExerciseTypeCatalog.all().entrySet().stream()
                .map(e -> Map.<String, Object>of("code", e.getKey(), "name", e.getValue()))
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .toList();
    }

    @GetMapping("/sources")
    public List<SyncSourceDto> getSources() {
        return syncSourceService.list();
    }

    /**
     * Origins are package names, so they arrive URL-encoded. Enabling replays whatever was held
     * for the source and reports what that produced.
     */
    @PutMapping("/sources/{origin}/{method}")
    public SyncSourceService.ReplayResult setSourceAllowed(@PathVariable String origin,
                                                           @PathVariable String method,
                                                           @RequestBody Map<String, Boolean> body) {
        boolean allowed = Boolean.TRUE.equals(body.get("allowed"));
        return syncSourceService.setAllowed(decode(origin), decode(method), allowed);
    }

    @DeleteMapping("/sources/{origin}/{method}/quarantine")
    public void dismissQuarantine(@PathVariable String origin, @PathVariable String method) {
        syncSourceService.dismissQuarantine(decode(origin), decode(method));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
