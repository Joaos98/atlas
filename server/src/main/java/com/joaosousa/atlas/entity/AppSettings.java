package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "app_settings")
public class AppSettings {
    public static final long SETTINGS_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_workouts_per_week")
    private int targetWorkoutsPerWeek;

    /**
     * Provider selector for insights: any OpenAI-compatible chat-completions base URL.
     * Seeded to Gemini's compat endpoint rather than left blank so the field documents
     * its own format — see the "Insights and providers" design note on the Saturn docs hub.
     */
    @Column(name = "insight_base_url")
    private String insightBaseUrl;

    /**
     * Never leaves the server. {@code AppSettingsDto} has no field for it, which is what
     * keeps {@code GET /api/settings} — unauthenticated by design — from serving it.
     */
    @Column(name = "insight_api_key")
    private String insightApiKey;

    @Column(name = "insight_model")
    private String insightModel;

    /**
     * Which units to render in. Storage stays canonical metric regardless — this is read at
     * the display boundary only.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_system")
    private UnitSystem unitSystem;
}
