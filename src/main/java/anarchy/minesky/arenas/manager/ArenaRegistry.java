package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro thread-safe de arenas por nome.
 */
public final class ArenaRegistry {

    private final Map<String, ArenaDefinition> byId = new ConcurrentHashMap<>();
    private final ArenaSpatialIndex spatial = new ArenaSpatialIndex();

    public void register(ArenaDefinition def) {
        ArenaDefinition old = byId.put(def.id(), def);
        if (old != null) {
            spatial.remove(old);
        }
        spatial.add(def);
    }

    public @Nullable ArenaDefinition remove(String id) {
        ArenaDefinition def = byId.remove(id);
        if (def != null) {
            spatial.remove(def);
        }
        return def;
    }

    public @Nullable ArenaDefinition get(String id) {
        return byId.get(id);
    }

    public Collection<ArenaDefinition> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public @Nullable ArenaDefinition at(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        World w = loc.getWorld();
        return spatial.findContaining(w, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public @Nullable ArenaDefinition atBlock(World world, int x, int y, int z) {
        return spatial.findContaining(world, x, y, z);
    }

    public boolean intersects(Cuboid other) {
        for (ArenaDefinition def : byId.values()) {
            if (cuboidsIntersect(def.cuboid(), other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cuboidsIntersect(Cuboid a, Cuboid b) {
        if (!a.worldId().equals(b.worldId())) {
            return false;
        }
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
                && a.maxY() >= b.minY() && a.minY() <= b.maxY()
                && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

    public void clear() {
        byId.clear();
        spatial.clear();
    }
}
