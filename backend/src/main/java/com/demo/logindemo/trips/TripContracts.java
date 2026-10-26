package com.demo.logindemo.trips;

import com.fasterxml.jackson.databind.JsonNode;

public final class TripContracts {
    private TripContracts() {}
    public record Snapshot(String tripId, long version, String title, JsonNode document, long updatedAt) {}
    public record Summary(String tripId, long version, String title, long updatedAt) {}
    public record Write(long baseVersion, String mutationId, String title, JsonNode document, boolean makeCurrent) {}
    public record Stored(long userId, Snapshot snapshot) {}
    public record Receipt(String requestHash, Snapshot snapshot) {}
    public static final class Conflict extends RuntimeException {
        private final Snapshot remote;
        public Conflict(Snapshot remote) { super("云端已有更新，请选择保留方式"); this.remote = remote; }
        public Snapshot getRemote() { return remote; }
    }
}
