package dev.prisonsutils.client.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.prisonsutils.config.Config;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls the CosmicPrisons bans API and pops a toast for each new punishment. */
public final class PunishmentNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(PunishmentNotifier.class);
    private static final String ENDPOINT = "https://www.cosmicprisons.com/api/bans?page=1&pageSize=10";
    private static final long INITIAL_DELAY_SECONDS = 5L;
    private static final long POLL_INTERVAL_SECONDS = 60L;
    private static final int MAX_TRACKED_IDS = 200;

    private static final int COLOR_BAN = 0xE74C3C;
    private static final int COLOR_TEMP_BAN = 0xE67E22;
    private static final int COLOR_WARNING = 0xF1C40F;
    private static final int COLOR_OTHER = 0x9B59B6;

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Set<String> seenIds = new HashSet<>();
    private static ScheduledExecutorService scheduler;
    private static boolean primed;

    private PunishmentNotifier() {}

    public static void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "prisonsutils-punishment-poll");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
                PunishmentNotifier::pollSafely, INITIAL_DELAY_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void pollSafely() {
        try {
            poll();
        } catch (Exception ex) {
            LOG.warn("Punishment poll failed: {}", ex.toString());
        }
    }

    private static void poll() throws Exception {
        if (!Config.get().notificationsEnabled || !Config.get().banNotificationsEnabled) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("User-Agent", "PrisonsUtils/0.1")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOG.warn("Punishment poll non-200: {}", response.statusCode());
            return;
        }
        handleResponse(response.body());
    }

    private static void handleResponse(String body) {
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception ex) {
            LOG.warn("Punishment poll: invalid JSON ({})", ex.getMessage());
            return;
        }
        JsonArray entries = root.has("entries") ? root.getAsJsonArray("entries") : null;
        if (entries == null) return;

        List<JsonObject> newEntries = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            String id = optString(entry, "id", null);
            if (id == null || seenIds.contains(id)) continue;
            seenIds.add(id);
            if (primed) newEntries.add(entry);
        }
        trimSeenIds(entries);

        if (!primed) {
            primed = true;
            return;
        }
        if (newEntries.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        for (JsonObject entry : newEntries) {
            Notification notification = buildNotification(entry);
            client.execute(() -> NotificationManager.push(notification));
        }
    }

    private static void trimSeenIds(JsonArray currentEntries) {
        if (seenIds.size() <= MAX_TRACKED_IDS) return;
        Set<String> currentIds = new HashSet<>();
        for (JsonElement element : currentEntries) {
            String id = optString(element.getAsJsonObject(), "id", null);
            if (id != null) currentIds.add(id);
        }
        seenIds.retainAll(currentIds);
    }

    private static Notification buildNotification(JsonObject entry) {
        String type = optString(entry, "type", "Punishment");
        String username = optString(entry, "username", "Unknown");
        String reason = optString(entry, "reason", "");
        String duration = optString(entry, "duration", null);
        String staff = optString(entry, "staffMember", "Unknown");

        int color = colorForType(type);
        String title = type + ": " + username;
        List<String> lines = new ArrayList<>();
        if (!reason.isEmpty()) lines.add(reason);
        if (duration != null && !duration.isEmpty()) lines.add("Duration: " + duration);
        lines.add("By " + staff);
        return new Notification(title, lines, color);
    }

    private static int colorForType(String type) {
        if (type == null) return COLOR_OTHER;
        String lower = type.toLowerCase();
        if (lower.contains("warn")) return COLOR_WARNING;
        if (lower.contains("temp")) return COLOR_TEMP_BAN;
        if (lower.contains("ban")) return COLOR_BAN;
        return COLOR_OTHER;
    }

    private static String optString(JsonObject object, String key, String defaultValue) {
        if (object == null || !object.has(key)) return defaultValue;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) return defaultValue;
        return element.getAsString();
    }
}
