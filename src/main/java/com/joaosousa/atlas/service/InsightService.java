package com.joaosousa.atlas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaosousa.atlas.dto.GoalProgressDto;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.entity.MetricType;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    private static final String SYSTEM_PROMPT =
        "You are a personal fitness coach. The user tracks body metrics and workouts in a personal app. " +
        "Analyze their latest measurement in context. Write in 2nd person. Be concise, specific, and honest. " +
        "Respond in this exact format:\n\n" +
        "VERDICT: <3-6 word status label summarizing the overall trend and direction, e.g. \"Plateau — slightly regressing\" or \"Strong progress, fat dropping\">\n" +
        "INSIGHT: <your analysis>\n\n" +
        "For the INSIGHT, write maximum 2 paragraphs. " +
        "First paragraph: interpret what the data means. Don't list numbers — the user sees them already. " +
        "What patterns emerge? Is anything surprising? How does training connect to body composition changes? " +
        "Second paragraph: one actionable, specific suggestion the user can act on. " +
        "Keep it warm and direct. Never invent or guess.";

    private final BodyMetricsRepository bodyMetricsRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final GoalService goalService;
    private final AppSettingsService appSettingsService;

    @Value("${app.insight.api-key:}")
    private String apiKey;

    @Value("${app.insight.model:gemini-2.0-flash-lite}")
    private String model;

    public InsightService(BodyMetricsRepository bodyMetricsRepository,
                          WorkoutLogRepository workoutLogRepository,
                          GoalService goalService,
                          AppSettingsService appSettingsService) {
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.goalService = goalService;
        this.appSettingsService = appSettingsService;
    }

    public InsightResult generateInsight(BodyMetrics latest) {
        List<BodyMetrics> allMetrics = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.ASC, "measuredOn"));
        BodyMetrics previous = allMetrics.size() >= 2 ? allMetrics.get(allMetrics.size() - 2) : null;

        String prompt = buildPrompt(latest, previous, allMetrics);
        log.debug("Insight prompt:\n{}", prompt);

        try {
            String text = callGemini(prompt);
            ParsedInsight parsed = parseRawText(text);
            return new InsightResult(parsed.verdict(), parsed.text(), latest.getMeasuredOn().atStartOfDay(), false);
        } catch (RestClientResponseException e) {
            return new InsightResult(null, apiErrorMessage(e.getStatusCode().value()), latest.getMeasuredOn().atStartOfDay(), true);
        } catch (Exception e) {
            log.warn("Insight generation failed: {}", e.getMessage());
            return new InsightResult(null,
                "An unexpected error occurred while generating the insight. Please try again later.",
                latest.getMeasuredOn().atStartOfDay(), true);
        }
    }

    private static String apiErrorMessage(int status) {
        return switch (status) {
            case 400 -> "Invalid request to insight service. Please try again or regenerate later.";
            case 401, 403 -> "Insight service authentication failed. Please check the API key.";
            case 429 -> "Insight service rate limited. Please try again later.";
            case 503 -> "Insight could not be generated due to high demand. Please try again later.";
            default -> "Insight service is temporarily unavailable (error " + status + "). Please try again later.";
        };
    }

    // --- prompt builder ---

    private String buildPrompt(BodyMetrics latest, BodyMetrics previous, List<BodyMetrics> allMetrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here is the user's fitness data for analysis:\n\n");

        // Latest measurement
        sb.append("## Latest measurement (").append(latest.getMeasuredOn()).append(")\n");
        if (previous != null) {
            long daysSince = ChronoUnit.DAYS.between(previous.getMeasuredOn(), latest.getMeasuredOn());
            sb.append("(").append(daysSince).append(" days since the previous measurement)\n");
        }
        sb.append(metricLine("Weight", latest.getWeightKg(), previous, "kg"));
        sb.append(metricLine("Muscle mass", latest.getMuscleMassKg(), previous, "kg"));
        sb.append(metricLine("Body water", latest.getWaterLiters(), previous, "L"));
        sb.append(metricLine("Body fat mass", latest.getBodyFatKg(), previous, "kg"));
        sb.append(metricLine("Body fat %", latest.getBodyFatPct(), previous, "%"));

        // Trends — compute rates over recent window and all time
        if (allMetrics.size() >= 3) {
            sb.append("\n## Rate per month (recent 3 entries)\n");
            List<BodyMetrics> recent = allMetrics.subList(allMetrics.size() - 3, allMetrics.size());
            BodyMetrics first = recent.get(0);
            BodyMetrics last = recent.get(recent.size() - 1);
            long days = ChronoUnit.DAYS.between(first.getMeasuredOn(), last.getMeasuredOn());
            if (days > 0) {
                for (MetricType type : MetricType.values()) {
                    appendTrend(sb, type, first, last, days);
                }
            }

            if (allMetrics.size() >= 3 && allMetrics.size() != recent.size()) {
                sb.append("\n## Rate per month (all time)\n");
                BodyMetrics firstAll = allMetrics.get(0);
                long daysAll = ChronoUnit.DAYS.between(firstAll.getMeasuredOn(), latest.getMeasuredOn());
                if (daysAll > 0) {
                    for (MetricType type : MetricType.values()) {
                        appendTrend(sb, type, firstAll, latest, daysAll);
                    }
                }
            }
        }

        // Training since last measurement
        if (previous != null) {
            LocalDate since = previous.getMeasuredOn();
            LocalDate until = latest.getMeasuredOn();
            int workoutDays = workoutLogRepository.countDistinctWorkoutDates(since, until);
            long totalWorkouts = workoutLogRepository.countWorkouts(since, until);
            Integer maxDuration = workoutLogRepository.maxDuration(since, until);
            Double avgDuration = workoutLogRepository.averageDuration(since, until);
            String mostFreq = workoutLogRepository.mostFrequentType(since, until);

            sb.append("\n## Training (since last measurement)\n");
            sb.append("- Total workouts: ").append(totalWorkouts)
              .append(" across ").append(workoutDays).append(" days\n");
            if (avgDuration != null) {
                sb.append("- Average duration: ").append(Math.round(avgDuration)).append(" min\n");
            }
            if (maxDuration != null) {
                sb.append("- Longest session: ").append(maxDuration).append(" min\n");
            }
            if (mostFreq != null) {
                sb.append("- Most frequent type: ").append(mostFreq).append("\n");
            }
            int targetPerWeek = appSettingsService.get().getTargetWorkoutsPerWeek();
            sb.append("- Weekly target: ").append(targetPerWeek).append(" workouts/week\n");
        }

        // Goals
        List<GoalProgressDto> goals = goalService.findAllWithProgress();
        List<GoalProgressDto> active = goals.stream()
                .filter(g -> "ACTIVE".equals(g.getStatus()))
                .collect(Collectors.toList());

        if (!active.isEmpty()) {
            sb.append("\n## Active goals\n");
            for (GoalProgressDto g : active) {
                sb.append("- ").append(goalLabel(g.getMetricType())).append(": target ")
                  .append(g.getTargetValue()).append(metricUnit(MetricType.valueOf(g.getMetricType())))
                  .append(", currently ").append(String.format("%.1f", g.getCurrentValue()));
                if (g.getStartValue() != null) {
                    sb.append(" (started at ").append(String.format("%.1f", g.getStartValue()))
                      .append(", ").append(progressLabel(g.getProgressPercent())).append(" progress)");
                }
                if (g.getEta() != null) {
                    sb.append(" — ETA ").append(g.getEta());
                } else if (g.getPaceStatus() != null) {
                    sb.append(" — pace: ").append(g.getPaceStatus());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private void appendTrend(StringBuilder sb, MetricType type, BodyMetrics first, BodyMetrics last, long days) {
        double v1 = getMetricValue(first, type);
        double v2 = getMetricValue(last, type);
        double perMonth = (v2 - v1) / days * 30;
        String symbol = perMonth >= 0 ? "+" : "";
        sb.append("- ").append(type.name()).append(": ")
          .append(symbol).append(String.format("%.2f", perMonth)).append("/month")
          .append(" (").append(String.format("%.1f", v1)).append(" → ").append(String.format("%.1f", v2)).append(")\n");
    }

    private String goalLabel(String metricType) {
        return switch (metricType) {
            case "MUSCLE_MASS" -> "Muscle mass";
            case "BODY_FAT_KG" -> "Body fat (kg)";
            case "BODY_FAT_PCT" -> "Body fat %";
            case "WATER" -> "Body water";
            case "WEIGHT" -> "Weight";
            default -> metricType;
        };
    }

    private String metricLine(String label, Double value, BodyMetrics previous, String unit) {
        if (value == null) return "";
        String line = "- " + label + ": " + String.format("%.1f", value) + " " + unit;
        if (previous != null) {
            Double prev = getMetricValueByName(previous, label);
            if (prev != null) {
                double delta = value - prev;
                String sign = delta >= 0 ? "+" : "";
                line += " (" + sign + String.format("%.1f", delta) + ")";
            }
        }
        return line + "\n";
    }

    private String progressLabel(Double pct) {
        if (pct == null) return "no baseline";
        return String.format("%.0f%%", pct);
    }

    private String metricUnit(MetricType type) {
        return switch (type) {
            case WEIGHT, MUSCLE_MASS, BODY_FAT_KG -> "kg";
            case WATER -> "L";
            case BODY_FAT_PCT -> "%";
        };
    }

    private Double getMetricValue(BodyMetrics bm, MetricType type) {
        return switch (type) {
            case WEIGHT -> bm.getWeightKg();
            case MUSCLE_MASS -> bm.getMuscleMassKg();
            case WATER -> bm.getWaterLiters();
            case BODY_FAT_KG -> bm.getBodyFatKg();
            case BODY_FAT_PCT -> bm.getBodyFatPct();
        };
    }

    private Double getMetricValueByName(BodyMetrics bm, String label) {
        return switch (label) {
            case "Weight" -> bm.getWeightKg();
            case "Muscle mass" -> bm.getMuscleMassKg();
            case "Body water" -> bm.getWaterLiters();
            case "Body fat mass" -> bm.getBodyFatKg();
            case "Body fat %" -> bm.getBodyFatPct();
            default -> null;
        };
    }

    // --- Gemini API ---

    private String callGemini(String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key not configured");
        }

        try {
            return doGeminiCall(prompt);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                log.warn("Gemini 503, retrying once: {}", e.getMessage());
                return doGeminiCall(prompt);
            }
            throw e;
        }
    }

    private String doGeminiCall(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of(
                "parts", List.of(Map.of("text", SYSTEM_PROMPT))
            ),
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 2000
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());
        return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
    }

    // --- result wrapper ---

    public record InsightResult(String verdict, String text, LocalDateTime generatedAt, boolean fallback) {}

    public static ParsedInsight parseRawText(String raw) {
        if (raw == null) return new ParsedInsight(null, null);
        if (raw.startsWith("VERDICT:")) {
            int verdictEnd = raw.indexOf("INSIGHT:");
            if (verdictEnd > 0) {
                String verdict = raw.substring("VERDICT:".length(), verdictEnd).trim();
                String text = raw.substring(verdictEnd + "INSIGHT:".length()).trim();
                return new ParsedInsight(verdict, text);
            }
        }
        return new ParsedInsight(null, raw);
    }

    public record ParsedInsight(String verdict, String text) {}
}
