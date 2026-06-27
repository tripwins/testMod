package dev.prisonsutils.client.api;

import com.google.gson.JsonObject;

/**
 * A parsed hook-event envelope from {@code cosmicapi:main}. {@link #data} is the event-specific
 * payload (may be null); {@link #raw} is the full envelope for anything not surfaced as a field.
 */
public final class CosmicEvent {
    public final String id;
    public final String type;
    public final int version;
    public final String createdAt;
    public final String serverScope;
    public final JsonObject data;
    public final JsonObject raw;

    public CosmicEvent(String id, String type, int version, String createdAt,
                       String serverScope, JsonObject data, JsonObject raw) {
        this.id = id;
        this.type = type;
        this.version = version;
        this.createdAt = createdAt;
        this.serverScope = serverScope;
        this.data = data;
        this.raw = raw;
    }

    public boolean isType(String t) {
        return t != null && t.equals(type);
    }
}
