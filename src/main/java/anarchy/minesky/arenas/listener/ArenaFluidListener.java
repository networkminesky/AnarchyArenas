package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaBlockPos;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public final class ArenaFluidListener implements Listener {

    private final AnarchyArenas plugin;

    public ArenaFluidListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    /**
     * Baldes de água e lava LIBERADOS no CPvP e ALL_BLOCKS.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }

        Block block = event.getBlock();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null) return;

        // Apenas no PvP tradicional é proibido
        if (def.mode() == ArenaMode.TRADITIONAL) {
            event.setCancelled(true);
            return;
        }

        // No CPvP e ALL_BLOCKS: Água e lava são 100% permitidas!
        if (plugin.blockTrack().tracks(def)) {
            ArenaBlockPos pos = new ArenaBlockPos(block.getX(), block.getY(), block.getZ());
            plugin.blockTrack().onPlace(def, block.getWorld(), pos);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlockClicked();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null || !plugin.blockTrack().tracks(def)) return;

        ArenaBlockPos pos = new ArenaBlockPos(block.getX(), block.getY(), block.getZ());
        plugin.blockTrack().removeTrackingIfPresent(def, block.getWorld(), pos);
    }

    /**
     * Contenção de escorrimento: a água/lava NUNCA vaza para fora da arena.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();

        ArenaDefinition defFrom = plugin.registry().atBlock(from.getWorld(), from.getX(), from.getY(), from.getZ());
        ArenaDefinition defTo = plugin.registry().atBlock(to.getWorld(), to.getX(), to.getY(), to.getZ());

        if (defFrom != null) {
            // Se o líquido tentar sair da arena -> CANCELE para não inundar o spawn!
            if (defTo == null || !defTo.id().equals(defFrom.id())) {
                event.setCancelled(true);
                return;
            }

            if (defFrom.mode() == ArenaMode.TRADITIONAL) {
                event.setCancelled(true);
                return;
            }

            // Dentro da arena o líquido escorre livremente
            if (plugin.blockTrack().tracks(defFrom)) {
                ArenaBlockPos toPos = new ArenaBlockPos(to.getX(), to.getY(), to.getZ());
                plugin.blockTrack().onPlace(defFrom, to.getWorld(), toPos);
            }
        } else if (defTo != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null) return;

        if (def.mode() == ArenaMode.TRADITIONAL) {
            event.setCancelled(true);
            return;
        }

        if (plugin.blockTrack().tracks(def)) {
            ArenaBlockPos pos = new ArenaBlockPos(block.getX(), block.getY(), block.getZ());
            plugin.blockTrack().onPlace(def, block.getWorld(), pos);
        }
    }
}