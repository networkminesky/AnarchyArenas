package anarchy.minesky.arenas.manager;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Materiais configuráveis (CPVP permitidos, TRADITIONAL bloqueados).
 */
public final class MaterialLists {

    private final Set<Material> cpvpAllowedPlace = new HashSet<>();
    private final Set<Material> traditionalBlockedItems = new HashSet<>();

    public void reload(FileConfiguration cfg) {
        cpvpAllowedPlace.clear();
        traditionalBlockedItems.clear();
        loadList(cfg, "cpvp-allowed-place", cpvpAllowedPlace);
        loadList(cfg, "traditional-blocked-items", traditionalBlockedItems);
    }

    private static void loadList(FileConfiguration cfg, String path, Set<Material> out) {
        List<String> list = cfg.getStringList(path);
        for (String raw : list) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String u = raw.trim().toUpperCase(Locale.ROOT);
            try {
                out.add(Material.valueOf(u));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public Set<Material> cpvpAllowedPlace() {
        return Collections.unmodifiableSet(cpvpAllowedPlace);
    }

    public boolean isCpvpAllowedPlace(Material m) {
        return cpvpAllowedPlace.contains(m);
    }

    public boolean isTraditionalBlockedItem(Material m) {
        return traditionalBlockedItems.contains(m);
    }
}
