package com.joaosousa.atlas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A workout Atlas refused to log because its source is not enabled, kept raw so it can be
 * replayed through the identical code path once the user says yes.
 *
 * <p><b>Quarantine is not optional.</b> The sender transmits a delta, not a window: each sync
 * carries new changes plus the previous sync's newest workout. An entry the backend refuses is
 * in general never sent again — so "reject unknown sources and let the user enable them later"
 * would mean permanent data loss for everything received before the click, not deferred import.
 * Rejection therefore has to be paired with storage. See sync-source-allowlist-spec.md §1.4.
 *
 * <p>Fields are stored exactly as received, unparsed, so replay makes no assumption this class
 * would have to keep in step with {@code SyncService}.
 */
/**
 * <p>Its uniqueness constraint — {@code (data_origin, recording_method, type, start_time)}, so
 * the webhook's double-fire cannot quarantine the same entry twice — is created by
 * {@link com.joaosousa.atlas.service.SqliteIndexes}, not declared here. SQLite has no
 * {@code ALTER TABLE ... ADD CONSTRAINT}, so a JPA annotation would be silently inert.
 *
 * <p>Note this cannot help a <i>drifting</i> source: its re-sends carry different timestamps by
 * definition, so each is genuinely a new row. A quarantine count that climbs every sync is,
 * usefully, exactly what a drifting source looks like from the UI.
 */
@Entity
@Getter
@Setter
@Table(name = "quarantined_entries")
public class QuarantinedEntry {

    /** The only cause today. A column rather than an implication, so the table is self-describing. */
    public static final String SOURCE_NOT_ALLOWED = "SOURCE_NOT_ALLOWED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_origin")
    private String dataOrigin;

    @Column(name = "recording_method")
    private String recordingMethod;

    /** Raw and unparsed, mirroring {@code SyncRequest.ExerciseEntry.type}. */
    @Column(name = "type")
    private String type;

    /** Raw ISO string, unparsed. */
    @Column(name = "start_time")
    private String startTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "reason")
    private String reason;
}
