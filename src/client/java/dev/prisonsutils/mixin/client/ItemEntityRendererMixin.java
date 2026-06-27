package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.rarity.RarityTint;
import dev.prisonsutils.client.render.DroppedItemGlint;
import dev.prisonsutils.config.Config;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises {@link DroppedItemGlint#suppress} only while a rarity-tinted dropped item is having its
 * model resolved (which is when {@code ItemStack.hasGlint} is consulted, via
 * {@code BasicItemModel}/{@code SpecialItemModel}). That lets {@link ItemStackGlintMixin} hide the
 * vanilla glint so dropped rarity items show no glint at all. Non-rarity items keep their normal
 * glint, and nothing changes when the toggle is off.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void prisonsutils$beginGlintSuppress(ItemEntity entity, ItemEntityRenderState state,
                                                 float tickDelta, CallbackInfo ci) {
        DroppedItemGlint.suppress =
                Config.get().hideDroppedRarityGlint && RarityTint.tintRgb(entity.getStack()) != null;
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void prisonsutils$endGlintSuppress(ItemEntity entity, ItemEntityRenderState state,
                                               float tickDelta, CallbackInfo ci) {
        DroppedItemGlint.suppress = false;
    }
}
