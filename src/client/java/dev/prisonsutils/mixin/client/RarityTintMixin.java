package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.rarity.RarityTint;
import dev.prisonsutils.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Glazes container slots with a rarity-colored tint behind the item. */
@Mixin(HandledScreen.class)
public abstract class RarityTintMixin<T extends ScreenHandler> {

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void prisonsutils$rarityTint(DrawContext ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!Config.get().rarityTintEnabled || slot == null || !slot.hasStack()) {
            return;
        }
        Integer rgb = RarityTint.tintRgb(slot.getStack());
        if (rgb != null) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x55000000 | rgb);
        }
    }
}
