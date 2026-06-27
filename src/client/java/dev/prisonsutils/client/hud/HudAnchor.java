package dev.prisonsutils.client.hud;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Config-backed position + scale for a movable HUD element. The stored X is an <i>anchor</i> point:
 * elements on the left half of the screen grow rightward from it, elements on the right half grow
 * leftward (right-justified). -1 means "use the default position".
 */
public final class HudAnchor {
    private final IntSupplier getX;
    private final IntConsumer setX;
    private final IntSupplier getY;
    private final IntConsumer setY;
    private final DoubleSupplier getScale;
    private final DoubleConsumer setScale;
    private final IntBinaryOperator defX; // (screenW, contentW) -> x
    private final IntUnaryOperator defY;  // (screenH) -> y
    private final boolean scalable;

    public HudAnchor(IntSupplier getX, IntConsumer setX, IntSupplier getY, IntConsumer setY,
                     DoubleSupplier getScale, DoubleConsumer setScale,
                     IntBinaryOperator defX, IntUnaryOperator defY, boolean scalable) {
        this.getX = getX; this.setX = setX; this.getY = getY; this.setY = setY;
        this.getScale = getScale; this.setScale = setScale;
        this.defX = defX; this.defY = defY; this.scalable = scalable;
    }

    public boolean scalable() { return scalable; }

    public float scale() {
        return scalable ? (float) Math.max(0.5, Math.min(3.0, getScale.getAsDouble())) : 1.0f;
    }

    public int anchorX(int screenW, int contentW) {
        int c = getX.getAsInt();
        return c >= 0 ? c : defX.applyAsInt(screenW, contentW);
    }

    public int anchorY(int screenH) {
        int c = getY.getAsInt();
        return c >= 0 ? c : defY.applyAsInt(screenH);
    }

    public boolean right(int screenW, int contentW) {
        return anchorX(screenW, contentW) > screenW / 2;
    }

    /** Left edge of the element's box, given its (scaled) content width. */
    public int boxLeft(int screenW, int contentW) {
        int ax = anchorX(screenW, contentW);
        return right(screenW, contentW) ? ax - contentW : ax;
    }

    /** Editor calls this on drag: converts a box left/top back into an anchor + side. */
    public void setBox(int left, int top, int screenW, int contentW) {
        boolean right = (left + contentW / 2) > screenW / 2;
        setX.accept(right ? left + contentW : left);
        setY.accept(top);
    }

    public void scaleBy(double delta) {
        if (!scalable) return;
        double s = Math.max(0.5, Math.min(3.0, getScale.getAsDouble() + delta));
        setScale.accept(Math.round(s * 10.0) / 10.0);
    }

    public void reset() {
        setX.accept(-1);
        setY.accept(-1);
        if (scalable) setScale.accept(1.0);
    }
}
