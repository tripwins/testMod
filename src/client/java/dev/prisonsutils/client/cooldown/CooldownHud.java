package dev.prisonsutils.client.cooldown;

import dev.prisonsutils.client.hud.CrosshairPopup;
import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Small HUD listing the cooldown of every trinket and pet in the player's inventory, so you know
 * when Blink/Heal/Shockwave etc. are ready. Cooldowns are read from the CosmicPrisons NBT we
 * already parse: trinkets carry {@code trinket_cooldown_seconds} + {@code trinket_last_use_ms};
 * pets carry {@code pet_last_use_ms} with the cooldown length in their lore. When an item comes off
 * cooldown a floating "X Ready!" popup appears next to the crosshair (plus an optional ping).
 */
public final class CooldownHud {
    private static final String BUKKIT = "PublicBukkitValues";
    private static final String NS = "cosmicprisons:";

    /** Per-item readiness, keyed by custom_item_uuid, for the ready edge detection. */
    private static final Map<String, Boolean> wasReady = new HashMap<>();

    private record Cd(String name, String uuid, long remainingMs) {
        boolean ready() { return remainingMs <= 0; }
    }

    private CooldownHud() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CooldownHud::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || !Config.get().cooldownHudEnabled) return;
        boolean soundOn = Config.get().cooldownHudSound;
        for (Cd cd : collect(mc.player)) {
            if (cd.uuid() == null) continue;
            Boolean prev = wasReady.get(cd.uuid());
            boolean now = cd.ready();
            if (prev != null && !prev && now) {
                CrosshairPopup.show(cd.name() + " Ready!", 0x6BFF6B);
                if (soundOn) {
                    mc.getSoundManager().play(PositionedSoundInstance.ui(
                            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f));
                }
            }
            wasReady.put(cd.uuid(), now);
        }
    }

    private static List<Cd> collect(PlayerEntity player) {
        List<Cd> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (custom == null) continue;
            NbtCompound bukkit = custom.copyNbt().getCompound(BUKKIT).orElse(null);
            if (bukkit == null) continue;
            String id = bukkit.getString(NS + "custom_item_id").orElse("");

            long lastUse;
            int cooldownSec;
            if (id.startsWith("trinket_")) {
                lastUse = bukkit.getLong(NS + "trinket_last_use_ms").orElse(0L);
                cooldownSec = bukkit.getInt(NS + "trinket_cooldown_seconds").orElse(0);
                if (cooldownSec <= 0) cooldownSec = loreCooldownSeconds(stack);
            } else if (id.startsWith("pet_")) {
                lastUse = bukkit.getLong(NS + "pet_last_use_ms").orElse(0L);
                cooldownSec = loreCooldownSeconds(stack);
            } else {
                continue;
            }
            if (cooldownSec <= 0 || lastUse <= 0) continue;

            long remaining = Math.max(0, lastUse + cooldownSec * 1000L - now);
            String uuid = bukkit.getString(NS + "custom_item_uuid").orElse(null);
            out.add(new Cd(shortName(stack.getName().getString()), uuid, remaining));
        }
        return out;
    }

    /** Parses the "Cooldown" lore (e.g. "1m 30s", "45s", "2m") into seconds. */
    private static int loreCooldownSeconds(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return 0;
        List<Text> lines = lore.lines();
        for (int i = 0; i < lines.size(); i++) {
            String s = strip(lines.get(i).getString()).trim();
            if (s.equalsIgnoreCase("Cooldown") && i + 1 < lines.size()) {
                return parseDuration(strip(lines.get(i + 1).getString()));
            }
            if (s.toLowerCase().startsWith("cooldown:")) {
                return parseDuration(s.substring(s.indexOf(':') + 1));
            }
        }
        return 0;
    }

    private static int parseDuration(String text) {
        int seconds = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s*([hms])").matcher(text.toLowerCase());
        boolean found = false;
        while (m.find()) {
            found = true;
            int v = Integer.parseInt(m.group(1));
            switch (m.group(2)) {
                case "h" -> seconds += v * 3600;
                case "m" -> seconds += v * 60;
                case "s" -> seconds += v;
                default -> { }
            }
        }
        return found ? seconds : 0;
    }

    private static String shortName(String raw) {
        String s = strip(raw);
        int cut = s.length();
        int p = s.indexOf('(');
        int b = s.indexOf('[');
        if (p >= 0) cut = Math.min(cut, p);
        if (b >= 0) cut = Math.min(cut, b);
        s = s.substring(0, cut).trim();
        s = s.replace(" Trinket", "").replace(" Pet", "").trim();
        return s.isEmpty() ? strip(raw) : s;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
