package dev.prisonsutils.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * A clickable menu row that runs an action (e.g. "Edit HUD Layout" → opens the HUD editor),
 * styled like a button with an accent call-to-action on the right.
 */
public final class ActionButton implements MenuEntry {
    private static final int LABEL = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF66D9FF;
    private static final int HOVER_BG = 0x30FFFFFF;
    public static final int ROW_HEIGHT = 16;

    private final String label;
    private final String description;
    private final String cta;
    private final Runnable action;

    public ActionButton(String label, String description, String cta, Runnable action) {
        this.label = label;
        this.description = description;
        this.cta = cta;
        this.action = action;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
        if (hover) ctx.fill(x - 2, y - 2, x + w + 2, y + ROW_HEIGHT - 2, HOVER_BG);
        ctx.drawText(font, label, x, y, LABEL, true);
        int ctaW = font.getWidth(cta);
        ctx.drawText(font, cta, x + w - ctaW, y, ACCENT, true);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int x, int y, int w) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {
            action.run();
            return true;
        }
        return false;
    }
}
