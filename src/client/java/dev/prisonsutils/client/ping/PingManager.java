package dev.prisonsutils.client.ping;

import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Cosmic-style location ping. <b>Hold</b> the ping key (default V) and a live marker follows your
 * crosshair (capped at {@link #MAX_DIST} blocks ahead); <b>release</b> to drop it. {@link PingRenderer}
 * draws a beam, an expanding sonar ring, and a name/distance label, and we play a "ping" sound on
 * release.
 *
 * <p>Markers are <b>local-only</b> for now — nothing is sent to the server. This is the test
 * harness for the visual + sound before any networking is added. Keys are polled raw via GLFW
 * (not a Fabric KeyBinding) so the feature works under Lunar.
 */
public final class PingManager {
    /** A live ping marker. {@code pos} is the world position, {@code color} is 0xAARRGGBB. */
    public record Ping(Vec3d pos, long createdAt, int color, String name) {}

    public static final long LIFETIME_MS = 8000L;
    /** Pings only reach this far ahead of the player. */
    private static final double MAX_DIST = 20.0;

    private static final List<Ping> ACTIVE = new ArrayList<>();
    private static boolean keyWasDown = false;

    /** Live preview while the key is held: position follows the crosshair, start time stays fixed. */
    private static Vec3d previewPos = null;
    private static long previewStart = 0L;

    private PingManager() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PingManager::onTick);
    }

    /** Committed markers, oldest first. Read-only use by the renderer. */
    public static List<Ping> pings() {
        return ACTIVE;
    }

    /** The marker being aimed while the key is held, or null. Rendered at full opacity. */
    public static Ping preview() {
        if (previewPos == null) return null;
        return new Ping(previewPos, previewStart, Config.get().pingColor, Config.get().pingLabel);
    }

    private static void onTick(MinecraftClient client) {
        prune();
        if (client == null || client.getWindow() == null) {
            reset();
            return;
        }
        if (!Config.get().pingEnabled || client.currentScreen != null) {
            reset();
            return;
        }

        boolean down = InputUtil.isKeyPressed(client.getWindow(), Config.get().pingKey);
        if (down) {
            if (!keyWasDown) previewStart = System.currentTimeMillis();
            Vec3d aim = aim(client);
            if (aim != null) previewPos = aim;
        } else if (keyWasDown && previewPos != null) {
            // Released: commit the previewed marker and play the ping.
            ACTIVE.add(new Ping(previewPos, System.currentTimeMillis(),
                    Config.get().pingColor, Config.get().pingLabel));
            playSound(client);
            previewPos = null;
        }
        keyWasDown = down;
    }

    /** Surface point the player is looking at, capped at {@link #MAX_DIST}; null if no world/player. */
    private static Vec3d aim(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) return null;

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(MAX_DIST));
        HitResult hit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    private static void playSound(MinecraftClient client) {
        if (!Config.get().pingSound) return;
        // Vanilla note-block "pling"; high pitch reads as a clean ping. The 2-arg ui() overload
        // defaults to 0.25 volume (easy to miss), so pass explicit volume 1.0 via ui(sound, pitch, volume).
        client.getSoundManager().play(
                PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.7f, 1.0f));
    }

    private static void reset() {
        keyWasDown = false;
        previewPos = null;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        Iterator<Ping> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (now - it.next().createdAt() > LIFETIME_MS) it.remove();
        }
    }
}
