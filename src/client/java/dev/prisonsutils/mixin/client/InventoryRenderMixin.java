package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the PrisonsUtils tab strip + menu on top of the player inventory. */
@Mixin(InventoryScreen.class)
public abstract class InventoryRenderMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void prisonsutils$renderTabs(
            DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HandledScreenAccessor acc = (HandledScreenAccessor) (Object) this;
        PrisonsMenu.render(
                ctx,
                MinecraftClient.getInstance().textRenderer,
                acc.prisonsutils$getX(),
                acc.prisonsutils$getY(),
                acc.prisonsutils$getBackgroundWidth(),
                mouseX,
                mouseY);
    }
}
