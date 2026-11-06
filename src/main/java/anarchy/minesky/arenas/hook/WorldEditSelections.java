package anarchy.minesky.arenas.hook;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.world.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Lê a seleção ativa do WorldEdit do jogador.
 */
public final class WorldEditSelections {

    private WorldEditSelections() {
    }

    public record SelectionBounds(World weWorld, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public org.bukkit.World bukkitWorld(Player player) {
            return Objects.requireNonNull(player.getServer().getWorld(weWorld.getName()));
        }
    }

    public static @Nullable SelectionBounds getCuboid(Player player) {
        org.bukkit.World bukkitWorld = player.getWorld();
        World weWorld = BukkitAdapter.adapt(bukkitWorld);
        LocalSession session = com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        Region region;
        try {
            region = session.getSelection(weWorld);
        } catch (IncompleteRegionException e) {
            return null;
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new SelectionBounds(weWorld, min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }
}
