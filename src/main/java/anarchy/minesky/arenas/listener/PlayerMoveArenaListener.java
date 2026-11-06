package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Detecta entrada/saída por mudança de bloco (otimizado).
 */
public final class PlayerMoveArenaListener implements Listener {

    private final AnarchyArenas plugin;

    public PlayerMoveArenaListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    /**
     * Após reload: realinha sessão e ocupação sem spam de actionbar.
     */
    public void reconcileAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcileSessionQuiet(player);
        }
    }

    private void reconcileSessionQuiet(Player player) {
        if (player.getWorld() == null) {
            return;
        }
        var loc = player.getLocation();
        ArenaDefinition current = plugin.registry().atBlock(player.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        ArenaDefinition prev = plugin.session().get(player.getUniqueId());
        if (current == prev) {
            return;
        }
        if (prev != null) {
            plugin.blockTrack().leavePlayer(prev);
        }
        if (current != null) {
            plugin.blockTrack().enterPlayer(current);
        }
        plugin.session().put(player.getUniqueId(), current);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Player player = event.getPlayer();
        handleTransition(player, event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null) {
            return;
        }
        handleTransition(player, event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getLocation().getWorld() == null) {
            return;
        }
        handleTransition(player, player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ArenaDefinition prev = plugin.session().get(player.getUniqueId());
        if (prev != null) {
            plugin.blockTrack().leavePlayer(prev);
            plugin.session().remove(player.getUniqueId());
        }
    }

    private void handleTransition(Player player, int bx, int by, int bz) {
        ArenaDefinition current = plugin.registry().atBlock(player.getWorld(), bx, by, bz);
        ArenaDefinition prev = plugin.session().put(player.getUniqueId(), current);

        if (current == prev) {
            return;
        }

        if (prev != null) {
            plugin.blockTrack().leavePlayer(prev);
            Map<String, String> r = new HashMap<>();
            r.put("arena", prev.id());
            Component exit = plugin.messages().message("arena-exit", r);
            player.sendActionBar(exit);
        }

        if (current != null) {
            plugin.blockTrack().enterPlayer(current);
            Map<String, String> r = new HashMap<>();
            r.put("arena", current.id());
            r.put("mode", current.mode().plainLabel());
            Component enter = plugin.messages().message("arena-enter", r);
            player.sendActionBar(enter);
        }
    }
}
