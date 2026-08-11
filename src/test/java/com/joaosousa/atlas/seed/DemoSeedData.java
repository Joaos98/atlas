package com.joaosousa.atlas.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.joaosousa.atlas.service.AppSettingsService;

import java.time.LocalDate;

/**
 * Deterministic demo dataset for the seed generator.
 *
 * All dates are stored as day offsets from the reference date (a Sunday), so
 * the demo can materialize them against the visitor's most recent Sunday by
 * shifting in whole-week (7-day) multiples without breaking week bucketing.
 */
public final class DemoSeedData {

    public static final int VERSION = 1;
    public static final int TARGET_WORKOUTS_PER_WEEK = AppSettingsService.DEFAULT_TARGET_WORKOUTS_PER_WEEK;

    private static final int FIRST_WEEK = -80;
    private static final int LAST_WEEK = -1;
    private static final int GAP_START = -40;
    private static final int GAP_END = -36;

    public static int firstWeekOffset() {
        return 7 * FIRST_WEEK;
    }

    public static int lastWeekOffset() {
        return 7 * LAST_WEEK;
    }

    private static final int[][] DAY_PATTERNS = {
            {1, 3, 5, 6},
            {0, 2, 4, 6},
            {1, 2, 5, 0},
            {3, 4, 6, 1}
    };

    private static final int[][] TYPE_PATTERNS = {
            {1, 2, 1, 3},
            {1, 4, 2, 1},
            {1, 3, 1, 4},
            {2, 1, 1, 3}
    };

    private static final int[] TYPE_COLORS = {0xe63946, 0x457b9d, 0x2a9d8f, 0xe9c46a};
    private static final String[] TYPE_NAMES = {"Run", "Strength", "Cycling", "Swimming"};

    private DemoSeedData() {
    }

    public static ObjectNode build() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("version", VERSION);
        root.put("referenceDate", FixedClockConfig.REFERENCE_DATE.toString());
        ObjectNode settings = root.putObject("appSettings");
        settings.put("targetWorkoutsPerWeek", TARGET_WORKOUTS_PER_WEEK);
        root.set("workoutTypes", workoutTypes(mapper));
        root.set("workoutLogs", workoutLogs(mapper));
        root.set("bodyMetrics", bodyMetrics(mapper));
        root.set("goals", goals(mapper));
        return root;
    }

    private static ArrayNode workoutTypes(ObjectMapper mapper) {
        ArrayNode types = mapper.createArrayNode();
        for (int id = 1; id <= 4; id++) {
            ObjectNode type = types.addObject();
            type.put("id", id);
            type.put("name", TYPE_NAMES[id - 1]);
            type.put("colorHex", String.format("#%06x", TYPE_COLORS[id - 1]));
        }
        return types;
    }

    private static ArrayNode workoutLogs(ObjectMapper mapper) {
        ArrayNode logs = mapper.createArrayNode();
        int logId = 1;
        for (int w = FIRST_WEEK; w <= LAST_WEEK; w++) {
            int count = isGapWeek(w) ? 1 + (Math.floorMod(w, 2)) : 4;
            int[] days = isGapWeek(w) ? new int[]{2} : DAY_PATTERNS[Math.floorMod(w, DAY_PATTERNS.length)];
            int[] types = isGapWeek(w) ? new int[]{2} : TYPE_PATTERNS[Math.floorMod(w, TYPE_PATTERNS.length)];
            for (int i = 0; i < count; i++) {
                ObjectNode log = logs.addObject();
                log.put("id", logId++);
                log.put("workoutTypeId", types[i % types.length]);
                log.put("dateOffsetDays", 7 * w + days[i % days.length]);
                log.put("durationMinutes", durationMinutes(types[i % types.length], w));
            }
        }
        ObjectNode today = logs.addObject();
        today.put("id", logId);
        today.put("workoutTypeId", 1);
        today.put("dateOffsetDays", 0);
        today.put("durationMinutes", 30);
        return logs;
    }

    private static int durationMinutes(int typeId, int w) {
        return switch (typeId) {
            case 1 -> 25 + Math.floorMod(w, 5) * 5;
            case 2 -> 40 + Math.floorMod(w, 7) * 5;
            case 3 -> 30 + Math.floorMod(w, 7) * 5;
            case 4 -> 20 + Math.floorMod(w, 5) * 5;
            default -> 30;
        };
    }

    private static ArrayNode bodyMetrics(ObjectMapper mapper) {
        ArrayNode metrics = mapper.createArrayNode();
        for (int w = FIRST_WEEK; w <= LAST_WEEK; w += 4) {
            ObjectNode metric = metrics.addObject();
            metric.put("dateOffsetDays", 7 * w);
            metric.put("weightKg", round1(weightAt(w)));
            metric.put("muscleMassKg", round1(36.5 + (w + 80) * 2.4 / 79));
            metric.put("waterLiters", round1(38.2 + (w + 80) * 2.2 / 79));
            metric.put("bodyFatPct", round1(24.0 + (w + 80) * (21.0 - 24.0) / 79));
            metric.put("bodyFatKg", round1(weightAt(w) * (24.0 + (w + 80) * (21.0 - 24.0) / 79) / 100));
            metric.putNull("insightText");
            metric.putNull("insightGeneratedAt");
        }
        return metrics;
    }

    private static ArrayNode goals(ObjectMapper mapper) {
        ArrayNode goals = mapper.createArrayNode();
        goals.add(goal(mapper, 1, 80.0, null, "ACHIEVED", 7 * -30, round1(weightAt(-30))));
        goals.add(goal(mapper, 2, 78.6, 42, "ACTIVE", 7 * -8, round1(weightAt(-8))));
        goals.add(goal(mapper, 3, 77.0, 14, "ACTIVE", 7 * -8, round1(weightAt(-8))));
        goals.add(goal(mapper, 4, 77.8, null, "ACTIVE", 7 * -8, round1(weightAt(-8))));
        return goals;
    }

    private static ObjectNode goal(ObjectMapper mapper, int id, double targetValue, Integer targetDateOffsetDays,
                                   String status, int createdAtOffsetDays, double startValue) {
        ObjectNode goal = mapper.createObjectNode();
        goal.put("id", id);
        goal.put("metricType", "WEIGHT");
        goal.put("targetValue", targetValue);
        if (targetDateOffsetDays != null) {
            goal.put("targetDateOffsetDays", targetDateOffsetDays);
        } else {
            goal.putNull("targetDateOffsetDays");
        }
        goal.put("status", status);
        goal.put("createdAtOffsetDays", createdAtOffsetDays);
        goal.put("startValue", startValue);
        return goal;
    }

    private static boolean isGapWeek(int w) {
        return w >= GAP_START && w <= GAP_END;
    }

    private static double weightAt(int w) {
        if (w <= -41) return 85.0 - (w + 80) * (3.0 / 39);
        if (w <= -36) return 82.0 + Math.floorMod(w, 2) * 0.2;
        if (w <= -26) return 82.2 + (w + 35) * 0.03;
        return 82.5 - (w + 25) * 0.16;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
