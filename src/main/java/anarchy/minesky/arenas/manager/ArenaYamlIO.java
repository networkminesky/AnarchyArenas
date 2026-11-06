package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.arena.ArenaBlockPos;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import anarchy.minesky.arenas.arena.Cuboid;
import anarchy.minesky.arenas.arena.RestoreMode;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura/gravação de arenas.yml (definitions + placed-blocks + placed-crystals).
 */
public final class ArenaYamlIO {

    private ArenaYamlIO() {
    }

    public static LoadResult load(File file) {
        Map<String, ArenaDefinition> definitions = new HashMap<>();
        Map<String, Map<ArenaBlockPos, UUID>> placed = new HashMap<>();
        Map<String, Set<UUID>> crystals = new HashMap<>();

        if (!file.exists()) {
            return new LoadResult(definitions, placed, crystals);
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection defs = yaml.getConfigurationSection("definitions");
        if (defs == null) {
            defs = yaml.getConfigurationSection("arenas");
        }
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                ConfigurationSection sec = defs.getConfigurationSection(id);
                if (sec == null) {
                    continue;
                }
                UUID worldId = parseUuid(sec.getString("world"));
                if (worldId == null) {
                    continue;
                }
                int minX = sec.getInt("min.x");
                int minY = sec.getInt("min.y");
                int minZ = sec.getInt("min.z");
                int maxX = sec.getInt("max.x");
                int maxY = sec.getInt("max.y");
                int maxZ = sec.getInt("max.z");
                ArenaMode mode = ArenaMode.fromArg(sec.getString("mode", ""));
                RestoreMode restore = RestoreMode.fromConfig(sec.getString("restore-mode", "RESET_ON_EMPTY"));
                int timer = sec.getInt("per-block-seconds", 30);
                boolean keepInv = sec.getBoolean("keep-inventory", true);
                if (mode == null) {
                    mode = ArenaMode.TRADITIONAL;
                }
                if (restore == null) {
                    restore = RestoreMode.RESET_ON_EMPTY;
                }
                Cuboid c = new Cuboid(worldId, minX, minY, minZ, maxX, maxY, maxZ);
                definitions.put(id, new ArenaDefinition(id, c, mode, restore, timer, keepInv));
            }
        }

        ConfigurationSection pb = yaml.getConfigurationSection("placed-blocks");
        if (pb != null) {
            for (String arenaId : pb.getKeys(false)) {
                List<String> list = pb.getStringList(arenaId);
                if (list.isEmpty()) {
                    continue;
                }
                Map<ArenaBlockPos, UUID> map = new HashMap<>();
                for (String entry : list) {
                    Parsed p = parsePlaced(entry);
                    if (p != null) {
                        map.put(p.pos, p.worldId);
                    }
                }
                if (!map.isEmpty()) {
                    placed.put(arenaId, map);
                }
            }
        }

        ConfigurationSection cry = yaml.getConfigurationSection("placed-crystals");
        if (cry != null) {
            for (String arenaId : cry.getKeys(false)) {
                List<String> list = cry.getStringList(arenaId);
                if (list.isEmpty()) {
                    continue;
                }
                Set<UUID> set = new HashSet<>();
                for (String s : list) {
                    try {
                        set.add(UUID.fromString(s.trim()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!set.isEmpty()) {
                    crystals.put(arenaId, set);
                }
            }
        }

        return new LoadResult(definitions, placed, crystals);
    }

    public static void save(File file, Map<String, ArenaDefinition> definitions, Map<String, Map<ArenaBlockPos, UUID>> placed,
            Map<String, Set<UUID>> crystals) throws IOException {
        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, ArenaDefinition> e : definitions.entrySet()) {
            ArenaDefinition def = e.getValue();
            Cuboid c = def.cuboid();
            String base = "definitions." + e.getKey();
            yaml.set(base + ".world", c.worldId().toString());
            yaml.set(base + ".min.x", c.minX());
            yaml.set(base + ".min.y", c.minY());
            yaml.set(base + ".min.z", c.minZ());
            yaml.set(base + ".max.x", c.maxX());
            yaml.set(base + ".max.y", c.maxY());
            yaml.set(base + ".max.z", c.maxZ());
            yaml.set(base + ".mode", def.mode().configKey());
            yaml.set(base + ".restore-mode", def.restoreMode().name());
            yaml.set(base + ".per-block-seconds", def.perBlockSeconds());
            yaml.set(base + ".keep-inventory", def.keepInventory());
        }
        for (Map.Entry<String, Map<ArenaBlockPos, UUID>> e : placed.entrySet()) {
            List<String> lines = new ArrayList<>(e.getValue().size());
            for (Map.Entry<ArenaBlockPos, UUID> b : e.getValue().entrySet()) {
                ArenaBlockPos pos = b.getKey();
                lines.add(b.getValue().toString() + "|" + pos.x() + "|" + pos.y() + "|" + pos.z());
            }
            yaml.set("placed-blocks." + e.getKey(), lines);
        }
        for (Map.Entry<String, Set<UUID>> e : crystals.entrySet()) {
            List<String> uuids = new ArrayList<>(e.getValue().size());
            for (UUID u : e.getValue()) {
                uuids.add(u.toString());
            }
            yaml.set("placed-crystals." + e.getKey(), uuids);
        }
        yaml.save(file);
    }

    public record LoadResult(Map<String, ArenaDefinition> definitions, Map<String, Map<ArenaBlockPos, UUID>> placed, Map<String, Set<UUID>> crystals) {
    }

    private record Parsed(ArenaBlockPos pos, UUID worldId) {
    }

    private static @org.jetbrains.annotations.Nullable Parsed parsePlaced(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|");
        if (parts.length != 4) {
            return null;
        }
        try {
            UUID wid = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Parsed(new ArenaBlockPos(x, y, z), wid);
        } catch (Exception ex) {
            return null;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            World w = Bukkit.getWorld(raw);
            return w != null ? w.getUID() : null;
        }
    }
}
