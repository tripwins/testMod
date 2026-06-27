package dev.prisonsutils.client.hud;

import dev.prisonsutils.config.Config;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Drag-to-move / scale editor for the movable HUD elements. Each element draws its real (sample)
 * content where it will appear; drag to move (snaps to the grid and screen center/edges), drag the
 * corner handle or scroll to scale, R resets, Esc saves.
 */
public final class HudEditorScreen extends Screen {
    private static final int SNAP = 8;
    private static final int GRID = 2;
    private static final int HANDLE = 6;

    private enum Mode { NONE, MOVE, SCALE }

    private final List<MovableHud> elements = MovableHud.all();
    private MovableHud active;
    private Mode mode = Mode.NONE;
    private int grabX, grabY;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    private int[] box(MovableHud e) {
        int cw = e.contentW(width);
        int left = e.anchor().boxLeft(width, cw);
        int top = e.anchor().anchorY(height);
        return new int[]{left, top, Math.max(cw, 12), Math.max(e.contentH(), 8)};
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xB0101418, 0xB0101418);
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§lHUD Editor"), width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Drag to move • corner/scroll to scale • §fR§7 reset • §fEsc§7 save"),
                width / 2, 24, 0xFFB8C4D0);

        for (MovableHud e : elements) {
            e.drawSample(ctx);
            int[] b = box(e);
            boolean hot = e == active || over(mouseX, mouseY, b);
            int border = hot ? 0xFF66D9FF : 0x80FFFFFF;
            outline(ctx, b[0], b[1], b[2], b[3], border);
            // resize handle (bottom-right)
            int hx = b[0] + b[2] - HANDLE, hy = b[1] + b[3] - HANDLE;
            if (e.anchor().scalable()) ctx.fill(hx, hy, hx + HANDLE, hy + HANDLE, 0xFF66D9FF);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("§7" + e.name() + " §8x" + String.format("%.1f", e.anchor().scale())),
                    b[0], b[1] - 10, 0xFFFFFFFF);
        }
    }

    private static void outline(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean over(int mx, int my, int[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x(), my = (int) click.y();
        for (MovableHud e : elements) {
            int[] b = box(e);
            int hx = b[0] + b[2] - HANDLE, hy = b[1] + b[3] - HANDLE;
            if (e.anchor().scalable() && mx >= hx && mx <= hx + HANDLE && my >= hy && my <= hy + HANDLE) {
                active = e;
                mode = Mode.SCALE;
                return true;
            }
            if (over(mx, my, b)) {
                active = e;
                mode = Mode.MOVE;
                grabX = mx - b[0];
                grabY = my - b[1];
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (active == null) return super.mouseDragged(click, offsetX, offsetY);
        if (mode == Mode.MOVE) {
            int[] b = box(active);
            int nx = snap((int) click.x() - grabX, width, b[2]);
            int ny = snap((int) click.y() - grabY, height, b[3]);
            active.anchor().setBox(nx, ny, width, b[2]);
            return true;
        }
        if (mode == Mode.SCALE) {
            active.anchor().scaleBy((offsetX + offsetY) * 0.004);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        active = null;
        mode = Mode.NONE;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (MovableHud e : elements) {
            if (e.anchor().scalable() && over((int) mouseX, (int) mouseY, box(e))) {
                e.anchor().scaleBy(vertical * 0.1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.getKeycode() == GLFW.GLFW_KEY_R) {
            for (MovableHud e : elements) e.anchor().reset();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int snap(int v, int total, int size) {
        int center = (total - size) / 2;
        if (Math.abs(v - center) <= SNAP) return center;
        if (Math.abs(v) <= SNAP) return 0;
        if (Math.abs(v - (total - size)) <= SNAP) return total - size;
        v = Math.round((float) v / GRID) * GRID;
        return Math.max(0, Math.min(total - size, v));
    }
}
