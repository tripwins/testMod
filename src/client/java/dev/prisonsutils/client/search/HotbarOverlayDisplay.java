package dev.prisonsutils.client.search;

import dev.prisonsutils.client.rarity.RarityTint;
import dev.prisonsutils.config.Config;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;

/** Draws item overlays (NRG / money note / xp / trinket charges) on the hotbar + offhand. */
public final class HotbarOverlayDisplay {
    private HotbarOverlayDisplay() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            if (mc.options.hudHidden) return;
            if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;

            boolean overlays = Config.get().itemOverlayEnabled || Config.get().enchantOverlayEnabled;
            boolean tint = Config.get().rarityTintEnabled;
            if (!overlays && !tint) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            int hotbarLeft = sw / 2 - 91;
            int slotItemY = sh - 19;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                int slotItemX = hotbarLeft + 3 + i * 20;
                if (tint) tint(ctx, stack, slotItemX, slotItemY);
                if (overlays) ItemOverlayUtil.renderForStack(ctx, stack, slotItemX, slotItemY);
            }

            ItemStack off = mc.player.getInventory().getStack(40);
            if (!off.isEmpty()) {
                boolean leftHanded = mc.player.getMainArm() == net.minecraft.util.Arm.LEFT;
                int offX = leftHanded ? hotbarLeft + 182 + 4 + 3 : hotbarLeft - 29 + 3;
                if (tint) tint(ctx, off, offX, slotItemY);
                if (overlays) ItemOverlayUtil.renderForStack(ctx, off, offX, slotItemY);
            }
        });
    }

    private static void tint(DrawContext ctx, ItemStack stack, int x, int y) {
        Integer rgb = RarityTint.tintRgb(stack);
        if (rgb != null) {
            ctx.fill(x, y, x + 16, y + 16, 0x55000000 | rgb);
        }
    }
}
