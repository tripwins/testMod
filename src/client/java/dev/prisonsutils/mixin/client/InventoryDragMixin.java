package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes drags to the PrisonsUtils menu (for the slider). mouseDragged is on HandledScreen. */
@Mixin(HandledScreen.class)
public abstract class InventoryDragMixin {

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$dragMenu(
            Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        HandledScreenAccessor acc = (HandledScreenAccessor) (Object) this;
        boolean consumed = PrisonsMenu.drag(
                (int) click.x(), (int) click.y(),
                acc.prisonsutils$getX(), acc.prisonsutils$getY(), acc.prisonsutils$getBackgroundWidth());
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
