package dev.prisonsutils.client.render;

/**
 * One-frame flag, raised by {@code ItemEntityRendererMixin} only while a rarity-tinted dropped item
 * is having its model resolved, and read by {@code ItemStackGlintMixin} to hide the vanilla enchant
 * glint on that item. Keeps dropped rarity items glint-free without touching any other caller of
 * {@code ItemStack.hasGlint} (inventory, held item, tooltips). Never read or written off the render
 * thread.
 */
public final class DroppedItemGlint {
    public static boolean suppress = false;

    private DroppedItemGlint() {}
}
