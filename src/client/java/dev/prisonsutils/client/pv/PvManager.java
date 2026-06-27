package dev.prisonsutils.client.pv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.prisonsutils.PrisonsUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores and persists scraped player-vault contents so the inventory's Storage tab can render every
 * vault (all slots, including empty) without re-opening each one. Keyed by vault index (1..N).
 * Persisted as JSON in the config dir. Item visuals are reconstructed from id + name + lore.
 */
public final class PvManager {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils/PV");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A saved item slot: the slot index plus the full ItemStack serialized as SNBT (so name,
     *  lore, enchants and all components are reconstructed faithfully). */
    public static final class VaultItem {
        public int slot;
        public String nbt;

        public VaultItem() {}

        public VaultItem(int slot, String nbt) {
            this.slot = slot; this.nbt = nbt;
        }
    }

    /** A whole vault: its size (rows) plus its non-empty items. */
    public static final class Vault {
        public int rows = 6;
        public List<VaultItem> items = new ArrayList<>();

        public Vault() {}

        public Vault(int rows, List<VaultItem> items) {
            this.rows = rows; this.items = items;
        }
    }

    private static final Map<Integer, Vault> VAULTS = new TreeMap<>();
    private static Path path;

    private PvManager() {}

    public static void init() {
        path = FabricLoader.getInstance().getConfigDir().resolve(PrisonsUtils.MOD_ID + "-vaults.json");
        load();
    }

    public static synchronized void setVault(int index, int rows, List<VaultItem> items) {
        VAULTS.put(index, new Vault(rows, items));
    }

    public static synchronized void clear() {
        VAULTS.clear();
    }

    public static synchronized List<Integer> indices() {
        return new ArrayList<>(VAULTS.keySet());
    }

    public static synchronized Vault vault(int index) {
        return VAULTS.getOrDefault(index, new Vault());
    }

    public static synchronized int count() {
        return VAULTS.size();
    }

    private static void load() {
        try {
            if (path != null && Files.exists(path)) {
                Map<String, Vault> parsed = GSON.fromJson(Files.readString(path),
                        new TypeToken<TreeMap<String, Vault>>() {}.getType());
                VAULTS.clear();
                if (parsed != null) {
                    for (var e : parsed.entrySet()) {
                        try {
                            VAULTS.put(Integer.parseInt(e.getKey()), e.getValue());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to load vault cache", ex);
        }
    }

    public static synchronized void save() {
        if (path == null) return;
        try {
            Map<String, Vault> out = new TreeMap<>();
            for (var e : VAULTS.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, GSON.toJson(out));
        } catch (Exception ex) {
            LOG.error("Failed to save vault cache", ex);
        }
    }
}
