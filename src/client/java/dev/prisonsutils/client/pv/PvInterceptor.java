package dev.prisonsutils.client.pv;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;

/**
 * Parses {@code /pv} commands and flags when the next opened container is a player vault. A typed or
 * clicked {@code /pv} reaches the server unchanged and opens the real vault GUI; we arm
 * {@link #markPendingVault} so {@link dev.prisonsutils.mixin.client.HandledScreenVaultButtonsMixin}
 * recognizes that container and overlays our tab strip on it. The flag is left disarmed while
 * {@link PvScanner} walks the vaults at join, so its rapid opens/closes stay plain.
 *
 * <p>Lunar-safe: only used from method-name mixin injections and our own screens.
 */
public final class PvInterceptor {
    private PvInterceptor() {}

    /** Parses the vault number from a {@code pv}/{@code pv N} command, 0 for the bare command, -1 if not pv. */
    public static int pvIndex(String command) {
        if (command == null) return -1;
        String c = command.trim().toLowerCase();
        if (!c.equals("pv") && !c.startsWith("pv ")) return -1;
        String rest = c.equals("pv") ? "" : c.substring(3).trim().replaceAll("[^0-9]", "");
        if (rest.isEmpty()) return 0;
        try {
            return Integer.parseInt(rest);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isPvCommand(String command) {
        return pvIndex(command) >= 0;
    }

    // ---- flag the next /pv container so we can overlay our tab strip --------

    private static long pendingUntil = 0L;

    /** Arm the overlay: the next container opened by a {@code /pv} is treated as a vault and gets our
     *  tab strip. Self-expires after a few seconds in case no container arrives. */
    public static void markPendingVault() {
        pendingUntil = System.currentTimeMillis() + 6000L;
    }

    public static boolean isVaultPending() {
        return System.currentTimeMillis() < pendingUntil;
    }

    public static void clearPendingVault() {
        pendingUntil = 0L;
    }

    /** True for the vault container we're waiting on, so the mixin overlays our tab buttons on it. */
    public static boolean shouldInterceptVault(Screen screen) {
        if (!isVaultPending()) return false;
        if (!(screen instanceof HandledScreen<?> hs)) return false;
        return hs.getScreenHandler() instanceof GenericContainerScreenHandler;
    }
}
