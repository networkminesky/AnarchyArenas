package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Mantém inventário ao morrer dentro da arena quando {@link ArenaDefinition#keepInventory()} está ativo.
 */
public final class PlayerDeathArenaListener implements Listener {

    private final AnarchyArenas plugin;

    public PlayerDeathArenaListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ArenaDefinition def = plugin.registry().at(player.getLocation());
        if (def == null || !def.keepInventory()) {
            return;
        }
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}
