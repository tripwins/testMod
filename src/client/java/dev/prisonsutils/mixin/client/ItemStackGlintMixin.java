package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.render.DroppedItemGlint;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the vanilla enchant glint while a rarity-tinted dropped item is being resolved (see
 * {@link ItemEntityRendererMixin}), so dropped rarity items show no glint. The flag is only ever up
 * during that narrow window, so every other caller of {@code hasGlint} (inventory, held item,
 * tooltips) is unaffected.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackGlintMixin {

    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$suppressDroppedGlint(CallbackInfoReturnable<Boolean> cir) {
        if (DroppedItemGlint.suppress) {
            cir.setReturnValue(false);
        }
    }
}
