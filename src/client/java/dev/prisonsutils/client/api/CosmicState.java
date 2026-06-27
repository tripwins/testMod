package dev.prisonsutils.client.api;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The latest known Cosmic API session + data, updated on the client thread as messages arrive.
 *
 * <p>Features read from here instead of touching the channel directly: check {@link #connected()} /
 * {@link #hasScope(String)} and pull the most recent payload for an event type via {@link #latest(String)}.
 */
public final class CosmicState {
    private CosmicState() {}

    private static volatile boolean connected = false;
    private static volatile String sessionId = null;
    private static volatile String expiresAt = null;
    private static volatile int eventCount = 0;
    private static volatile String lastEventType = null;

    private static final Set<String> ALLOWED_SCOPES = new HashSet<>();
    private static final Set<String> ALLOWED_HOOKS = new HashSet<>();
    private static final Map<String, String> DENIED = new HashMap<>();      // scope/hook -> reason
    private static final Map<String, JsonObject> LATEST = new HashMap<>();  // event type -> last data

    public static boolean connected() { return connected; }
    public static String sessionId() { return sessionId; }
    public static String expiresAt() { return expiresAt; }
    public static int eventCount() { return eventCount; }
    public static String lastEventType() { return lastEventType; }

    public static boolean hasScope(String scope) { return ALLOWED_SCOPES.contains(scope); }
    public static boolean hasHook(String hook) { return ALLOWED_HOOKS.contains(hook); }
    public static String deniedReason(String scopeOrHook) { return DENIED.get(scopeOrHook); }
    public static Map<String, String> denied() { return Collections.unmodifiableMap(DENIED); }

    public static Set<String> allowedScopes() { return Collections.unmodifiableSet(ALLOWED_SCOPES); }
    public static Set<String> allowedHooks() { return Collections.unmodifiableSet(ALLOWED_HOOKS); }

    /** Last payload seen for an event type (e.g. {@code "player.cooldowns.changed"}), or null. */
    public static JsonObject latest(String eventType) { return LATEST.get(eventType); }

    static void onHandshake(String sid, String exp, Set<String> scopes, Set<String> hooks,
                            Map<String, String> denied) {
        sessionId = sid;
        expiresAt = exp;
        ALLOWED_SCOPES.clear(); ALLOWED_SCOPES.addAll(scopes);
        ALLOWED_HOOKS.clear(); ALLOWED_HOOKS.addAll(hooks);
        DENIED.clear(); DENIED.putAll(denied);
        connected = true;
    }

    static void onEvent(CosmicEvent e) {
        eventCount++;
        if (e.type != null) {
            lastEventType = e.type;
            if (e.data != null) {
                LATEST.put(e.type, e.data);
            }
        }
    }

    static void reset() {
        connected = false;
        sessionId = null;
        expiresAt = null;
        ALLOWED_SCOPES.clear();
        ALLOWED_HOOKS.clear();
        DENIED.clear();
        LATEST.clear();
        eventCount = 0;
        lastEventType = null;
    }
}
