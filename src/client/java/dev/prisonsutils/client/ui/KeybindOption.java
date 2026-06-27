package dev.prisonsutils.client.ui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;

/**
 * A rebindable-key row: shows the current key on the right; clicking opens a small capture screen
 * ({@link KeybindScreen}) that binds the next key pressed. Keyboard keys only, since the bound key
 * is polled via {@code InputUtil.isKeyPressed}.
 */
public final class KeybindOption implements MenuEntry {
    private static final int LABEL = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF66D9FF;
    private static final int HOVER_BG = 0x30FFFFFF;
    public static final int ROW_HEIGHT = 16;

    private final String label;
    private final String description;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public KeybindOption(String label, String description, IntSupplier getter, IntConsumer setter) {
        this.label = label;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public String description() {
        return description;
    }

    static String keyName(int code) {
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    @Override
    public void render(DrawContext ctx, TextRenderer font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2;
        if (hover) ctx.fill(x - 2, y - 2, x + w + 2, y + ROW_HEIGHT - 2, HOVER_BG);
        ctx.drawText(font, label, x, y, LABEL, true);
        String key = "[ " + keyName(getter.getAsInt()) + " ]";
        int kw = font.getWidth(key);
        ctx.drawText(font, key, x + w - kw, y, ACCENT, true);
    }

    @Override
    public boolean click(int mouseX, int mouseY, int x, int y, int w) {
        if (mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + ROW_HEIGHT - 2) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.setScreen(new KeybindScreen(mc.currentScreen, label, setter));
            return true;
        }
        return false;
    }
}
