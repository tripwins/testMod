package dev.prisonsutils.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.prisonsutils.PrisonsUtils;
import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.config.Config;
import dev.prisonsutils.config.PrisonsConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the CosmicPrisons API over the {@code cosmicapi:main} plugin channel.
 *
 * <p>Off by default. When {@code cosmicApiEnabled} is set and a {@code clientId} is configured, this
 * sends a {@code client_hello} on join, tracks the granted session, and dispatches hook events into
 * {@link CosmicState} and any registered listeners. Guards are intentionally NOT requested (see
 * {@link CosmicScopes}); the existing manual / name-scan guard system is unchanged.
 *
 * <p>Nothing is ever sent unless the server actually advertises the channel
 * ({@link ClientPlayNetworking#canSend}), so this is inert on normal servers and while the API is
 * unavailable. The wire format (handshake + event shapes) follows the published docs but has not been
 * verified against a live server yet.
 */
public final class CosmicApi {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils/CosmicApi");
    private static final int DEDUPE_MEMORY = 256;

    private static final Set<String> SEEN_EVENT_IDS = new HashSet<>();
    private static final Deque<String> SEEN_ORDER = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Consumer<CosmicEvent>> LISTENERS = new CopyOnWriteArrayList<>();

    private static boolean registered = false;

    // While disconnected, retry the handshake whenever the channel is available — on CosmicPrisons the
    // channel only registers after the proxy hands you to a sub-server, well after JOIN fires, so we
    // must keep trying rather than give up once.
    private static int retryCooldown = 0;
    private static boolean notifiedSent = false;
    private static boolean notifiedUnrecognized = false;
    private static final int RETRY_INTERVAL_TICKS = 60; // ~3s between attempts while disconnected

    // Registered CosmicPrisons app for this build, kept in code so a stale config file can't override
    // it. Swap these when you register/change the app.
    public static final String CLIENT_ID = "client_mqnfk5ucndjz8669ig";
    public static final String MOD_ID = "this-is-private-for-testing";

    private CosmicApi() {}

    /** Subscribe to hook events (invoked on the client thread). Safe to call at init. */
    public static void addListener(Consumer<CosmicEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Register the channel, receiver, and connection hooks. Call once at client init. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // Declare the channel + codec so we can both send and receive. Harmless on servers that
        // don't speak it (Fabric just advertises support; nothing is sent until handshake time).
        PayloadTypeRegistry.playC2S().register(CosmicPayload.ID, CosmicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicPayload.ID, CosmicPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(CosmicPayload.ID, (payload, context) ->
                context.client().execute(() -> handleIncoming(payload.json())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(CosmicApi::requestHandshake));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(CosmicApi::onDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(CosmicApi::onTick);
    }

    /** Force an immediate handshake attempt on the next tick (used by join + /cosmicapi resend). */
    public static void requestHandshake() {
        retryCooldown = 0;
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || !Config.get().cosmicApiEnabled || CosmicState.connected()) {
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        // Keep retrying until we have a session: the channel can come up late (proxy sub-server
        // transfer), so we re-attempt every few seconds while disconnected and the channel is ready.
        if (ClientPlayNetworking.canSend(CosmicPayload.ID)) {
            sendHello();
        }
        retryCooldown = RETRY_INTERVAL_TICKS;
    }

    /** Send the {@code client_hello} handshake, if enabled, configured, and the server speaks the channel. */
    public static void sendHello() {
        PrisonsConfig cfg = Config.get();
        if (!cfg.cosmicApiEnabled) {
            return;
        }
        if (!ClientPlayNetworking.canSend(CosmicPayload.ID)) {
            // Server doesn't advertise cosmicapi:main — there's nothing to talk to here.
            return;
        }

        JsonObject hello = new JsonObject();
        hello.addProperty("v", 1);
        hello.addProperty("kind", "client_hello");
        hello.addProperty("clientId", CLIENT_ID);
        hello.addProperty("modId", MOD_ID);
        hello.addProperty("installId", installId(cfg));
        hello.addProperty("modVersion", PrisonsUtils.MOD_VERSION);
        hello.addProperty("minecraftVersion", minecraftVersion());
        hello.add("requestedScopes", toArray(CosmicScopes.REQUESTED_SCOPES));
        hello.add("requestedHooks", toArray(CosmicScopes.REQUESTED_HOOKS));

        send(hello.toString());
        LOG.info("Sent Cosmic API client_hello (clientId={})", CLIENT_ID);
        if (!notifiedSent) {
            notifiedSent = true;
            chat("§7[§bCosmicAPI§7] §7Handshake sent — waiting for reply…");
        }
    }

    private static void send(String json) {
        try {
            ClientPlayNetworking.send(new CosmicPayload(json));
        } catch (Exception ex) {
            LOG.warn("Failed to send on cosmicapi:main", ex);
        }
    }

    private static void chat(String legacy) {
        var p = MinecraftClient.getInstance().player;
        if (p != null) {
            p.sendMessage(Chat.of(legacy), false);
        }
    }

    private static void onDisconnect() {
        CosmicState.reset();
        SEEN_EVENT_IDS.clear();
        SEEN_ORDER.clear();
        retryCooldown = 0;
        notifiedSent = false;
        notifiedUnrecognized = false;
    }

    /** Parse and route one inbound message (client thread). */
    static void handleIncoming(String json) {
        LOG.info("cosmicapi:main <- {}", json.length() > 2000 ? json.substring(0, 2000) + "…" : json);
        JsonObject obj;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) {
                return;
            }
            obj = el.getAsJsonObject();
        } catch (Exception ex) {
            LOG.warn("Unparseable cosmicapi:main message", ex);
            return;
        }

        // The handshake reply carries a sessionId + allowed lists; hook events carry a type + id.
        if (obj.has("sessionId") || obj.has("allowedScopes")) {
            handleHandshake(obj);
        } else if (obj.has("type") && obj.has("id")) {
            handleEvent(obj);
        } else if (!notifiedUnrecognized) {
            notifiedUnrecognized = true;
            chat("§7[§bCosmicAPI§7] §eUnrecognized reply: §8"
                    + (json.length() > 140 ? json.substring(0, 140) + "…" : json));
        }
    }

    private static void handleHandshake(JsonObject obj) {
        String sid = optString(obj, "sessionId");
        String exp = optString(obj, "expiresAt");
        Set<String> scopes = toSet(optArray(obj, "allowedScopes"));
        Set<String> hooks = toSet(optArray(obj, "allowedHooks"));
        Map<String, String> denied = new HashMap<>();
        collectDenied(obj, "deniedScopes", "scope", denied);
        collectDenied(obj, "deniedHooks", "type", denied);

        CosmicState.onHandshake(sid, exp, scopes, hooks, denied);
        LOG.info("Cosmic API session established: {} scopes, {} hooks, {} denied",
                scopes.size(), hooks.size(), denied.size());

        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Chat.of("§7[§bCosmicAPI§7] §aConnected §7— §f" + scopes.size()
                    + "§7 scopes, §f" + hooks.size() + "§7 hooks"
                    + (denied.isEmpty() ? "" : "§7, §e" + denied.size() + " denied")), false);
        }
    }

    private static void handleEvent(JsonObject obj) {
        String id = optString(obj, "id");
        if (id == null || !remember(id)) {
            return; // dedupe — delivery is at-least-once
        }

        CosmicEvent event = new CosmicEvent(
                id,
                optString(obj, "type"),
                obj.has("version") && obj.get("version").isJsonPrimitive() ? obj.get("version").getAsInt() : 1,
                optString(obj, "createdAt"),
                optString(obj, "serverScope"),
                obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null,
                obj);

        CosmicState.onEvent(event);
        for (Consumer<CosmicEvent> l : LISTENERS) {
            try {
                l.accept(event);
            } catch (Exception ex) {
                LOG.warn("Cosmic API listener threw for {}", event.type, ex);
            }
        }
    }

    /** @return true if this id is new (and records it); false if already seen. */
    private static boolean remember(String id) {
        if (!SEEN_EVENT_IDS.add(id)) {
            return false;
        }
        SEEN_ORDER.addLast(id);
        if (SEEN_ORDER.size() > DEDUPE_MEMORY) {
            String old = SEEN_ORDER.pollFirst();
            if (old != null) {
                SEEN_EVENT_IDS.remove(old);
            }
        }
        return true;
    }

    // ---- helpers -----------------------------------------------------------


    private static String installId(PrisonsConfig cfg) {
        if (cfg.cosmicInstallId == null || cfg.cosmicInstallId.isBlank()) {
            cfg.cosmicInstallId = "ins_" + UUID.randomUUID();
            Config.save();
        }
        return cfg.cosmicInstallId;
    }

    private static String minecraftVersion() {
        try {
            return MinecraftClient.getInstance().getGameVersion();
        } catch (Exception ex) {
            return PrisonsUtils.MOD_VERSION;
        }
    }

    private static JsonArray toArray(String[] values) {
        JsonArray arr = new JsonArray();
        for (String v : values) {
            arr.add(v);
        }
        return arr;
    }

    private static JsonArray optArray(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : null;
    }

    private static Set<String> toSet(JsonArray arr) {
        Set<String> out = new HashSet<>();
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonPrimitive()) {
                    out.add(el.getAsString());
                }
            }
        }
        return out;
    }

    private static void collectDenied(JsonObject obj, String key, String nameField, Map<String, String> out) {
        JsonArray arr = optArray(obj, key);
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String name = optString(o, nameField);
            String reason = optString(o, "reason");
            if (name != null) {
                out.put(name, reason != null ? reason : "denied");
            }
        }
    }

    private static String optString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }
}
