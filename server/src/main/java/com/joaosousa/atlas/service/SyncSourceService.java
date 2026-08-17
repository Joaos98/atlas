package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.HeldEntryDto;
import com.joaosousa.atlas.dto.SyncSourceDto;
import com.joaosousa.atlas.entity.QuarantinedEntry;
import com.joaosousa.atlas.entity.SyncSource;
import com.joaosousa.atlas.repository.QuarantinedEntryRepository;
import com.joaosousa.atlas.repository.SyncSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncSourceService {

    private static final Logger log = LoggerFactory.getLogger(SyncSourceService.class);

    private final SyncSourceRepository sourceRepository;
    private final QuarantinedEntryRepository quarantineRepository;
    private final SyncService syncService;

    public SyncSourceService(SyncSourceRepository sourceRepository,
                             QuarantinedEntryRepository quarantineRepository,
                             SyncService syncService) {
        this.sourceRepository = sourceRepository;
        this.quarantineRepository = quarantineRepository;
        this.syncService = syncService;
    }

    public List<SyncSourceDto> list() {
        Map<SyncSource.Key, Long> counts = new HashMap<>();
        for (Object[] row : quarantineRepository.countGroupedBySource()) {
            counts.put(new SyncSource.Key((String) row[0], (String) row[1]), (Long) row[2]);
        }

        return sourceRepository.findAllByOrderByFirstSeenAsc().stream()
                .map(source -> new SyncSourceDto(
                        source.getDataOrigin(),
                        source.getRecordingMethod(),
                        source.isAllowed(),
                        source.getFirstSeen(),
                        source.getLastSeen(),
                        counts.getOrDefault(
                                new SyncSource.Key(source.getDataOrigin(), source.getRecordingMethod()), 0L)))
                .toList();
    }

    /**
     * Enabling replays everything held for this source; disabling stops future logging and
     * deliberately leaves existing workouts alone. Symmetric and non-destructive — turning a
     * source off is not a statement that its history was wrong.
     *
     * <p>Not transactional: replay inserts through the same per-entry path as a live sync, each
     * in its own transaction, so one bad entry cannot roll back the rest.
     */
    public ReplayResult setAllowed(String dataOrigin, String recordingMethod, boolean allowed) {
        SyncSource source = sourceRepository.findById(new SyncSource.Key(dataOrigin, recordingMethod))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown sync source"));

        source.setAllowed(allowed);
        sourceRepository.save(source);

        if (!allowed) {
            return new ReplayResult(0, 0, 0);
        }
        return replay(dataOrigin, recordingMethod);
    }

    /**
     * Idempotent by construction: the signature constraint absorbs anything already inserted,
     * so a partial failure can simply be retried. Every row is removed from quarantine either
     * way — an entry that is now a workout, a duplicate of one, or unusable has no reason to
     * stay held.
     */
    private ReplayResult replay(String dataOrigin, String recordingMethod) {
        List<QuarantinedEntry> held =
                quarantineRepository.findByDataOriginAndRecordingMethod(dataOrigin, recordingMethod);

        int created = 0;
        int duplicates = 0;
        int malformed = 0;

        for (QuarantinedEntry entry : held) {
            SyncService.EntryOutcome outcome = syncService.logEntry(
                    entry.getType(), entry.getStartTime(),
                    entry.getDurationSeconds() == null ? 0 : entry.getDurationSeconds(),
                    null);

            switch (outcome) {
                case CREATED -> created++;
                case DUPLICATE -> duplicates++;
                case MALFORMED -> {
                    malformed++;
                    log.warn("Discarding unusable quarantined entry {} from {}/{}: type={}, start_time={}",
                            entry.getId(), dataOrigin, recordingMethod, entry.getType(), entry.getStartTime());
                }
                // An ignored activity still leaves quarantine: the user has decided about it.
                case IGNORED -> duplicates++;
            }
            quarantineRepository.delete(entry);
        }

        log.info("Enabled sync source {}/{}: replayed {} entries — {} logged, {} already present, {} unusable",
                dataOrigin, recordingMethod, held.size(), created, duplicates, malformed);
        return new ReplayResult(created, duplicates, malformed);
    }

    /** Oldest first, which is the order they would replay in. */
    public List<HeldEntryDto> heldEntries(String dataOrigin, String recordingMethod) {
        return quarantineRepository.findByDataOriginAndRecordingMethod(dataOrigin, recordingMethod).stream()
                .sorted(Comparator.comparing(QuarantinedEntry::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(entry -> new HeldEntryDto(
                        entry.getId(),
                        activityName(entry.getType()),
                        entry.getType(),
                        entry.getStartTime(),
                        entry.getDurationSeconds() == null ? null
                                : (int) Math.ceil(entry.getDurationSeconds() / 60.0),
                        entry.getReceivedAt()))
                .toList();
    }

    /** A held entry is unparsed, so its type may not be a number at all. */
    private static String activityName(String rawType) {
        try {
            return ExerciseTypeCatalog.nameFor(Integer.parseInt(rawType));
        } catch (NumberFormatException e) {
            return "Unrecognised activity";
        }
    }

    @Transactional
    public void dismissQuarantine(String dataOrigin, String recordingMethod) {
        quarantineRepository.deleteByDataOriginAndRecordingMethod(dataOrigin, recordingMethod);
    }

    public long totalQuarantined() {
        return quarantineRepository.count();
    }

    public record ReplayResult(int created, int alreadyPresent, int unusable) {}
}
