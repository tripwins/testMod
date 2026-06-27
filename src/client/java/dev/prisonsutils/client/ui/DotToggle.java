package dev.prisonsutils.client.ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * On/off control: a label with a colored status dot and "Enabled"/"Disabled" text on the
 * right. Backed by a config getter/setter so toggling persists immediately.
 */
public final class DotToggle implements MenuEntry {
    private static final int GREEN = 0xFF4ADE80;
    private static final int GRAY = 0xFFAAB2BD;
    private static final int LABEL = 0xFFFFFFFF;
    private static final int HOVER_BG = 0x30FFFFFF;
    public static final int ROW_HEIGHT = 16;
    public static final String WIDEST_STATUS = "Disabled";

    private final String label;
    private final String description;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public DotToggle(String label, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.label = label;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public String description() {
        return description;
    }

    public boolean isOn() {
        return getter.getAsBoolean();
    }

    @Override
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
        if (hover) {
            ctx.fill(x - 2, y - 2, x + w + 2, y + ROW_HEIGHT - 2, HOVER_BG);
        }

        ctx.drawText(font, label, x, y, LABEL, true);

        boolean on = isOn();
        String status = on ? "Enabled" : "Disabled";
        int statusW = font.getWidth(status);
        int dotX = x + w - statusW - 10;
        ctx.fill(dotX, y, dotX + 6, y + 6, on ? GREEN : GRAY);
        ctx.drawText(font, status, dotX + 9, y, on ? GREEN : GRAY, true);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int x, int y, int w) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {
            setter.accept(!isOn());
            return true;
        }
        return false;
    }
}
