package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.Cuboid;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class ArenaSchematicManager {

    private final AnarchyArenas plugin;
    private final File schemFolder;

    public ArenaSchematicManager(AnarchyArenas plugin) {
        this.plugin = plugin;
        this.schemFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schemFolder.exists()) {
            schemFolder.mkdirs();
        }
    }

    /**
     * Salva o estado original do spawn como schematic.
     */
    public boolean saveSnapshot(ArenaDefinition def) {
        World world = Bukkit.getWorld(def.cuboid().worldId());
        if (world == null) return false;

        Cuboid c = def.cuboid();
        CuboidRegion region = new CuboidRegion(
                BukkitAdapter.adapt(world),
                BlockVector3.at(c.minX(), c.minY(), c.minZ()),
                BlockVector3.at(c.maxX(), c.maxY(), c.maxZ())
        );

        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.at(c.minX(), c.minY(), c.minZ()));

        try (EditSession editSession = WorldEdit.getInstance().newEditSession(region.getWorld())) {
            ForwardExtentCopy forwardCopy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
            forwardCopy.setCopyingEntities(false);
            Operations.complete(forwardCopy);

            File file = new File(schemFolder, def.id() + ".schem");
            try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(file))) {
                writer.write(clipboard);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Erro ao salvar schematic da arena " + def.id() + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Restaura 100% da arena com o WorldEdit, limpando água, lava, obsidiana e cristais.
     */
    public boolean restoreSnapshot(ArenaDefinition def) {
        File file = new File(schemFolder, def.id() + ".schem");
        if (!file.exists()) {
            // Se o schematic não existe, cai de volta para a limpeza padrão de blocos
            return false;
        }

        World world = Bukkit.getWorld(def.cuboid().worldId());
        if (world == null) return false;

        // 1. Limpa entidades restantes (cristais do end)
        removeEntitiesInCuboid(world, def.cuboid());

        // 2. Cola o schematic original (incluindo ar, limpando tudo o que os players colocaram)
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) return false;

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operation op = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(def.cuboid().minX(), def.cuboid().minY(), def.cuboid().minZ()))
                        .ignoreAirBlocks(false) // OBRIGATÓRIO: cola os blocos de ar para deletar obsidiana, água e lava
                        .build();
                Operations.complete(op);
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().severe("Erro ao restaurar schematic da arena " + def.id() + ": " + ex.getMessage());
            return false;
        }
    }

    private void removeEntitiesInCuboid(World world, Cuboid cuboid) {
        int minCx = cuboid.minX() >> 4;
        int maxCx = cuboid.maxX() >> 4;
        int minCz = cuboid.minZ() >> 4;
        int maxCz = cuboid.maxZ() >> 4;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                int fcx = cx;
                int fcz = cz;
                Bukkit.getRegionScheduler().execute(plugin, world, fcx, fcz, () -> {
                    if (!world.isChunkLoaded(fcx, fcz)) return;
                    for (Entity entity : world.getChunkAt(fcx, fcz).getEntities()) {
                        if (entity instanceof EnderCrystal && cuboid.contains(entity.getLocation())) {
                            entity.getScheduler().run(plugin, task -> entity.remove(), null);
                        }
                    }
                });
            }
        }
    }
}