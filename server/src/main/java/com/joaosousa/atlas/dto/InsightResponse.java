package com.joaosousa.atlas.dto;

import java.time.LocalDateTime;

public class InsightResponse {

    private final String verdict;
    private final String text;
    private final LocalDateTime generatedAt;
    private final boolean fallback;
    private final InsightState state;

    public InsightResponse(String verdict, String text, LocalDateTime generatedAt, InsightState state) {
        this.verdict = verdict;
        this.text = text;
        this.generatedAt = generatedAt;
        this.state = state;
        this.fallback = state != InsightState.OK;
    }

    public String getVerdict() { return verdict; }
    public String getText() { return text; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public InsightState getState() { return state; }

    /** @deprecated prefer {@link #getState()} — kept until the frontend stops reading it. */
    @Deprecated
    public boolean isFallback() { return fallback; }
}
