package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.UUID;

/**
 * Rastreio de {@link EnderCrystal} colocados e remoção de UUID do armazenamento.
 */
public final class ArenaEntityListener implements Listener {

    private final AnarchyArenas plugin;

    public ArenaEntityListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof EnderCrystal)) {
            return;
        }
        Block against = event.getBlock();
        ArenaDefinition def = plugin.registry().atBlock(against.getWorld(), against.getX(), against.getY(), against.getZ());
        if (def == null || !plugin.blockTrack().tracks(def)) {
            return;
        }
        plugin.blockTrack().onCrystalPlaced(def, entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity ent = event.getEntity();
        if (!(ent instanceof EnderCrystal)) {
            return;
        }
        UUID id = ent.getUniqueId();
        String arenaId = plugin.blockTrack().findArenaIdForCrystal(id);
        plugin.blockTrack().removeCrystalTracking(arenaId, id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        UUID id = crystal.getUniqueId();
        String arenaId = plugin.blockTrack().findArenaIdForCrystal(id);
        plugin.blockTrack().removeCrystalTracking(arenaId, id);
    }
}
