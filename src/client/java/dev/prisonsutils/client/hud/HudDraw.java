package dev.prisonsutils.client.hud;

import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Draws a block of text lines anchored to a point, scaled, and either left- or right-justified.
 * Right-justification grows the text leftward from the anchor (like right-align in a word
 * processor) so elements on the right edge of the screen read naturally.
 */
public final class HudDraw {
    public static final int LINE_H = 10;

    public record Line(String text, int color) {}

    private HudDraw() {}

    public static int width(TextRenderer f, List<Line> lines) {
        int m = 0;
        for (Line l : lines) m = Math.max(m, f.getWidth(l.text()));
        return m;
    }

    /** Anchored at (anchorX, y). When {@code right}, each line's right edge sits at anchorX. */
    public static void draw(DrawContext ctx, TextRenderer f, int anchorX, int y,
                            float scale, boolean right, List<Line> lines) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) anchorX, (float) y);
        ctx.getMatrices().scale(scale, scale);
        int ly = 0;
        for (Line l : lines) {
            int dx = right ? -f.getWidth(l.text()) : 0;
            ctx.drawText(f, Text.literal(l.text()), dx, ly, l.color(), true);
            ly += LINE_H;
        }
        ctx.getMatrices().popMatrix();
    }
}
