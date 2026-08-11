package com.joaosousa.atlas.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joaosousa.atlas.entity.Goal;
import com.joaosousa.atlas.entity.GoalStatus;
import com.joaosousa.atlas.entity.MetricType;
import com.joaosousa.atlas.repository.GoalRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replays a demo-seed.json through the real HTTP API, exactly the way the
 * frontend would, so the recorded derived responses are an honest contract.
 *
 * Goals are the one deliberate deviation: the API stamps createdAt and
 * startValue from the latest measurement, but ETA and pace need goals with
 * history, so goals are inserted through the repository with their backdated
 * values from the seed.
 */
public class SeedReplayer {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final GoalRepository goalRepository;

    public SeedReplayer(MockMvc mockMvc, ObjectMapper objectMapper, GoalRepository goalRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.goalRepository = goalRepository;
    }

    public void replay(JsonNode seed, LocalDate anchor) throws Exception {
        for (JsonNode type : seed.withArray("workoutTypes")) {
            ObjectNode body = ((ObjectNode) type).deepCopy();
            body.remove("id");
            mockMvc.perform(post("/api/workout-types")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }

        for (JsonNode log : seed.withArray("workoutLogs")) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("logDate", anchor.plusDays(log.get("dateOffsetDays").asLong()).toString());
            body.putObject("workoutType").put("id", log.get("workoutTypeId").asLong());
            body.put("durationMinutes", log.get("durationMinutes").asInt());
            mockMvc.perform(post("/api/workout-logs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }

        for (JsonNode metric : seed.withArray("bodyMetrics")) {
            ObjectNode body = ((ObjectNode) metric).deepCopy();
            body.put("measuredOn", anchor.plusDays(metric.get("dateOffsetDays").asLong()).toString());
            body.remove("dateOffsetDays");
            body.remove("insightText");
            body.remove("insightGeneratedAt");
            mockMvc.perform(post("/api/body-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());
        }

        List<Goal> goals = new ArrayList<>();
        for (JsonNode g : seed.withArray("goals")) {
            Goal goal = new Goal();
            goal.setMetricType(MetricType.valueOf(g.get("metricType").asText()));
            goal.setTargetValue(g.get("targetValue").asDouble());
            JsonNode targetDate = g.get("targetDateOffsetDays");
            goal.setTargetDate(targetDate.isNull() ? null : anchor.plusDays(targetDate.asLong()));
            goal.setStatus(GoalStatus.valueOf(g.get("status").asText()));
            goal.setCreatedAt(anchor.plusDays(g.get("createdAtOffsetDays").asLong()).atStartOfDay());
            goal.setStartValue(g.get("startValue").asDouble());
            goals.add(goal);
        }
        goalRepository.saveAll(goals);
    }
}
