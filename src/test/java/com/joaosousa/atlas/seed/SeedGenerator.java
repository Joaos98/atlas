package com.joaosousa.atlas.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joaosousa.atlas.AbstractSqliteIntegrationTest;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.repository.GoalRepository;
import com.joaosousa.atlas.service.InsightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Regenerate deliberately: mvnw test -Dtest=SeedGenerator -Dsurefire.excludedGroups=
 * Set GEMINI_API_KEY to freeze a real insight onto the latest measurement.
 * The free Gemini tier rate-limits heavily, so the insight call retries with
 * a backoff until a genuine response arrives, instead of the single call the
 * plan's §4.7 wording ("once") originally described.
 */
@Tag("seed-generator")
@AutoConfigureMockMvc
class SeedGenerator extends AbstractSqliteIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SeedGenerator.class);

    private static final int MAX_INSIGHT_ATTEMPTS = 10;
    private static final int INSIGHT_RETRY_DELAY_SECONDS = 15;

    static {
        resetDb("seed-generator.db");
    }

    @DynamicPropertySource
    static void sqliteDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/spike/seed-generator.db");
    }

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private InsightService insightService;

    @Autowired
    private BodyMetricsRepository bodyMetricsRepository;

    @Test
    void generateSeedAndFixture() throws Exception {
        ObjectNode seed = DemoSeedData.build();
        new SeedReplayer(mockMvc, objectMapper, goalRepository)
                .replay(seed, FixedClockConfig.REFERENCE_DATE);

        maybeFreezeInsight(seed);

        ObjectNode expected = objectMapper.createObjectNode();
        expected.put("version", DemoSeedData.VERSION);
        expected.put("referenceDate", FixedClockConfig.REFERENCE_DATE.toString());

        ObjectNode stats = expected.putObject("stats");
        stats.put("year", FixedClockConfig.REFERENCE_DATE.getYear());
        stats.put("month", FixedClockConfig.REFERENCE_DATE.getMonthValue());
        stats.set("response", getJson("/api/stats?year=" + FixedClockConfig.REFERENCE_DATE.getYear()
                + "&month=" + FixedClockConfig.REFERENCE_DATE.getMonthValue()));

        ObjectNode streaks = expected.putObject("streaks");
        streaks.set("response", getJson("/api/workout-logs/streaks"));

        ObjectNode heatmap = expected.putObject("heatmap");
        heatmap.put("startOffsetDays", DemoSeedData.firstWeekOffset());
        heatmap.put("endOffsetDays", 0);
        heatmap.set("response", getJson("/api/workout-logs/heatmap?startDate="
                + FixedClockConfig.REFERENCE_DATE.plusDays(DemoSeedData.firstWeekOffset())
                + "&endDate=" + FixedClockConfig.REFERENCE_DATE));

        ObjectNode goals = expected.putObject("goals");
        goals.set("response", getJson("/api/goals"));

        Path dir = Path.of(seedDir);
        Files.createDirectories(dir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("demo-seed.json").toFile(), seed);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("expected-derived.json").toFile(), expected);
    }

    private void maybeFreezeInsight(ObjectNode seed) throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            log.info("GEMINI_API_KEY not set; seed will carry no insight text");
            return;
        }
        List<BodyMetrics> all = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.DESC, "measuredOn"));
        if (all.isEmpty()) {
            return;
        }
        ReflectionTestUtils.setField(insightService, "apiKey", key);

        BodyMetrics latest = all.get(0);
        InsightService.InsightResult result = null;
        for (int attempt = 1; attempt <= MAX_INSIGHT_ATTEMPTS; attempt++) {
            result = insightService.generateInsight(latest);
            if (!result.fallback()) {
                break;
            }
            if (attempt < MAX_INSIGHT_ATTEMPTS) {
                log.warn("Insight attempt {}/{} failed (free-tier usage); retrying in {}s: {}",
                        attempt, MAX_INSIGHT_ATTEMPTS, INSIGHT_RETRY_DELAY_SECONDS, result.text());
                Thread.sleep(INSIGHT_RETRY_DELAY_SECONDS * 1000L);
            } else {
                log.warn("Insight attempt {}/{} failed (free-tier usage): {}",
                        attempt, MAX_INSIGHT_ATTEMPTS, result.text());
            }
        }
        if (result.fallback()) {
            log.warn("Giving up on insight after {} attempts; seed will carry no insight text", MAX_INSIGHT_ATTEMPTS);
            return;
        }
        String stored = result.verdict() != null
                ? "VERDICT:" + result.verdict() + "\nINSIGHT:" + result.text()
                : result.text();
        JsonNode metrics = seed.get("bodyMetrics");
        ObjectNode latestNode = (ObjectNode) metrics.get(metrics.size() - 1);
        latestNode.put("insightText", stored);
        latestNode.put("insightGeneratedAt", result.generatedAt().toString());
    }
}
