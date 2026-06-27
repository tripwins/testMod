package dev.prisonsutils.client.pv;

import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Walks {@code /pv 1, 2, 3 …} on join, scraping each vault GUI into {@link PvManager} until the
 * server reports no access (chat) or a vault simply never opens (timeout). State machine driven by
 * the client tick; chat detection is a fast-path, the timeout is the safety net.
 *
 * <p>Untested against the live server — the PV menu title, row count, exact "no access" wording and
 * timing may need tuning, but the timeout makes it self-terminating regardless.
 */
public final class PvScanner {
    private static final int MAX_VAULTS = 54;
    private static final long SETTLE_MS = 300;    // wait for the menu contents to arrive
    private static final long TIMEOUT_MS = 3500;  // no menu opened → assume out of vaults
    private static final long JOIN_DELAY_MS = 4000;

    private enum State { IDLE, WAITING }

    private static State state = State.IDLE;
    private static int index;
    private static long waitStart;
    private static int lastSyncId = -1;
    private static long menuOpenAt;
    private static boolean menuScraped;

    private static boolean autoPending;
    private static long autoAt;

    private PvScanner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PvScanner::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (Config.get().pvAutoScan) {
                autoPending = true;
                autoAt = System.currentTimeMillis() + JOIN_DELAY_MS;
            }
        });
    }

    public static boolean scanning() {
        return state != State.IDLE;
    }

    public static void startScan() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state != State.IDLE || mc.player == null || mc.getNetworkHandler() == null) return;
        PvManager.clear();
        index = 1;
        lastSyncId = -1;
        menuScraped = false;
        state = State.WAITING;
        waitStart = System.currentTimeMillis();
        chat(mc, "§7[§bPV§7] §fScanning your vaults…");
        sendPv(index);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        if (autoPending && System.currentTimeMillis() >= autoAt && state == State.IDLE) {
            autoPending = false;
            startScan();
            return;
        }
        if (state != State.WAITING) return;

        long now = System.currentTimeMillis();
        ScreenHandler handler = mc.currentScreen instanceof HandledScreen<?> hs ? hs.getScreenHandler() : null;
        if (handler instanceof GenericContainerScreenHandler gc && handler.syncId != 0) {
            if (handler.syncId != lastSyncId) {
                lastSyncId = handler.syncId;
                menuOpenAt = now;
                menuScraped = false;
            }
            if (!menuScraped && now - menuOpenAt > SETTLE_MS) {
                PvManager.setVault(index, gc.getRows(), scrape(gc));
                menuScraped = true;
                if (mc.player != null) mc.player.closeHandledScreen();
                advance(mc);
            }
        } else if (now - waitStart > TIMEOUT_MS) {
            finish(mc); // no menu appeared — out of vaults
        }
    }

    private static void advance(MinecraftClient mc) {
        index++;
        if (index > MAX_VAULTS) {
            finish(mc);
            return;
        }
        lastSyncId = -1;
        menuScraped = false;
        waitStart = System.currentTimeMillis();
        state = State.WAITING;
        sendPv(index);
    }

    private static void onChat(String text) {
        if (state != State.WAITING) return;
        String t = text.toLowerCase();
        if (t.contains("do not have access") && t.contains("/pv")) {
            finish(MinecraftClient.getInstance());
        }
    }

    private static void finish(MinecraftClient mc) {
        state = State.IDLE;
        PvManager.save();
        chat(mc, "§7[§bPV§7] §aCached " + PvManager.count() + " vault(s). §7Open with the Vaults button.");
    }

    private static List<PvManager.VaultItem> scrape(GenericContainerScreenHandler gc) {
        List<PvManager.VaultItem> items = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return items;
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());

        int container = gc.getRows() * 9;
        for (int i = 0; i < container && i < gc.slots.size(); i++) {
            Slot slot = gc.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;
            try {
                NbtCompound c = new NbtCompound();
                c.put("i", ItemStack.CODEC, ops, stack);
                items.add(new PvManager.VaultItem(i, c.toString()));
            } catch (Exception ignored) {}
        }
        return items;
    }

    private static void sendPv(int n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("pv " + n);
    }

    private static void chat(MinecraftClient mc, String msg) {
        if (mc.player != null) mc.player.sendMessage(Chat.of(msg), false);
    }
}
