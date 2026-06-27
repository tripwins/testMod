package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.slotlock.SlotLockManager;
import dev.prisonsutils.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slot locking: Alt+click a player-inventory slot to toggle its lock; locked slots can't
 * be moved or dropped from. Also draws a padlock overlay on locked slots.
 */
@Mixin(HandledScreen.class)
public abstract class SlotLockMixin {

    @Inject(
            method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void prisonsutils$blockLockedSlot(
            Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (!Config.get().slotLockEnabled) return;

        // Number-key (button 0-8) and offhand-swap (button 40) move items in/out of a player
        // hotbar/offhand slot no matter which slot is hovered — block when that target is locked.
        if (actionType == SlotActionType.SWAP && SlotLockManager.isLocked(button)) {
            ci.cancel();
            return;
        }

        if (slot == null || !(slot.inventory instanceof PlayerInventory)) {
            return;
        }
        int idx = slot.getIndex();

        if (altDown()) {
            SlotLockManager.toggle(idx);
            ci.cancel();
            return;
        }

        if (SlotLockManager.isLocked(idx)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void prisonsutils$drawLockOverlay(
            DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot == null || !(slot.inventory instanceof PlayerInventory)) {
            return;
        }
        if (SlotLockManager.isLocked(slot.getIndex())) {
            SlotLockManager.renderOverlay(ctx, slot.x, slot.y);
        }
    }

    private static boolean altDown() {
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }
}
