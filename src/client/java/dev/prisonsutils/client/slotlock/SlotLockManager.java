package dev.prisonsutils.client.slotlock;

import dev.prisonsutils.config.Config;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Tracks which player-inventory slots are locked. Locked slots can't be moved or dropped.
 * Alt+click a slot to toggle its lock. Indices are PlayerInventory indices (0-8 hotbar,
 * 9-35 main, 36-39 armor, 40 offhand), persisted in config.
 */
public final class SlotLockManager {
    private static final int DIM = 0x55101010; // light darken so the item still reads
    private static final Identifier LOCK = Identifier.of("prisonsutils", "textures/gui/lock.png");

    private SlotLockManager() {}

    public static boolean isLocked(int invIndex) {
        return Config.get().slotLockEnabled && Config.get().lockedSlots.contains(invIndex);
    }

    public static void toggle(int invIndex) {
        var locked = Config.get().lockedSlots;
        if (!locked.remove(Integer.valueOf(invIndex))) {
            locked.add(invIndex);
        }
        Config.save();
    }

    /** Draw a subtle dim + the padlock texture over a locked slot. (x, y) is the slot's item origin. */
    public static void renderOverlay(DrawContext ctx, int x, int y) {
        ctx.fill(x, y, x + 16, y + 16, DIM);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOCK, x, y, 0f, 0f, 16, 16, 16, 16);
    }
}
