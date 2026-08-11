package com.joaosousa.atlas;

import com.joaosousa.atlas.service.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FreshInstallTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("atlas-fresh-install.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/atlas-fresh-install.db");
    }

    @Test
    void settingsAreSeededAndStreaksDoNot500OnFreshInstall() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetWorkoutsPerWeek").value(AppSettingsService.DEFAULT_TARGET_WORKOUTS_PER_WEEK));

        mockMvc.perform(get("/api/workout-logs/streaks"))
                .andExpect(status().isOk());
    }
}
