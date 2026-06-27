package dev.prisonsutils.client.api;

/**
 * The scopes and hook events PrisonsUtils requests over {@code cosmicapi:main}.
 *
 * <p>Guards are intentionally excluded — no {@code server.guards:read} scope and no
 * {@code server.guards.snapshot.changed} hook — so the existing manual / name-scan guard system stays
 * the source of truth for guard marks. Everything else the API documents is requested here.
 */
public final class CosmicScopes {
    private CosmicScopes() {}

    public static final String[] REQUESTED_SCOPES = {
            // Public / server-wide reads
            "server.status:read",
            "events:read",
            "server.merchants:read",
            "server.meteors:read",
            // Player current-state reads (so HUDs have a value before the first change event)
            "player.cooldowns:read",
            "player.effects:read",
            "player.pets:read",
            "player.trinkets:read",
            "player.chat_channel:read",
            // Hook-backing scopes
            "hooks.player.enchant_proc:read",
            "hooks.player.absorber:read",
            "hooks.player.command:read",
            "hooks.bandit.kill:read",
            "gang.messages:read",
            // Sensitive — drop these two if you want a lighter review:
            "player.inventory:read",
            "player.private_vaults:read",
            // server.guards:read intentionally omitted (guards excluded).
    };

    public static final String[] REQUESTED_HOOKS = {
            "player.enchant_proc",
            "player.cooldowns.changed",
            "player.effects.changed",
            "player.absorber.used",
            "player.command.succeeded",
            "player.pet.changed",
            "player.trinket.changed",
            "player.chat_channel.changed",
            "bandit.killed",
            "gang.chat.message.created",
            "server.event.schedule.changed",
            "server.meteor.landing.changed",
            "server.merchant.spawned",
            "server.merchant.despawned",
            // server.guards.snapshot.changed intentionally omitted (guards excluded).
    };
}
