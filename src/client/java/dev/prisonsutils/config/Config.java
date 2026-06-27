package dev.prisonsutils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.prisonsutils.PrisonsUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Static holder + JSON persistence for {@link PrisonsConfig}. */
public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger(Config.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static PrisonsConfig instance;
    private static Path path;

    private Config() {}

    public static void init() {
        path = FabricLoader.getInstance().getConfigDir().resolve(PrisonsUtils.MOD_ID + ".json");
        load();
    }

    public static PrisonsConfig get() {
        if (instance == null) {
            instance = new PrisonsConfig();
        }
        return instance;
    }

    private static void load() {
        try {
            if (path != null && Files.exists(path)) {
                PrisonsConfig parsed = GSON.fromJson(Files.readString(path), PrisonsConfig.class);
                instance = parsed != null ? parsed : new PrisonsConfig();
            } else {
                instance = new PrisonsConfig();
            }
        } catch (Exception ex) {
            LOG.error("Failed to load config; using defaults", ex);
            instance = new PrisonsConfig();
        }
    }

    public static void save() {
        if (instance == null || path == null) {
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(instance));
        } catch (Exception ex) {
            LOG.error("Failed to save config", ex);
        }
    }
}
