package dev.prisonsutils.client.guard;

import dev.prisonsutils.config.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/**
 * Auto-mark: while enabled, periodically scans nearby entities and marks any whose name contains
 * "guard" (case-insensitive). Marks feed the persistent manual list (deduped by block position) so
 * they survive turning auto-mark back off.
 *
 * <p>Best-effort only — the name heuristic can miss real guards or catch unrelated entities, and a
 * guard following a moving player drops a fresh mark on every block it steps to. Meant to be
 * switched on in an EMPTY mine and switched off once the stationary guards are captured.
 */
public final class AutoGuardScanner {
    private static final double SCAN_RADIUS = 48.0;
    private static final long SCAN_INTERVAL_MS = 500L;

    private static long lastScanMs = 0L;

    private AutoGuardScanner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoGuardScanner::tick);
    }

    /** Force the next tick to scan immediately (called when the toggle is switched on). */
    public static void onEnabled() {
        lastScanMs = 0L;
    }

    private static void tick(MinecraftClient mc) {
        if (!Config.get().autoGuardMarkEnabled) return;
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanMs < SCAN_INTERVAL_MS) return;
        lastScanMs = now;

        Box box = player.getBoundingBox().expand(SCAN_RADIUS);
        for (Entity e : world.getOtherEntities(player, box)) {
            if (isGuard(e)) {
                GuardManager.markIfAbsent(e.getBlockPos(), e.getYaw());
            }
        }
    }

    private static boolean isGuard(Entity e) {
        String name = e.getName().getString();
        return name != null && name.toLowerCase().contains("guard");
    }
}
