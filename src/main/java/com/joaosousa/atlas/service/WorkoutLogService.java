package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.HeatmapDayDto;
import com.joaosousa.atlas.dto.StreakDto;
import com.joaosousa.atlas.dto.WorkoutLogDto;
import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.repository.AppSettingsRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkoutLogService {
    private final WorkoutLogRepository workoutLogRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final ZoneId appZone;

    public WorkoutLogService(WorkoutLogRepository workoutLogRepository,
                             AppSettingsRepository appSettingsRepository,
                             @Value("${app.timezone}") ZoneId appZone) {
        this.workoutLogRepository = workoutLogRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.appZone = appZone;
    }

    public WorkoutLog save(WorkoutLog workoutLog) {
        return workoutLogRepository.save(workoutLog);
    }

    public Page<WorkoutLog> findAll(Pageable pageable) {
        return workoutLogRepository.findAll(pageable);
    }

    public void delete(Long id) {
        workoutLogRepository.deleteById(id);
    }

    public WorkoutLog updateWorkoutLog(Long id, WorkoutLog updated) {
        WorkoutLog existing = workoutLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        existing.setLogDate(updated.getLogDate());
        existing.setWorkoutType(updated.getWorkoutType());
        existing.setDurationMinutes(updated.getDurationMinutes());

        return workoutLogRepository.save(existing);
    }

    public List<HeatmapDayDto> getHeatmapData(LocalDate startDate, LocalDate endDate) {
        List<WorkoutLog> logs = workoutLogRepository.findByDateRangeFetched(startDate, endDate);

        Map<LocalDate, List<WorkoutLog>> byDate = logs.stream()
                .collect(Collectors.groupingBy(WorkoutLog::getLogDate));

        return byDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<WorkoutLogDto> workoutDtos = entry.getValue().stream()
                            .map(wl -> new WorkoutLogDto(
                                    wl.getWorkoutType().getName(),
                                    wl.getWorkoutType().getColorHex(),
                                    wl.getDurationMinutes()
                            ))
                            .toList();

                    return new HeatmapDayDto(entry.getKey(), workoutDtos);
                })
                .toList();
    }

    public StreakDto calculateStreaks() {
        int target = appSettingsRepository.findById(1L)
                .orElseThrow()
                .getTargetWorkoutsPerWeek();

        List<LocalDate> allDates = workoutLogRepository.findDistinctWorkoutDates(
                LocalDate.of(2000, 1, 1), LocalDate.now(appZone)
        );

        if (allDates.isEmpty()) return new StreakDto(0, 0);

        Map<LocalDate, Long> countByWeek = allDates.stream()
                .collect(Collectors.groupingBy(
                        date -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)),
                        Collectors.counting()
                ));

        List<LocalDate> qualifyingWeeks = countByWeek.entrySet().stream()
                .filter(e -> e.getValue() >= target)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (qualifyingWeeks.isEmpty()) return new StreakDto(0, 0);

        int longest = 1;
        int current = 1;
        for (int i = 1; i < qualifyingWeeks.size(); i++) {
            LocalDate prev = qualifyingWeeks.get(i - 1);
            LocalDate curr = qualifyingWeeks.get(i);
            if (prev.plusWeeks(1).equals(curr)) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }

        LocalDate thisWeekSunday = LocalDate.now(appZone).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate lastWeekSunday = thisWeekSunday.minusWeeks(1);
        LocalDate mostRecentQualifying = qualifyingWeeks.getLast();

        int currentStreak;
        if (mostRecentQualifying.equals(thisWeekSunday) || mostRecentQualifying.equals(lastWeekSunday)) {
            currentStreak = 1;
            for (int i = qualifyingWeeks.size() - 2; i >= 0; i--) {
                if (qualifyingWeeks.get(i).plusWeeks(1).equals(qualifyingWeeks.get(i + 1))) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        } else {
            currentStreak = 0;
        }

        return new StreakDto(currentStreak, longest);
    }
}
