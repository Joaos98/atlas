package com.joaosousa.atlas.entity;

/**
 * Display preference only. The database is always canonical metric — see
 * units-preference-spec.md §2 — so nothing about storage or arithmetic branches on this;
 * it is read at the two places that render a unit to a human, and nowhere else.
 */
public enum UnitSystem {
    METRIC,
    IMPERIAL
}
