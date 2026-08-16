package com.joaosousa.atlas;

import com.joaosousa.atlas.service.ExerciseTypeCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers exercise-type-vocabulary-spec.md §7 case 8. */
class ExerciseTypeCatalogTest {

    @Test
    void hasNoBlankNamesAndNoDuplicates() {
        Map<Integer, String> all = ExerciseTypeCatalog.all();
        assertFalse(all.isEmpty());

        Set<String> names = new HashSet<>();
        for (Map.Entry<Integer, String> entry : all.entrySet()) {
            assertNotNull(entry.getValue(), "null name for code " + entry.getKey());
            assertFalse(entry.getValue().isBlank(), "blank name for code " + entry.getKey());
            assertTrue(names.add(entry.getValue()),
                    "duplicate name '" + entry.getValue() + "' — two codes would collide into one type");
        }
    }

    /**
     * The six codes this install mapped by hand, before any catalog existed. They are the only
     * ground truth available for the transcription, so they are pinned: a change here means
     * either a transcription fix or a silent relabel of real history.
     */
    @Test
    void agreesWithTheCodesThisInstallAlreadyMapped() {
        assertEquals("Other workout", ExerciseTypeCatalog.nameFor(0));
        assertEquals("Biking (stationary)", ExerciseTypeCatalog.nameFor(9));
        assertEquals("Elliptical", ExerciseTypeCatalog.nameFor(25));
        assertEquals("Running (treadmill)", ExerciseTypeCatalog.nameFor(57));
        assertEquals("Strength training", ExerciseTypeCatalog.nameFor(70));
        assertEquals("Walking", ExerciseTypeCatalog.nameFor(79));
    }

    @Test
    void unknownCodesGetAGenericNameRatherThanBeingDropped() {
        assertFalse(ExerciseTypeCatalog.isKnown(9999));
        assertEquals("Activity 9999", ExerciseTypeCatalog.nameFor(9999));
    }

    /** The gaps are real, not omissions — asserted so a future "fix" has to be deliberate. */
    @Test
    void theUnusedCodeRangesStayUnused() {
        for (int code : new int[]{1, 3, 6, 7, 12, 15, 20, 30, 41, 45, 49, 67, 77}) {
            assertFalse(ExerciseTypeCatalog.isKnown(code), "code " + code + " is not a Health Connect constant");
        }
    }

    @Test
    void everyTypeGetsAColorHoweverManyExist() {
        for (int count = 0; count < ExerciseTypeCatalog.all().size() + 5; count++) {
            String color = ExerciseTypeCatalog.colorFor(count);
            assertTrue(color.matches("#[0-9A-Fa-f]{6}"), "bad color at index " + count + ": " + color);
        }
    }
}
