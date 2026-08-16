package com.joaosousa.atlas.controller;

import com.joaosousa.atlas.dto.PendingReviewTypeDto;
import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.service.WorkoutTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /** Types auto-created by sync, awaiting a look. Drives the dashboard notice. */
    @GetMapping("/pending-review")
    public List<PendingReviewTypeDto> pendingReview() {
        return workoutTypeService.findPendingReview();
    }

    @PostMapping("/{sourceId}/merge-into/{targetId}")
    public WorkoutType merge(@PathVariable Long sourceId, @PathVariable Long targetId) {
        return workoutTypeService.merge(sourceId, targetId);
    }

    @PatchMapping("/{id}")
    public WorkoutType rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return workoutTypeService.rename(id, body.get("name"));
    }

    @PostMapping("/{id}/dismiss-review")
    public ResponseEntity<Void> dismissReview(@PathVariable Long id) {
        workoutTypeService.dismissReview(id);
        return ResponseEntity.noContent().build();
    }
}