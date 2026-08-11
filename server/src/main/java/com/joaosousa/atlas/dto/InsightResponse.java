package com.joaosousa.atlas.dto;

import java.time.LocalDateTime;

public class InsightResponse {

    private final String verdict;
    private final String text;
    private final LocalDateTime generatedAt;
    private final boolean fallback;

    public InsightResponse(String verdict, String text, LocalDateTime generatedAt, boolean fallback) {
        this.verdict = verdict;
        this.text = text;
        this.generatedAt = generatedAt;
        this.fallback = fallback;
    }

    public String getVerdict() { return verdict; }
    public String getText() { return text; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public boolean isFallback() { return fallback; }
}
