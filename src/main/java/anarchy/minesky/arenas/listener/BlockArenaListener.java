package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaBlockPos;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import anarchy.minesky.arenas.manager.MaterialLists;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Regras de bloco por modo de arena.
 */
public final class BlockArenaListener implements Listener {

    private final AnarchyArenas plugin;

    public BlockArenaListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceHigh(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }
        Block block = event.getBlockPlaced();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null) {
            return;
        }
        MaterialLists lists = plugin.materialLists();
        switch (def.mode()) {
            case TRADITIONAL -> event.setCancelled(true);
            case CPVP -> {
                // Glowstone só recarrega âncora (clique na âncora); não como bloco sólido no chão
                if (block.getType() == Material.GLOWSTONE) {
                    event.setCancelled(true);
                    return;
                }
                if (!lists.isCpvpAllowedPlace(block.getType())) {
                    event.setCancelled(true);
                }
            }
            case ALL_BLOCKS -> {
                // permitido
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceMonitor(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null || !plugin.blockTrack().tracks(def)) {
            return;
        }
        plugin.blockTrack().onPlace(def, block.getWorld(), new ArenaBlockPos(block.getX(), block.getY(), block.getZ()));
        org.bukkit.Bukkit.getGlobalRegionScheduler().execute(plugin, plugin.blockTrack()::flushSync);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakHigh(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }
        Block block = event.getBlock();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null) {
            return;
        }
        switch (def.mode()) {
            case TRADITIONAL -> event.setCancelled(true);
            case CPVP, ALL_BLOCKS -> {
                ArenaBlockPos pos = new ArenaBlockPos(block.getX(), block.getY(), block.getZ());
                if (!plugin.blockTrack().isPlayerPlaced(def, block.getWorld(), pos)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakMonitor(BlockBreakEvent event) {
        Block block = event.getBlock();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null || !plugin.blockTrack().tracks(def)) {
            return;
        }
        ArenaBlockPos pos = new ArenaBlockPos(block.getX(), block.getY(), block.getZ());
        plugin.blockTrack().removeTrackingIfPresent(def, block.getWorld(), pos);
        org.bukkit.Bukkit.getGlobalRegionScheduler().execute(plugin, plugin.blockTrack()::flushSync);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractHigh(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }
        ArenaDefinition def = plugin.session().get(player.getUniqueId());
        if (def == null) {
            return;
        }

        // Bloqueia baldes em CPVP
        if (def.mode() == ArenaMode.CPVP && event.getMaterial().name().endsWith("_BUCKET")) {
            event.setCancelled(true);
            return;
        }

        if (def.mode() == ArenaMode.CPVP) {
            if (event.getMaterial() == Material.GLOWSTONE) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    Block clicked = event.getClickedBlock();
                    if (clicked != null && clicked.getType() == Material.RESPAWN_ANCHOR) {
                        return; // Permite recarregar a âncora
                    }
                }
                event.setCancelled(true);
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getMaterial() == Material.END_CRYSTAL) {
                return;
            }
            return;
        }

        if (def.mode() != ArenaMode.TRADITIONAL) {
            return;
        }
        Material mat = event.getMaterial();
        MaterialLists lists = plugin.materialLists();
        if (lists.isTraditionalBlockedItem(mat) || mat == Material.FLINT_AND_STEEL || mat == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (mat.name().endsWith("_BUCKET") || mat == Material.FISHING_ROD) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        plugin.blockTrack().handleExplosionBlocks(event.getLocation().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        plugin.blockTrack().handleExplosionBlocks(event.getBlock().getWorld(), event.blockList());
    }
}
