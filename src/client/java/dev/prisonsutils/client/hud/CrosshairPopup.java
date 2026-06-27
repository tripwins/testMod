package dev.prisonsutils.client.hud;

import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Short "toast" text that floats upward from a movable anchor (default: just above the crosshair)
 * and fades out over ~1.4s. Used for cooldown-ready alerts. Movable/scalable in the HUD editor,
 * and right-justifies on the screen's right half.
 */
public final class CrosshairPopup {
    private static final long LIFE_MS = 1400L;
    private static final String SAMPLE = "Blink Ready!";
    private static final List<Pop> active = new ArrayList<>();

    private record Pop(String text, int rgb, long start) {}

    private static final HudAnchor ANCHOR = new HudAnchor(
            () -> Config.get().cooldownPopupX, v -> Config.get().cooldownPopupX = v,
            () -> Config.get().cooldownPopupY, v -> Config.get().cooldownPopupY = v,
            () -> Config.get().cooldownPopupScale, s -> Config.get().cooldownPopupScale = (float) s,
            (sw, cw) -> (sw - cw) / 2, sh -> sh / 2 - 26, true);

    public static final MovableHud MOVABLE = new MovableHud() {
        public String name() { return "Cooldown Popup"; }
        public HudAnchor anchor() { return ANCHOR; }
        public int contentW(int sw) { return (int) (font().getWidth(SAMPLE) * ANCHOR.scale()); }
        public int contentH() { return (int) (HudDraw.LINE_H * ANCHOR.scale()); }
        public void drawSample(DrawContext ctx) {
            drawLine(ctx, SAMPLE, 0xFF6BFF6B, 0);
        }
    };

    private CrosshairPopup() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
    }

    public static void show(String text, int rgb) {
        active.add(new Pop(text, rgb & 0xFFFFFF, System.currentTimeMillis()));
        while (active.size() > 5) active.remove(0);
    }

    private static void render(DrawContext ctx) {
        if (active.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        long now = System.currentTimeMillis();
        int idx = 0;
        Iterator<Pop> it = active.iterator();
        while (it.hasNext()) {
            Pop p = it.next();
            long age = now - p.start();
            if (age >= LIFE_MS) { it.remove(); continue; }
            float prog = age / (float) LIFE_MS;
            int alpha = Math.max(0, Math.min(255, (int) (255 * (1f - prog * prog))));
            int rise = (int) (prog * 16) + idx * 11;
            drawLine(ctx, p.text(), (alpha << 24) | p.rgb(), rise);
            idx++;
        }
    }

    /** Draws one line at the anchor, lifted by {@code rise} unscaled pixels. */
    private static void drawLine(DrawContext ctx, String text, int color, int rise) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float scale = ANCHOR.scale();
        int cw = (int) (font.getWidth(text) * scale);
        int ax = ANCHOR.anchorX(sw, cw);
        int ay = ANCHOR.anchorY(sh) - (int) (rise * scale);
        HudDraw.draw(ctx, font, ax, ay, scale, ANCHOR.right(sw, cw), List.of(new HudDraw.Line(text, color)));
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }
}
