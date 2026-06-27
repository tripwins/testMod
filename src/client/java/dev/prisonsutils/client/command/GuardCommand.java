package dev.prisonsutils.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.prisonsutils.client.guard.Guard;
import dev.prisonsutils.client.guard.GuardManager;
import dev.prisonsutils.client.guard.GuardRenderer;
import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.config.Config;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class GuardCommand {
    private GuardCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> registerNodes(dispatcher));
    }

    private static void registerNodes(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("guard")
                .then(ClientCommandManager.literal("mark").executes(GuardCommand::mark))
                .then(ClientCommandManager.literal("unmark").executes(GuardCommand::unmark))
                .then(ClientCommandManager.literal("clear").executes(GuardCommand::clear))
                .then(ClientCommandManager.literal("list").executes(GuardCommand::list))
                .then(ClientCommandManager.literal("show").executes(c -> setVisible(c, true)))
                .then(ClientCommandManager.literal("hide").executes(c -> setVisible(c, false)))
                .then(ClientCommandManager.literal("toggle").executes(GuardCommand::toggleVisible))
                .executes(GuardCommand::help));
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Chat.of(
                "§7[§eGuard§7] §fcommands: §amark§7/§aunmark§7/§aclear§7/§alist§7/§ashow§7/§ahide§7/§atoggle"
                        + " §7(wooden shovel: right-click to mark/unmark, left-click to unmark)"));
        return 1;
    }

    private static int setVisible(CommandContext<FabricClientCommandSource> ctx, boolean visible) {
        Config.get().guardViewEnabled = visible;
        Config.save();
        if (visible) GuardRenderer.onVisibilityEnabled();
        ctx.getSource().sendFeedback(Chat.of(
                "§7[§eGuard§7] §fView " + (visible ? "§ashown" : "§chidden") + "§f."));
        return 1;
    }

    private static int toggleVisible(CommandContext<FabricClientCommandSource> ctx) {
        return setVisible(ctx, !Config.get().guardViewEnabled);
    }

    private static int mark(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        BlockPos target = pickedBlock(mc);
        if (target == null) {
            ctx.getSource().sendError(Chat.of("§cLook at a block to mark a guard."));
            return 0;
        }
        float yaw = mc.player.getYaw();
        GuardManager.addManual(new Guard(target, yaw));
        ctx.getSource().sendFeedback(Chat.of(
                "§7[§eGuard§7] §fMarked §a" + target.toShortString()
                        + "§f facing §a" + (int) yaw + "° §7(" + GuardManager.manualCount() + " manual)"));
        return 1;
    }

    private static int unmark(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        BlockPos look = pickedBlock(mc);
        BlockPos near = look != null ? look : mc.player.getBlockPos();
        Guard best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (Guard g : GuardManager.manualGuards()) {
            long dx = g.pos().getX() - near.getX();
            long dy = g.pos().getY() - near.getY();
            long dz = g.pos().getZ() - near.getZ();
            long dd = dx * dx + dy * dy + dz * dz;
            if (dd < bestDistSq) { bestDistSq = dd; best = g; }
        }
        if (best == null) {
            ctx.getSource().sendError(Chat.of("§cNo manual guards to unmark."));
            return 0;
        }
        GuardManager.removeManual(best.pos());
        ctx.getSource().sendFeedback(Chat.of(
                "§7[§eGuard§7] §fUnmarked §c" + best.pos().toShortString()
                        + "§7 (" + GuardManager.manualCount() + " left)"));
        return 1;
    }

    private static int clear(CommandContext<FabricClientCommandSource> ctx) {
        GuardManager.clearManual();
        ctx.getSource().sendFeedback(Chat.of("§7[§eGuard§7] §fCleared all manual guards."));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Chat.of(
                "§7[§eGuard§7] §f" + GuardManager.manualCount() + " marked, "
                        + GuardRenderer.visibleCount() + " visible blocks"));
        return 1;
    }

    private static BlockPos pickedBlock(MinecraftClient mc) {
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        return null;
    }
}
