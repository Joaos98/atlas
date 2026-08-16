package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.ExerciseTypeMapping;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.ExerciseTypeMappingRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the bridge from Health Connect's vocabulary to a user's labels, on demand.
 *
 * <p>Before this existed, an exercise type with no hand-made mapping row was dropped, so a new
 * user had to discover that walking is code 79 before a single walk was ever logged. The
 * prerequisite was backwards: the app knows the vocabulary and was asking the user to reproduce
 * it. "Unmapped" is no longer a reason to skip anything.
 *
 * <p>An explicit mapping always wins — this only fills gaps — so existing installs need no
 * migration and no hand-made label is ever overwritten.
 */
@Component
public class WorkoutTypeAutoCreator {

    private static final Logger log = LoggerFactory.getLogger(WorkoutTypeAutoCreator.class);

    private final WorkoutTypeRepository workoutTypeRepository;
    private final ExerciseTypeMappingRepository mappingRepository;

    public WorkoutTypeAutoCreator(WorkoutTypeRepository workoutTypeRepository,
                                  ExerciseTypeMappingRepository mappingRepository) {
        this.workoutTypeRepository = workoutTypeRepository;
        this.mappingRepository = mappingRepository;
    }

    /**
     * Creates the type and its mapping for a code that has neither. Its own transaction, so a
     * failure here cannot poison the rest of the batch.
     *
     * <p>An existing type with the same name is reused rather than duplicated — otherwise a
     * hand-made "Walking" would sit beside an auto-created one, and the only repair would be a
     * merge the user has to notice they need.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkoutType createFor(int healthConnectType) {
        String name = ExerciseTypeCatalog.nameFor(healthConnectType);

        WorkoutType type = workoutTypeRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            WorkoutType created = new WorkoutType();
            created.setName(name);
            created.setColorHex(ExerciseTypeCatalog.colorFor((int) workoutTypeRepository.count()));
            created.setPendingReview(true);
            return workoutTypeRepository.save(created);
        });

        ExerciseTypeMapping mapping = new ExerciseTypeMapping();
        mapping.setHealthConnectType(healthConnectType);
        mapping.setWorkoutType(type);
        mappingRepository.save(mapping);

        log.info("New activity type from Health Connect code {}: '{}'", healthConnectType, name);
        return type;
    }
}
