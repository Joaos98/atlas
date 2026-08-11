package com.joaosousa.atlas;

import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.entity.WorkoutLog;
import com.joaosousa.atlas.entity.WorkoutType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class SqliteApiIntegrationTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("atlas-api.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-api.db");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void seed() {
        WorkoutType run = new WorkoutType();
        run.setName("Run");
        run.setColorHex("#ff0000");
        entityManager.persist(run);

        WorkoutType gym = new WorkoutType();
        gym.setName("Gym");
        gym.setColorHex("#00ff00");
        entityManager.persist(gym);

        entityManager.persist(log(run, LocalDate.of(2026, 7, 27), 45));
        entityManager.persist(log(run, LocalDate.of(2026, 7, 29), 30));
        entityManager.persist(log(gym, LocalDate.of(2026, 7, 30), 60));

        BodyMetrics m = new BodyMetrics();
        m.setMeasuredOn(LocalDate.of(2026, 7, 26));
        m.setWeightKg(80.0);
        m.setMuscleMassKg(40.0);
        m.setWaterLiters(40.0);
        m.setBodyFatKg(15.0);
        m.setBodyFatPct(18.0);
        entityManager.persist(m);
    }

    private static WorkoutLog log(WorkoutType type, LocalDate date, int minutes) {
        WorkoutLog log = new WorkoutLog();
        log.setWorkoutType(type);
        log.setLogDate(date);
        log.setDurationMinutes(minutes);
        return log;
    }

    @Test
    void statsAndHeatmapWorkOnSqlite() throws Exception {
        mockMvc.perform(get("/api/stats").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workoutStats.totalWorkoutsThisMonth").value(3))
                .andExpect(jsonPath("$.workoutStats.totalWorkoutsThisYear").value(3))
                .andExpect(jsonPath("$.workoutStats.mostFrequentTypeThisMonth").value("Run"))
                .andExpect(jsonPath("$.workoutStats.totalMinutesThisMonth").value(135))
                .andExpect(jsonPath("$.bodyCompositionStats.totalMeasurements").value(1))
                .andExpect(jsonPath("$.streakStats.currentStreak").value(1));

        mockMvc.perform(get("/api/workout-logs/heatmap")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-07-27"))
                .andExpect(jsonPath("$[0].workouts[0].type").value("Run"))
                .andExpect(jsonPath("$[2].workouts[0].durationMinutes").value(60));
    }
}
