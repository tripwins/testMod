package dev.prisonsutils.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Color setting with an inline color-picker grid. Click the swatch to open a grid of
 * colors below the row; click a cell to pick it. The grid is part of the menu content,
 * so it scrolls with everything else.
 */
public final class ColorOption implements MenuEntry {
    private static final int LABEL = 0xFFFFFFFF;
    private static final int HOVER_BG = 0x30FFFFFF;
    private static final int BORDER = 0xFF000000;
    private static final int SELECTED = 0xFFFFFFFF;
    private static final int SWATCH = 14;

    private static final int COLS = 12;
    private static final int ROWS = 5;
    private static final int CELL = 12;
    private static final int GRID_PAD = 4;
    private static final int[] GRID = new int[COLS * ROWS];

    static {
        for (int col = 0; col < COLS; col++) {
            float h = col * (360f / COLS);
            GRID[col] = hsv(h, 1f, 1f);
            GRID[COLS + col] = hsv(h, 1f, 0.7f);
            GRID[2 * COLS + col] = hsv(h, 1f, 0.45f);
            GRID[3 * COLS + col] = hsv(h, 0.45f, 1f);
            GRID[4 * COLS + col] = hsv(0f, 0f, col / (float) (COLS - 1)); // grayscale
        }
    }

    private final String label;
    private final String description;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private boolean expanded;

    public ColorOption(String label, String description, IntSupplier getter, IntConsumer setter) {
        this.label = label;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + DotToggle.ROW_HEIGHT - 2;
        if (hover) {
            ctx.fill(x - 2, y - 2, x + w + 2, y + DotToggle.ROW_HEIGHT - 2, HOVER_BG);
        }
        ctx.drawText(font, label, x, y, LABEL, true);

        int sx = x + w - SWATCH;
        int sy = y - 1;
        int color = 0xFF000000 | (getter.getAsInt() & 0xFFFFFF);
        ctx.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, expanded ? SELECTED : BORDER);
        ctx.fill(sx, sy, sx + SWATCH, sy + SWATCH, color);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int x, int y, int w) {
        int sx = x + w - SWATCH;
        int sy = y - 1;
        if (mouseX >= sx - 1 && mouseX < sx + SWATCH + 1 && mouseY >= sy - 1 && mouseY < sy + SWATCH + 1) {
            expanded = !expanded;
            return true;
        }
        return false;
    }

    @Override
    public int expandedHeight() {
        return expanded ? ROWS * CELL + GRID_PAD : 0;
    }

    @Override
    public void renderExpansion(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        if (!expanded) return;
        int gx = x + DotToggle.ROW_HEIGHT; // small indent
        int gy = y + GRID_PAD / 2;
        for (int i = 0; i < GRID.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx = gx + col * CELL;
            int cy = gy + row * CELL;
            ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, GRID[i]);
        }
    }

    @Override
    public boolean clickExpansion(int mouseX, int mouseY, int x, int y, int w) {
        if (!expanded) return false;
        int gx = x + DotToggle.ROW_HEIGHT;
        int gy = y + GRID_PAD / 2;
        for (int i = 0; i < GRID.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx = gx + col * CELL;
            int cy = gy + row * CELL;
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                setter.accept(GRID[i]);
                return true; // keep the picker open after choosing
            }
        }
        return false;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = v - c;
        float r, g, b;
        int seg = (int) (h / 60f) % 6;
        switch (seg) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
