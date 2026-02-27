package com.epictrails.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing color codes in plugin messages and display names.
 *
 * <p>Supports three formats:
 * <ol>
 *   <li><b>MiniMessage</b> – e.g. {@code <red>text</red>}, {@code <gradient:#ff0000:#0000ff>}</li>
 *   <li><b>Legacy &-codes</b> – e.g. {@code &cRed Text}</li>
 *   <li><b>Hex #RRGGBB</b> inline inside legacy strings – e.g. {@code &#ff6b35Orange}</li>
 * </ol>
 *
 * <p>All public methods return Adventure {@link Component} objects ready for sending
 * via Paper's audience API.
 */
public final class ColorUtil {

    /** Matches &#RRGGBB hex colour tags embedded in legacy strings. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    /** MiniMessage instance (Adventure). */
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Legacy serialiser using {@code &} as the colour character. */
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {}

    /**
     * Parses a raw string that may contain MiniMessage tags, legacy {@code &}-codes,
     * and/or inline hex {@code &#RRGGBB} codes into an Adventure {@link Component}.
     *
     * <p>The method first attempts MiniMessage parsing. If the string contains any
     * {@code &}-code or hex-code sequences, it converts those to MiniMessage-compatible
     * syntax before parsing.</p>
     *
     * @param raw the raw string to parse
     * @return the parsed {@link Component}
     */
    public static Component parse(String raw) {
        if (raw == null) return Component.empty();
        String converted = convertLegacyToMiniMessage(raw);
        return MINI.deserialize(converted);
    }

    /**
     * Strips all color/format codes from a raw string and returns the plain text.
     *
     * @param raw the raw string
     * @return plain text without any formatting
     */
    public static String stripColor(String raw) {
        if (raw == null) return "";
        return MINI.stripTags(convertLegacyToMiniMessage(raw));
    }

    /**
     * Serialises a {@link Component} back to a legacy {@code §}-prefixed string
     * suitable for APIs that do not accept Adventure components.
     *
     * @param component the component to serialise
     * @return legacy colour-coded string
     */
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Convenience overload: parses {@code raw} then serialises immediately to a
     * legacy {@code §}-prefixed string.
     *
     * @param raw the raw string
     * @return legacy colour-coded string
     */
    public static String parseLegacy(String raw) {
        return toLegacy(parse(raw));
    }

    /**
     * Converts legacy {@code &}-codes and inline hex {@code &#RRGGBB} sequences
     * to their MiniMessage equivalents so that the single MiniMessage parser can
     * handle all three formats in one pass.
     *
     * <p>Conversion rules:
     * <ul>
     *   <li>{@code &#RRGGBB} → {@code <#RRGGBB>}</li>
     *   <li>{@code &c} → {@code <red>} (and so on for all 16 Minecraft colour/format codes)</li>
     * </ul>
     * </p>
     *
     * @param input raw input string
     * @return MiniMessage-compatible string
     */
    public static String convertLegacyToMiniMessage(String input) {
        if (input == null) return "";

        // First replace hex patterns: &#RRGGBB → <#RRGGBB>
        Matcher hex = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (hex.find()) {
            hex.appendReplacement(sb, "<#" + hex.group(1) + ">");
        }
        hex.appendTail(sb);
        String result = sb.toString();

        // Then replace legacy &-codes
        result = result
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&A", "<green>")
                .replace("&b", "<aqua>")
                .replace("&B", "<aqua>")
                .replace("&c", "<red>")
                .replace("&C", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&D", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&E", "<yellow>")
                .replace("&f", "<white>")
                .replace("&F", "<white>")
                .replace("&k", "<obfuscated>")
                .replace("&K", "<obfuscated>")
                .replace("&l", "<bold>")
                .replace("&L", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&M", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&N", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&O", "<italic>")
                .replace("&r", "<reset>")
                .replace("&R", "<reset>");

        return result;
    }
}
