package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.dto.SyncResponse;
import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.ExerciseTypeMappingRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final String EXPECTED_ORIGIN = "com.xiaomi.wearable";
    private static final String EXPECTED_METHOD = "automatically_recorded";

    private final ExerciseTypeMappingRepository mappingRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final WorkoutTypeRepository workoutTypeRepository;

    public SyncService(ExerciseTypeMappingRepository mappingRepository,
                       WorkoutLogRepository workoutLogRepository,
                       WorkoutTypeRepository workoutTypeRepository) {
        this.mappingRepository = mappingRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.workoutTypeRepository = workoutTypeRepository;
    }

    public SyncResponse sync(SyncRequest request) {
        if (request.getExercise() == null || request.getExercise().isEmpty()) {
            return new SyncResponse(0, 0);
        }

        List<SyncRequest.ExerciseEntry> filtered = request.getExercise().stream()
                .filter(e -> e.getMetadata() != null
                        && EXPECTED_ORIGIN.equals(e.getMetadata().getData_origin())
                        && EXPECTED_METHOD.equals(e.getMetadata().getRecording_method()))
                .toList();

        Set<String> seen = new HashSet<>();
        int created = 0;
        int skipped = 0;

        for (SyncRequest.ExerciseEntry entry : filtered) {
            String dedupKey = entry.getStart_time() + "|" + entry.getDuration_seconds();
            if (!seen.add(dedupKey)) {
                skipped++;
                continue;
            }

            int healthConnectType;
            try {
                healthConnectType = Integer.parseInt(entry.getType());
            } catch (NumberFormatException e) {
                log.warn("Skipping exercise with non-numeric type: {}", entry.getType());
                skipped++;
                continue;
            }

            Optional<ExerciseTypeMapping> mapping = mappingRepository.findById(healthConnectType);
            if (mapping.isEmpty()) {
                log.info("Unmapped Health Connect exercise type: {}. Add a mapping to auto-log this type.", healthConnectType);
                skipped++;
                continue;
            }

            WorkoutType workoutType = mapping.get().getWorkoutType();
            LocalDate logDate = parseUtcDate(entry.getStart_time());
            int durationMinutes = (int) Math.ceil(entry.getDuration_seconds() / 60.0);

            boolean exists = workoutLogRepository.existsByLogDateAndWorkoutTypeAndDurationMinutes(
                    logDate, workoutType, durationMinutes);
            if (exists) {
                skipped++;
                continue;
            }

            WorkoutLog workoutLog = new WorkoutLog();
            workoutLog.setWorkoutType(workoutType);
            workoutLog.setLogDate(logDate);
            workoutLog.setDurationMinutes(durationMinutes);
            workoutLogRepository.save(workoutLog);
            created++;
        }

        log.info("Sync complete: {} created, {} skipped", created, skipped);
        return new SyncResponse(created, skipped);
    }

    public List<ExerciseTypeMapping> getMappings() {
        return mappingRepository.findAll();
    }

    public ExerciseTypeMapping addMapping(int healthConnectType, Long workoutTypeId) {
        WorkoutType workoutType = workoutTypeRepository.findById(workoutTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Workout type not found: " + workoutTypeId));

        ExerciseTypeMapping mapping = new ExerciseTypeMapping();
        mapping.setHealthConnectType(healthConnectType);
        mapping.setWorkoutType(workoutType);
        return mappingRepository.save(mapping);
    }

    public void deleteMapping(int healthConnectType) {
        mappingRepository.deleteById(healthConnectType);
    }

    private LocalDate parseUtcDate(String utcString) {
        Instant instant = Instant.parse(utcString);
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
