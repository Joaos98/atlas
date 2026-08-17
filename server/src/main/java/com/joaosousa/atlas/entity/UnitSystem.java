package com.joaosousa.atlas.entity;

/**
 * Display preference only. The database is always canonical metric — see the
 * "Units at the display boundary" design note on the Saturn docs hub — so nothing
 * about storage or arithmetic branches on this;
 * it is read at the two places that render a unit to a human, and nowhere else.
 */
public enum UnitSystem {
    METRIC,
    IMPERIAL
}
