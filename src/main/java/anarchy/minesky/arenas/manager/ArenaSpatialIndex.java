package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.Cuboid;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indexa arenas por chunk para reduzir checagens em eventos de bloco.
 */
public final class ArenaSpatialIndex {

    private final Map<String, List<ArenaDefinition>> chunkBuckets = new ConcurrentHashMap<>();

    private static String key(UUID worldId, int chunkX, int chunkZ) {
        return worldId + ":" + chunkX + ":" + chunkZ;
    }

    public void add(ArenaDefinition def) {
        Cuboid c = def.cuboid();
        UUID wid = c.worldId();
        int minCx = c.minX() >> 4;
        int maxCx = c.maxX() >> 4;
        int minCz = c.minZ() >> 4;
        int maxCz = c.maxZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunkBuckets.computeIfAbsent(key(wid, cx, cz), k -> new ArrayList<>()).add(def);
            }
        }
    }

    public void remove(ArenaDefinition def) {
        Cuboid c = def.cuboid();
        UUID wid = c.worldId();
        int minCx = c.minX() >> 4;
        int maxCx = c.maxX() >> 4;
        int minCz = c.minZ() >> 4;
        int maxCz = c.maxZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<ArenaDefinition> list = chunkBuckets.get(key(wid, cx, cz));
                if (list != null) {
                    list.removeIf(d -> d.id().equals(def.id()));
                    if (list.isEmpty()) {
                        chunkBuckets.remove(key(wid, cx, cz), list);
                    }
                }
            }
        }
    }

    public @org.jetbrains.annotations.Nullable ArenaDefinition findContaining(World world, int bx, int by, int bz) {
        if (world == null) {
            return null;
        }
        UUID wid = world.getUID();
        int cx = bx >> 4;
        int cz = bz >> 4;
        List<ArenaDefinition> list = chunkBuckets.get(key(wid, cx, cz));
        if (list == null) {
            return null;
        }
        for (ArenaDefinition def : list) {
            if (def.cuboid().contains(world, bx, by, bz)) {
                return def;
            }
        }
        return null;
    }

    public void clear() {
        chunkBuckets.clear();
    }
}
