package dev.prisonsutils.client.warp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

/**
 * Intercepts CosmicPrisons' {@code /warp} chest GUI (a 3-row generic container titled "Warps") and
 * lets us show {@link WarpScreen} in its place. The server's handler is kept and read LIVE each
 * frame — its slots populate a tick after the screen opens, and the server keeps syncing it even
 * while our custom screen is displayed — so clicking a warp still teleports via {@code clickSlot}.
 *
 * <p>Real lore format (per mine):
 * <pre>
 *   UNLOCKED
 *   Ore: Coal
 *   Players: 0
 *   Location: -1090x, 142y, -340z
 *   Required Level: 1
 *   Your Level: 100
 *   Danger: ▏▏▏...
 *   (Click to teleport)
 * </pre>
 *
 * <p>Lunar-safe: only used from method-name mixin injections.
 */
public final class WarpInterceptor {
    private WarpInterceptor() {}

    /** The exact title the server uses for the warp menu. */
    private static final String WARP_TITLE = "Warps";

    private static long pendingUntil = 0L;

    public enum Category { MINES, BADLANDS, EVENTS }

    /**
     * A single parsed warp slot, read live from the server handler. {@code lore} keeps the original
     * coloured {@link Text} lines so the hover tooltip preserves the server's formatting.
     */
    public record WarpEntry(int slotIndex, ItemStack stack, String name, String status,
                            int players, int requiredLevel, Category category, boolean locked,
                            List<Text> lore) {}

    public static void markPending() {
        pendingUntil = System.currentTimeMillis() + 6000L;
    }

    public static boolean isPending() {
        return System.currentTimeMillis() < pendingUntil;
    }

    public static void clearPending() {
        pendingUntil = 0L;
    }

    public static boolean isWarpCommand(String command) {
        if (command == null) return false;
        String c = command.trim().toLowerCase();
        return c.equals("warp") || c.equals("warps")
                || c.startsWith("warp ") || c.startsWith("warps ");
    }

    /** True for the server's warp chest. Gated on the exact title so other menus pass through. */
    public static boolean shouldIntercept(Screen screen) {
        if (!(screen instanceof HandledScreen<?> hs)) return false;
        if (!(hs.getScreenHandler() instanceof GenericContainerScreenHandler)) return false;
        return hs.getTitle().getString().equalsIgnoreCase(WARP_TITLE);
    }

    /** A container slot paired with its index, so the parser works on both live and cached stacks. */
    public record IndexedStack(int index, ItemStack stack) {}

    /** Parses the container portion of the handler into warp entries (live; may be empty briefly). */
    public static List<WarpEntry> parse(GenericContainerScreenHandler handler) {
        List<IndexedStack> list = new ArrayList<>();
        int containerSlots = handler.getRows() * 9;
        for (int i = 0; i < containerSlots && i < handler.slots.size(); i++) {
            list.add(new IndexedStack(i, handler.getSlot(i).getStack()));
        }
        return parseStacks(list);
    }

    /** Shared parser core: turns (slot, stack) pairs into warp entries, skipping empties/fillers. */
    public static List<WarpEntry> parseStacks(List<IndexedStack> stacks) {
        List<WarpEntry> out = new ArrayList<>();
        for (IndexedStack is : stacks) {
            int i = is.index();
            ItemStack stack = is.stack();
            if (stack == null || stack.isEmpty()) continue;
            if (isFiller(stack)) continue;

            String name = strip(stack.getName().getString());
            List<Text> lore = loreTexts(stack);
            List<String> plain = lore.stream().map(t -> strip(t.getString())).toList();

            String status = parseStatus(plain);
            int players = parseLabeledInt(plain, "Players:");
            int reqLevel = parseLabeledInt(plain, "Required Level:");
            Category cat = categoryOf(plain, i);
            boolean locked = status.contains("LOCKED") && !status.contains("UNLOCKED");

            double[] loc = parseLocation(plain);
            if (loc != null) MINE_LOCS.put(name, new MineLoc(name, loc[0], loc[1], loc[2]));

            out.add(new WarpEntry(i, stack, name, status, players, reqLevel, cat, locked, lore));
        }
        return out;
    }

    private static boolean isFiller(ItemStack stack) {
        // Only treat unnamed items as filler. Locked warps are sometimes shown as a named
        // glass pane / barrier, so we must NOT drop panes just for being panes.
        return strip(stack.getName().getString()).trim().isEmpty();
    }

    private static List<Text> loreTexts(ItemStack stack) {
        List<Text> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            lines.addAll(lore.lines());
        }
        return lines;
    }

    /** First lore line that is a known status keyword. */
    private static String parseStatus(List<String> lore) {
        for (String raw : lore) {
            String s = strip(raw).trim().toUpperCase();
            switch (s) {
                case "UNLOCKED", "LOCKED", "AVAILABLE", "CLOSED", "OPEN" -> {
                    return s;
                }
                default -> { /* keep scanning */ }
            }
        }
        return "";
    }

    private static Category categoryOf(List<String> lore, int slot) {
        for (String raw : lore) {
            String s = strip(raw).trim();
            if (s.startsWith("Ore Bandits:")) return Category.BADLANDS;
            if (s.startsWith("Ore:")) return Category.MINES;
        }
        // Fallback by chest row (mines on row 0, badlands row 1, events row 2) so locked
        // warps without the "Ore:" lore line still land in the right section.
        int row = slot / 9;
        if (row == 0) return Category.MINES;
        if (row == 1) return Category.BADLANDS;
        return Category.EVENTS;
    }

    /** Reads the integer after a "Label: N" lore line (commas tolerated). -1 if absent. */
    private static int parseLabeledInt(List<String> lore, String label) {
        for (String raw : lore) {
            String s = strip(raw).trim();
            if (s.startsWith(label)) {
                String num = s.substring(label.length()).replaceAll("[^0-9]", "");
                if (!num.isEmpty()) {
                    try {
                        return Integer.parseInt(num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return -1;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    // ---- mine location cache (for the coords/mine HUD) ----------------------

    public record MineLoc(String name, double x, double y, double z) {}

    private static final Map<String, MineLoc> MINE_LOCS = new HashMap<>();

    /** Parses "Location: -1090x, 142y, -340z" into {x, y, z}; null if absent. */
    private static double[] parseLocation(List<String> lore) {
        for (String raw : lore) {
            String s = strip(raw).trim();
            if (s.startsWith("Location:")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+").matcher(s);
                double[] v = new double[3];
                int n = 0;
                while (m.find() && n < 3) v[n++] = Double.parseDouble(m.group());
                if (n == 3) return v;
            }
        }
        return null;
    }

    /** Name of the nearest cached mine within ~130 blocks of (px,pz); null if none/too far. */
    public static String mineAt(double px, double pz) {
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (MineLoc l : MINE_LOCS.values()) {
            double dx = l.x() - px, dz = l.z() - pz;
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = l.name(); }
        }
        return (best != null && bestD <= 130.0 * 130.0) ? best : null;
    }
}
