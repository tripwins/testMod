package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.pv.PvInterceptor;
import dev.prisonsutils.client.pv.PvScanner;
import dev.prisonsutils.client.warp.WarpInterceptor;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flags a pending {@code /warp} (its container is swapped for our warp map) and a pending {@code /pv}
 * (the real vault GUI gets our tab strip). Method-name HEAD injection only (Lunar-safe).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendChatCommand", at = @At("HEAD"))
    private void prisonsutils$onChatCommand(String command, CallbackInfo ci) {
        if (WarpInterceptor.isWarpCommand(command)) {
            WarpInterceptor.markPending();
        }
        // A typed /pv (bare or numbered) opens the real server vault GUI untouched; we just flag it so
        // HandledScreenVaultButtonsMixin overlays our tab strip on the opened container. Skipped while
        // PvScanner walks the vaults at join (its rapid opens/closes must stay invisible).
        if (PvInterceptor.isPvCommand(command) && !PvScanner.scanning()) {
            PvInterceptor.markPendingVault();
        }
    }
}
