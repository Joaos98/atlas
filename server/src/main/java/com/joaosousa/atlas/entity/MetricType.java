package com.joaosousa.atlas.entity;

/**
 * These are <b>opaque identifiers, not descriptions</b>. {@code BODY_FAT_KG} means "body fat
 * as a mass"; the unit token in the name is legacy and does not imply the value is displayed
 * in kilograms — under an imperial preference it is shown in pounds.
 *
 * <p>Renaming it to {@code BODY_FAT_MASS} was considered and rejected in
 * units-preference-spec.md §2.2: the value is persisted as a string in {@code goals.metric_type}
 * and this app has no migration tooling, so the rename costs a hand-written one-shot data fixup,
 * kept forever, for a cosmetic gain. Every user-visible label comes from a lookup, so the
 * identifier never reaches the screen.
 */
public enum MetricType {
    WEIGHT, MUSCLE_MASS, WATER, BODY_FAT_KG, BODY_FAT_PCT
}
