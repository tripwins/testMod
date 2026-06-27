package dev.prisonsutils.client.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Reads a "<n> block radius" value from a held item's lore (e.g. pets). */
public final class HeldItemAnalyzer {
    private static final Pattern RADIUS_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+block\\s+radius", Pattern.CASE_INSENSITIVE);

    private HeldItemAnalyzer() {}

    public static Float radiusFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return null;
        }
        for (Text line : lore.lines()) {
            String text = line.getString();
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher matcher = RADIUS_PATTERN.matcher(text);
            if (!matcher.find()) {
                continue;
            }
            try {
                float radius = Float.parseFloat(matcher.group(1));
                if (radius > 0f && radius < 256f) {
                    return radius;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
