package dev.prisonsutils.client.hud;

import dev.prisonsutils.config.Config;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * Always-on XYZ coordinate readout, drawn as our own HUD. Movable/scalable via the HUD editor.
 */
public final class CoordsHud {
    private static final int COORD_COLOR = 0xFFFFFFFF; // white

    private static final HudAnchor ANCHOR = new HudAnchor(
            () -> Config.get().coordsHudX, v -> Config.get().coordsHudX = v,
            () -> Config.get().coordsHudY, v -> Config.get().coordsHudY = v,
            () -> Config.get().coordsHudScale, s -> Config.get().coordsHudScale = (float) s,
            (sw, cw) -> 4, sh -> 4, true);

    public static final MovableHud MOVABLE = new MovableHud() {
        public String name() { return "Coordinates"; }
        public HudAnchor anchor() { return ANCHOR; }
        public int contentW(int sw) {
            return (int) (font().getWidth("XYZ: -00000 000 -00000") * ANCHOR.scale());
        }
        public int contentH() { return (int) (HudDraw.LINE_H * ANCHOR.scale()); }
        public void drawSample(DrawContext ctx) {
            drawLines(ctx, List.of(new HudDraw.Line("XYZ: 0 64 0", COORD_COLOR)));
        }
    };

    private CoordsHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        if (!Config.get().coordsHudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        int px = MathHelper.floor(mc.player.getX());
        int py = MathHelper.floor(mc.player.getY());
        int pz = MathHelper.floor(mc.player.getZ());
        drawLines(ctx, List.of(new HudDraw.Line("XYZ: " + px + " " + py + " " + pz, COORD_COLOR)));
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
