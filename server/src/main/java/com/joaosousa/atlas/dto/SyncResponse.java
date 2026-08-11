package com.joaosousa.atlas.dto;

public class SyncResponse {
    private final int created;
    private final int skipped;

    public SyncResponse(int created, int skipped) {
        this.created = created;
        this.skipped = skipped;
    }

    public int getCreated() { return created; }
    public int getSkipped() { return skipped; }
}
