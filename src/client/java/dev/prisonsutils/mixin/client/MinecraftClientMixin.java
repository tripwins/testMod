package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.mining.MiningState;
import dev.prisonsutils.client.warp.WarpInterceptor;
import dev.prisonsutils.client.warp.WarpScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks when {@code doItemUse} is running so {@code InteractionManagerMixin} can let item
 * use through while mining. Uses method-name injections only (Lunar can't resolve
 * {@code @At INVOKE} targets without a refmap).
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void prisonsutils$beginDoItemUse(CallbackInfo ci) {
        MiningState.inDoItemUse = true;
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void prisonsutils$endDoItemUse(CallbackInfo ci) {
        MiningState.inDoItemUse = false;
    }

    /**
     * Swaps the server's {@code /warp} chest GUI for our warp map, keeping the original handler so
     * clicks still hit the real slots. Gated by {@link WarpInterceptor}'s pending flag.
     */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen prisonsutils$swapCustomScreen(Screen screen) {
        if (WarpInterceptor.shouldIntercept(screen)) {
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            GenericContainerScreenHandler handler = (GenericContainerScreenHandler) hs.getScreenHandler();
            WarpInterceptor.clearPending();
            // Keep the live handler; WarpScreen reads its slots each frame (they populate a tick later).
            return new WarpScreen(handler, handler.syncId);
        }
        // A /pv vault is shown as the real server GUI; HandledScreenVaultButtonsMixin overlays our
        // tab strip on it (flagged via PvInterceptor). No screen swap here.
        return screen;
    }
}
