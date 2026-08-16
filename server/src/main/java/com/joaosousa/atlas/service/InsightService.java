package com.joaosousa.atlas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joaosousa.atlas.dto.GoalProgressDto;
import com.joaosousa.atlas.dto.InsightState;
import com.joaosousa.atlas.entity.AppSettings;
import com.joaosousa.atlas.entity.BodyMetrics;
import com.joaosousa.atlas.entity.MetricType;
import com.joaosousa.atlas.entity.UnitSystem;
import com.joaosousa.atlas.repository.BodyMetricsRepository;
import com.joaosousa.atlas.repository.WorkoutLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public InsightService(BodyMetricsRepository bodyMetricsRepository,
                          WorkoutLogRepository workoutLogRepository,
                          GoalService goalService,
                          AppSettingsService appSettingsService) {
        this.bodyMetricsRepository = bodyMetricsRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.goalService = goalService;
        this.appSettingsService = appSettingsService;
    }

    public static final String NOT_CONFIGURED_MESSAGE = "Insights are off — add a provider key in Settings.";

    /** Provider error bodies are quoted back to the user, so they are capped. */
    private static final int PROVIDER_DETAIL_LIMIT = 300;

    public boolean isProviderConfigured() {
        String apiKey = appSettingsService.get().getInsightApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Writes a result onto a measurement, but only when it is a real insight.
     *
     * <p>The rule lives here because it was originally spelled out at each call site and one
     * of them always got it wrong: a failure written into {@code insight_text} destroys the
     * last good insight, and {@code GET /api/insights} then re-serves that error prose as
     * though it were an insight, so the loss is invisible. See insight-provider-spec.md §8.
     *
     * @return whether anything was written, so callers can skip a pointless save
     */
    public static boolean applyIfGenerated(BodyMetrics entry, InsightResult result) {
        if (result.state() != InsightState.OK) {
            return false;
        }
        entry.setInsightText(result.verdict() != null
                ? "VERDICT:" + result.verdict() + "\nINSIGHT:" + result.text()
                : result.text());
        entry.setInsightGeneratedAt(result.generatedAt());
        return true;
    }

    public InsightResult generateInsight(BodyMetrics latest) {
        LocalDateTime generatedAt = latest.getMeasuredOn().atStartOfDay();
        AppSettings settings = appSettingsService.get();
        String apiKey = settings.getInsightApiKey();

        // Checked before building the prompt: having no key is a state, not a failure.
        if (apiKey == null || apiKey.isBlank()) {
            return new InsightResult(null, NOT_CONFIGURED_MESSAGE, generatedAt, InsightState.NOT_CONFIGURED);
        }

        List<BodyMetrics> allMetrics = bodyMetricsRepository.findAll(Sort.by(Sort.Direction.ASC, "measuredOn"));
        BodyMetrics previous = allMetrics.size() >= 2 ? allMetrics.get(allMetrics.size() - 2) : null;

        String prompt = buildPrompt(latest, previous, allMetrics);
        log.debug("Insight prompt:\n{}", prompt);

        try {
            String text = callProvider(prompt, settings, apiKey);
            ParsedInsight parsed = parseRawText(text);
            return new InsightResult(parsed.verdict(), parsed.text(), generatedAt, InsightState.OK);
        } catch (ResourceAccessException e) {
            log.warn("Insight provider unreachable at {}", settings.getInsightBaseUrl());
            return new InsightResult(null, unreachableMessage(settings.getInsightBaseUrl()),
                    generatedAt, InsightState.UNREACHABLE);
        } catch (RestClientResponseException e) {
            // Status only. The body is echoed to the user but never to the log — providers
            // quote the request back, and the key rode in that request's headers.
            log.warn("Insight provider returned {}", e.getStatusCode().value());
            return new InsightResult(null, providerErrorMessage(e), generatedAt, InsightState.PROVIDER_ERROR);
        } catch (Exception e) {
            log.warn("Insight generation failed: {}", e.getMessage());
            return new InsightResult(null,
                "An unexpected error occurred while generating the insight. Please try again later.",
                generatedAt, InsightState.PROVIDER_ERROR);
        }
    }

    /** Naming the URL back is what makes a typo in it self-evident. */
    private static String unreachableMessage(String baseUrl) {
        return "Couldn't reach the insight provider at " + baseUrl + ".";
    }

    private static String providerErrorMessage(RestClientResponseException e) {
        String friendly = apiErrorMessage(e.getStatusCode().value());
        String detail = providerDetail(e);
        return detail.isBlank() ? friendly : friendly + " Provider said: " + detail;
    }

    /**
     * OpenAI-compatible errors carry a human-readable reason at {@code error.message} — the
     * "model 'llama3.2' not found" that turns a bare 404 into something actionable. Anything
     * that is not that shape falls back to the raw body.
     */
    private static String providerDetail(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode message = new ObjectMapper().readTree(body).path("error").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return truncate(message.asText());
            }
        } catch (Exception ignored) {
            // Not JSON, or not the OpenAI error shape.
        }
        return truncate(body);
    }

    private static String truncate(String text) {
        String collapsed = text.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= PROVIDER_DETAIL_LIMIT
                ? collapsed
                : collapsed.substring(0, PROVIDER_DETAIL_LIMIT) + "…";
    }

    private static String apiErrorMessage(int status) {
        return switch (status) {
            case 400 -> "The insight provider rejected the request.";
            case 401, 403 -> "The insight provider rejected the API key — check it in Settings.";
            case 404 -> "The insight provider returned 404 — check the base URL and model name in Settings.";
            case 429 -> "The insight provider is rate limiting. Please try again later.";
            case 503 -> "The insight provider is busy. Please try again later.";
            default -> "The insight provider is unavailable (error " + status + "). Please try again later.";
        };
    }

    // --- prompt builder ---

    private static final double KG_TO_LB = 2.20462;

    /**
     * Every number in the prompt is formatted against {@link Locale#ROOT}, not the JVM default.
     * On a pt-BR machine the default renders 82.3 as "82,3", so the same app produced a
     * different prompt depending on where it ran — and a comma decimal is ambiguous to a model
     * that may read it as a thousands separator. Latent since the prompt was written; found
     * when the unit tests below started asserting on prompt text.
     */

    /**
     * The prompt writes units into text a human reads, so it has to follow the display
     * preference — otherwise the card shows pounds while the insight text talks in kilos.
     * This is the second of exactly two conversion sites; see units-preference-spec.md §4.
     */
    private static double convert(double value, MetricType type, UnitSystem system) {
        if (system != UnitSystem.IMPERIAL || type == MetricType.BODY_FAT_PCT) return value;
        return value * KG_TO_LB;
    }

    private static String unitLabel(MetricType type, UnitSystem system) {
        if (type == MetricType.BODY_FAT_PCT) return "%";
        if (system == UnitSystem.IMPERIAL) return "lb";
        return type == MetricType.WATER ? "L" : "kg";
    }

    private String buildPrompt(BodyMetrics latest, BodyMetrics previous, List<BodyMetrics> allMetrics) {
        UnitSystem system = appSettingsService.get().getUnitSystem();
        if (system == null) system = UnitSystem.METRIC;

        StringBuilder sb = new StringBuilder();
        sb.append("Here is the user's fitness data for analysis:\n\n");

        // Latest measurement
        sb.append("## Latest measurement (").append(latest.getMeasuredOn()).append(")\n");
        if (previous != null) {
            long daysSince = ChronoUnit.DAYS.between(previous.getMeasuredOn(), latest.getMeasuredOn());
            sb.append("(").append(daysSince).append(" days since the previous measurement)\n");
        }
        sb.append(metricLine("Weight", latest.getWeightKg(), previous, MetricType.WEIGHT, system));
        sb.append(metricLine("Muscle mass", latest.getMuscleMassKg(), previous, MetricType.MUSCLE_MASS, system));
        sb.append(metricLine("Body water", latest.getWaterLiters(), previous, MetricType.WATER, system));
        sb.append(metricLine("Body fat mass", latest.getBodyFatKg(), previous, MetricType.BODY_FAT_KG, system));
        sb.append(metricLine("Body fat %", latest.getBodyFatPct(), previous, MetricType.BODY_FAT_PCT, system));

        // Trends — compute rates over recent window and all time
        if (allMetrics.size() >= 3) {
            sb.append("\n## Rate per month (recent 3 entries)\n");
            List<BodyMetrics> recent = allMetrics.subList(allMetrics.size() - 3, allMetrics.size());
            BodyMetrics first = recent.get(0);
            BodyMetrics last = recent.get(recent.size() - 1);
            long days = ChronoUnit.DAYS.between(first.getMeasuredOn(), last.getMeasuredOn());
            if (days > 0) {
                for (MetricType type : MetricType.values()) {
                    appendTrend(sb, type, first, last, days, system);
                }
            }

            if (allMetrics.size() >= 3 && allMetrics.size() != recent.size()) {
                sb.append("\n## Rate per month (all time)\n");
                BodyMetrics firstAll = allMetrics.get(0);
                long daysAll = ChronoUnit.DAYS.between(firstAll.getMeasuredOn(), latest.getMeasuredOn());
                if (daysAll > 0) {
                    for (MetricType type : MetricType.values()) {
                        appendTrend(sb, type, firstAll, latest, daysAll, system);
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
                MetricType goalType = MetricType.valueOf(g.getMetricType());
                sb.append("- ").append(goalLabel(g.getMetricType())).append(": target ")
                  .append(String.format(Locale.ROOT, "%.1f", convert(g.getTargetValue(), goalType, system)))
                  .append(" ").append(unitLabel(goalType, system))
                  .append(", currently ").append(String.format(Locale.ROOT, "%.1f", convert(g.getCurrentValue(), goalType, system)));
                if (g.getStartValue() != null) {
                    sb.append(" (started at ").append(String.format(Locale.ROOT, "%.1f", convert(g.getStartValue(), goalType, system)))
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

    /**
     * Converted alongside everything else — a rate quoted in kilos beside measurements quoted
     * in pounds is worse than either on its own. The unit is stated here now; it was implicit
     * before, which only worked while there was exactly one.
     */
    private void appendTrend(StringBuilder sb, MetricType type, BodyMetrics first, BodyMetrics last,
                             long days, UnitSystem system) {
        double v1 = convert(getMetricValue(first, type), type, system);
        double v2 = convert(getMetricValue(last, type), type, system);
        double perMonth = (v2 - v1) / days * 30;
        String symbol = perMonth >= 0 ? "+" : "";
        sb.append("- ").append(type.name()).append(": ")
          .append(symbol).append(String.format(Locale.ROOT, "%.2f", perMonth)).append(" ").append(unitLabel(type, system)).append("/month")
          .append(" (").append(String.format(Locale.ROOT, "%.1f", v1)).append(" → ").append(String.format(Locale.ROOT, "%.1f", v2)).append(")\n");
    }

    private String goalLabel(String metricType) {
        return switch (metricType) {
            case "MUSCLE_MASS" -> "Muscle mass";
            case "BODY_FAT_KG" -> "Body fat (mass)";
            case "BODY_FAT_PCT" -> "Body fat %";
            case "WATER" -> "Body water";
            case "WEIGHT" -> "Weight";
            default -> metricType;
        };
    }

    private String metricLine(String label, Double value, BodyMetrics previous, MetricType type, UnitSystem system) {
        if (value == null) return "";
        String line = "- " + label + ": " + String.format(Locale.ROOT, "%.1f", convert(value, type, system))
                + " " + unitLabel(type, system);
        if (previous != null) {
            Double prev = getMetricValueByName(previous, label);
            if (prev != null) {
                // The delta converts directly: the scale is linear with no offset.
                double delta = convert(value - prev, type, system);
                String sign = delta >= 0 ? "+" : "";
                line += " (" + sign + String.format(Locale.ROOT, "%.1f", delta) + ")";
            }
        }
        return line + "\n";
    }

    private String progressLabel(Double pct) {
        if (pct == null) return "no baseline";
        return String.format(Locale.ROOT, "%.0f%%", pct);
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

    // --- provider API (OpenAI-compatible chat completions) ---

    /**
     * The provider is whatever {@code insight_base_url} points at. OpenAI, Gemini's compat
     * endpoint, Groq, OpenRouter, Ollama and LM Studio all speak this one wire format, so
     * changing provider is a settings edit rather than a code change — see
     * insight-provider-spec.md §2.
     */
    private String callProvider(String prompt, AppSettings settings, String apiKey) throws Exception {
        try {
            return doProviderCall(prompt, settings, apiKey);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 503) {
                // Deliberately without e.getMessage(): providers echo the request back in
                // error bodies, and the key travels in the headers of that request.
                log.warn("Insight provider returned 503, retrying once");
                return doProviderCall(prompt, settings, apiKey);
            }
            throw e;
        }
    }

    private String doProviderCall(String prompt, AppSettings settings, String apiKey) throws Exception {
        Map<String, Object> body = Map.of(
            "model", settings.getInsightModel(),
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.7,
            "max_tokens", 2000
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(
                chatCompletionsUrl(settings.getInsightBaseUrl()), request, String.class);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            // A 200 that carries no choices means the endpoint is not the one we think it is.
            throw new IllegalStateException("Insight provider returned no choices");
        }
        return choices.get(0).path("message").path("content").asText();
    }

    /** Tolerates a trailing slash on the configured base URL, which is easy to paste in. */
    private static String chatCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/chat/completions";
    }

    // --- result wrapper ---

    public record InsightResult(String verdict, String text, LocalDateTime generatedAt, InsightState state) {

        /** Kept for existing callers: anything that is not a generated insight is a fallback. */
        public boolean fallback() {
            return state != InsightState.OK;
        }
    }

    /**
     * Tolerates the labels being wrapped in markdown emphasis, cased differently, or preceded
     * by blank lines. The strict {@code startsWith("VERDICT:")} this replaces was fine against
     * Gemini, which complies reliably — but any model can be selected now, and smaller ones
     * routinely emit {@code **VERDICT:**}. On a near-miss the old parser dumped the entire
     * reply, labels and all, into the insight body, which read as an app bug rather than a
     * model mismatch. See insight-provider-spec.md §6.
     */
    private static final Pattern VERDICT_INSIGHT = Pattern.compile(
            "^\\s*\\**\\s*verdict\\s*:\\**\\s*(.+?)\\s*\\**\\s*insight\\s*:\\**\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static ParsedInsight parseRawText(String raw) {
        if (raw == null) return new ParsedInsight(null, null);
        Matcher matcher = VERDICT_INSIGHT.matcher(raw);
        if (matcher.matches()) {
            return new ParsedInsight(matcher.group(1).trim(), matcher.group(2).trim());
        }
        // No recognisable verdict: keep the prose, drop nothing. A null verdict is now a
        // normal outcome for a weaker model rather than a sign something went wrong.
        return new ParsedInsight(null, raw.trim());
    }

    public record ParsedInsight(String verdict, String text) {}
}
