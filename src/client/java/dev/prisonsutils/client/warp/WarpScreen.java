package dev.prisonsutils.client.warp;

import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.client.warp.WarpInterceptor.Category;
import dev.prisonsutils.client.warp.WarpInterceptor.WarpEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Custom "floating ores" warp map shown in place of CosmicPrisons' /warp chest. Backed by the
 * server's {@link GenericContainerScreenHandler}, which is read LIVE every frame (its slots arrive
 * a tick after opening). Layout (see design sketch):
 * <ul>
 *   <li><b>Mines</b> — scattered, bobbing ore icons across the top.</li>
 *   <li><b>Left column</b> — "Outpost" with 4 stacked icons ({@code /outpost 1..4}), plus Pit,
 *       KOTH and Stronghold.</li>
 *   <li><b>Badlands</b> — a labelled banner box along the bottom holding the 4 PvP mines.</li>
 * </ul>
 * Mine/Badlands/Pit/KOTH/Stronghold icons click their real server slot; the 4 Outpost icons run
 * {@code /outpost N} chat commands instead (the server exposes only a single Outposts entry).
 */
public final class WarpScreen extends Screen {

    // Outpost rank tiers (slot /outpost 1..4): Trainee→wooden, Pilot→gold, Hero→iron, Cosmonaut→diamond.
    private static final String[] OUTPOST_RANKS = {"§fTrainee", "§ePilot", "§7Hero", "§bCosmonaut"};
    private static final Item[] OUTPOST_ITEMS =
            {Items.WOODEN_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD};

    private final GenericContainerScreenHandler handler;
    private final int syncId;
    private boolean leaving = false;

    /** A laid-out, clickable icon. Either {@code slotIndex>=0} (clickSlot) or {@code command!=null}. */
    private record Icon(ItemStack stack, String label, String sub, int slotIndex, String command,
                        WarpEntry entry, int cx, int baseCy, float scale, boolean locked) {
        int half() { return (int) (8 * scale) + 3; }
    }

    public WarpScreen() {
        this(null, -1);
    }

    public WarpScreen(GenericContainerScreenHandler handler, int syncId) {
        super(Text.literal("Warps"));
        this.handler = handler;
        this.syncId = syncId;
    }

    // Parsing the live handler (regex over every lore line) is too costly to do per-frame, so we
    // cache the parsed entries + the laid-out icons and refresh a few times a second.
    private List<WarpEntry> cachedEntries = List.of();
    private long lastParse = -1;
    private boolean persisted = false;
    private List<Icon> cachedIcons;
    private long iconsStamp = -2;
    private int iconsW = -1, iconsH = -1;

    private List<WarpEntry> entries() {
        if (handler == null) return CONCEPT;
        long now = System.currentTimeMillis();
        if (now - lastParse > 400) {
            List<WarpEntry> live = WarpInterceptor.parse(handler);
            if (!live.isEmpty()) {
                // Live data is in — render it and refresh the disk cache once per open.
                cachedEntries = live;
                if (!persisted) {
                    WarpManager.update(handler);
                    persisted = true;
                }
            } else if (cachedEntries.isEmpty()) {
                // Server hasn't synced the container yet — paint last-known layout instantly.
                cachedEntries = WarpManager.cachedEntries();
            }
            lastParse = now;
        }
        return cachedEntries;
    }

    private List<Icon> icons() {
        List<WarpEntry> e = entries();
        if (cachedIcons == null || lastParse != iconsStamp || width != iconsW || height != iconsH) {
            cachedIcons = layout(e);
            iconsStamp = lastParse;
            iconsW = width;
            iconsH = height;
        }
        return cachedIcons;
    }

    // ---- rendering ---------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Light translucent GRAY filter — keep the game/world visible behind the menu.
        ctx.fillGradient(0, 0, width, height, 0x59404040, 0x66222222);

