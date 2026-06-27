package dev.prisonsutils.client.warp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.prisonsutils.PrisonsUtils;
import dev.prisonsutils.client.pv.PvItems;
import dev.prisonsutils.client.warp.WarpInterceptor.IndexedStack;
import dev.prisonsutils.client.warp.WarpInterceptor.WarpEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists the last-seen {@code /warp} layout so {@link WarpScreen} can paint instantly from cache
 * while the live server handler (re)populates — no more blank "Loading warps…" gap. Only the icons
 * and their positions come from cache; live data (player counts, KOTH timers) and click-to-teleport
 * take over the moment the server syncs the real container (slot indices are stable, so cached-frame
 * clicks still hit the right slot). Item visuals are reconstructed faithfully from SNBT.
 */
public final class WarpManager {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils/Warp");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A cached warp slot: the container slot index plus its ItemStack serialized as SNBT. */
    public static final class CachedSlot {
        public int slot;
        public String nbt;

        public CachedSlot() {}

        public CachedSlot(int slot, String nbt) {
            this.slot = slot;
            this.nbt = nbt;
        }
    }

    private static final List<CachedSlot> SLOTS = new ArrayList<>();
    private static Path path;

    private WarpManager() {}

    public static void init() {
        path = FabricLoader.getInstance().getConfigDir().resolve(PrisonsUtils.MOD_ID + "-warps.json");
        load();
    }

    /** Write-through: snapshot the live warp container's non-empty slots to disk. */
    public static synchronized void update(GenericContainerScreenHandler handler) {
        RegistryOps<NbtElement> ops = PvItems.ops();
        if (ops == null) return;
        List<CachedSlot> out = new ArrayList<>();
        int container = handler.getRows() * 9;
        for (int i = 0; i < container && i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack == null || stack.isEmpty()) continue;
            try {
                out.add(new CachedSlot(i, PvItems.encode(ops, stack)));
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) return; // never clobber a good cache with an unsynced (empty) handler
        SLOTS.clear();
        SLOTS.addAll(out);
        save();
    }

    /** Warp entries reconstructed from the cache (empty if nothing cached or the world isn't ready). */
    public static synchronized List<WarpEntry> cachedEntries() {
        RegistryOps<NbtElement> ops = PvItems.ops();
        if (ops == null || SLOTS.isEmpty()) return List.of();
        List<IndexedStack> stacks = new ArrayList<>();
        for (CachedSlot cs : SLOTS) {
            ItemStack st = PvItems.fromNbt(ops, cs.nbt);
            if (!st.isEmpty()) stacks.add(new IndexedStack(cs.slot, st));
        }
        return WarpInterceptor.parseStacks(stacks);
    }

    private static void load() {
        try {
            if (path != null && Files.exists(path)) {
                List<CachedSlot> parsed = GSON.fromJson(Files.readString(path),
                        new TypeToken<ArrayList<CachedSlot>>() {}.getType());
                SLOTS.clear();
                if (parsed != null) SLOTS.addAll(parsed);
            }
        } catch (Exception ex) {
            LOG.error("Failed to load warp cache", ex);
        }
    }

    public static synchronized void save() {
        if (path == null) return;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, GSON.toJson(SLOTS));
        } catch (Exception ex) {
            LOG.error("Failed to save warp cache", ex);
        }
    }
}
