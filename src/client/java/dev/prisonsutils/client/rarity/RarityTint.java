package dev.prisonsutils.client.rarity;

import dev.prisonsutils.client.util.ItemMemo;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * Detects an item's rarity/variety (from CosmicPrisons enchant-tier NBT or a rarity word
 * in the name/lore) and returns a tint color. Used to glaze enchant books and other
 * graded items in slots/hotbar.
 */
public final class RarityTint {
    // Ordered longest-first so e.g. "ULTIMATE" is matched before a shorter substring.
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();

    static {
        // CosmicPrisons gear rarities (low → high).
        COLORS.put("LEGENDARY", 0xFFFFAA00); // gold/orange
        COLORS.put("ULTIMATE", 0xFFFFE93D);  // yellow
        COLORS.put("UNCOMMON", 0xFF44DD55);  // green
        COLORS.put("GODLY", 0xFFFF3344);     // red
        COLORS.put("ELITE", 0xFF4499FF);     // blue
        COLORS.put("SIMPLE", 0xFFB0B0B0);    // gray
    }

    private static final String BUKKIT = "PublicBukkitValues";
    private static final String K_TIER = "cosmicprisons:gear_enchant_tier";

    private RarityTint() {}

    // Rarity is static per item, so a generous TTL is safe; identity keying means a changed item
    // (a fresh ItemStack from the server) recomputes immediately.
    private static final ItemMemo<Integer> CACHE = new ItemMemo<>(1000L);

    /** RGB (0xRRGGBB) tint for the stack's rarity, or null if none detected. Cached per item. */
    public static Integer tintRgb(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return CACHE.get(stack, RarityTint::computeTintRgb);
    }

    private static Integer computeTintRgb(ItemStack stack) {
        // 1) Enchant book tier from NBT.
        NbtCompound bukkit = bukkitValues(stack);
        if (bukkit != null) {
            String tier = bukkit.getString(K_TIER).orElse("");
            if (!tier.isEmpty()) {
                Integer c = COLORS.get(tier.toUpperCase());
                if (c != null) return c & 0xFFFFFF;
            }
        }

        // 2) Rarity word in the name or lore.
        String name = stack.getName().getString().toUpperCase();
        Integer fromName = match(name);
        if (fromName != null) return fromName;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                Integer c = match(line.getString().toUpperCase());
                if (c != null) return c;
            }
        }
        return null;
    }

    private static Integer match(String upper) {
        for (Map.Entry<String, Integer> e : COLORS.entrySet()) {
            if (upper.contains(e.getKey())) {
                return e.getValue() & 0xFFFFFF;
            }
        }
        return null;
    }

    private static NbtCompound bukkitValues(ItemStack stack) {
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null) return null;
        NbtCompound tag = cd.copyNbt();
        return tag.contains(BUKKIT) ? tag.getCompound(BUKKIT).orElse(null) : null;
    }
}
