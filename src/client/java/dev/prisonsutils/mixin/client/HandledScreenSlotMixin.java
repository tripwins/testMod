package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.search.ItemOverlayUtil;
import dev.prisonsutils.client.search.SearchManager;
import dev.prisonsutils.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenSlotMixin<T extends ScreenHandler> {

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void prisonsutils$drawSlotOverlay(DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (Config.get().itemOverlayEnabled || Config.get().enchantOverlayEnabled) {
            ItemOverlayUtil.renderSlotOverlay(ctx, slot);
        }
        if (Config.get().searchBarEnabled && slot != null && slot.hasStack()
                && SearchManager.matches(slot.getStack())) {
            ItemOverlayUtil.drawSearchHighlight(ctx, slot, 0x80FFEB3B);
        }
    }
}
