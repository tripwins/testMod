package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pushes the player inventory down to make room for the PrisonsUtils tab strip (and the
 * expanded menu above it). Shifting {@code y} at the TAIL of {@code HandledScreen.init}
 * happens before RecipeBookScreen/InventoryScreen finish placing their widgets, so the
 * recipe-book button and player model stay aligned with the moved window.
 *
 * <p>Targets HandledScreen (where {@code y}/{@code backgroundHeight} are declared) so the
 * shadows resolve under Lunar's mixin engine; guarded to the player inventory only.
 */
@Mixin(HandledScreen.class)
public abstract class InventoryShiftMixin {
    @Shadow protected int y;
    @Shadow protected int backgroundHeight;

    @Inject(method = "init", at = @At("TAIL"))
    private void prisonsutils$shiftForTabs(CallbackInfo ci) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        PrisonsMenu.resetIfNewScreen((InventoryScreen) (Object) this);

        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int reserved = PrisonsMenu.reservedTopHeight();
        int comboTop = (screenHeight - (reserved + backgroundHeight)) / 2;
        this.y = comboTop + reserved;
    }
}
