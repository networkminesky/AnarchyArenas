package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.util.Mini;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mensagens MiniMessage a partir do config.yml.
 */
public final class Messages {

    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Component message(String key, Map<String, String> repl) {
        String raw = plugin.getConfig().getString("messages." + key, "<red>Mensagem ausente: " + key);
        return Mini.parse(raw, repl == null ? Collections.emptyMap() : repl);
    }

    public Component prefixed(String key, Map<String, String> repl) {
        Map<String, String> map = new HashMap<>();
        if (repl != null) {
            map.putAll(repl);
        }
        return message("prefix", Collections.emptyMap()).append(message(key, map));
    }
}
