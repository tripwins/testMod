package dev.prisonsutils.client.search;

import dev.prisonsutils.client.enchant.EnchantBookHelper;
import dev.prisonsutils.client.rarity.RarityTint;
import dev.prisonsutils.client.util.ItemMemo;
import dev.prisonsutils.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public final class ItemOverlayUtil {
    private ItemOverlayUtil() {}

    public static void renderSlotOverlay(DrawContext ctx, Slot slot) {
        if (slot == null || !slot.hasStack()) return;
        renderForStack(ctx, slot.getStack(), slot.x, slot.y);
    }

    // The overlay decision is parse-heavy (NBT/lore scans), so memoize it per item instead of
    // recomputing every frame for every slot. Short TTL keeps counters (charges/energy) visually live.
    private static final ItemMemo<Overlay> CACHE = new ItemMemo<>(300L);

    /** A decided overlay: text + ARGB color, or null for no overlay. */
    private record Overlay(String text, int color) {}

    public static void renderForStack(DrawContext ctx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Overlay overlay = CACHE.get(stack, ItemOverlayUtil::computeOverlay);
        if (overlay != null) {
            drawTextAt(ctx, overlay.text(), x, y, overlay.color());
        }
    }

    private static Overlay computeOverlay(ItemStack stack) {
        int color = Config.get().overlayColor;

        if (Config.get().itemOverlayEnabled) {
            String energy = SearchManager.getCosmicEnergyAmount(stack);
            if (energy != null) return new Overlay(energy, color);

            String charges = SearchManager.getTrinketCharges(stack);
            if (charges != null) return new Overlay(charges, color);

            String noteValue = SearchManager.getMoneyNoteValue(stack);
            if (noteValue != null) return new Overlay(noteValue, color);

            String expValue = SearchManager.getExpBottleValue(stack);
            if (expValue != null) return new Overlay(expValue, color);
        }

        if (Config.get().enchantOverlayEnabled) {
            String abbr = EnchantBookHelper.overlayAbbreviation(stack);
            if (abbr != null) {
                Integer rarity = RarityTint.tintRgb(stack);
                int abbrColor = rarity != null ? (0xFF000000 | rarity) : color;
                return new Overlay(abbr, abbrColor);
            }
        }
        return null;
    }

    private static void drawTextAt(DrawContext ctx, String text, int x, int y, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        float overlayScale = Math.max(0.5f, Math.min(2.0f, Config.get().itemOverlayScale));
        float baseScale = 0.5f * overlayScale;
        int textWidth = font.getWidth(text);
        if (textWidth <= 0) return;
        float scale = baseScale;
        float maxW = 16.0f * overlayScale;
        if (textWidth * scale > maxW) scale = maxW / textWidth;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale, scale);
        int textX = (int) ((x + 16) / scale) - textWidth;
        int textY = (int) ((y + 16) / scale) - font.fontHeight;
        drawOutlined(ctx, font, text, textX, textY, color);
        ctx.getMatrices().popMatrix();
    }

    /**
     * Draws {@code text} with a solid black outline (8-directional) instead of a single drop shadow,
     * so values stay legible over bright/busy item icons.
     */
    private static void drawOutlined(DrawContext ctx, TextRenderer font, String text, int x, int y, int color) {
        int outline = 0xFF000000;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ctx.drawText(font, text, x + dx, y + dy, outline, false);
            }
        }
        ctx.drawText(font, text, x, y, color, false);
    }

    public static void drawSearchHighlight(DrawContext ctx, Slot slot, int color) {
        if (slot == null) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }
}
