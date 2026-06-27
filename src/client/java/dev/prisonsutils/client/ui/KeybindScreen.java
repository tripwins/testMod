package dev.prisonsutils.client.ui;

import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Tiny modal that binds the next keyboard key pressed (Esc cancels) via the given setter, then
 * returns to the screen it was opened from. Used by {@link KeybindOption} for the ping key.
 */
public final class KeybindScreen extends Screen {
    private final Screen parent;
    private final String action;
    private final IntConsumer setter;

    public KeybindScreen(Screen parent, String action, IntConsumer setter) {
        super(Text.literal("Set Key"));
        this.parent = parent;
        this.action = action;
        this.setter = setter;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xC0101418, 0xC0101418);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§b§lBind: §f" + action), width / 2, height / 2 - 16, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Press a key…  §8(Esc to cancel)"), width / 2, height / 2, 0xFFB8C4D0);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int code = input.getKeycode();
        if (code != GLFW.GLFW_KEY_ESCAPE) {
            setter.accept(code);
        }
        close();
        return true;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
