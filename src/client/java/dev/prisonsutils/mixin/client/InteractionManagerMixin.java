package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.guard.Guard;
import dev.prisonsutils.client.guard.GuardManager;
import dev.prisonsutils.client.mining.MiningState;
import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.config.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side interaction tweaks:
 * <ul>
 *   <li>Lets you use items / right-click while mining: reports "not breaking" only during
 *       {@code doItemUse} so the early-return in that method is skipped.</li>
 *   <li>With a wooden shovel: right-click a block to toggle a guard mark there (mark if absent,
 *       unmark if already marked); left-click a marked block to unmark it. Purely local — the
 *       interaction is cancelled so no packet reaches the server.</li>
 * </ul>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {

    @Inject(method = "isBreakingBlock", at = @At("RETURN"), cancellable = true)
    private void prisonsutils$allowUseWhileMining(CallbackInfoReturnable<Boolean> cir) {
        if (MiningState.inDoItemUse && Config.get().rightClickWhileMining) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$shovelMarkGuard(ClientPlayerEntity player, Hand hand,
                                              BlockHitResult hitResult,
                                              CallbackInfoReturnable<ActionResult> cir) {
        if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isOf(Items.WOODEN_SHOVEL)) {
            return;
        }
        BlockPos pos = hitResult.getBlockPos();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (GuardManager.removeManual(pos)) {
            player.sendMessage(Chat.of("§7[§eGuard§7] §fUnmarked §c" + pos.toShortString()
                    + "§7 (" + GuardManager.manualCount() + " left)"), true);
            mc.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8f, 0.7f));
        } else {
            float yaw = player.getYaw();
            GuardManager.addManual(new Guard(pos, yaw));
            player.sendMessage(Chat.of("§7[§eGuard§7] §fMarked §a" + pos.toShortString()
                    + "§f facing §a" + (int) yaw + "° §7(" + GuardManager.manualCount() + ")"), true);
            mc.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.5f, 0.7f));
        }
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void prisonsutils$shovelUnmarkGuard(BlockPos pos, Direction direction,
                                                CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || !player.getStackInHand(Hand.MAIN_HAND).isOf(Items.WOODEN_SHOVEL)) {
            return;
        }
        if (GuardManager.removeManual(pos)) {
            player.sendMessage(Chat.of("§7[§eGuard§7] §fUnmarked §c" + pos.toShortString()
                    + "§7 (" + GuardManager.manualCount() + " left)"), true);
            mc.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8f, 0.7f));
            cir.setReturnValue(true); // handled — don't begin breaking the block
        }
    }
}
