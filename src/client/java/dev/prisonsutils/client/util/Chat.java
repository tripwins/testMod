package dev.prisonsutils.client.util;

import java.util.Map;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Renders the mod's chat messages with 24-bit hex colors.
 *
 * <p>Messages are still authored with the familiar legacy {@code §} codes for brevity, but
 * {@link #of(String)} maps each code to a tuned RGB color from the PrisonsUtils palette (via
 * {@link TextColor#fromRgb}) instead of Minecraft's 16 fixed named colors — so everything the mod
 * prints uses real hex colors. Formatting codes ({@code §l §o §n §m §k §r}) are honored too.
 * {@code §b} is the brand cyan that matches the default ping color.
 */
public final class Chat {
    private Chat() {}

    // PrisonsUtils palette (RGB). Brand cyan matches the default ping color (0x33E1FF).
    private static final int BRAND = 0x33E1FF;
    private static final int TEXT = 0xE8EAED;
    private static final int MUTED = 0x9098A3;
    private static final int FAINT = 0x5A6068;
    private static final int GOOD = 0x57E08A;
    private static final int BAD = 0xFF6B6B;
    private static final int GOLD = 0xFFC861;
    private static final int ACCENT = 0xFFE066;

    /** Legacy color code → palette RGB. */
    private static final Map<Character, Integer> COLORS = Map.ofEntries(
            Map.entry('0', 0x14161A),
            Map.entry('1', 0x2B4ACB),
            Map.entry('2', 0x3FB860),
            Map.entry('3', 0x29B6C8),
            Map.entry('4', 0xC23B3B),
            Map.entry('5', 0xA64DD6),
            Map.entry('6', GOLD),
            Map.entry('7', MUTED),
            Map.entry('8', FAINT),
            Map.entry('9', 0x4C8DFF),
            Map.entry('a', GOOD),
            Map.entry('b', BRAND),
            Map.entry('c', BAD),
            Map.entry('d', 0xE493F0),
            Map.entry('e', ACCENT),
            Map.entry('f', TEXT));

    /** Parse a legacy §-formatted string into a hex-colored {@link MutableText}. */
    public static MutableText of(String legacy) {
        MutableText root = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c != '§' || i + 1 >= legacy.length()) {
                buf.append(c);
                continue;
            }
            char code = Character.toLowerCase(legacy.charAt(++i));
            Integer rgb = COLORS.get(code);
            if (rgb == null && "klmnor".indexOf(code) < 0) {
                buf.append(c).append(legacy.charAt(i)); // unknown code: keep it literal
                continue;
            }
            if (buf.length() > 0) {
                root.append(Text.literal(buf.toString()).setStyle(style));
                buf.setLength(0);
            }
            if (rgb != null) {
                style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)); // color resets other formats
            } else {
                style = switch (code) {
                    case 'k' -> style.withObfuscated(true);
                    case 'l' -> style.withBold(true);
                    case 'm' -> style.withStrikethrough(true);
                    case 'n' -> style.withUnderline(true);
                    case 'o' -> style.withItalic(true);
                    default -> Style.EMPTY; // 'r'
                };
            }
        }
        if (buf.length() > 0) {
            root.append(Text.literal(buf.toString()).setStyle(style));
        }
        return root;
    }
}
