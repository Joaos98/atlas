package com.joaosousa.atlas.dto;

import com.joaosousa.atlas.entity.AppSettings;

/**
 * What {@code GET /api/settings} serves. The endpoint has no auth by design (self-host plan
 * §3), so it is readable by anything on the LAN — which is why the insight API key is
 * represented here by a configured flag and its last four characters, and why this type has
 * no field capable of holding the key itself.
 *
 * <p>Serialising the {@link AppSettings} entity directly, as this controller used to, would
 * have leaked the key the moment the column existed. See insight-provider-spec.md §3.2.
 */
public class AppSettingsDto {

    private final int targetWorkoutsPerWeek;
    private final String insightBaseUrl;
    private final String insightModel;
    private final boolean insightApiKeyConfigured;
    private final String insightApiKeyLast4;

    public AppSettingsDto(int targetWorkoutsPerWeek,
                          String insightBaseUrl,
                          String insightModel,
                          boolean insightApiKeyConfigured,
                          String insightApiKeyLast4) {
        this.targetWorkoutsPerWeek = targetWorkoutsPerWeek;
        this.insightBaseUrl = insightBaseUrl;
        this.insightModel = insightModel;
        this.insightApiKeyConfigured = insightApiKeyConfigured;
        this.insightApiKeyLast4 = insightApiKeyLast4;
    }

    public static AppSettingsDto from(AppSettings settings) {
        String key = settings.getInsightApiKey();
        boolean configured = key != null && !key.isBlank();
        return new AppSettingsDto(
                settings.getTargetWorkoutsPerWeek(),
                settings.getInsightBaseUrl(),
                settings.getInsightModel(),
                configured,
                configured ? last4(key) : null
        );
    }

    /** Short keys are hinted at rather than echoed; four characters of a five-character key is not a hint. */
    private static String last4(String key) {
        return key.length() > 4 ? key.substring(key.length() - 4) : null;
    }

    public int getTargetWorkoutsPerWeek() { return targetWorkoutsPerWeek; }
    public String getInsightBaseUrl() { return insightBaseUrl; }
    public String getInsightModel() { return insightModel; }
    public boolean isInsightApiKeyConfigured() { return insightApiKeyConfigured; }
    public String getInsightApiKeyLast4() { return insightApiKeyLast4; }
}
