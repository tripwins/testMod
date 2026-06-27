package dev.prisonsutils.client.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Numeric slider: drag the knob, click the track, or scroll over it to change the value. */
public final class ScaleSlider implements MenuEntry {
    private static final int LABEL = 0xFFFFFFFF;
    private static final int VALUE = 0xFF4ADE80;
    private static final int HOVER_BG = 0x30FFFFFF;
    private static final int TRACK = 0xFF3A434F;
    private static final int FILL = 0xFF4ADE80;
    private static final int KNOB = 0xFFE8ECF1;
    private static final int TRACK_W = 70;
    private static final int VALUE_W = 30;
    private static final int TRACK_H = 4;

    private final String label;
    private final String description;
    private final String unit;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min;
    private final double max;
    private final double step;

    public ScaleSlider(String label, String description, String unit, double min, double max, double step,
                       DoubleSupplier getter, DoubleConsumer setter) {
        this.label = label;
        this.description = description;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public String description() {
        return description;
    }

    private int trackX(int x, int w) {
        return x + w - VALUE_W - TRACK_W;
    }

    private boolean inRow(int mouseX, int mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y - 3 && mouseY < y + DotToggle.ROW_HEIGHT - 2;
    }

    @Override
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        if (inRow(mouseX, mouseY, x, y, w)) {
            ctx.fill(x - 2, y - 2, x + w + 2, y + DotToggle.ROW_HEIGHT - 2, HOVER_BG);
        }
        ctx.drawText(font, label, x, y, LABEL, true);

        double value = clamp(getter.getAsDouble());
        float frac = (float) ((value - min) / (max - min));
        int tx = trackX(x, w);
        int ty = y + 3;
        ctx.fill(tx, ty, tx + TRACK_W, ty + TRACK_H, TRACK);
        ctx.fill(tx, ty, tx + (int) (TRACK_W * frac), ty + TRACK_H, FILL);
        int knobX = tx + (int) (TRACK_W * frac) - 2;
        ctx.fill(knobX, y, knobX + 4, y + 10, KNOB);

        String valueText = String.format("%.1f%s", value, unit);
        ctx.drawText(font, valueText, x + w - font.getWidth(valueText), y, VALUE, true);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int x, int y, int w) {
        return setFromX(mouseX, mouseY, x, y, w);
    }

    @Override
    public boolean drag(int mouseX, int mouseY, int x, int y, int w) {
        // Generous vertical band so a drag that strays slightly still tracks.
        if (mouseY >= y - 6 && mouseY < y + DotToggle.ROW_HEIGHT + 2) {
            int tx = trackX(x, w);
            float frac = Math.max(0f, Math.min(1f, (mouseX - tx) / (float) TRACK_W));
            setValueFromFraction(frac);
            return true;
        }
        return false;
    }

    private boolean setFromX(int mouseX, int mouseY, int x, int y, int w) {
        int tx = trackX(x, w);
        if (mouseX >= tx - 3 && mouseX < tx + TRACK_W + 3 && inRow(mouseX, mouseY, x, y, w)) {
            float frac = Math.max(0f, Math.min(1f, (mouseX - tx) / (float) TRACK_W));
            setValueFromFraction(frac);
            return true;
        }
        return false;
    }

    private void setValueFromFraction(float frac) {
        setter.accept(round(clamp(min + frac * (max - min))));
    }

    private double round(double v) {
        return clamp(Math.round(v / step) * step);
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
