package com.joaosousa.atlas;

import com.joaosousa.atlas.entity.WorkoutType;
import com.joaosousa.atlas.repository.WorkoutTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduces what an <b>existing</b> install looks like the moment a new column is added.
 *
 * <p>{@code ddl-auto=update} issues {@code ALTER TABLE ADD COLUMN}, which gives every
 * pre-existing row NULL. That is harmless for an object type and fatal for a primitive: reading
 * NULL into {@code boolean pendingReview} fails, and since every workout log fetches its type,
 * it takes {@code /api/workout-types} and {@code /api/workout-logs} down with it.
 *
 * <p>The fresh-install tests could never have caught this — they create the column and the rows
 * together, so the column is never NULL. Only an upgrade produces the state.
 */
@AutoConfigureMockMvc
class UpgradedInstallTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("atlas-upgraded.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-upgraded.db");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkoutTypeRepository workoutTypeRepository;

    @Test
    void workoutTypesSurviveANullPendingReviewFromAnAlterTable() throws Exception {
        jdbc.update("INSERT INTO workout_types (name, color_hex, pending_review) VALUES ('Cardio', '#4F8DFF', NULL)");

        List<WorkoutType> types = workoutTypeRepository.findAll();
        assertEquals(1, types.size());
        assertFalse(types.get(0).isPendingReview(), "a pre-existing type is not awaiting review");

        mockMvc.perform(get("/api/workout-types")).andExpect(status().isOk());
        mockMvc.perform(get("/api/workout-logs")).andExpect(status().isOk());
    }
}
