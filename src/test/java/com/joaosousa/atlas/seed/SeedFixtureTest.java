package com.joaosousa.atlas.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joaosousa.atlas.AbstractSqliteIntegrationTest;
import com.joaosousa.atlas.repository.GoalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization test: replays the committed demo-seed.json through the real
 * API and asserts the derived responses still equal expected-derived.json.
 * Fails loudly if Java logic changes without regenerating the seed deliberately
 * (mvnw test -Dtest=SeedGenerator -Dsurefire.excludedGroups=).
 */
@AutoConfigureMockMvc
class SeedFixtureTest extends AbstractSqliteIntegrationTest {

    static {
        resetDb("seed-fixture.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/seed-fixture.db");
    }

    @Autowired
    private GoalRepository goalRepository;

    @Test
    void replayMatchesRecordedFixture() throws Exception {
        Path dir = Path.of(seedDir);
        Path seedFile = dir.resolve("demo-seed.json");
        Path expectedFile = dir.resolve("expected-derived.json");
        assertTrue(Files.exists(seedFile) && Files.exists(expectedFile),
                "Run the seed generator first: mvnw test -Dtest=SeedGenerator -Dsurefire.excludedGroups=");

        ObjectNode seed = (ObjectNode) objectMapper.readTree(seedFile.toFile());
        new SeedReplayer(mockMvc, objectMapper, goalRepository)
                .replay(seed, FixedClockConfig.REFERENCE_DATE);

        ObjectNode expected = (ObjectNode) objectMapper.readTree(expectedFile.toFile());

        JsonNode stats = expected.get("stats");
        JsonNode statsResponse = getJson("/api/stats?year=" + stats.get("year").asInt()
                + "&month=" + stats.get("month").asInt());
        assertEquals(stats.get("response"), statsResponse, "stats drifted from recorded fixture");

        JsonNode streaks = expected.get("streaks");
        assertEquals(streaks.get("response"), getJson("/api/workout-logs/streaks"),
                "streaks drifted from recorded fixture");

        JsonNode heatmap = expected.get("heatmap");
        String heatmapRange = "/api/workout-logs/heatmap?startDate="
                + FixedClockConfig.REFERENCE_DATE.plusDays(heatmap.get("startOffsetDays").asInt())
                + "&endDate=" + FixedClockConfig.REFERENCE_DATE.plusDays(heatmap.get("endOffsetDays").asInt());
        assertEquals(heatmap.get("response"), getJson(heatmapRange),
                "heatmap drifted from recorded fixture");

        assertEquals(expected.get("goals").get("response"), getJson("/api/goals"),
                "goals drifted from recorded fixture");
    }
}
