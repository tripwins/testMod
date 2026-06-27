package dev.prisonsutils.client.ui;

import dev.prisonsutils.client.api.CosmicApi;
import dev.prisonsutils.client.guard.AutoGuardScanner;
import dev.prisonsutils.client.guard.GuardRenderer;
import dev.prisonsutils.client.pv.PvInterceptor;
import dev.prisonsutils.client.pv.PvItems;
import dev.prisonsutils.client.pv.PvManager;
import dev.prisonsutils.client.pv.PvScanner;
import dev.prisonsutils.client.search.SearchManager;
import dev.prisonsutils.client.util.Chat;
import dev.prisonsutils.client.warp.WarpScreen;
import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Vanilla-styled inventory buttons plus a categorized settings menu. Features are grouped
 * into collapsible categories; each entry has a control row, wrapped description, and an
 * optional inline expansion (e.g. the color picker grid). The panel is a FIXED size and
 * scrolls; collapsing categories never changes its size. Driven by screen mixins (Fabric
 * ScreenEvents don't fire under Lunar).
 */
public final class PrisonsMenu {
    private static final int BTN_SIZE = 20;
    private static final int BTN_SPACING = 22;
    private static final int BTN_RESERVE = 24;

    private static final Identifier BTN_SPRITE = Identifier.ofVanilla("widget/button");
    private static final Identifier BTN_SPRITE_HIGHLIGHTED =
            Identifier.ofVanilla("widget/button_highlighted");

    private static final int PAD = 12;
    private static final int TITLE_H = 24;
    private static final int MENU_GAP = 3;
    private static final int SCROLLBAR_W = 4;
    private static final int CONTENT_W = 300;
    private static final int DESC_INDENT = 6;
    private static final int DESC_W = CONTENT_W - DESC_INDENT;
    private static final int DESC_LINE_H = 9;
    private static final int ENTRY_GAP = 5;
    private static final int HEADER_H = 13;
    private static final int HEADER_BLOCK = HEADER_H + 3;
    private static final int CATEGORY_GAP = 8;

    private static final int INV_H = 166;
    private static final int TOP_MARGIN = 8;
    private static final int MIN_VIEWPORT = 64;
    private static final int FIXED_VIEWPORT = 190; // panel is always this tall (scrolls)
    private static final int SCROLL_STEP = 24;

    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int HEADER_COLOR = 0xFFFFE08A;
    private static final int DESC_COLOR = 0xFFADB6C0;
    private static final int DIVIDER = 0x70FFFFFF;
    private static final int HEADER_DIVIDER = 0x33FFFFFF;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int SCROLL_TRACK = 0x40000000;
    private static final int SCROLL_THUMB = 0x90FFFFFF;
    private static final int ACCENT = 0xFF4ADE80;

    private enum ButtonAction { MENU, WARP, STORAGE }

    private record Button(Text label, ItemStack icon, ButtonAction action) {}

    private static final List<Button> BUTTONS = List.of(
            new Button(Text.literal("Settings"), new ItemStack(Items.COMPARATOR), ButtonAction.MENU),
            new Button(Text.literal("Info"), new ItemStack(Items.BOOK), ButtonAction.MENU),
            new Button(Text.literal("Warps"), new ItemStack(Items.DIAMOND_ORE), ButtonAction.WARP),
            new Button(Text.literal("Storage"), new ItemStack(Items.ENDER_CHEST), ButtonAction.STORAGE));

    private record Category(String name, MenuEntry[] entries) {}

    private static final Category[] CATEGORIES = {
            new Category("Inventory", new MenuEntry[]{
                    new DotToggle("Search Bar",
                            "Adds a search box below any inventory. Type to highlight matching "
                                    + "items. The text stays when you reopen inventories.",
                            () -> Config.get().searchBarEnabled,
                            v -> { Config.get().searchBarEnabled = v; Config.save(); }),
                    new DotToggle("Item Overlays",
                            "Shows Cosmic Energy, money note, XP bottle, and trinket charge values "
                                    + "on items in slots and on your hotbar.",
                            () -> Config.get().itemOverlayEnabled,
                            v -> { Config.get().itemOverlayEnabled = v; Config.save(); }),
                    new ScaleSlider("Overlay Scale",
                            "Size of the value text drawn on items. Drag, click, or scroll.",
                            "x", 0.5, 2.0, 0.1,
                            () -> Config.get().itemOverlayScale,
                            v -> { Config.get().itemOverlayScale = (float) v; Config.save(); }),
                    new ColorOption("Overlay Color",
                            "Color of all item overlays: NRG, money notes, XP, trinket charges, "
                                    + "and enchant labels.",
                            () -> Config.get().overlayColor,
                            c -> { Config.get().overlayColor = c; Config.save(); }),
                    new DotToggle("Slot Locking",
                            "Alt+click a slot in any inventory to lock or unlock it. Locked slots "
                                    + "show a padlock and can't be moved, swapped, or dropped.",
                            () -> Config.get().slotLockEnabled,
                            v -> { Config.get().slotLockEnabled = v; Config.save(); }),
                    new DotToggle("Enchant Helper",
                            "Adds enchant-book stats (tier, success/destroy %, level, energy) and "
                                    + "paging cost math to the item's tooltip.",
                            () -> Config.get().enchantHelperEnabled,
                            v -> { Config.get().enchantHelperEnabled = v; Config.save(); }),
                    new DotToggle("Enchant Overlay",
                            "Draws a short enchant label on enchant books (Electrocution I -> E1, "
                                    + "TitanBlood IV -> TB4).",
                            () -> Config.get().enchantOverlayEnabled,
                            v -> { Config.get().enchantOverlayEnabled = v; Config.save(); }),
                    new DotToggle("Rarity Tint",
                            "Glazes enchant books and graded items (Legendary, Godly, Ultimate, "
                                    + "etc.) with a rarity-colored tint in slots and the hotbar.",
                            () -> Config.get().rarityTintEnabled,
                            v -> { Config.get().rarityTintEnabled = v; Config.save(); }),
                    new DotToggle("Hide Glint on Drops",
                            "Removes the enchant glint from dropped rarity items so they aren't shiny "
                                    + "on the ground. The in-slot rarity tint is unaffected.",
                            () -> Config.get().hideDroppedRarityGlint,
                            v -> { Config.get().hideDroppedRarityGlint = v; Config.save(); }),
            }),
            new Category("Guards", new MenuEntry[]{
                    new DotToggle("Guard ESP",
                            "Highlights guard danger zones in the world. Mark guards by hand "
                                    + "with a wooden shovel: right-click a block to toggle a mark "
                                    + "(or /guard mark while looking at it), left-click a marked "
                                    + "block to remove it. /guard clear removes all marks, "
                                    + "/guard list shows them.",
                            () -> Config.get().guardViewEnabled,
                            v -> {
                                Config.get().guardViewEnabled = v;
                                Config.save();
                                if (v) GuardRenderer.onVisibilityEnabled();
                            }),
                    new DotToggle("Auto-Mark Guards",
                            "Auto-marks nearby entities named \"guard\". Best-effort: it may miss "
                                    + "guards or mark the wrong entity, and a guard following a player "
                                    + "gets re-marked every block it moves. Use it in an EMPTY mine, "
                                    + "then turn it OFF once the guards are marked. Left-click a marked "
                                    + "block with a wooden shovel to remove a mark.",
                            () -> Config.get().autoGuardMarkEnabled,
                            v -> {
                                Config.get().autoGuardMarkEnabled = v;
                                Config.save();
                                if (v) {
                                    AutoGuardScanner.onEnabled();
                                    warnAutoMark();
                                }
                            }),
                    new DotToggle("Danger HUD Warning",
                            "Flashes an on-screen warning when you step into a guard's danger zone "
                                    + "(works even with the ESP boxes hidden).",
                            () -> Config.get().guardWarningEnabled,
                            v -> { Config.get().guardWarningEnabled = v; Config.save(); }),
                    new ColorOption("Danger Color",
                            "Color for zones where a guard can see you. Click the swatch to pick.",
                            () -> Config.get().guardDangerColor,
                            c -> { Config.get().guardDangerColor = c; Config.save(); }),
                    new ColorOption("Warning Color",
                            "Color for the warning edge around a danger zone.",
                            () -> Config.get().guardWarningColor,
                            c -> { Config.get().guardWarningColor = c; Config.save(); }),
                    new ColorOption("Blocked Color",
                            "Color where the guard's line of sight is blocked.",
                            () -> Config.get().guardNoLosColor,
                            c -> { Config.get().guardNoLosColor = c; Config.save(); }),
            }),
            new Category("Pets", new MenuEntry[]{
                    new DotToggle("Pet Radius",
                            "Draws a circle at your feet showing the radius of the pet you're "
                                    + "holding (read from \"<n> block radius\" in its lore).",
                            () -> Config.get().petRadiusEnabled,
                            v -> { Config.get().petRadiusEnabled = v; Config.save(); }),
                    new ScaleSlider("Line Thickness",
                            "Thickness of the radius circle lines.",
                            "px", 1.0, 10.0, 0.5,
                            () -> Config.get().petRadiusThickness,
                            v -> { Config.get().petRadiusThickness = (float) v; Config.save(); }),
                    new ColorOption("Radius Color",
                            "Color of the pet radius circle.",
                            () -> Config.get().petRadiusColor,
                            c -> { Config.get().petRadiusColor = c; Config.save(); }),
                    new DotToggle("Intruder Alert",
                            "When another player steps inside the radius, the ring pulses in the "
                                    + "alert color — without marking where the player is.",
                            () -> Config.get().petRadiusAlertEnabled,
                            v -> { Config.get().petRadiusAlertEnabled = v; Config.save(); }),
                    new ColorOption("Alert Color",
                            "Ring color used while a player is inside the radius.",
                            () -> Config.get().petRadiusAlertColor,
                            c -> { Config.get().petRadiusAlertColor = c; Config.save(); }),
            }),
            new Category("Gameplay", new MenuEntry[]{
                    new DotToggle("Damage Indicators",
                            "Shows floating damage numbers (with decimals) above entities you hit.",
                            () -> Config.get().damageIndicatorsEnabled,
                            v -> { Config.get().damageIndicatorsEnabled = v; Config.save(); }),
                    new DotToggle("Right-Click While Mining",
                            "Lets you use items / right-click without stopping while you mine.",
                            () -> Config.get().rightClickWhileMining,
                            v -> { Config.get().rightClickWhileMining = v; Config.save(); }),
                    new DotToggle("Blink Preview",
                            "While holding the Blink trinket, shows a ghost box where you'd "
                                    + "teleport to.",
                            () -> Config.get().blinkPreviewEnabled,
                            v -> { Config.get().blinkPreviewEnabled = v; Config.save(); }),
                    new ColorOption("Blink Preview Color",
                            "Color of the Blink teleport marker.",
                            () -> Config.get().blinkPreviewColor,
                            c -> { Config.get().blinkPreviewColor = c; Config.save(); }),
            }),
            new Category("Waypoint", new MenuEntry[]{
                    new DotToggle("Pings",
                            "Hold the ping key to aim a location marker, release to drop it. Draws a "
                                    + "beam, a sonar ring, and a name/distance label, with a sound.",
                            () -> Config.get().pingEnabled,
                            v -> { Config.get().pingEnabled = v; Config.save(); }),
                    new KeybindOption("Ping Key",
                            "Key you hold to aim a ping and release to drop it. Default V.",
                            () -> Config.get().pingKey,
                            k -> { Config.get().pingKey = k; Config.save(); }),
                    new ColorOption("Ping Color",
                            "Color of the ping beam, ring, and label background.",
                            () -> Config.get().pingColor,
                            c -> { Config.get().pingColor = c; Config.save(); }),
                    new DotToggle("Ping Sound",
                            "Play a sound when a ping is dropped.",
                            () -> Config.get().pingSound,
                            v -> { Config.get().pingSound = v; Config.save(); }),
            }),
            new Category("HUD", new MenuEntry[]{
                    new ActionButton("Edit HUD Layout",
                            "Drag HUD elements to move them, drag the corner (or scroll) to scale. "
                                    + "Snaps to the grid and screen center/edges; right-justifies "
                                    + "on the right half of the screen.",
                            "Open ▶",
                            () -> MinecraftClient.getInstance().setScreen(
                                    new dev.prisonsutils.client.hud.HudEditorScreen())),
                    new DotToggle("Coordinates (XYZ)",
                            "Always-on XYZ readout. Top-left by default; move or scale it in "
                                    + "Edit HUD Layout.",
                            () -> Config.get().coordsHudEnabled,
                            v -> { Config.get().coordsHudEnabled = v; Config.save(); }),
                    new DotToggle("Cooldown Alerts",
                            "Floats a \"<item> Ready!\" popup when a trinket or pet comes off "
                                    + "cooldown.",
                            () -> Config.get().cooldownHudEnabled,
                            v -> { Config.get().cooldownHudEnabled = v; Config.save(); }),
                    new DotToggle("Cooldown Ready Sound",
                            "Play a subtle ping alongside the cooldown-ready popup.",
                            () -> Config.get().cooldownHudSound,
                            v -> { Config.get().cooldownHudSound = v; Config.save(); }),
            }),
            new Category("Alerts", new MenuEntry[]{
                    new DotToggle("Notifications",
                            "Master switch for pop-up toast alerts in the bottom-right corner.",
                            () -> Config.get().notificationsEnabled,
                            v -> { Config.get().notificationsEnabled = v; Config.save(); }),
                    new DotToggle("Ban Alerts",
                            "Pops a notification for each new punishment on the CosmicPrisons bans "
                                    + "list (checked every minute).",
                            () -> Config.get().banNotificationsEnabled,
                            v -> { Config.get().banNotificationsEnabled = v; Config.save(); }),
            }),
            new Category("Cosmic API", new MenuEntry[]{
                    new DotToggle("Cosmic API (beta)",
                            "Connects to the official CosmicPrisons API over cosmicapi:main for "
                                    + "accurate data (cooldowns, trinkets, pets, merchants, meteors, "
                                    + "events, and more). Guards are NOT included — marks stay "
                                    + "manual. Requires an approved app: paste your clientId into "
                                    + "config/prisonsutils.json (\"cosmicClientId\"), then reconnect. "
                                    + "Does nothing on servers that don't support the API.",
                            () -> Config.get().cosmicApiEnabled,
                            v -> {
                                Config.get().cosmicApiEnabled = v;
                                Config.save();
                                if (v) CosmicApi.requestHandshake();
                            }),
            }),
    };

    private static final String[] INFO_LINES = {
            "PrisonsUtils — quality-of-life tools for CosmicPrisons.",
            "Open this menu from the buttons above your inventory.",
            "Click a category header to collapse or expand it.",
    };

    private static int active = -1; // -1 = closed
    private static int scrollOffset = 0;
    private static Screen lastResetScreen;

    // Storage (Player Vaults) panel — index 3 in BUTTONS.
    private static final int STORAGE_INDEX = 3;
    private static final int PV_SLOT = 18;
    private static final int PV_COLS = 9;
    private static final int PV_GRID_W = PV_COLS * PV_SLOT;
    private static final int PV_COL_GAP = 14;
    // Storage panel is wider than Settings so two vaults sit side by side.
    private static final int STORAGE_CONTENT_W = 2 * PV_GRID_W + PV_COL_GAP;
    private static final Map<Integer, Map<Integer, ItemStack>> storageStacks = new LinkedHashMap<>();

    private PrisonsMenu() {}

    private static void rebuildStorage() {
        storageStacks.clear();
        for (int idx : PvManager.indices()) {
            storageStacks.put(idx, PvItems.stacksOf(PvManager.vault(idx)));
        }
    }

    // Search-match badge on the Storage tab button: lit when the query matches a cached vault item,
    // even when Storage isn't the open tab. Recomputed only when the query string changes (parsing
    // every vault each frame would be wasteful), so the per-frame cost is just a string compare.
    private static String storageMatchQuery;
    private static boolean storageHasSearchMatch;

    private static boolean storageMatchesSearch() {
        String q = SearchManager.getSearchQuery();
        if (q == null || q.isEmpty()) {
            storageMatchQuery = q;
            storageHasSearchMatch = false;
            return false;
        }
        if (!q.equals(storageMatchQuery)) {
            storageMatchQuery = q;
            storageHasSearchMatch = computeStorageMatch();
        }
        return storageHasSearchMatch;
    }

    private static boolean computeStorageMatch() {
        for (int idx : PvManager.indices()) {
            for (ItemStack st : PvItems.stacksOf(PvManager.vault(idx)).values()) {
                if (SearchManager.matches(st)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Open the player inventory with {@code tabIndex} pre-selected. Used by the tab buttons we overlay
     * on a real vault GUI ({@link dev.prisonsutils.mixin.client.HandledScreenVaultButtonsMixin}): any
     * open server container (the vault) is closed first so the server doesn't think it's still open.
     */
    public static void openInventoryWithTab(int tabIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen(); // leave the live vault cleanly (sends the close packet)
        }
        if (tabIndex == STORAGE_INDEX) {
            if (PvManager.count() == 0) {
                PvScanner.startScan();
                return;
            }
            rebuildStorage();
        }
        InventoryScreen screen = new InventoryScreen(mc.player);
        mc.setScreen(screen);
        active = tabIndex;
        scrollOffset = 0;
        screen.resize(screen.width, screen.height);
    }

    private static int rowMaxRows(List<Integer> idxs, int i) {
        int rows = PvManager.vault(idxs.get(i)).rows;
        if (i + 1 < idxs.size()) rows = Math.max(rows, PvManager.vault(idxs.get(i + 1)).rows);
        return rows;
    }

    private static int storageContentHeight() {
        List<Integer> idxs = PvManager.indices();
        int h = 0;
        for (int i = 0; i < idxs.size(); i += 2) {
            h += HEADER_H + rowMaxRows(idxs, i) * PV_SLOT + CATEGORY_GAP;
        }
        return Math.max(h, 1);
    }

    /** Draws the vault grids two-per-row; returns the hovered stack (tooltip drawn outside scissor). */
    private static ItemStack renderStorage(DrawContext ctx, TextRenderer font, int contentX, int y,
                                           int mouseX, int mouseY) {
        ItemStack hovered = null;
        String query = SearchManager.getSearchQuery();
        boolean searching = query != null && !query.isEmpty();
        List<Integer> idxs = PvManager.indices();
        int rightX = contentX + PV_GRID_W + PV_COL_GAP;
        for (int i = 0; i < idxs.size(); i += 2) {
            ItemStack h1 = drawVault(ctx, font, idxs.get(i), contentX, y, mouseX, mouseY, searching);
            if (h1 != null) hovered = h1;
            if (i + 1 < idxs.size()) {
                ItemStack h2 = drawVault(ctx, font, idxs.get(i + 1), rightX, y, mouseX, mouseY, searching);
                if (h2 != null) hovered = h2;
            }
            y += HEADER_H + rowMaxRows(idxs, i) * PV_SLOT + CATEGORY_GAP;
        }
        return hovered;
    }

    private static ItemStack drawVault(DrawContext ctx, TextRenderer font, int idx, int vx, int y,
                                       int mouseX, int mouseY, boolean searching) {
        PvManager.Vault v = PvManager.vault(idx);
        Map<Integer, ItemStack> stacks = storageStacks.getOrDefault(idx, Map.of());
        ItemStack hovered = null;
        boolean hasMatch = false;
        int gy = y + HEADER_H;
        for (int s = 0; s < v.rows * PV_COLS; s++) {
            int sx = vx + (s % PV_COLS) * PV_SLOT, sy = gy + (s / PV_COLS) * PV_SLOT;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x55101820);
            ItemStack st = stacks.get(s);
            if (st == null || st.isEmpty()) continue;
            boolean match = searching && SearchManager.matches(st);
            if (match) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x9944DD55); // highlight behind matches
                hasMatch = true;
            }
            ctx.drawItem(st, sx, sy);
            ctx.drawStackOverlay(font, st, sx, sy);
            if (searching && !match) ctx.fill(sx, sy, sx + 16, sy + 16, 0xA0101820); // dim non-matches
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) hovered = st;
        }
        boolean headerHover = mouseX >= vx - 2 && mouseX < vx + PV_GRID_W && mouseY >= y - 1 && mouseY < y + HEADER_H - 1;
        if (headerHover) ctx.fill(vx - 2, y - 1, vx + PV_GRID_W, y + HEADER_H - 1, HOVER_BG);
        int headerColor = (searching && hasMatch) ? 0xFF55FF55 : HEADER_COLOR;
        ctx.drawText(font, "Vault " + idx + (headerHover ? " §7▶ edit" : ""), vx, y + 2, headerColor, true);
        if (searching && hasMatch) {
            ctx.fill(vx - 2, y - 1, vx + PV_GRID_W, y, 0xFF55FF55); // accent line over a matching vault
        }
        return hovered;
    }


    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Soft click feedback for our custom UI buttons/toggles (vanilla UI button volume). */
    private static void playClick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(
                    PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
    }

    /** Chat warning shown when auto-mark is switched on, so its caveats are hard to miss. */
    private static void warnAutoMark() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.sendMessage(Chat.of("§6§l⚠ Auto-Mark Guards enabled"), false);
        mc.player.sendMessage(Chat.of("§eBest-effort: it may miss guards or mark the wrong entity."), false);
        mc.player.sendMessage(Chat.of("§eA guard following a player is re-marked every block it moves."), false);
        mc.player.sendMessage(Chat.of("§eUse it in an EMPTY mine, then turn it OFF once guards are marked."), false);
    }

    public static int reservedTopHeight() {
        if (active < 0) {
            return 0;
        }
        return BTN_RESERVE + MENU_GAP + menuHeight();
    }

    public static void resetIfNewScreen(InventoryScreen screen) {
        if (screen != lastResetScreen) {
            active = -1;
            scrollOffset = 0;
            lastResetScreen = screen;
        }
    }

    private static boolean isCollapsed(String name) {
        return Config.get().collapsedCategories.contains(name);
    }

    private static void toggleCollapse(String name) {
        var l = Config.get().collapsedCategories;
        if (!l.remove(name)) l.add(name);
        Config.save();
    }

    // ---- sizing (panel size is fixed; only the scrollable content changes) -----------

    private static List<String> wrap(TextRenderer font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (font.getWidth(trial) > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static int descLines(TextRenderer font, MenuEntry e) {
        return wrap(font, e.description(), DESC_W).size();
    }

    private static int entryHeight(TextRenderer font, MenuEntry e) {
        return DotToggle.ROW_HEIGHT + descLines(font, e) * DESC_LINE_H + e.expandedHeight() + ENTRY_GAP;
    }

    private static int contentHeight() {
        TextRenderer font = font();
        if (active == STORAGE_INDEX) {
            return storageContentHeight();
        }
        if (active != 0) {
            return INFO_LINES.length * DESC_LINE_H + CATEGORY_GAP;
        }
        int total = 0;
        for (Category cat : CATEGORIES) {
            total += HEADER_BLOCK;
            if (!isCollapsed(cat.name)) {
                for (MenuEntry e : cat.entries) total += entryHeight(font, e);
            }
            total += CATEGORY_GAP;
        }
        return total;
    }

    private static int menuWidth() {
        int cw = active == STORAGE_INDEX ? STORAGE_CONTENT_W : CONTENT_W;
        return cw + 2 * PAD + SCROLLBAR_W;
    }

    /** Fixed panel viewport — same height no matter how many categories are collapsed. */
    private static int viewportHeight() {
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int fit = screenH - INV_H - BTN_RESERVE - MENU_GAP - TOP_MARGIN - TITLE_H - PAD;
        return Math.max(MIN_VIEWPORT, Math.min(FIXED_VIEWPORT, fit));
    }

    private static int menuHeight() {
        return TITLE_H + viewportHeight() + PAD;
    }

    private static int scrollMax() {
        return Math.max(0, contentHeight() - viewportHeight());
    }

    private static int menuLeft(int invX, int bw) {
        return invX + bw / 2 - menuWidth() / 2;
    }

    private static int menuTop(int invY) {
        return invY - BTN_RESERVE - MENU_GAP - menuHeight();
    }

    // ---- rendering -------------------------------------------------------------------

    public static void render(
            DrawContext ctx, TextRenderer font, int invX, int invY, int bw, int mouseX, int mouseY) {
        if (active >= 0 && active < BUTTONS.size()) {
            renderMenu(ctx, font, invX, invY, bw, mouseX, mouseY);
        }

        int btnTop = invY - BTN_RESERVE;
        int hovered = buttonAt(mouseX, mouseY, invX, invY);
        boolean storageMatch = storageMatchesSearch();
        for (int i = 0; i < BUTTONS.size(); i++) {
            int bx = invX + i * BTN_SPACING;
            renderButton(ctx, bx, btnTop, BUTTONS.get(i).icon(),
                    i == active, i == hovered, i == STORAGE_INDEX && storageMatch);
        }

        if (hovered >= 0) {
            ctx.drawTooltip(font, BUTTONS.get(hovered).label(), mouseX, mouseY);
        }
    }

    private static void renderButton(DrawContext ctx, int x, int y, ItemStack icon,
                                     boolean active, boolean hover, boolean notify) {
        Identifier sprite = (active || hover) ? BTN_SPRITE_HIGHLIGHTED : BTN_SPRITE;
        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, y, BTN_SIZE, BTN_SIZE);
        ctx.drawItem(icon, x + 2, y + 2);
        if (active) {
            ctx.fill(x + 2, y + BTN_SIZE - 2, x + BTN_SIZE - 2, y + BTN_SIZE - 1, ACCENT);
        }
        if (notify) {
            // Search-match badge: a small green dot in the top-right corner.
            ctx.fill(x + BTN_SIZE - 7, y + 1, x + BTN_SIZE - 1, y + 7, 0xFF14160F);
            ctx.fill(x + BTN_SIZE - 6, y + 2, x + BTN_SIZE - 2, y + 6, ACCENT);
        }
    }

    private static void renderMenu(
            DrawContext ctx, TextRenderer font, int invX, int invY, int bw, int mouseX, int mouseY) {
        int mW = menuWidth();
        int mH = menuHeight();
        int mLeft = menuLeft(invX, bw);
        int mTop = menuTop(invY);

        ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BTN_SPRITE, mLeft, mTop, mW, mH);

        ctx.drawText(font, BUTTONS.get(active).label(), mLeft + PAD, mTop + PAD, TITLE_COLOR, true);
        ctx.fill(mLeft + PAD, mTop + TITLE_H - 4, mLeft + mW - PAD, mTop + TITLE_H - 3, DIVIDER);

        int viewTop = mTop + TITLE_H;
        int viewH = viewportHeight();
        int max = scrollMax();
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));

        ctx.enableScissor(mLeft, viewTop, mLeft + mW, viewTop + viewH);
        int contentX = mLeft + PAD;
        int y = viewTop - scrollOffset;
        ItemStack storageHover = null;
        if (active == STORAGE_INDEX) {
            storageHover = renderStorage(ctx, font, contentX, y, mouseX, mouseY);
        } else if (active == 0) {
            for (Category cat : CATEGORIES) {
                boolean collapsed = isCollapsed(cat.name);
                if (inRow(mouseX, mouseY, contentX, y, CONTENT_W, HEADER_H)) {
                    ctx.fill(contentX - 2, y - 1, contentX + CONTENT_W + 2, y + HEADER_H, HOVER_BG);
                }
                drawTriangle(ctx, contentX, y + 3, !collapsed, HEADER_COLOR);
                ctx.drawText(font, cat.name, contentX + 11, y + 2, HEADER_COLOR, true);
                ctx.fill(contentX, y + HEADER_H, contentX + CONTENT_W, y + HEADER_H + 1, HEADER_DIVIDER);
                y += HEADER_BLOCK;

                if (!collapsed) {
                    for (MenuEntry e : cat.entries) {
                        e.render(ctx, font, contentX, y, CONTENT_W, mouseX, mouseY);
                        int dl = descLines(font, e);
                        int descY = y + DotToggle.ROW_HEIGHT;
                        for (String line : wrap(font, e.description(), DESC_W)) {
                            ctx.drawText(font, line, contentX + DESC_INDENT, descY, DESC_COLOR, true);
                            descY += DESC_LINE_H;
                        }
                        if (e.expandedHeight() > 0) {
                            e.renderExpansion(ctx, font, contentX, descY, CONTENT_W, mouseX, mouseY);
                        }
                        y += DotToggle.ROW_HEIGHT + dl * DESC_LINE_H + e.expandedHeight() + ENTRY_GAP;
                    }
                }
                y += CATEGORY_GAP;
            }
        } else {
            for (String line : INFO_LINES) {
                ctx.drawText(font, line, contentX, y, DESC_COLOR, true);
                y += DESC_LINE_H;
            }
        }
        ctx.disableScissor();

        if (storageHover != null) {
            ctx.drawItemTooltip(font, storageHover, mouseX, mouseY);
        }

        if (max > 0) {
            int trackX = mLeft + mW - SCROLLBAR_W - 2;
            ctx.fill(trackX, viewTop, trackX + SCROLLBAR_W, viewTop + viewH, SCROLL_TRACK);
            int thumbH = Math.max(16, viewH * viewH / contentHeight());
            int thumbY = viewTop + (viewH - thumbH) * scrollOffset / max;
            ctx.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, SCROLL_THUMB);
        }
    }

    private static void drawTriangle(DrawContext ctx, int x, int y, boolean expanded, int c) {
        if (expanded) {
            ctx.fill(x, y, x + 7, y + 1, c);
            ctx.fill(x + 1, y + 1, x + 6, y + 2, c);
            ctx.fill(x + 2, y + 2, x + 5, y + 3, c);
            ctx.fill(x + 3, y + 3, x + 4, y + 4, c);
        } else {
            ctx.fill(x + 1, y - 1, x + 2, y + 6, c);
            ctx.fill(x + 2, y, x + 3, y + 5, c);
            ctx.fill(x + 3, y + 1, x + 4, y + 4, c);
            ctx.fill(x + 4, y + 2, x + 5, y + 3, c);
        }
    }

    // ---- input -----------------------------------------------------------------------

    public static boolean click(int mouseX, int mouseY, int invX, int invY, int bw, Screen screen) {
        int clickedButton = buttonAt(mouseX, mouseY, invX, invY);
        if (clickedButton >= 0) {
            playClick();
            ButtonAction action = BUTTONS.get(clickedButton).action();
            if (action == ButtonAction.WARP) {
                // Trigger the real server /warp flow; WarpInterceptor swaps the server GUI for ours.
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null && mc.getNetworkHandler() != null) {
                    dev.prisonsutils.client.warp.WarpInterceptor.markPending();
                    mc.getNetworkHandler().sendChatCommand("warp");
                } else {
                    mc.setScreen(new WarpScreen());
                }
                return true;
            }
            if (action == ButtonAction.STORAGE && PvManager.count() == 0) {
                PvScanner.startScan(); // nothing cached yet → scan first
                return true;
            }
            if (action == ButtonAction.STORAGE) {
                rebuildStorage();
            }
            active = clickedButton == active ? -1 : clickedButton;
            scrollOffset = 0;
            screen.resize(screen.width, screen.height);
            return true;
        }

        if (active >= 0) {
            int mW = menuWidth();
            int mH = menuHeight();
            int mLeft = menuLeft(invX, bw);
            int mTop = menuTop(invY);
            if (mouseX >= mLeft && mouseX < mLeft + mW && mouseY >= mTop && mouseY < mTop + mH) {
                if (active == 0) {
                    clickSettings(mouseX, mouseY, mLeft + PAD, mTop + TITLE_H);
                } else if (active == STORAGE_INDEX) {
                    clickStorage(mouseX, mouseY, mLeft + PAD, mTop + TITLE_H);
                }
                return true;
            }
        }
        return false;
    }

    private static void clickSettings(int mouseX, int mouseY, int contentX, int viewTop) {
        int viewH = viewportHeight();
        if (mouseY < viewTop || mouseY >= viewTop + viewH) {
            return;
        }
        TextRenderer font = font();
        int y = viewTop - scrollOffset;
        for (Category cat : CATEGORIES) {
            if (inRow(mouseX, mouseY, contentX, y, CONTENT_W, HEADER_H)) {
                playClick();
                toggleCollapse(cat.name); // panel size is fixed; keep the scroll position
                return;
            }
            y += HEADER_BLOCK;
            if (!isCollapsed(cat.name)) {
                for (MenuEntry e : cat.entries) {
                    if (e.click(mouseX, mouseY, contentX, y, CONTENT_W)) {
                        playClick();
                        return;
                    }
                    int dl = descLines(font, e);
                    if (e.expandedHeight() > 0) {
                        int expY = y + DotToggle.ROW_HEIGHT + dl * DESC_LINE_H;
                        if (e.clickExpansion(mouseX, mouseY, contentX, expY, CONTENT_W)) {
                            playClick();
                            return;
                        }
                    }
                    y += entryHeight(font, e);
                }
            }
            y += CATEGORY_GAP;
        }
    }

    /** Storage tab: clicking a vault opens the real server vault ({@code /pv N}) with our tab buttons overlaid. */
    private static void clickStorage(int mouseX, int mouseY, int contentX, int viewTop) {
        int viewH = viewportHeight();
        if (mouseY < viewTop || mouseY >= viewTop + viewH) {
            return;
        }
        List<Integer> idxs = PvManager.indices();
        int rightX = contentX + PV_GRID_W + PV_COL_GAP;
        int y = viewTop - scrollOffset;
        for (int i = 0; i < idxs.size(); i += 2) {
            if (hitVault(mouseX, mouseY, contentX, y, idxs.get(i))) {
                playClick();
                openVault(idxs.get(i));
                return;
            }
            if (i + 1 < idxs.size() && hitVault(mouseX, mouseY, rightX, y, idxs.get(i + 1))) {
                playClick();
                openVault(idxs.get(i + 1));
                return;
            }
            y += HEADER_H + rowMaxRows(idxs, i) * PV_SLOT + CATEGORY_GAP;
        }
    }

    /** True if (mouseX,mouseY) is over vault {@code idx}'s header+grid block drawn at (vx,y). */
    private static boolean hitVault(int mouseX, int mouseY, int vx, int y, int idx) {
        int bottom = y + HEADER_H + PvManager.vault(idx).rows * PV_SLOT;
        return mouseX >= vx - 2 && mouseX < vx + PV_GRID_W && mouseY >= y - 1 && mouseY < bottom;
    }

    /** Send the real {@code /pv N} and flag the opened vault GUI so it gets our tab buttons. */
    private static void openVault(int idx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return;
        }
        PvInterceptor.markPendingVault();
        mc.getNetworkHandler().sendChatCommand("pv " + idx);
    }

    // ---- vault overlay: real /pv GUI + our tab buttons -------------------------------
    // The vault is the unmodified server container; HandledScreenVaultButtonsMixin draws this strip
    // on top of it and routes clicks here. Buttons behave just like in the inventory — Settings/Info/
    // Storage open our inventory on that tab, Warps fires /warp — each leaving the vault first.

    /** Draw only the tab-button strip, anchored above a real vault container at (invX, invY). */
    public static void renderVaultButtons(
            DrawContext ctx, TextRenderer font, int invX, int invY, int mouseX, int mouseY) {
        int btnTop = invY - BTN_RESERVE;
        int hovered = buttonAt(mouseX, mouseY, invX, invY);
        boolean storageMatch = storageMatchesSearch();
        for (int i = 0; i < BUTTONS.size(); i++) {
            int bx = invX + i * BTN_SPACING;
            renderButton(ctx, bx, btnTop, BUTTONS.get(i).icon(),
                    false, i == hovered, i == STORAGE_INDEX && storageMatch);
        }
        if (hovered >= 0) {
            ctx.drawTooltip(font, BUTTONS.get(hovered).label(), mouseX, mouseY);
        }
    }

    /** Route a click on the vault's tab strip. Returns true if a button was hit. */
    public static boolean vaultButtonClick(int mouseX, int mouseY, int invX, int invY) {
        int i = buttonAt(mouseX, mouseY, invX, invY);
        if (i < 0) {
            return false;
        }
        playClick();
        if (BUTTONS.get(i).action() == ButtonAction.WARP) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.getNetworkHandler() != null) {
                mc.player.closeHandledScreen(); // leave the vault before the warp GUI arrives
                dev.prisonsutils.client.warp.WarpInterceptor.markPending();
                mc.getNetworkHandler().sendChatCommand("warp");
            }
        } else {
            openInventoryWithTab(i); // Settings(0) / Info(1) / Storage(3); closes the vault itself
        }
        return true;
    }

    public static boolean drag(int mouseX, int mouseY, int invX, int invY, int bw) {
        if (active != 0) {
            return false;
        }
        int mLeft = menuLeft(invX, bw);
        int mTop = menuTop(invY);
        int contentX = mLeft + PAD;
        TextRenderer font = font();
        int y = mTop + TITLE_H - scrollOffset;
        for (Category cat : CATEGORIES) {
            y += HEADER_BLOCK;
            if (!isCollapsed(cat.name)) {
                for (MenuEntry e : cat.entries) {
                    if (e.drag(mouseX, mouseY, contentX, y, CONTENT_W)) {
                        return true;
                    }
                    y += entryHeight(font, e);
                }
            }
            y += CATEGORY_GAP;
        }
        return false;
    }

    public static boolean scroll(double mouseX, double mouseY, double verticalAmount, int invX, int invY, int bw) {
        if (active < 0) {
            return false;
        }
        int mW = menuWidth();
        int mH = menuHeight();
        int mLeft = menuLeft(invX, bw);
        int mTop = menuTop(invY);
        if (mouseX < mLeft || mouseX >= mLeft + mW || mouseY < mTop || mouseY >= mTop + mH) {
            return false;
        }

        // First: let an entry (slider) consume the scroll.
        if (active == 0) {
            TextRenderer font = font();
            int contentX = mLeft + PAD;
            int y = mTop + TITLE_H - scrollOffset;
            for (Category cat : CATEGORIES) {
                y += HEADER_BLOCK;
                if (!isCollapsed(cat.name)) {
                    for (MenuEntry e : cat.entries) {
                        if (e.scroll((int) mouseX, (int) mouseY, contentX, y, CONTENT_W, verticalAmount)) {
                            return true;
                        }
                        y += entryHeight(font, e);
                    }
                }
                y += CATEGORY_GAP;
            }
        }

        if (scrollMax() > 0) {
            scrollOffset = Math.max(0, Math.min(scrollMax(),
                    scrollOffset - (int) Math.signum(verticalAmount) * SCROLL_STEP));
        }
        return true; // consume scroll while over the menu
    }

    private static boolean inRow(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int buttonAt(int mouseX, int mouseY, int invX, int invY) {
        int btnTop = invY - BTN_RESERVE;
        if (mouseY < btnTop || mouseY >= btnTop + BTN_SIZE) {
            return -1;
        }
        for (int i = 0; i < BUTTONS.size(); i++) {
            int bx = invX + i * BTN_SPACING;
            if (mouseX >= bx && mouseX < bx + BTN_SIZE) {
                return i;
            }
        }
        return -1;
    }
}
