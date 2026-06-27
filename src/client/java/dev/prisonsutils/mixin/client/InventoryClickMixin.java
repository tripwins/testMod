package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.ui.PrisonsMenu;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes clicks on the tab strip / open menu to {@link PrisonsMenu}. {@code mouseClicked}
 * is declared on RecipeBookScreen (InventoryScreen inherits it), so the injection binds
 * here; the instanceof guard keeps it scoped to the player inventory.
 */
@Mixin(RecipeBookScreen.class)
public abstract class InventoryClickMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$clickTabs(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) {
            return;
        }
        HandledScreenAccessor acc = (HandledScreenAccessor) (Object) this;
        boolean consumed = PrisonsMenu.click(
                (int) click.x(),
                (int) click.y(),
                acc.prisonsutils$getX(),
                acc.prisonsutils$getY(),
                acc.prisonsutils$getBackgroundWidth(),
                (Screen) (Object) this);
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
