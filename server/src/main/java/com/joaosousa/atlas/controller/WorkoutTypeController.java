package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.service.WorkoutTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workout-types")
public class WorkoutTypeController {

    private final WorkoutTypeService workoutTypeService;

    public WorkoutTypeController(WorkoutTypeService workoutTypeService) {
        this.workoutTypeService = workoutTypeService;
    }

    @GetMapping
    public List<WorkoutType> findAll() {
        return workoutTypeService.findAll();
    }

    @PostMapping
    public WorkoutType save(@RequestBody WorkoutType workoutType) {
        return workoutTypeService.save(workoutType);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkoutType(@PathVariable Long id) {
        workoutTypeService.deleteWorkoutType(id);
        return ResponseEntity.noContent().build();
    }
}