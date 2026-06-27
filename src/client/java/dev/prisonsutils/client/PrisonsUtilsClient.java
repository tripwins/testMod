package dev.prisonsutils.client;

import dev.prisonsutils.client.api.CosmicApi;
import dev.prisonsutils.client.api.CosmicApiCommand;
import dev.prisonsutils.client.command.GuardCommand;
import dev.prisonsutils.client.damage.DamageIndicatorManager;
import dev.prisonsutils.client.enchant.EnchantBookHelper;
import dev.prisonsutils.client.guard.AutoGuardScanner;
import dev.prisonsutils.client.guard.GuardManager;
import dev.prisonsutils.client.guard.GuardRenderer;
import dev.prisonsutils.client.notification.NotificationManager;
import dev.prisonsutils.client.notification.PunishmentNotifier;
import dev.prisonsutils.client.ping.PingManager;
import dev.prisonsutils.client.pv.PvManager;
import dev.prisonsutils.client.pv.PvScanner;
import dev.prisonsutils.client.render.RadiusCircleRenderer;
import dev.prisonsutils.client.cooldown.CooldownHud;
import dev.prisonsutils.client.guard.GuardWarningHud;
import dev.prisonsutils.client.hud.CoordsHud;
import dev.prisonsutils.client.hud.CrosshairPopup;
import dev.prisonsutils.client.render.BlinkPreviewRenderer;
import dev.prisonsutils.client.render.PingRenderer;
import dev.prisonsutils.client.search.HotbarOverlayDisplay;
import dev.prisonsutils.client.warp.WarpDump;
import dev.prisonsutils.client.warp.WarpManager;
import dev.prisonsutils.config.Config;
import net.fabricmc.api.ClientModInitializer;

public final class PrisonsUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.init();

        // Guards: mark by hand (/guard mark or right-click a block with a wooden shovel) or let
        // auto-mark scan for entities named "guard". Left-click a marked block to unmark.
        GuardManager.load();
        GuardRenderer.register();
        GuardCommand.register();
        AutoGuardScanner.register();

        // Item overlays (hotbar) — container slots handled by HandledScreenSlotMixin.
        HotbarOverlayDisplay.register();

        // Enchant book tooltip helper.
        EnchantBookHelper.register();

        // Pet radius circle at the player's feet.
        RadiusCircleRenderer.register();

        // Damage indicators.
        DamageIndicatorManager.register();

        // Notifications
        NotificationManager.register();
        PunishmentNotifier.start();

        // Crosshair popups + trinket/pet cooldown alerts.
        CrosshairPopup.register();
        CooldownHud.register();

        // Blink trinket teleport preview.
        BlinkPreviewRenderer.register();

        // Guard danger HUD warning.
        GuardWarningHud.register();

        // Always-on XYZ coordinate readout.
        CoordsHud.register();

        // Player-vault scanner + viewer.
        PvManager.init();
        PvScanner.register();

        // Warp map: cached layout opens instantly, live handler takes over once synced.
        WarpManager.init();

        // Cosmic-style location ping (hold V): beam + sonar ring + name/distance label + sound.
        // Local-only test harness for now — nothing is sent to the server.
        PingManager.register();
        PingRenderer.register();

        // Debug: press Insert while a container is open to dump it to config/prisonsutils-warpdump.json.
        WarpDump.register();

        // CosmicPrisons API (cosmicapi:main): declares the channel and, when enabled + a clientId is
        // set, handshakes on join and dispatches hook events. Inert otherwise. Guards excluded.
        // /cosmicapi shows live connection status; /cosmicapi resend re-fires the handshake.
        CosmicApi.register();
        CosmicApiCommand.register();

        // The tab UI + search bar + slot locking are driven by screen mixins.
    }
}
