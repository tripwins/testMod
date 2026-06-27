package dev.prisonsutils.client.enchant;

import dev.prisonsutils.config.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Reimplementation of cp_utils' enchant-book tooltip helper. Detects CosmicPrisons enchant
 * books (custom_item_id == gear_enchant_book, or success/destroy lore) and appends parsed
 * stats plus paging math (page-yield table + energy-per-1% formula) to the tooltip.
 */
public final class EnchantBookHelper {
    private static final Pattern SUCCESS = Pattern.compile("(\\d{1,3}(?:[.,]\\d+)?)%\\s*Success", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTROY = Pattern.compile("(\\d{1,3}(?:[.,]\\d+)?)%\\s*Destroy", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENERGY = Pattern.compile("Energy:\\s*[\\d,]+\\s*/\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAX_LEVEL = Pattern.compile("Maximum\\s+Level\\s+([IVX]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROMAN = Pattern.compile("\\b([IVX]+)\\b");

    private static final String BUKKIT = "PublicBukkitValues";
    private static final String K_ID = "cosmicprisons:custom_item_id";
    private static final String K_TIER = "cosmicprisons:gear_enchant_tier";
    private static final String K_SUCCESS = "cosmicprisons:gear_enchant_success";
    private static final String K_DESTROY = "cosmicprisons:gear_enchant_destroy";
    private static final String K_LEVEL = "cosmicprisons:gear_enchant_level";
    private static final String K_MAX = "cosmicprisons:gear_enchant_max";
    private static final String K_MAX2 = "cosmicprisons:gear_enchant_max_level";
    private static final String K_REQUIRED = "cosmicprisons:gear_enchant_required";

    private EnchantBookHelper() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!Config.get().enchantHelperEnabled) {
                return;
            }
            try {
                appendTooltip(stack, lines);
            } catch (Exception ignored) {
            }
        });
    }

    private static void appendTooltip(ItemStack stack, List<Text> lines) {
        NbtCompound bukkit = bukkitValues(stack);
        List<String> lore = loreStrings(stack);

        if (!isEnchantBook(bukkit, lore)) {
            return;
        }

        Integer success = percent(bukkit, K_SUCCESS, SUCCESS, lore);
        Integer destroy = percent(bukkit, K_DESTROY, DESTROY, lore);
        Integer level = bukkit != null ? intVal(bukkit, K_LEVEL) : null;
        if (level == null) level = parseLevelFromName(stack.getName().getString());
        Integer maxLevel = bukkit != null ? firstNonNull(intVal(bukkit, K_MAX), intVal(bukkit, K_MAX2)) : null;
        if (maxLevel == null) maxLevel = parseMaxLevel(lore);
        Long energy = bukkit != null ? longVal(bukkit, K_REQUIRED) : null;
        if (energy == null) energy = parseEnergy(lore);

        boolean maxed = level != null && maxLevel != null && level >= maxLevel;

        // The action to take with this book.
        String action;
        String detail;
        int color;
        if (maxed) {
            action = "Max Level";
            detail = "This enchant is already maxed.";
            color = 0xFFD24A;
        } else if (destroy != null && destroy == 0) {
            action = "Upgrade";
            detail = "Apply it directly to raise the level.";
            color = 0x55FF7A;
        } else if (destroy != null && destroy >= 60) {
            action = "Page";
            detail = "Let it fail in the Wormhole to turn it into a page.";
            color = 0xC78BFF;
        } else if (destroy != null && success != null && success < destroy) {
            action = "Tinker";
            detail = "Run it through the Tinkerer.";
            color = 0x3DD6FF;
        } else {
            action = "Upgrade";
            detail = "Apply it directly to raise the level.";
            color = 0x55FF7A;
        }

        lines.add(gradient("Enchant Helper", 0xFFE08A, 0xFFB454));
        lines.add(colored(action, color, false));
        lines.add(colored(detail, 0xA7B0BA, false));
        if (!maxed && energy != null) {
            lines.add(colored("Next Level: ", 0x8D97A1, false)
                    .append(colored(format(energy) + " energy", 0xC78BFF, false)));
        }
    }

    private static MutableText colored(String text, int rgb, boolean bold) {
        return Text.literal(text).styled(s -> {
            net.minecraft.text.Style styled = s.withColor(TextColor.fromRgb(rgb));
            return bold ? styled.withBold(true) : styled;
        });
    }

    private static Text gradient(String text, int from, int to) {
        MutableText out = Text.empty();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            float t = len <= 1 ? 0f : (float) i / (len - 1);
            int rgb = lerp(from, to, t);
            out.append(colored(String.valueOf(text.charAt(i)), rgb, false));
        }
        return out;
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Short slot-overlay label for an enchant book: the uppercase letters of the enchant
     * name + its level, with the level token stripped from the name first so "Lightning IV"
     * → "L4" (not "LIV4") and "TitanBlood 4" → "TB4". Returns null if not an enchant book.
     */
    public static String overlayAbbreviation(ItemStack stack) {
        NbtCompound bukkit = bukkitValues(stack);
        List<String> lore = loreStrings(stack);
        if (!isEnchantBook(bukkit, lore)) {
            return null;
        }

        String name = stack.getName().getString().trim();

        // The level shows in the nametag (usually a roman numeral); prefer it, falling back
        // to NBT. Strip the level token from the name before abbreviating so its letters
        // (I/V/X…) aren't folded into the abbreviation — even when the name has trailing
        // whitespace or symbols that would defeat a whole-string regex match.
        Integer level = extractLevelFromName(name);
        if (level == null && bukkit != null) {
            level = intVal(bukkit, K_LEVEL);
        }

        String abbr = abbreviate(stripLevelTokens(name));
        if (abbr.isEmpty()) {
            return null;
        }
        return level != null ? abbr + level : abbr;
    }

