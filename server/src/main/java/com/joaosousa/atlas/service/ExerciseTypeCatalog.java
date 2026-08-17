package com.joaosousa.atlas.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Health Connect exercise type code → display name. Data, not logic.
 *
 * <p><b>Source:</b> {@code androidx.health.connect.client.records.ExerciseSessionRecord}
 * companion constants. Transcribed 2026-08-16. Both of Google's reference pages render their
 * constant tables via JavaScript and cannot be scraped, so this was written out by hand — which
 * means <b>the codes are the part that matters</b>: a wrong name is cosmetic and the user can
 * rename it, a wrong code silently mislabels every workout of that activity.
 *
 * <p>Corroborated against the six codes this install had already mapped by hand — 0, 9, 25, 57,
 * 70, 79 — all of which agree with the labels chosen for them independently. The gaps in the
 * numbering (1, 3, 6, 7, 12, 15, 17–24, 30, 40–43, 45, 49, 67, 77) are real: those values are
 * unused in the enum.
 *
 * <p>Names are lightly edited for readability rather than mechanically de-underscored —
 * "Biking (stationary)" rather than "Biking stationary". No grouping judgment is encoded: there
 * is exactly one row per constant, and nothing is folded into an "Other" bucket.
 */
public final class ExerciseTypeCatalog {

    private ExerciseTypeCatalog() {
    }

    /**
     * Assigned by creation order, so colors are stable within an install and adjacent types
     * never collide. Extends the family already used by the demo seed.
     */
    private static final String[] PALETTE = {
            "#4F8DFF", "#2A9D8F", "#E9C46A", "#E63946", "#8B5CF6", "#457B9D",
            "#2DD4BF", "#F472B6", "#FACC15", "#FB923C", "#3DD68C", "#A78BFA"
    };

    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(0, "Other workout");
        NAMES.put(2, "Badminton");
        NAMES.put(4, "Baseball");
        NAMES.put(5, "Basketball");
        NAMES.put(8, "Biking");
        NAMES.put(9, "Biking (stationary)");
        NAMES.put(10, "Boot camp");
        NAMES.put(11, "Boxing");
        NAMES.put(13, "Calisthenics");
        NAMES.put(14, "Cricket");
        NAMES.put(16, "Dancing");
        NAMES.put(25, "Elliptical");
        NAMES.put(26, "Exercise class");
        NAMES.put(27, "Fencing");
        NAMES.put(28, "Football (American)");
        NAMES.put(29, "Football (Australian)");
        NAMES.put(31, "Frisbee disc");
        NAMES.put(32, "Golf");
        NAMES.put(33, "Guided breathing");
        NAMES.put(34, "Gymnastics");
        NAMES.put(35, "Handball");
        NAMES.put(36, "HIIT");
        NAMES.put(37, "Hiking");
        NAMES.put(38, "Ice hockey");
        NAMES.put(39, "Ice skating");
        NAMES.put(44, "Martial arts");
        NAMES.put(46, "Paddling");
        NAMES.put(47, "Paragliding");
        NAMES.put(48, "Pilates");
        NAMES.put(50, "Racquetball");
        NAMES.put(51, "Rock climbing");
        NAMES.put(52, "Roller hockey");
        NAMES.put(53, "Rowing");
        NAMES.put(54, "Rowing machine");
        NAMES.put(55, "Rugby");
        NAMES.put(56, "Running");
        NAMES.put(57, "Running (treadmill)");
        NAMES.put(58, "Sailing");
        NAMES.put(59, "Scuba diving");
        NAMES.put(60, "Skating");
        NAMES.put(61, "Skiing");
        NAMES.put(62, "Snowboarding");
        NAMES.put(63, "Snowshoeing");
        NAMES.put(64, "Soccer");
        NAMES.put(65, "Softball");
        NAMES.put(66, "Squash");
        NAMES.put(68, "Stair climbing");
        NAMES.put(69, "Stair climbing machine");
        NAMES.put(70, "Strength training");
        NAMES.put(71, "Stretching");
        NAMES.put(72, "Surfing");
        NAMES.put(73, "Swimming (open water)");
        NAMES.put(74, "Swimming (pool)");
        NAMES.put(75, "Table tennis");
        NAMES.put(76, "Tennis");
        NAMES.put(78, "Volleyball");
        NAMES.put(79, "Walking");
        NAMES.put(80, "Water polo");
        NAMES.put(81, "Weightlifting");
        NAMES.put(82, "Wheelchair");
        NAMES.put(83, "Yoga");
    }

    /**
     * A code absent from the catalog — a future Health Connect release — still gets a name and
     * is never dropped. The only decision available to the user is "yes, name it", so asking
     * would be theatre; they rename it in Settings.
     */
    public static String nameFor(int healthConnectType) {
        return NAMES.getOrDefault(healthConnectType, "Activity " + healthConnectType);
    }

    public static boolean isKnown(int healthConnectType) {
        return NAMES.containsKey(healthConnectType);
    }

    /**
     * The first palette colour nobody is using, or a generated one once the palette runs out.
     *
     * <p>Twelve curated colours is plenty for a hand-picked list and not for one that grows by
     * itself: with fine-grained types an install can pass twelve in a year, and simply cycling
     * the palette would put two activities in the same colour on every chart. Past the palette
     * the hue circle is walked by the golden angle, which keeps consecutive generated colours
     * far apart rather than adjacent.
     */
    public static String nextColor(Collection<String> colorsInUse) {
        Set<String> taken = colorsInUse.stream()
                .filter(Objects::nonNull)
                .map(color -> color.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        for (String hex : PALETTE) {
            if (!taken.contains(hex.toLowerCase(Locale.ROOT))) return hex;
        }
        for (int step = 1; step <= 360; step++) {
            String hex = generated(step);
            if (!taken.contains(hex.toLowerCase(Locale.ROOT))) return hex;
        }
        return PALETTE[0];
    }

    private static String generated(int step) {
        return hsl((step * 137.508) % 360, 0.55, 0.60);
    }

    private static String hsl(double hue, double saturation, double lightness) {
        double c = (1 - Math.abs(2 * lightness - 1)) * saturation;
        double x = c * (1 - Math.abs((hue / 60.0) % 2 - 1));
        double m = lightness - c / 2;
        double r, g, b;
        if (hue < 60)       { r = c; g = x; b = 0; }
        else if (hue < 120) { r = x; g = c; b = 0; }
        else if (hue < 180) { r = 0; g = c; b = x; }
        else if (hue < 240) { r = 0; g = x; b = c; }
        else if (hue < 300) { r = x; g = 0; b = c; }
        else                { r = c; g = 0; b = x; }
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    public static Map<Integer, String> all() {
        return Map.copyOf(NAMES);
    }

    static int paletteSize() {
        return PALETTE.length;
    }
}
