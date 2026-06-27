package dev.prisonsutils.config;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

/** Persisted feature flags + state. Loaded/saved by {@link Config}. */
public final class PrisonsConfig {
    public boolean guardViewEnabled = false;
    public int guardDangerColor = 0xFFEF4444;  // red
    public int guardWarningColor = 0xFFFF8C1A; // orange
    public int guardNoLosColor = 0xFF33CCFF;   // blue
    /** Auto-mark nearby entities named "guard". Meant for an empty mine; disable once marked. */
    public boolean autoGuardMarkEnabled = false;

    public boolean itemOverlayEnabled = true;
    public float itemOverlayScale = 1.0f;
    public int overlayColor = 0xFFFFFFFF; // color for ALL item overlays (NRG/money/XP/trinket/enchant)
    public boolean searchBarEnabled = true;
    public boolean enchantHelperEnabled = true;
    public boolean enchantOverlayEnabled = true;
    public boolean rarityTintEnabled = true;
    /** Hide the enchant glint on dropped rarity items (in-slot rarity tint is unaffected). */
    public boolean hideDroppedRarityGlint = true;

    /** Category names the user has collapsed in the menu. */
    public List<String> collapsedCategories = new ArrayList<>();

    public boolean slotLockEnabled = true;
    /** Player-inventory slot indices that are locked (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand). */
    public List<Integer> lockedSlots = new ArrayList<>();

    public boolean petRadiusEnabled = false;
    public int petRadiusColor = 0xFF5DADE2; // light blue
    public float petRadiusThickness = 4.0f; // line width in px
    /** When another player enters the radius, pulse the ring in an alert color (no player marker). */
    public boolean petRadiusAlertEnabled = true;
    public int petRadiusAlertColor = 0xFFFF3344; // red

    public boolean damageIndicatorsEnabled = true;
    public boolean rightClickWhileMining = true;

    /** Floating "X ready" popup near the crosshair when a trinket/pet comes off cooldown. */
    public boolean cooldownHudEnabled = true;
    public boolean cooldownHudSound = true;

    /** Ghost marker showing where the Blink trinket would teleport you. */
    public boolean blinkPreviewEnabled = true;
    public int blinkPreviewColor = 0xFF66D9FF;

    /** HUD warning when you're standing in a guard's danger zone. */
    public boolean guardWarningEnabled = true;

    /** Movable HUD anchors (top-left, scaled pixels). -1 = auto/default position. */
    public int guardWarnX = -1;
    public int guardWarnY = -1;
    public float guardWarnScale = 1.0f;
    public int cooldownPopupX = -1;
    public int cooldownPopupY = -1;
    public float cooldownPopupScale = 1.0f;

    /** Always-on XYZ coordinate readout (movable via the HUD editor). */
    public boolean coordsHudEnabled = true;
    public int coordsHudX = -1;
    public int coordsHudY = -1;
    public float coordsHudScale = 1.0f;

    public boolean notificationsEnabled = true;
    public boolean banNotificationsEnabled = true;

    /** Auto-scan /pv 1..N on join and cache the contents for the client-side vault viewer. */
    public boolean pvAutoScan = true;

    /**
     * CosmicPrisons API (cosmicapi:main) integration. Off until you register an app and paste in the
     * clientId below. Guards are intentionally NOT requested — the manual/name-scan guard system
     * stays in charge of marks. Requires an approved app + in-game grant to do anything.
     */
    public boolean cosmicApiEnabled = true;
    /** Unused: the handshake clientId/modId are hardcoded in CosmicApi so a stale config can't break them. */
    public String cosmicClientId = "client_mqnfk5ucndjz8669ig";
    public String cosmicModId = "this-is-private-for-testing";
    /** Per-install id, generated automatically on first connect. */
    public String cosmicInstallId = "";

    /**
     * Cosmic-style location ping (press V): beacon beam + sonar ring + distance label, plus a
     * ping sound. Local-only for now — nothing is sent to the server.
     */
    public boolean pingEnabled = true;
    public int pingColor = 0xFF33E1FF; // cyan
    public String pingLabel = "Ping";
    public boolean pingSound = true;
    /** GLFW key code that fires a ping (hold to aim, release to place). Default V. */
    public int pingKey = GLFW.GLFW_KEY_V;
}
