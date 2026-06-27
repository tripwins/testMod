package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.pv.PvInterceptor;
import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overlays the PrisonsUtils tab strip (Settings / Info / Warps / Storage) on the real server vault
 * GUI. A typed or clicked {@code /pv} shows the actual player vault untouched; these buttons just let
 * you hop to our menus. The strip only appears when the container was opened by a {@code /pv}, flagged
 * via {@link PvInterceptor}. Method-name HEAD/TAIL injections only (Lunar-safe).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenVaultButtonsMixin extends Screen {
    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private boolean prisonsutils$vaultButtons;

    protected HandledScreenVaultButtonsMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void prisonsutils$flagVault(CallbackInfo ci) {
        // init() also re-runs on resize; once flagged this screen instance stays flagged.
        if (PvInterceptor.shouldInterceptVault((Screen) (Object) this)) {
            prisonsutils$vaultButtons = true;
            PvInterceptor.clearPendingVault();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void prisonsutils$renderVaultButtons(
            DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!prisonsutils$vaultButtons) return;
        // render() TAIL is screen-space (renderMain's translate is push/pop-scoped), drawn on top.
        PrisonsMenu.renderVaultButtons(
                ctx, MinecraftClient.getInstance().textRenderer, this.x, this.y, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$clickVaultButtons(
            Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!prisonsutils$vaultButtons) return;
        if (PrisonsMenu.vaultButtonClick((int) click.x(), (int) click.y(), this.x, this.y)) {
            cir.setReturnValue(true);
        }
    }
}
