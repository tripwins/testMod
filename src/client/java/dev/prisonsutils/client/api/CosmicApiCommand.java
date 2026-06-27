package dev.prisonsutils.client.api;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.config.Config;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * {@code /cosmicapi} — prints the live Cosmic API connection state (enabled, channel available,
 * connected, session, granted scopes/hooks, denials). {@code /cosmicapi resend} re-fires the
 * handshake. A quick way to confirm whether the integration is actually working in-game.
 */
public final class CosmicApiCommand {
    private CosmicApiCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> registerNodes(d));
    }

    private static void registerNodes(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(ClientCommandManager.literal("cosmicapi")
                .then(ClientCommandManager.literal("resend").executes(CosmicApiCommand::resend))
                .executes(CosmicApiCommand::status));
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource src = ctx.getSource();
        boolean channel = ClientPlayNetworking.canSend(CosmicPayload.ID);

        src.sendFeedback(Chat.of("§7[§bCosmicAPI§7] §fStatus"));
        src.sendFeedback(Chat.of("§7• Enabled: " + yn(Config.get().cosmicApiEnabled)));
        src.sendFeedback(Chat.of("§7• Channel on this server: "
                + (channel ? "§ayes" : "§cno §8(server isn't offering cosmicapi:main)")));
        src.sendFeedback(Chat.of("§7• Connected: " + yn(CosmicState.connected())));
        String sid = CosmicState.sessionId();
        src.sendFeedback(Chat.of("§7• Session: " + (sid != null ? "§a" + sid : "§8none")));
        src.sendFeedback(Chat.of("§7• Scopes granted: §f" + CosmicState.allowedScopes().size()
                + " §7• Hooks granted: §f" + CosmicState.allowedHooks().size()));
        src.sendFeedback(Chat.of("§7• Events received: §f" + CosmicState.eventCount()
                + (CosmicState.lastEventType() != null
                        ? " §7(last: §f" + CosmicState.lastEventType() + "§7)" : "")));

        Map<String, String> denied = CosmicState.denied();
        if (!denied.isEmpty()) {
            src.sendFeedback(Chat.of("§7• Denied (§e" + denied.size() + "§7):"));
            for (Map.Entry<String, String> e : denied.entrySet()) {
                src.sendFeedback(Chat.of("§8   - §e" + e.getKey() + " §8(" + e.getValue() + ")"));
            }
        }
        src.sendFeedback(Chat.of("§8clientId=" + CosmicApi.CLIENT_ID + "  modId=" + CosmicApi.MOD_ID));
        return 1;
    }

    private static int resend(CommandContext<FabricClientCommandSource> ctx) {
        CosmicApi.requestHandshake();
        boolean channel = ClientPlayNetworking.canSend(CosmicPayload.ID);
        ctx.getSource().sendFeedback(Chat.of("§7[§bCosmicAPI§7] §fRe-sending… §7channel here: "
                + (channel ? "§ayes" : "§cno §8(server isn't offering cosmicapi:main)")));
        return 1;
    }

    private static String yn(boolean b) {
        return b ? "§ayes" : "§cno";
    }
}
