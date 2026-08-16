package com.joaosousa.atlas.dto;

/**
 * What {@code PUT /api/settings} accepts. Every field is nullable and <b>absent means
 * unchanged</b>, so a client may send only what it is editing — the settings row is a
 * singleton that several unrelated screens write to, and a partial save must not blank the
 * fields it said nothing about.
 *
 * <p>The API key carries the one extra rule from insight-provider-spec.md §3.2: blank also
 * means unchanged, because the client has no way to read the current key back and would
 * otherwise wipe it on every unrelated save. Clearing therefore needs its own explicit
 * signal, {@link #isClearInsightApiKey()}.
 */
public class AppSettingsUpdateRequest {

    private Integer targetWorkoutsPerWeek;
    private String insightBaseUrl;
    private String insightModel;
    private String insightApiKey;
    private boolean clearInsightApiKey;

    public Integer getTargetWorkoutsPerWeek() { return targetWorkoutsPerWeek; }
    public void setTargetWorkoutsPerWeek(Integer targetWorkoutsPerWeek) { this.targetWorkoutsPerWeek = targetWorkoutsPerWeek; }

    public String getInsightBaseUrl() { return insightBaseUrl; }
    public void setInsightBaseUrl(String insightBaseUrl) { this.insightBaseUrl = insightBaseUrl; }

    public String getInsightModel() { return insightModel; }
    public void setInsightModel(String insightModel) { this.insightModel = insightModel; }

    public String getInsightApiKey() { return insightApiKey; }
    public void setInsightApiKey(String insightApiKey) { this.insightApiKey = insightApiKey; }

    public boolean isClearInsightApiKey() { return clearInsightApiKey; }
    public void setClearInsightApiKey(boolean clearInsightApiKey) { this.clearInsightApiKey = clearInsightApiKey; }
}
