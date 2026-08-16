package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.SyncRequest;
import com.joaosousa.atlas.dto.SyncResponse;
import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.ExerciseTypeMappingRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final ExerciseTypeMappingRepository mappingRepository;
    private final WorkoutTypeRepository workoutTypeRepository;
    private final WorkoutLogInserter workoutLogInserter;
    private final SyncSourceRegistry sourceRegistry;
    private final QuarantineWriter quarantineWriter;
    private final WorkoutTypeAutoCreator autoCreator;
    private final ZoneId appZone;

    public SyncService(ExerciseTypeMappingRepository mappingRepository,
                       WorkoutTypeRepository workoutTypeRepository,
                       WorkoutLogInserter workoutLogInserter,
                       SyncSourceRegistry sourceRegistry,
                       QuarantineWriter quarantineWriter,
                       WorkoutTypeAutoCreator autoCreator,
                       @Value("${app.timezone}") ZoneId appZone) {
        this.mappingRepository = mappingRepository;
        this.workoutTypeRepository = workoutTypeRepository;
        this.workoutLogInserter = workoutLogInserter;
        this.sourceRegistry = sourceRegistry;
        this.quarantineWriter = quarantineWriter;
        this.autoCreator = autoCreator;
        this.appZone = appZone;
    }

    /**
     * Must not be annotated @Transactional: each insert runs in its own REQUIRES_NEW
     * transaction (WorkoutLogInserter) so a duplicate cannot poison the whole batch.
     */
    public SyncResponse sync(SyncRequest request) {
        if (request.getExercise() == null || request.getExercise().isEmpty()) {
            return new SyncResponse(0, 0);
        }

        LocalDateTime now = LocalDateTime.now(appZone);
        Set<String> seen = new HashSet<>();
        Map<SourceKey, Integer> rejectedBySource = new LinkedHashMap<>();
        int created = 0;
        int skipped = 0;
        int rejected = 0;
        int ignored = 0;

        for (SyncRequest.ExerciseEntry entry : request.getExercise()) {
            SyncRequest.MetadataEntry metadata = entry.getMetadata();
            String origin = SyncSourceRegistry.normalize(
                    metadata == null ? null : metadata.getData_origin());
            String method = SyncSourceRegistry.normalize(
                    metadata == null ? null : metadata.getRecording_method());

            // Recorded before it is judged, so an unknown source becomes visible in Settings
            // rather than vanishing. This replaced a hardcoded origin/method pair that filtered
            // every other device out before dedup, before logging, and before the response —
            // a user with any other watch got an empty app and no explanation.
            if (!sourceRegistry.seenAndAllowed(origin, method, now)) {
                // Never dropped: the sender transmits a delta, so a refused entry is not sent
                // again and rejecting without storing would lose it permanently.
                if (quarantineWriter.quarantine(entry, origin, method, now)) {
                    rejected++;
                    rejectedBySource.merge(new SourceKey(origin, method), 1, Integer::sum);
                }
                continue;
            }

            switch (logEntry(entry.getType(), entry.getStart_time(), entry.getDuration_seconds(), seen)) {
                case CREATED -> created++;
                case IGNORED -> ignored++;
                case DUPLICATE, MALFORMED -> skipped++;
            }
        }

        // One line per sync, so `docker logs` answers "why zero workouts" without the UI.
        if (rejectedBySource.isEmpty()) {
            log.info("Sync complete: {} created, {} skipped, {} ignored", created, skipped, ignored);
        } else {
            log.info("Sync complete: {} created, {} skipped, {} ignored, {} rejected from sources not enabled: {}",
                    created, skipped, ignored, rejected, rejectedBySource);
        }

        List<SyncResponse.RejectedSource> sources = rejectedBySource.entrySet().stream()
                .map(e -> new SyncResponse.RejectedSource(
                        e.getKey().origin(), e.getKey().method(), e.getValue()))
                .toList();

        return new SyncResponse(created, skipped, rejected, ignored, sources);
    }

    /**
     * What became of one entry. Shared by live sync and by replay, so an entry recovered from
     * quarantine goes through byte-for-byte the same path it would have taken had its source
     * been enabled at the time — no second implementation to drift.
     */
    enum EntryOutcome { CREATED, DUPLICATE, IGNORED, MALFORMED }

    /**
     * @param seen in-request duplicate guard; pass null when replaying, where each entry is
     *             already distinct and the database constraint is the only check that matters
     */
    EntryOutcome logEntry(String rawType, String rawStartTime, int durationSeconds, Set<String> seen) {
        int healthConnectType;
        try {
            healthConnectType = Integer.parseInt(rawType);
        } catch (NumberFormatException e) {
            log.warn("Skipping exercise with non-numeric type: {}", rawType);
            return EntryOutcome.MALFORMED;
        }

        Instant start;
        try {
            start = Instant.parse(rawStartTime);
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("Skipping exercise with unparseable start_time: {}", rawStartTime);
            return EntryOutcome.MALFORMED;
        }

        String signature = start.toEpochMilli() + "|" + healthConnectType;

        if (seen != null && !seen.add(signature)) {
            return EntryOutcome.DUPLICATE;
        }

        // The mapping row is consulted first, so an explicit label always beats the catalog and
        // an ignore row suppresses auto-create as well as logging.
        Optional<ExerciseTypeMapping> mapping = mappingRepository.findById(healthConnectType);
        WorkoutType workoutType;
        if (mapping.isPresent()) {
            workoutType = mapping.get().getWorkoutType();
            if (workoutType == null) {
                // A mapping to nothing means "never log this activity". Dropped rather than
                // quarantined: quarantine holds entries awaiting a decision, and this one has
                // been made — and the types people ignore are the high-volume ones.
                return EntryOutcome.IGNORED;
            }
        } else {
            workoutType = autoCreator.createFor(healthConnectType);
        }

        WorkoutLog workoutLog = new WorkoutLog();
        workoutLog.setWorkoutType(workoutType);
        workoutLog.setLogDate(start.atZone(appZone).toLocalDate());
        workoutLog.setDurationMinutes((int) Math.ceil(durationSeconds / 60.0));
        workoutLog.setSyncSignature(signature);

        try {
            workoutLogInserter.insert(workoutLog);
            return EntryOutcome.CREATED;
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate sync signature {}, skipping", signature);
            return EntryOutcome.DUPLICATE;
        }
    }

    /** Groups rejections for the response and the log line. */
    private record SourceKey(String origin, String method) {
        @Override
        public String toString() {
            return origin + "/" + method;
        }
    }

    public List<ExerciseTypeMapping> getMappings() {
        return mappingRepository.findAll();
    }

    /** A null {@code workoutTypeId} records an ignore rule: this activity is never logged. */
    public ExerciseTypeMapping addMapping(int healthConnectType, Long workoutTypeId) {
        WorkoutType workoutType = workoutTypeId == null ? null
                : workoutTypeRepository.findById(workoutTypeId)
                        .orElseThrow(() -> new IllegalArgumentException("Workout type not found: " + workoutTypeId));

        ExerciseTypeMapping mapping = new ExerciseTypeMapping();
        mapping.setHealthConnectType(healthConnectType);
        mapping.setWorkoutType(workoutType);
        return mappingRepository.save(mapping);
    }

    public void deleteMapping(int healthConnectType) {
        mappingRepository.deleteById(healthConnectType);
    }
}
