package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scrolls the PrisonsUtils menu. {@code mouseScrolled} is overridden on HandledScreen
 * (it's only a default on ParentElement, not declared on Screen), so the injection binds
 * here; the instanceof guard scopes it to the player inventory.
 */
@Mixin(HandledScreen.class)
public abstract class InventoryScrollMixin {

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$scrollMenu(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        HandledScreenAccessor acc = (HandledScreenAccessor) (Object) this;
        boolean consumed = PrisonsMenu.scroll(
                mouseX, mouseY, verticalAmount,
                acc.prisonsutils$getX(), acc.prisonsutils$getY(), acc.prisonsutils$getBackgroundWidth());
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
