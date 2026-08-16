package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A device/app pairing Atlas has received workouts from, and whether it is trusted to log them.
 *
 * <p>This is a <b>dedup-safety gate, not a device preference</b>. Phone activity-detection
 * records revise their timestamps between syncs, so the signature
 * {@code startEpochMillis|healthConnectType} is different every time and such a record can
 * never be deduplicated — it would accumulate a fresh copy on every delivery. Watch-recorded
 * sessions are whole-second and byte-identical across re-sends. That distinction, not the
 * vendor, is what the list is really tracking.
 *
 * <p>The key is the <b>pair</b>, not the origin alone: one vendor can emit both a stable and a
 * drifting stream. Fresh installs start empty — no vendor ships as a product default, the app
 * learns from real payloads and asks.
 *
 * <p>See sync-source-allowlist-spec.md §1.1 and §4.2.
 */
@Entity
@Getter
@Setter
@Table(name = "sync_sources")
@IdClass(SyncSource.Key.class)
public class SyncSource {

    /** Substituted when a payload carries no metadata at all — see {@link #NONE}. */
    public static final String NONE = "(none)";

    @Id
    @Column(name = "data_origin")
    private String dataOrigin;

    @Id
    @Column(name = "recording_method")
    private String recordingMethod;

    @Column(name = "allowed")
    private boolean allowed;

    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    /**
     * Entry counts are deliberately not stored here — they are derived from
     * {@code quarantined_entries} so there is one source of truth.
     */
    public static class Key implements java.io.Serializable {
        private String dataOrigin;
        private String recordingMethod;

        public Key() {
        }

        public Key(String dataOrigin, String recordingMethod) {
            this.dataOrigin = dataOrigin;
            this.recordingMethod = recordingMethod;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return java.util.Objects.equals(dataOrigin, key.dataOrigin)
                    && java.util.Objects.equals(recordingMethod, key.recordingMethod);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(dataOrigin, recordingMethod);
        }
    }
}
