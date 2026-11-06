package anarchy.minesky.arenas.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage com substituição simples de placeholders %key%.
 */
public final class Mini {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%]+)%");

    private Mini() {
    }

    public static Component parse(String raw, Map<String, String> replacements) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        String out = applyPlaceholders(raw, replacements);
        return MM.deserialize(out);
    }

    public static Component parsePlain(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MM.deserialize(raw);
    }

    private static String applyPlaceholders(String template, Map<String, String> replacements) {
        if (replacements == null || replacements.isEmpty()) {
            return template;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = replacements.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
