package com.joaosousa.atlas.dto;

import java.util.List;

/**
 * Four outcomes, deliberately counted apart, because each has a different fix:
 *
 * <ul>
 *   <li>{@code created} — logged.</li>
 *   <li>{@code skipped} — duplicate, malformed type, or unparseable timestamp. Nothing to do.</li>
 *   <li>{@code rejected} — the source is not enabled. Quarantined; enable it to recover them.</li>
 *   <li>{@code ignored} — the activity is mapped to nothing on purpose. Working as asked.</li>
 * </ul>
 *
 * <p>Additive: the frontend never calls {@code POST /api/sync} and the demo does not implement
 * it, so nothing breaks. These fields exist for curl-debugging and the log line — the phone
 * posts here and reads nothing back, so the UI is where discovery actually happens.
 */
public class SyncResponse {

    private final int created;
    private final int skipped;
    private final int rejected;
    private final int ignored;
    private final List<RejectedSource> rejectedSources;

    public SyncResponse(int created, int skipped) {
        this(created, skipped, 0, 0, List.of());
    }

    public SyncResponse(int created, int skipped, int rejected, int ignored,
                        List<RejectedSource> rejectedSources) {
        this.created = created;
        this.skipped = skipped;
        this.rejected = rejected;
        this.ignored = ignored;
        this.rejectedSources = rejectedSources;
    }

    public int getCreated() { return created; }
    public int getSkipped() { return skipped; }
    public int getRejected() { return rejected; }
    public int getIgnored() { return ignored; }
    public List<RejectedSource> getRejectedSources() { return rejectedSources; }

    public record RejectedSource(String origin, String method, int count) {}
}
