package dev.prisonsutils.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A single row in the settings menu (toggle, color, slider...). */
public interface MenuEntry {
    /** Wrapped description shown under the control. */
    String description();

    /** Draw the label + control on one row at (x, y) with the given width. */
    void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY);

    default boolean click(int mouseX, int mouseY, int x, int y, int w) {
        return false;
    }

    /** Called while the mouse is dragged with a button held. */
    default boolean drag(int mouseX, int mouseY, int x, int y, int w) {
        return false;
    }

    /** Called when the wheel scrolls over this entry's row. */
    default boolean scroll(int mouseX, int mouseY, int x, int y, int w, double amount) {
        return false;
    }

    /** Extra height (below the description) when the entry is expanded (e.g. a color grid). */
    default int expandedHeight() {
        return 0;
    }

    /** Draw the expansion area at (x, y). */
    default void renderExpansion(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
    }

    /** Handle a click in the expansion area. */
    default boolean clickExpansion(int mouseX, int mouseY, int x, int y, int w) {
        return false;
    }
}
