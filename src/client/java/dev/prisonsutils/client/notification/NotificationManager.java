package dev.prisonsutils.client.notification;

import dev.prisonsutils.config.Config;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Toast-style notifications stacked at the bottom-right. Static; rendered via HudRenderCallback. */
public final class NotificationManager {
    private static final int BG_RGB = 0x101418;
    private static final int BORDER_RGB = 0x2E3338;
    private static final int TITLE_RGB = 0xFFFFFF;
    private static final int TEXT_RGB = 0xC8C8C8;
    private static final int BG_BASE_ALPHA = 0xC0;

    private static final int MAX_NOTIFICATIONS = 6;
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_GAP = 2;
    private static final int ACCENT_WIDTH = 3;
    private static final int RIGHT_MARGIN = 6;
    private static final int BOTTOM_MARGIN = 6;
    private static final int GAP_BETWEEN = 4;
    private static final int MAX_WIDTH = 220;

    private static final Deque<Notification> active = new ArrayDeque<>();

    private NotificationManager() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            render(ctx, mc.textRenderer, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        });
    }

    public static void push(Notification notification) {
        if (!Config.get().notificationsEnabled) return;
        while (active.size() >= MAX_NOTIFICATIONS) {
            active.pollFirst();
        }
        active.addLast(notification);
    }

    public static void clear() {
        active.clear();
    }

    private static void render(DrawContext ctx, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        Iterator<Notification> it = active.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(now)) it.remove();
        }
        if (active.isEmpty()) return;

        int rightEdge = screenWidth - RIGHT_MARGIN;
        // Bottom-anchored: the newest toast's bottom edge sits just above the screen bottom and older
        // ones stack upward. Anchoring by the bottom (not a fixed-height top) keeps multi-line toasts
        // fully on-screen instead of spilling off the bottom edge.
        int currentBottom = screenHeight - BOTTOM_MARGIN;
        Iterator<Notification> desc = active.descendingIterator();
        while (desc.hasNext()) {
            Notification n = desc.next();
            float alpha = n.alpha(now);
            if (alpha <= 0f) continue;
            int width = computeWidth(textRenderer, n);
            int height = computeHeight(n);
            int x = rightEdge - width; // right-justified against the screen edge
            int y = currentBottom - height;
            renderOne(ctx, textRenderer, x, y, width, height, n, alpha);
            currentBottom = y - GAP_BETWEEN;
        }
    }

    private static void renderOne(DrawContext ctx, TextRenderer textRenderer, int x, int y,
                                  int width, int height, Notification notification, float alpha) {
        int bgAlpha = clampByte((int) (BG_BASE_ALPHA * alpha));
        int textAlpha = clampByte((int) (0xFF * alpha));

        int bgArgb = (bgAlpha << 24) | BG_RGB;
        int borderArgb = (bgAlpha << 24) | BORDER_RGB;
        int accentArgb = (textAlpha << 24) | (notification.accentColor & 0xFFFFFF);
        int titleArgb = (textAlpha << 24) | TITLE_RGB;
        int textArgb = (textAlpha << 24) | TEXT_RGB;

        ctx.fill(x, y, x + width, y + height, bgArgb);
        ctx.fill(x, y, x + width, y + 1, borderArgb);
        ctx.fill(x, y + height - 1, x + width, y + height, borderArgb);
        ctx.fill(x, y, x + 1, y + height, borderArgb);
        ctx.fill(x + width - 1, y, x + width, y + height, borderArgb);
        ctx.fill(x, y, x + ACCENT_WIDTH, y + height, accentArgb);

        int textX = x + ACCENT_WIDTH + PADDING_X;
        int textY = y + PADDING_Y;
        ctx.drawText(textRenderer, notification.title, textX, textY, titleArgb, false);
        textY += LINE_HEIGHT + TITLE_GAP;
        for (String line : notification.lines) {
            ctx.drawText(textRenderer, line, textX, textY, textArgb, false);
            textY += LINE_HEIGHT;
        }
    }

    private static int computeWidth(TextRenderer textRenderer, Notification notification) {
        int max = textRenderer.getWidth(notification.title);
        for (String line : notification.lines) {
            max = Math.max(max, textRenderer.getWidth(line));
        }
        return Math.min(MAX_WIDTH, ACCENT_WIDTH + PADDING_X * 2 + max);
    }

    private static int computeHeight(Notification notification) {
        int lines = notification.lines.size();
        if (lines == 0) return PADDING_Y * 2 + LINE_HEIGHT;
        return PADDING_Y * 2 + LINE_HEIGHT + TITLE_GAP + lines * LINE_HEIGHT;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(0xFF, value));
    }
}
