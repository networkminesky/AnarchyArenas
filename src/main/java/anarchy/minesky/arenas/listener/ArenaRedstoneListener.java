package anarchy.minesky.arenas.listener;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Bloqueia uso, colocação e ativação de pistões e componentes de redstone nas arenas.
 */
public final class ArenaRedstoneListener implements Listener {

    private final AnarchyArenas plugin;

    // Conjunto rápido com todos os materiais proibidos de Redstone e Pistão
    private static final Set<Material> REDSTONE_MATERIALS = EnumSet.noneOf(Material.class);

    static {
        // Pistões
        REDSTONE_MATERIALS.add(Material.PISTON);
        REDSTONE_MATERIALS.add(Material.STICKY_PISTON);

        // Fios, tochas e blocos de carga
        REDSTONE_MATERIALS.add(Material.REDSTONE);
        REDSTONE_MATERIALS.add(Material.REDSTONE_WIRE);
        REDSTONE_MATERIALS.add(Material.REDSTONE_TORCH);
        REDSTONE_MATERIALS.add(Material.REDSTONE_WALL_TORCH);
        REDSTONE_MATERIALS.add(Material.REDSTONE_BLOCK);

        // Componentes lógicos
        REDSTONE_MATERIALS.add(Material.REPEATER);
        REDSTONE_MATERIALS.add(Material.COMPARATOR);
        REDSTONE_MATERIALS.add(Material.OBSERVER);
        REDSTONE_MATERIALS.add(Material.TARGET);
        REDSTONE_MATERIALS.add(Material.DAYLIGHT_DETECTOR);
        REDSTONE_MATERIALS.add(Material.SCULK_SENSOR);
        REDSTONE_MATERIALS.add(Material.CALIBRATED_SCULK_SENSOR);

        // Mecanismos de disparo e armazenamento
        REDSTONE_MATERIALS.add(Material.DISPENSER);
        REDSTONE_MATERIALS.add(Material.DROPPER);
        REDSTONE_MATERIALS.add(Material.HOPPER);
        REDSTONE_MATERIALS.add(Material.LEVER);
        REDSTONE_MATERIALS.add(Material.TRIPWIRE_HOOK);
        REDSTONE_MATERIALS.add(Material.NOTE_BLOCK);

        // Suporte a novas versões (1.21 Crafter)
        Material crafter = Material.getMaterial("CRAFTER");
        if (crafter != null) {
            REDSTONE_MATERIALS.add(crafter);
        }
    }

    public ArenaRedstoneListener(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    private boolean isRedstoneOrPiston(Material mat) {
        if (REDSTONE_MATERIALS.contains(mat)) {
            return true;
        }
        String name = mat.name();
        // Bloqueia qualquer botão ou placa de pressão
        return name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE");
    }

    /**
     * 1. Bloqueia colocar pistões e redstone na arena.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }

        Block block = event.getBlockPlaced();
        ArenaDefinition def = plugin.registry().atBlock(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (def == null) {
            return;
        }

        if (isRedstoneOrPiston(block.getType())) {
            event.setCancelled(true);
            player.sendMessage("§cPistões e itens de redstone são proibidos dentro da arena!");
        }
    }

    /**
     * 2. Bloqueia interação manual (ex: puxar alavanca, clicar em repetidor/comparador).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission("anarchyarenas.admin.bypass")) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        ArenaDefinition def = plugin.registry().atBlock(clicked.getWorld(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (def == null) {
            return;
        }

        if (isRedstoneOrPiston(clicked.getType())) {
            event.setCancelled(true);
        }
    }

    /**
     * 3. Bloqueia extensão do pistão (empurrar blocos dentro, para fora ou para dentro da arena).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        ArenaDefinition defPiston = plugin.registry().atBlock(piston.getWorld(), piston.getX(), piston.getY(), piston.getZ());

        // Se o pistão estiver dentro da arena -> CANCELA
        if (defPiston != null) {
            event.setCancelled(true);
            return;
        }

        // Se o pistão estiver fora, mas tentar empurrar blocos para DENTRO da arena -> CANCELA
        for (Block b : event.getBlocks()) {
            Block target = b.getRelative(event.getDirection());
            if (plugin.registry().atBlock(target.getWorld(), target.getX(), target.getY(), target.getZ()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 4. Bloqueia retração do pistão (puxar blocos para dentro ou para fora da arena).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        ArenaDefinition defPiston = plugin.registry().atBlock(piston.getWorld(), piston.getX(), piston.getY(), piston.getZ());

        if (defPiston != null) {
            event.setCancelled(true);
            return;
        }

        for (Block b : event.getBlocks()) {
            if (plugin.registry().atBlock(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 5. Anula corrente de redstone dentro de qualquer arena (evita circuitos e lag).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstoneCurrent(BlockRedstoneEvent event) {
        Block b = event.getBlock();
        if (plugin.registry().atBlock(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null) {
            event.setNewCurrent(0);
        }
    }

    /**
     * 6. Impede ejetores/dispensers de dispararem projéteis ou água/lava.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block b = event.getBlock();
        if (plugin.registry().atBlock(b.getWorld(), b.getX(), b.getY(), b.getZ()) != null) {
            event.setCancelled(true);
        }
    }
}