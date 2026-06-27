package dev.prisonsutils.client.guard;

import dev.prisonsutils.client.hud.HudAnchor;
import dev.prisonsutils.client.hud.HudDraw;
import dev.prisonsutils.client.hud.MovableHud;
import dev.prisonsutils.config.Config;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD warning while the player is in (or near) a guard's danger zone. Stays visible over chat and
 * while jumping; red "DANGER ZONE" inside a zone, yellow "Guard Close" in the warning band.
 * Position/scale are user-movable via the HUD editor (and it right-justifies on the screen's
 * right half).
 */
public final class GuardWarningHud {
    private static final String DANGER_TEXT = "⚠ GUARD DANGER ZONE ⚠";
    private static final String CLOSE_TEXT = "⚠ Guard Close";

    private static final HudAnchor ANCHOR = new HudAnchor(
            () -> Config.get().guardWarnX, v -> Config.get().guardWarnX = v,
            () -> Config.get().guardWarnY, v -> Config.get().guardWarnY = v,
            () -> Config.get().guardWarnScale, s -> Config.get().guardWarnScale = (float) s,
            (sw, cw) -> (sw - cw) / 2, sh -> sh / 2 - 40, true);

    public static final MovableHud MOVABLE = new MovableHud() {
        public String name() { return "Guard Warning"; }
        public HudAnchor anchor() { return ANCHOR; }
        public int contentW(int sw) { return (int) (font().getWidth(DANGER_TEXT) * ANCHOR.scale()); }
        public int contentH() { return (int) (HudDraw.LINE_H * ANCHOR.scale()); }
        public void drawSample(DrawContext ctx) {
            drawLines(ctx, List.of(new HudDraw.Line(DANGER_TEXT, 0xFFFF4040)));
        }
    };

    private GuardWarningHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        if (!Config.get().guardWarningEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return; // shown even over chat

        int zone = GuardRenderer.playerZone();
        if (zone == 0) return;

        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0);
        int alpha = Math.max(0, Math.min(255, (int) (140 + 115 * pulse)));
        int color = (alpha << 24) | (zone == 2 ? 0xFF4040 : 0xFFD24A);
        String text = zone == 2 ? DANGER_TEXT : CLOSE_TEXT;
        drawLines(ctx, List.of(new HudDraw.Line(text, color)));
    }

    private static void drawLines(DrawContext ctx, List<HudDraw.Line> lines) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int cw = (int) (HudDraw.width(font, lines) * ANCHOR.scale());
        int ax = ANCHOR.anchorX(sw, cw);
        int ay = ANCHOR.anchorY(sh);
        HudDraw.draw(ctx, font, ax, ay, ANCHOR.scale(), ANCHOR.right(sw, cw), lines);
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }
}
