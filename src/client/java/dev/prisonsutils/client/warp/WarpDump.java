package dev.prisonsutils.client.warp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.prisonsutils.PrisonsUtils;
import dev.prisonsutils.client.util.Chat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Debug tool: dumps an open container GUI (e.g. the server's /warp chest) — screen class, handler
 * class, title, and every slot's id/name/lore — to {@code config/prisonsutils-warpdump.json}.
 *
 * <p>Two triggers, for maximum reliability on Lunar:
 * <ul>
 *   <li><b>Auto</b>: {@code WarpDumpMixin} calls {@link #onContainerOpen} when any server-opened
 *       container (syncId != 0) appears. This is the primary path — it doesn't depend on tick
 *       events or key detection.</li>
 *   <li><b>Manual</b>: press INSERT while a container is open (polled via {@link ClientTickEvents}).</li>
 * </ul>
 */
public final class WarpDump {
    private static final Logger LOG = LoggerFactory.getLogger("PrisonsUtils/WarpDump");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DUMP_KEY = GLFW.GLFW_KEY_INSERT;

    private static boolean keyWasDown = false;
    private static int lastDumpedSyncId = Integer.MIN_VALUE;

    private WarpDump() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WarpDump::onTick);
    }

    /**
     * Called every frame by {@code WarpDumpMixin}. The server populates the container slots a tick
     * or two AFTER the screen opens, so we wait until at least one container slot is non-empty
     * before dumping (otherwise we'd only capture the player's own inventory).
     */
    public static void onScreenRender(HandledScreen<?> hs) {
        if (hs == null) return;
        ScreenHandler handler = hs.getScreenHandler();
        if (handler == null) return;
        // syncId 0 is the player's own inventory; anything else was opened by the server.
        if (handler.syncId == 0) return;
        if (handler.syncId == lastDumpedSyncId) return; // already dumped this menu

        int containerCount = containerSlotCount(handler);
        boolean populated = false;
        for (int i = 0; i < containerCount && i < handler.slots.size(); i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                populated = true;
                break;
            }
        }
        if (!populated) return; // contents haven't arrived yet

        lastDumpedSyncId = handler.syncId;
        dump(MinecraftClient.getInstance(), hs, "auto");
    }

    /** Number of leading slots belonging to the container itself (not the player inventory). */
    private static int containerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler gc) {
            return gc.getRows() * 9;
        }
        return Math.max(0, handler.slots.size() - 36); // 36 = player main(27) + hotbar(9)
    }

    private static void onTick(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) return;
        boolean down = InputUtil.isKeyPressed(mc.getWindow(), DUMP_KEY);
        if (down && !keyWasDown) {
            if (mc.currentScreen instanceof HandledScreen<?> hs) {
                dump(mc, hs, "manual");
            } else {
                chat(mc, "§c[WarpDump] No container screen open. Open the /warp menu, then press Insert.");
            }
        }
        keyWasDown = down;
        // Reset dedupe when no screen is open so reopening the same menu dumps again.
        if (mc.currentScreen == null) lastDumpedSyncId = Integer.MIN_VALUE;
    }

    private static void dump(MinecraftClient mc, HandledScreen<?> hs, String trigger) {
        ScreenHandler handler = hs.getScreenHandler();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("trigger", trigger);
        root.put("screenClass", hs.getClass().getName());
        root.put("handlerClass", handler.getClass().getName());
        root.put("title", hs.getTitle().getString());
        root.put("syncId", handler.syncId);
        root.put("isGenericContainer", handler instanceof GenericContainerScreenHandler);
        if (handler instanceof GenericContainerScreenHandler gc) {
            root.put("rows", gc.getRows());
        }
        root.put("totalSlots", handler.slots.size());

        int containerCount = containerSlotCount(handler);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("slot", i);
            s.put("containerSlot", i < containerCount);
            s.put("inventory", slot.inventory.getClass().getSimpleName());
            s.put("item", Registries.ITEM.getId(stack.getItem()).toString());
            s.put("count", stack.getCount());
            s.put("name", stack.getName().getString());

            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                List<String> lines = new ArrayList<>();
                for (Text line : lore.lines()) lines.add(line.getString());
                s.put("lore", lines);
            }
            NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom != null) {
                s.put("customData", custom.copyNbt().toString());
            }
            slots.add(s);
        }
        root.put("slots", slots);

        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve(PrisonsUtils.MOD_ID + "-warpdump.json");
        try {
            Files.writeString(path, GSON.toJson(root));
            chat(mc, "§a[WarpDump] §fWrote " + slots.size() + " items (" + trigger + ") to §e" + path);
            chat(mc, "§7screen=§f" + hs.getClass().getSimpleName()
                    + " §7handler=§f" + handler.getClass().getSimpleName()
                    + " §7generic=§f" + (handler instanceof GenericContainerScreenHandler));
            LOG.info("WarpDump ({}) written to {}", trigger, path);
        } catch (Exception ex) {
            chat(mc, "§c[WarpDump] Failed to write dump: " + ex.getMessage());
            LOG.error("WarpDump failed", ex);
        }
    }

    private static void chat(MinecraftClient mc, String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Chat.of(msg), false);
        }
    }
}
