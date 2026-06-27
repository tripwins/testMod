package dev.prisonsutils.client.guard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.prisonsutils.PrisonsUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks manually-marked guards — placed via {@code /guard mark} or by right-clicking with a
 * wooden shovel. Entries persist to JSON.
 */
public final class GuardManager {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils-Guard");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<Guard> MANUAL = new CopyOnWriteArrayList<>();
    private static volatile long version = 0L;

    private GuardManager() {}

    public static List<Guard> guards() {
        return Collections.unmodifiableList(MANUAL);
    }

    public static List<Guard> manualGuards() {
        return Collections.unmodifiableList(MANUAL);
    }

    public static long version() {
        return version;
    }

    public static int count() {
        return MANUAL.size();
    }

    public static int manualCount() {
        return MANUAL.size();
    }

    public static void addManual(Guard g) {
        if (g == null) return;
        MANUAL.removeIf(existing -> existing.pos().equals(g.pos()));
        MANUAL.add(g);
        version++;
        save();
    }

    /**
     * Add a guard only if none is marked at that position yet. Returns {@code true} if a new mark
     * was added. Used by auto-mark so stationary guards don't churn the list (and trigger constant
     * ESP rebuilds) every scan.
     */
    public static boolean markIfAbsent(BlockPos pos, float yaw) {
        for (Guard g : MANUAL) {
            if (g.pos().equals(pos)) return false;
        }
        MANUAL.add(new Guard(pos, yaw));
        version++;
        save();
        return true;
    }

    public static boolean removeManual(BlockPos pos) {
        boolean removed = MANUAL.removeIf(g -> g.pos().equals(pos));
        if (removed) {
            version++;
            save();
        }
        return removed;
    }

    public static void clearManual() {
        if (MANUAL.isEmpty()) return;
        MANUAL.clear();
        version++;
        save();
    }

    // ----- persistence (manual only) -----

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(PrisonsUtils.MOD_ID + "-guards.json");
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        try {
            JsonElement el = GSON.fromJson(Files.readString(p), JsonElement.class);
            if (el == null || !el.isJsonArray()) return;
            JsonArray arr = el.getAsJsonArray();
            MANUAL.clear();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                int x = o.get("x").getAsInt();
                int y = o.get("y").getAsInt();
                int z = o.get("z").getAsInt();
                float yaw = o.get("yaw").getAsFloat();
                MANUAL.add(new Guard(new BlockPos(x, y, z), yaw));
            }
            version++;
            LOG.info("Loaded {} manual guards", MANUAL.size());
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to load guards", ex);
        }
    }

    private static void save() {
        Path p = file();
        try {
            JsonArray arr = new JsonArray();
            for (Guard g : MANUAL) {
                JsonObject o = new JsonObject();
                o.addProperty("x", g.pos().getX());
                o.addProperty("y", g.pos().getY());
                o.addProperty("z", g.pos().getZ());
                o.addProperty("yaw", g.yaw());
                arr.add(o);
            }
            Files.writeString(p, GSON.toJson(arr));
        } catch (IOException ex) {
            LOG.warn("Failed to save guards", ex);
        }
    }
}
