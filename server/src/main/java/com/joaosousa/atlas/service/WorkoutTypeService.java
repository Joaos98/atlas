package com.joaosousa.atlas.service;

import com.joaosousa.atlas.dto.PendingReviewTypeDto;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.ExerciseTypeMappingRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WorkoutTypeService {
    private final WorkoutTypeRepository workoutTypeRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final ExerciseTypeMappingRepository mappingRepository;

    public WorkoutTypeService(WorkoutTypeRepository workoutTypeRepository,
                              WorkoutLogRepository workoutLogRepository,
                              ExerciseTypeMappingRepository mappingRepository) {
        this.workoutTypeRepository = workoutTypeRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.mappingRepository = mappingRepository;
    }

    public WorkoutType save(WorkoutType workoutType) {
        return workoutTypeRepository.save(workoutType);
    }

    public List<WorkoutType> findAll() {
        return workoutTypeRepository.findAll();
    }

    /**
     * Reassigns everything belonging to {@code sourceId} onto {@code targetId}, then removes the
     * source type.
     *
     * <p>Necessary because auto-create makes near-duplicates likely — "Run" beside "Running", or
     * a grouping type fragmenting one activity at a time — and {@link #deleteWorkoutType} refuses
     * any type that has logs. Without merge, a duplicate would be permanent.
     *
     * <p><b>There are no foreign keys anywhere in this schema.</b> {@code workout_logs.workout_type_id}
     * is a bare bigint, so nothing at the database level will catch a merge that misses rows —
     * the two updates below have to be right unaided, and the test asserts on orphans directly
     * rather than waiting for an integrity error that cannot happen.
     */
    @Transactional
    public WorkoutType merge(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot merge a type into itself");
        }

        WorkoutType source = workoutTypeRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source workout type not found"));
        WorkoutType target = workoutTypeRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target workout type not found"));

        workoutLogRepository.reassignWorkoutType(sourceId, targetId);
        mappingRepository.reassignWorkoutType(sourceId, targetId);
        workoutTypeRepository.delete(source);

        // Merging is a review, so the target stops asking to be reviewed.
        if (target.isPendingReview()) {
            target.setPendingReview(false);
            workoutTypeRepository.save(target);
        }
        return target;
    }

    /** Renaming is also a review — the user has looked at it and decided what it is. */
    @Transactional
    public WorkoutType rename(Long id, String name) {
        WorkoutType type = workoutTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout type not found"));
        type.setName(name);
        type.setPendingReview(false);
        return workoutTypeRepository.save(type);
    }

    @Transactional
    public void dismissReview(Long id) {
        WorkoutType type = workoutTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout type not found"));
        type.setPendingReview(false);
        workoutTypeRepository.save(type);
    }

    public List<PendingReviewTypeDto> findPendingReview() {
        return workoutTypeRepository.findByPendingReviewTrue().stream()
                .map(type -> new PendingReviewTypeDto(
                        type.getId(), type.getName(), type.getColorHex(),
                        workoutLogRepository.countByWorkoutTypeId(type.getId())))
                .toList();
    }

    public void deleteWorkoutType(Long id) {
        WorkoutType type = workoutTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout type not found"));

        if (workoutLogRepository.existsByWorkoutType(type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete: this type has existing workout logs");
        }

        workoutTypeRepository.delete(type);
    }
}
