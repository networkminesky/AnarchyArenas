package anarchy.minesky.arenas.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Região alinhada a blocos inclusiva nos eixos mín/máx.
 */
public final class Cuboid {

    private final UUID worldId;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(UUID worldId, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public UUID worldId() {
        return worldId;
    }

    public boolean contains(World world, int bx, int by, int bz) {
        if (world == null || !worldId.equals(world.getUID())) {
            return false;
        }
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    public boolean contains(Location loc) {
        return loc != null && loc.getWorld() != null && contains(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }
}
