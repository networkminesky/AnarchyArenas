package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaBlockPos;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import anarchy.minesky.arenas.arena.RestoreMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rastreia blocos colocados (CPVP / ALL_BLOCKS), timers em tempo real e ocupação para RESET_ON_EMPTY.
 * Folia: não usar {@code Bukkit.getCurrentTick()} em tarefas globais; remoção de blocos via {@link org.bukkit.RegionScheduler#execute}.
 */
public final class BlockTrackManager {

    private final AnarchyArenas plugin;
    private final File arenasFile;

    /** arenaId -> pos -> worldId */
    private final Map<String, Map<ArenaBlockPos, UUID>> placedByArena = new ConcurrentHashMap<>();
    /** arenaId -> pos -> due time (epoch millis) — PER_BLOCK_TIMER */
    private final Map<String, Map<ArenaBlockPos, Long>> dueMillisByArena = new ConcurrentHashMap<>();
    /** Jogadores com pé dentro da arena */
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> occupancy = new ConcurrentHashMap<>();

    public BlockTrackManager(AnarchyArenas plugin, File arenasFile) {
        this.plugin = plugin;
        this.arenasFile = arenasFile;
    }

    public File arenasFile() {
        return arenasFile;
    }

    /**
     * Aplica resultado já lido de arenas.yml (placed-blocks) com o registry atual.
     */
    public void applyLoaded(ArenaYamlIO.LoadResult lr, ArenaRegistry registry) {
        placedByArena.clear();
        dueMillisByArena.clear();
        occupancy.clear();
        long nowMs = System.currentTimeMillis();
        for (Map.Entry<String, Map<ArenaBlockPos, UUID>> e : lr.placed().entrySet()) {
            placedByArena.put(e.getKey(), new ConcurrentHashMap<>(e.getValue()));
        }
        for (Map.Entry<String, Map<ArenaBlockPos, UUID>> e : placedByArena.entrySet()) {
            String arenaId = e.getKey();
            ArenaDefinition def = registry.get(arenaId);
            if (!shouldTrack(def)) {
                Map<ArenaBlockPos, UUID> snapshot = new HashMap<>(e.getValue());
                placedByArena.remove(arenaId);
                scheduleClearArenaPerBlock(snapshot, true, null);
                continue;
            }
            if (def.restoreMode() == RestoreMode.PER_BLOCK_TIMER) {
                Map<ArenaBlockPos, Long> due = new ConcurrentHashMap<>();
                long expireMs = nowMs + (long) def.perBlockSeconds() * 1000L;
                for (ArenaBlockPos pos : e.getValue().keySet()) {
                    due.put(pos, expireMs);
                }
                dueMillisByArena.put(arenaId, due);
            }
        }
    }

    public void flushSync() {
        try {
            Map<String, ArenaDefinition> defs = new HashMap<>();
            for (ArenaDefinition def : plugin.registry().all()) {
                defs.put(def.id(), def);
            }
            ArenaYamlIO.save(arenasFile, defs, placedByArena);
        } catch (IOException ex) {
            plugin.getLogger().severe("Falha ao salvar arenas.yml: " + ex.getMessage());
        }
    }

    private static boolean shouldTrack(@Nullable ArenaDefinition def) {
        return def != null && (def.mode().isCpvp() || def.mode().isAllBlocks());
    }

    public boolean tracks(ArenaDefinition def) {
        return shouldTrack(def);
    }

    public void onPlace(ArenaDefinition def, World world, ArenaBlockPos pos) {
        if (!tracks(def)) {
            return;
        }
        UUID wid = world.getUID();
        placedByArena.computeIfAbsent(def.id(), k -> new ConcurrentHashMap<>()).put(pos, wid);
        if (def.restoreMode() == RestoreMode.PER_BLOCK_TIMER) {
            long dueMs = System.currentTimeMillis() + (long) def.perBlockSeconds() * 1000L;
            dueMillisByArena.computeIfAbsent(def.id(), k -> new ConcurrentHashMap<>()).put(pos, dueMs);
        }
    }

    public void cancelTimer(ArenaDefinition def, ArenaBlockPos pos) {
        if (!tracks(def)) {
            return;
        }
        Map<ArenaBlockPos, Long> due = dueMillisByArena.get(def.id());
        if (due != null) {
            due.remove(pos);
        }
    }

    public boolean isPlayerPlaced(ArenaDefinition def, World world, ArenaBlockPos pos) {
        Map<ArenaBlockPos, UUID> map = placedByArena.get(def.id());
        if (map == null) {
            return false;
        }
        UUID exp = map.get(pos);
        return exp != null && exp.equals(world.getUID());
    }

    public boolean removeTrackingIfPresent(ArenaDefinition def, World world, ArenaBlockPos pos) {
        Map<ArenaBlockPos, UUID> map = placedByArena.get(def.id());
        if (map == null) {
            return false;
        }
        UUID exp = map.remove(pos);
        cancelTimer(def, pos);
        return exp != null && exp.equals(world.getUID());
    }

    public void enterPlayer(ArenaDefinition def) {
        occupancy.computeIfAbsent(def.id(), k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
    }

    public void leavePlayer(ArenaDefinition def) {
        java.util.concurrent.atomic.AtomicInteger c = occupancy.get(def.id());
        if (c != null) {
            int v = c.decrementAndGet();
            if (v <= 0) {
                occupancy.remove(def.id());
                if (def.restoreMode() == RestoreMode.RESET_ON_EMPTY && tracks(def)) {
                    clearArenaPerBlockDispatch(def.id(), true, () -> Bukkit.getGlobalRegionScheduler().execute(plugin, this::flushSync));
                }
            }
        }
    }

    /**
     * Chamado por tarefa repetida (ex.: scheduler síncrono global): expira PER_BLOCK_TIMER.
     */
    public void tick() {
        long nowMs = System.currentTimeMillis();
        for (ArenaDefinition def : plugin.registry().all()) {
            if (!tracks(def) || def.restoreMode() != RestoreMode.PER_BLOCK_TIMER) {
                continue;
            }
            Map<ArenaBlockPos, Long> dueMap = dueMillisByArena.get(def.id());
            if (dueMap == null || dueMap.isEmpty()) {
                continue;
            }
            World w = Bukkit.getWorld(def.cuboid().worldId());
            if (w == null) {
                continue;
            }
            for (Iterator<Map.Entry<ArenaBlockPos, Long>> it = dueMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<ArenaBlockPos, Long> e = it.next();
                if (e.getValue() > nowMs) {
                    continue;
                }
                ArenaBlockPos pos = e.getKey();
                it.remove();
                scheduleRemovalAtWorld(w, pos, def.id(), true);
            }
        }
    }

    /**
     * Remove rastro e agenda limpeza por bloco (Folia-safe).
     */
    public void clearArenaSplitWorlds(String arenaId, boolean decay, @Nullable Runnable after) {
        clearArenaPerBlockDispatch(arenaId, decay, after);
    }

    private void clearArenaPerBlockDispatch(String arenaId, boolean decay, @Nullable Runnable after) {
        Map<ArenaBlockPos, UUID> map = placedByArena.remove(arenaId);
        dueMillisByArena.remove(arenaId);
        if (map == null || map.isEmpty()) {
            if (after != null) {
                after.run();
            }
            return;
        }
        scheduleClearArenaPerBlock(map, decay, after);
    }

    private void scheduleClearArenaPerBlock(Map<ArenaBlockPos, UUID> map, boolean decay, @Nullable Runnable after) {
        FileConfiguration cfg = plugin.getConfig();
        Sound snd = parseSound(cfg.getString("block-decay-sound", "BLOCK_FIRE_EXTINGUISH"));
        float vol = (float) cfg.getDouble("block-decay-volume", 0.35);
        float pitch = (float) cfg.getDouble("block-decay-pitch", 1.15);
        Particle particle = parseParticle(cfg.getString("block-decay-particle", "CLOUD"));
        int count = cfg.getInt("block-decay-particle-count", 12);

        AtomicInteger pending = new AtomicInteger(map.size());
        Runnable doneOne = () -> {
            if (pending.decrementAndGet() == 0 && after != null) {
                after.run();
            }
        };

        for (Map.Entry<ArenaBlockPos, UUID> e : map.entrySet()) {
            World w = Bukkit.getWorld(e.getValue());
            if (w == null) {
                doneOne.run();
                continue;
            }
            ArenaBlockPos pos = e.getKey();
            Location loc = new Location(w, pos.x(), pos.y(), pos.z());
            Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
                try {
                    Block b = loc.getBlock();
                    if (decay) {
                        decayBlockVisual(b, particle, count, snd, vol, pitch);
                    }
                    b.setType(Material.AIR, false);
                } finally {
                    doneOne.run();
                }
            });
        }
    }

    private void scheduleRemovalAtWorld(World world, ArenaBlockPos pos, String arenaId, boolean decay) {
        Location loc = new Location(world, pos.x(), pos.y(), pos.z());
        Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
            Map<ArenaBlockPos, UUID> map = placedByArena.get(arenaId);
            if (map == null || !map.containsKey(pos)) {
                return;
            }
            Block b = loc.getBlock();
            if (decay) {
                FileConfiguration cfg = plugin.getConfig();
                Sound snd = parseSound(cfg.getString("block-decay-sound", "BLOCK_FIRE_EXTINGUISH"));
                float vol = (float) cfg.getDouble("block-decay-volume", 0.35);
                float pitch = (float) cfg.getDouble("block-decay-pitch", 1.15);
                Particle particle = parseParticle(cfg.getString("block-decay-particle", "CLOUD"));
                int count = cfg.getInt("block-decay-particle-count", 12);
                decayBlockVisual(b, particle, count, snd, vol, pitch);
            }
            b.setType(Material.AIR, false);
            map.remove(pos);
            if (map.isEmpty()) {
                placedByArena.remove(arenaId, map);
            }
            Bukkit.getGlobalRegionScheduler().execute(plugin, this::flushSync);
        });
    }

    private static void decayBlockVisual(Block block, Particle particle, int count, Sound sound, float volume, float pitch) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        World w = block.getWorld();
        w.spawnParticle(particle, center, count, 0.25, 0.25, 0.25, 0.01);
        w.playSound(center, sound, volume, pitch);
    }

    /**
     * Filtra lista de blocos da explosão: mapa original não explode; blocos colocados removem rastro.
     */
    public void handleExplosionBlocks(World world, List<Block> affected) {
        affected.removeIf(b -> {
            ArenaDefinition def = plugin.registry().atBlock(world, b.getX(), b.getY(), b.getZ());
            if (def == null) {
                return false;
            }
            if (def.mode() == ArenaMode.TRADITIONAL) {
                return true;
            }
            if (!tracks(def)) {
                return false;
            }
            ArenaBlockPos pos = new ArenaBlockPos(b.getX(), b.getY(), b.getZ());
            if (!isPlayerPlaced(def, world, pos)) {
                return true;
            }
            removeTrackingIfPresent(def, world, pos);
            return false;
        });
        Bukkit.getGlobalRegionScheduler().execute(plugin, this::flushSync);
    }

    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return Sound.BLOCK_FIRE_EXTINGUISH;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        return s != null ? s : Sound.BLOCK_FIRE_EXTINGUISH;
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return Particle.CLOUD;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        Particle p = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));
        return p != null ? p : Particle.CLOUD;
    }
}
