package dev.prisonsutils.client.search;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public final class SearchManager {
    private static String searchQuery = "";

    private SearchManager() {}

    public static String getSearchQuery() {
        return searchQuery;
    }

    public static void setSearchQuery(String query) {
        searchQuery = query == null ? "" : query;
    }

    public static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || searchQuery == null || searchQuery.isEmpty()) {
            return false;
        }
        String query = searchQuery.toLowerCase();

        if (stack.getName().getString().toLowerCase().contains(query)) return true;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && lore.lines().stream()
                .anyMatch(line -> line.getString().toLowerCase().contains(query))) {
            return true;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.toString().toLowerCase().contains(query);
    }

    public static String getCosmicEnergyAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!stack.isOf(Items.LIGHT_BLUE_DYE)) return null;

        String fromData = getEnergyFromCustomData(stack);
        if (fromData != null) return fromData;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        return lore.lines().stream()
                .map(Text::getString)
                .filter(t -> t.contains("Cosmic Energy"))
                .map(SearchManager::extractEnergyFromLore)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String getEnergyFromCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;
        var tag = customData.copyNbt();
        if (!tag.contains("PublicBukkitValues")) return null;
        var bukkit = tag.getCompound("PublicBukkitValues").orElse(null);
        if (bukkit == null) return null;
        if (!"cosmic_energy".equals(bukkit.getString("cosmicprisons:custom_item_id").orElse(""))) return null;
        if (bukkit.contains("cosmicprisons:amount")) {
            return formatNumber(bukkit.getDouble("cosmicprisons:amount").orElse(0.0));
        }
        return null;
    }

    private static String extractEnergyFromLore(String text) {
        if (text.contains(":")) {
            String[] parts = text.split(":");
            if (parts.length > 1) return parts[1].trim();
        }
        if (text.contains("Contains")) {
            String e = text.replace("Contains", "").replace("Cosmic Energy", "").trim();
            if (!e.isEmpty()) return e;
        }
        String numeric = text.replaceAll("[^0-9,]", "").trim();
        return numeric.isEmpty() ? null : numeric;
    }

    public static String getMoneyNoteValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            var tag = customData.copyNbt();
            if (tag.contains("noteValue")) {
                try {
                    double v = tag.getDouble("noteValue").orElse(0.0);
                    return formatNumber(v);
                } catch (Exception ignored) {}
            }
            if (tag.contains("PublicBukkitValues")) {
                var bukkit = tag.getCompound("PublicBukkitValues").orElse(null);
                if (bukkit != null && "money_note".equals(bukkit.getString("cosmicprisons:custom_item_id").orElse(""))) {
                    if (bukkit.contains("cosmicprisons:amount")) {
                        return "$" + formatNumber(bukkit.getDouble("cosmicprisons:amount").orElse(0.0));
                    }
                }
            }
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String t = line.getString();
                if (t.contains("Value $")) {
                    try {
                        String v = t.substring(t.indexOf('$') + 1).replace(",", "").trim();
                        return formatNumber(Double.parseDouble(v));
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    public static String getExpBottleValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null) {
            var tag = customData.copyNbt();
            if (tag.contains("expValue")) {
                try {
                    double v = tag.getDouble("expValue").orElse(0.0);
                    return formatNumber(v);
                } catch (Exception ignored) {}
            }
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                String t = line.getString();
                if (t.contains("Value ") && t.contains(" XP")) {
                    try {
                        String v = t.substring(t.indexOf("Value ") + 6, t.indexOf(" XP")).replace(",", "").trim();
                        return formatNumber(Double.parseDouble(v));
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    public static String getTrinketCharges(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String name = stack.getName().getString();
        if (name.contains("Trinket") && name.contains("(") && name.endsWith(")")) {
            int lastOpen = name.lastIndexOf('(');
            String charges = name.substring(lastOpen + 1, name.length() - 1);
            if (charges.replace(",", "").matches("\\d+")) return charges;
        }
        return null;
    }

    private static String formatNumber(double value) {
        if (value < 1000) return String.valueOf((int) value);
        if (value < 1_000_000) return String.format("%.1fk", value / 1000.0).replace(".0k", "k");
        if (value < 1_000_000_000) return String.format("%.1fm", value / 1_000_000.0).replace(".0m", "m");
        return String.format("%.1fb", value / 1_000_000_000.0).replace(".0b", "b");
    }
}