        List<WarpEntry> all = entries();
        if (all.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Loading warps…"),
                    width / 2, height / 2, 0xFFAACCEE);
            return;
        }

        drawSectionLabels(ctx);

        List<Icon> icons = icons();
        long now = System.currentTimeMillis();
        Icon hovered = null;
        for (Icon ic : icons) {
            boolean hover = inHit(mouseX, mouseY, ic);
            if (hover) hovered = ic;
            float scale = hover ? ic.scale() + 0.35f : ic.scale();

            // Floating bob — ores (mines) only.
            int cy = ic.baseCy();
            if (ic.entry() != null && ic.entry().category() == Category.MINES) {
                cy += Math.round((float) Math.sin(now / 500.0 + ic.cx() * 0.35) * 3.5f);
            }

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(ic.cx(), cy);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawItem(ic.stack(), -8, -8);
            ctx.getMatrices().popMatrix();

            if (ic.locked()) {
                // Red tint over the icon so locked warps read as unavailable.
                int ih = (int) (8 * scale) + 1;
                ctx.fill(ic.cx() - ih, cy - ih, ic.cx() + ih, cy + ih, 0x80E01515);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§c🔒"),
                        ic.cx(), cy - 4, 0xFFFF5555);
            }

            int nameColor = hover ? 0xFFFFE066 : (ic.locked() ? 0xFFD79A9A : 0xFFFFFFFF);
            if (!ic.label().isEmpty()) {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(ic.label()),
                        ic.cx(), ic.baseCy() + ic.half() + 2, nameColor);
            }
            if (ic.sub() != null) {
                int subY = ic.baseCy() + ic.half() + (ic.label().isEmpty() ? 2 : 12);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(ic.sub()), ic.cx(), subY, 0xFFFFFFFF);
            }
        }

        if (hovered != null && hovered.entry() != null) {
            ctx.drawTooltip(textRenderer, tooltip(hovered.entry()), mouseX, mouseY);
        }
    }

    // ---- centred fixed panel (keeps the menu tight regardless of screen size) ----
    private int panelW() { return Math.min(width - 40, 560); }
    private int panelH() { return Math.min(height - 40, 320); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private int fx(float f) { return panelX() + Math.round(f * panelW()); }
    private int fy(float f) { return panelY() + Math.round(f * panelH()); }

    /** Only the Pit/Stronghold/KOTH captions remain (Outpost & Badlands headers removed). */
    private void drawSectionLabels(DrawContext ctx) {
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§c§lPit"), fx(0.0f), fy(0.66f), 0xFFFF7A7A);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§6§lStronghold"), fx(0.11f), fy(0.66f), 0xFFFFC04D);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§a§lKOTH"), fx(0.22f), fy(0.66f), 0xFF8CFF8C);
    }

    /** Builds positioned icons grouped by section per the design sketch. */
    private List<Icon> layout(List<WarpEntry> all) {
        List<Icon> out = new ArrayList<>();
        List<WarpEntry> mines = new ArrayList<>();
        List<WarpEntry> badlands = new ArrayList<>();
        WarpEntry outposts = null, pit = null, koth = null, stronghold = null;

        for (WarpEntry e : all) {
            switch (e.category()) {
                case MINES -> mines.add(e);
                case BADLANDS -> badlands.add(e);
                case EVENTS -> {
                    String n = e.name().toLowerCase();
                    if (n.contains("outpost")) outposts = e;
                    else if (n.contains("pit")) pit = e;
                    else if (n.contains("king of the hill") || n.contains("koth")) koth = e;
                    else if (n.contains("stronghold")) stronghold = e;
                }
            }
        }

        // --- Mines: tidy scatter across the top-centre/right, two interleaved heights ---
        int m = mines.size();
        for (int i = 0; i < m; i++) {
            float t = m == 1 ? 0.5f : (float) i / (m - 1);
            int cx = fx(0.34f + 0.64f * t);
            int cy = fy(i % 2 == 0 ? 0.13f : 0.31f);
            out.add(icon(mines.get(i), shortMine(mines.get(i).name()), playerSub(mines.get(i)), 3.6f, cx, cy));
        }

        // --- Outposts: 4 icons in a straight vertical line, running /outpost N ---
        // (kept above the Pit/Stronghold/KOTH row at 0.50 so the Cosmonaut label doesn't collide)
        for (int k = 0; k < 4; k++) {
            out.add(new Icon(OUTPOST_ITEMS[k].getDefaultStack(), OUTPOST_RANKS[k], null, -1,
                    "outpost " + (k + 1), outposts, fx(0.05f), fy(0.06f + k * 0.105f), 1.3f, false));
        }

        // --- Pit · Stronghold · KOTH (down & left, Stronghold bigger in the middle) ---
        if (pit != null) out.add(icon(pit, "", playerSub(pit), 1.6f, fx(0.0f), fy(0.78f)));
        if (stronghold != null) out.add(icon(stronghold, "", playerSub(stronghold), 2.4f,
                fx(0.11f), fy(0.78f)));
        if (koth != null) out.add(icon(koth, "", kothSub(koth), 1.6f, fx(0.22f), fy(0.78f)));

        // --- Badlands: row shifted right ---
        int b = badlands.size();
        for (int j = 0; j < b; j++) {
            float t = b == 1 ? 0.5f : (float) j / (b - 1);
            int cx = fx(0.44f + 0.52f * t);
            int cy = fy(0.79f);
            out.add(icon(badlands.get(j), shortBadlands(badlands.get(j).name()), playerSub(badlands.get(j)), 1.7f, cx, cy));
        }

        return out;
    }

    private Icon icon(WarpEntry e, String label, String sub, float scale, int cx, int cy) {
        return new Icon(e.stack(), label, sub, e.slotIndex(), null, e, cx, cy, scale, e.locked());
    }

    private String playerSub(WarpEntry e) {
        if (e.locked()) return "§cLocked" + (e.requiredLevel() > 0 ? " §7Lv" + e.requiredLevel() : "");
        if (e.players() >= 0) {
            String c = e.players() > 0 ? "§a" : "§8";
            return c + e.players() + " §7online";
        }
        return statusSub(e);
    }

    private String statusSub(WarpEntry e) {
        if (!e.status().isEmpty() && !e.status().equals("UNLOCKED")) return "§7" + capitalize(e.status());
        return null;
    }

    /** Live KOTH state: countdown from "Next Scheduled KOTH: …" lore, or OPEN/CLOSED status. */
    private String kothSub(WarpEntry e) {
        if ("OPEN".equals(e.status())) return "§a§lOPEN";
        for (Text l : e.lore()) {
            String s = l.getString().replaceAll("§.", "").trim();
            if (s.startsWith("Next Scheduled KOTH:")) {
                String v = s.substring(s.indexOf(':') + 1).trim();
                if (!v.isEmpty() && !v.equals("0s")) return "§ein " + v;
            }
        }
        return statusSub(e);
    }

    private List<Text> tooltip(WarpEntry e) {
        List<Text> tip = new ArrayList<>();
        tip.add(Text.literal("§e" + e.name()));
        for (Text l : e.lore()) {
            if (l.getString().isBlank()) continue;
            tip.add(l); // keep the server's original colours
        }
        return tip;
    }

    private static String shortMine(String name) {
        return name.endsWith(" Mine") ? name.substring(0, name.length() - 5) : name;
    }

    private static String shortBadlands(String name) {
        int c = name.indexOf(':');
        return c >= 0 ? name.substring(c + 1).trim() : name;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    // ---- input -------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyInput input) {
        // The inventory ("menu") key closes the warp screen, just like Escape.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && mc.options.inventoryKey.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();
        for (Icon ic : icons()) {
            if (inHit(mx, my, ic)) {
                activate(ic);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void activate(Icon ic) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (ic.command() != null) {
            // Command-based warp (outposts): close the server menu, then run the command.
            leaving = true;
            mc.player.closeHandledScreen();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendChatCommand(ic.command());
            }
            return;
        }
        if (handler != null && mc.interactionManager != null) {
            mc.interactionManager.clickSlot(syncId, ic.slotIndex(), 0, SlotActionType.PICKUP, mc.player);
            leaving = true;
            mc.setScreen(null);
            return;
        }
        // Concept fallback.
        mc.setScreen(null);
        mc.player.sendMessage(Chat.of("§7[§bWarps§7] §fSelected §e" + ic.label()), false);
    }

    @Override
    public void close() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!leaving && handler != null && mc.player != null) {
            mc.player.closeHandledScreen();
            return;
        }
        super.close();
    }

    private boolean inHit(int mx, int my, Icon ic) {
        int half = ic.half();
        return mx >= ic.cx() - half && mx <= ic.cx() + half
                && my >= ic.baseCy() - half && my <= ic.baseCy() + half + 16;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- offline concept fallback -----------------------------------------

    private static final List<WarpEntry> CONCEPT = List.of(
            concept("Coal Mine", Items.COAL_ORE.getDefaultStack()),
            concept("Iron Mine", Items.IRON_ORE.getDefaultStack()),
            concept("Gold Mine", Items.GOLD_ORE.getDefaultStack()),
            concept("Redstone Mine", Items.REDSTONE_ORE.getDefaultStack()),
            concept("Lapis Mine", Items.LAPIS_ORE.getDefaultStack()),
            concept("Diamond Mine", Items.DIAMOND_ORE.getDefaultStack()),
            concept("Emerald Mine", Items.EMERALD_ORE.getDefaultStack()));

    private static WarpEntry concept(String name, ItemStack icon) {
        return new WarpEntry(-1, icon, name, "UNLOCKED", -1, -1, Category.MINES, false, List.of());
    }
}
