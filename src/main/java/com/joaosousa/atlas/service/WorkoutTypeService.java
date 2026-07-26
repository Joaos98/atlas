package com.joaosousa.atlas.service;

import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class WorkoutTypeService {
    private final WorkoutTypeRepository workoutTypeRepository;
    private final WorkoutLogRepository workoutLogRepository;

    public WorkoutTypeService(WorkoutTypeRepository workoutTypeRepository, WorkoutLogRepository workoutLogRepository) {
        this.workoutTypeRepository = workoutTypeRepository;
        this.workoutLogRepository = workoutLogRepository;
    }

    public WorkoutType save(WorkoutType workoutType) {
        return workoutTypeRepository.save(workoutType);
    }

    public List<WorkoutType> findAll() {
        return workoutTypeRepository.findAll();
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
