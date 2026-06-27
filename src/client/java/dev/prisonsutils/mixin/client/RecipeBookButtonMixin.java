package dev.prisonsutils.mixin.client;

import net.minecraft.client.gui.ScreenPos;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla computes the recipe-book toggle button's Y from {@code height/2 - 22} (screen
 * center), so it doesn't follow when {@link InventoryShiftMixin} pushes the inventory
 * down. Re-anchor it to the inventory's own y/backgroundHeight instead — identical to
 * vanilla when unshifted, but tracks the shift when the menu is open.
 */
@Mixin(InventoryScreen.class)
public abstract class RecipeBookButtonMixin {

    @Inject(method = "getRecipeBookButtonPos", at = @At("RETURN"), cancellable = true)
    private void prisonsutils$followInventory(CallbackInfoReturnable<ScreenPos> cir) {
        HandledScreenAccessor acc = (HandledScreenAccessor) (Object) this;
        int x = acc.prisonsutils$getX() + 104;
        int y = acc.prisonsutils$getY() + acc.prisonsutils$getBackgroundHeight() / 2 - 22;
        cir.setReturnValue(new ScreenPos(x, y));
    }
}