    /** Last standalone roman-numeral or digit word in the name (the level), or null. */
    private static Integer extractLevelFromName(String name) {
        Integer level = null;
        for (String word : name.split("\\s+")) {
            if (word.matches("\\d+")) {
                try {
                    level = Integer.parseInt(word);
                } catch (NumberFormatException ignored) {
                }
            } else if (word.matches("[ivxlcdmIVXLCDM]+")) {
                Integer v = romanToInt(word);
                if (v != null) level = v;
            }
        }
        return level;
    }

    /** The name with any standalone level tokens (roman numerals / digits) removed. */
    private static String stripLevelTokens(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (word.isEmpty()
                    || word.matches("[ivxlcdmIVXLCDM]+")
                    || word.matches("\\d+")) {
                continue;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append(word);
        }
        return sb.toString();
    }

    private static String abbreviate(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            if (Character.isUpperCase(name.charAt(i))) {
                sb.append(name.charAt(i));
            }
        }
        if (sb.length() == 0) {
            for (int i = 0; i < name.length(); i++) {
                if (Character.isLetter(name.charAt(i))) {
                    sb.append(Character.toUpperCase(name.charAt(i)));
                    break;
                }
            }
        }
        if (sb.length() > 3) {
            sb.setLength(3);
        }
        return sb.toString();
    }

    // ---- parsing helpers -------------------------------------------------------------

    private static boolean isEnchantBook(NbtCompound bukkit, List<String> lore) {
        if (bukkit != null && "gear_enchant_book".equals(str(bukkit, K_ID))) {
            return true;
        }
        boolean s = lore.stream().anyMatch(l -> SUCCESS.matcher(l).find());
        boolean d = lore.stream().anyMatch(l -> DESTROY.matcher(l).find());
        return s && d;
    }

    private static NbtCompound bukkitValues(ItemStack stack) {
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (cd == null) return null;
        NbtCompound tag = cd.copyNbt();
        return tag.contains(BUKKIT) ? tag.getCompound(BUKKIT).orElse(null) : null;
    }

    private static List<String> loreStrings(ItemStack stack) {
        List<String> out = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) out.add(t.getString());
        }
        return out;
    }

    private static Integer percent(NbtCompound bukkit, String key, Pattern pattern, List<String> lore) {
        // Read NBT as a double (so a fraction like 0.85 isn't truncated to 0 by getInt).
        Integer nbt = null;
        if (bukkit != null) {
            Double d = doubleVal(bukkit, key);
            if (d != null) nbt = normalizePercent(d);
        }
        if (nbt != null && nbt > 0) {
            return nbt;
        }
        // Fall back to the lore line if NBT is missing or zero.
        for (String line : lore) {
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1).replace(',', '.'));
                    return (int) Math.round(v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return nbt; // genuine NBT zero (or null)
    }

    private static int normalizePercent(double v) {
        return v > 0 && v <= 1.0 ? (int) Math.round(v * 100) : (int) Math.round(v);
    }

    private static Integer parseMaxLevel(List<String> lore) {
        for (String line : lore) {
            Matcher m = MAX_LEVEL.matcher(line);
            if (m.find()) return romanToInt(m.group(1));
        }
        return null;
    }

    private static Integer parseLevelFromName(String name) {
        if (name == null) return null;
        Matcher m = ROMAN.matcher(name);
        Integer last = null;
        while (m.find()) {
            Integer v = romanToInt(m.group(1));
            if (v != null) last = v;
        }
        return last;
    }

    private static Long parseEnergy(List<String> lore) {
        for (String line : lore) {
            Matcher m = ENERGY.matcher(line);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static Integer romanToInt(String roman) {
        if (roman == null || roman.isEmpty()) return null;
        int total = 0;
        int prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int val = switch (Character.toUpperCase(roman.charAt(i))) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            if (val == 0) return null;
            total += val < prev ? -val : val;
            prev = val;
        }
        return total;
    }

    private static String str(NbtCompound c, String key) {
        String v = c.getString(key).orElse("");
        return v.isEmpty() ? null : v;
    }

    private static Integer intVal(NbtCompound c, String key) {
        return c.contains(key) ? c.getInt(key).orElse(null) : null;
    }

    private static Double doubleVal(NbtCompound c, String key) {
        return c.contains(key) ? c.getDouble(key).orElse(null) : null;
    }

    private static Long longVal(NbtCompound c, String key) {
        if (!c.contains(key)) return null;
        Integer i = c.getInt(key).orElse(null);
        if (i != null) return i.longValue();
        Double d = c.getDouble(key).orElse(null);
        return d != null ? d.longValue() : null;
    }

    private static Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static String display(String tier) {
        if (tier.isEmpty()) return tier;
        return tier.substring(0, 1).toUpperCase() + tier.substring(1).toLowerCase();
    }

    private static String format(long value) {
        return String.format("%,d", value);
    }

    private static Text stat(String name, String value, Formatting valueColor) {
        return Text.literal(name + ": ").formatted(Formatting.GRAY)
                .append(Text.literal(value).formatted(valueColor));
    }

    private static Text indent(String text, Formatting color) {
        return Text.literal("  " + text).formatted(color);
    }
}
