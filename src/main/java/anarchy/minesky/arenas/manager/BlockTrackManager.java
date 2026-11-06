package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaBlockPos;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import anarchy.minesky.arenas.arena.Cuboid;
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
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BlockTrackManager {

    private final AnarchyArenas plugin;
    private final File arenasFile;

    private final Map<String, Map<ArenaBlockPos, UUID>> placedByArena = new ConcurrentHashMap<>();
    private final Map<String, Map<ArenaBlockPos, Long>> dueMillisByArena = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> placedCrystalsByArena = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> occupancy = new ConcurrentHashMap<>();

    // Evita I/O excessivo no disco quando muitos blocos são colocados rapidamente
    private final AtomicBoolean isDirty = new AtomicBoolean(false);

    public BlockTrackManager(AnarchyArenas plugin, File arenasFile) {
        this.plugin = plugin;
        this.arenasFile = arenasFile;
    }

    public File arenasFile() {
        return arenasFile;
    }

    public void markDirty() {
        this.isDirty.set(true);
    }

    public void applyLoaded(ArenaYamlIO.LoadResult lr, ArenaRegistry registry) {
        placedByArena.clear();
        dueMillisByArena.clear();
        placedCrystalsByArena.clear();
        occupancy.clear();
        long nowMs = System.currentTimeMillis();

        for (Map.Entry<String, Map<ArenaBlockPos, UUID>> e : lr.placed().entrySet()) {
            placedByArena.put(e.getKey(), new ConcurrentHashMap<>(e.getValue()));
        }
        for (Map.Entry<String, Set<UUID>> e : lr.crystals().entrySet()) {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            set.addAll(e.getValue());
            placedCrystalsByArena.put(e.getKey(), set);
        }

        for (Map.Entry<String, Map<ArenaBlockPos, UUID>> e : placedByArena.entrySet()) {
            String arenaId = e.getKey();
            ArenaDefinition def = registry.get(arenaId);
            if (!shouldTrack(def)) {
                Map<ArenaBlockPos, UUID> snap = new HashMap<>(e.getValue());
                placedByArena.remove(arenaId);
                scheduleClearByChunkBatch(snap, true, null);
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
            ArenaYamlIO.save(arenasFile, defs, placedByArena, placedCrystalsByArena);
            isDirty.set(false);
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
        markDirty();
    }

    public void onCrystalPlaced(ArenaDefinition def, UUID crystalEntityId) {
        if (!tracks(def)) {
            return;
        }
        placedCrystalsByArena.computeIfAbsent(def.id(), k -> ConcurrentHashMap.newKeySet()).add(crystalEntityId);
        markDirty();
    }

    public @Nullable String findArenaIdForCrystal(UUID entityId) {
        for (Map.Entry<String, Set<UUID>> e : placedCrystalsByArena.entrySet()) {
            Set<UUID> set = e.getValue();
            if (set != null && set.contains(entityId)) {
                return e.getKey();
            }
        }
        return null;
    }

    public void removeCrystalTracking(@Nullable String arenaId, UUID entityId) {
        if (arenaId != null) {
            Set<UUID> set = placedCrystalsByArena.get(arenaId);
            if (set != null && set.remove(entityId) && set.isEmpty()) {
                placedCrystalsByArena.remove(arenaId, set);
            }
        } else {
            for (Iterator<Map.Entry<String, Set<UUID>>> it = placedCrystalsByArena.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Set<UUID>> e = it.next();
                Set<UUID> set = e.getValue();
                if (set != null && set.remove(entityId) && set.isEmpty()) {
                    placedCrystalsByArena.remove(e.getKey(), set);
                }
            }
        }
        markDirty();
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
        boolean ok = exp != null && exp.equals(world.getUID());
        if (ok) {
            markDirty();
        }
        return ok;
    }

    public void enterPlayer(ArenaDefinition def) {
        occupancy.computeIfAbsent(def.id(), k -> new AtomicInteger()).incrementAndGet();
    }

    public void leavePlayer(ArenaDefinition def) {
        AtomicInteger c = occupancy.get(def.id());
        if (c != null) {
            int v = c.decrementAndGet();
            if (v <= 0) {
                occupancy.remove(def.id());
                if (def.restoreMode() == RestoreMode.RESET_ON_EMPTY && tracks(def)) {
                    clearArenaFull(def, true, () -> Bukkit.getGlobalRegionScheduler().execute(plugin, this::flushSync));
                }
            }
        }
    }

    /**
     * Chamado periodicamente (executa em GlobalRegionScheduler).
     */
    public void tick() {
        long nowMs = System.currentTimeMillis();

        // Salva no disco periodicamente apenas se houve alterações
        if (isDirty.compareAndSet(true, false)) {
            flushSync();
        }

        for (ArenaDefinition def : plugin.registry().all()) {
            if (!tracks(def) || def.restoreMode() != RestoreMode.PER_BLOCK_TIMER) {
                continue;
            }
            String arenaId = def.id();
            Map<ArenaBlockPos, Long> dueMap = dueMillisByArena.get(arenaId);
            if (dueMap == null || dueMap.isEmpty()) {
                continue;
            }
            World w = Bukkit.getWorld(def.cuboid().worldId());
            if (w == null) {
                continue;
            }

            List<ArenaBlockPos> expired = new ArrayList<>();
            for (Iterator<Map.Entry<ArenaBlockPos, Long>> it = dueMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<ArenaBlockPos, Long> e = it.next();
                if (e.getValue() <= nowMs) {
                    expired.add(e.getKey());
                    it.remove();
                }
            }

            if (expired.isEmpty()) {
                continue;
            }

            Map<ArenaBlockPos, UUID> batch = new HashMap<>();
            UUID wid = w.getUID();
            Map<ArenaBlockPos, UUID> live = placedByArena.get(arenaId);
            for (ArenaBlockPos p : expired) {
                if (live != null && live.containsKey(p)) {
                    batch.put(p, wid);
                }
            }

            if (batch.isEmpty()) {
                continue;
            }

            scheduleClearByChunkBatch(batch, true, () -> {
                Map<ArenaBlockPos, UUID> m = placedByArena.get(arenaId);
                if (m != null) {
                    for (ArenaBlockPos p : batch.keySet()) {
                        m.remove(p);
                    }
                    if (m.isEmpty()) {
                        placedByArena.remove(arenaId, m);
                    }
                }
                markDirty();
            });
        }
    }

    public void clearArenaSplitWorlds(String arenaId, boolean decay, @Nullable Runnable after) {
        ArenaDefinition def = plugin.registry().get(arenaId);
        if (def != null) {
            clearArenaFull(def, decay, after);
        } else {
            Map<ArenaBlockPos, UUID> map = placedByArena.remove(arenaId);
            dueMillisByArena.remove(arenaId);
            Set<UUID> cry = placedCrystalsByArena.remove(arenaId);
            List<Consumer<Runnable>> parts = new ArrayList<>();
            if (map != null && !map.isEmpty()) {
                parts.add(cb -> scheduleClearByChunkBatch(map, decay, cb));
            }
            if (cry != null && !cry.isEmpty()) {
                parts.add(cb -> removeTrackedCrystals(cry, cb));
            }
            runSequence(parts, after);
        }
    }

    private void clearArenaFull(ArenaDefinition def, boolean decay, @Nullable Runnable after) {
        String id = def.id();

        // 1. TENTA RESTAURAR COM O SCHEMATIC DO WORLDEDIT (PLANO INFALÍVEL)
        boolean restoredBySchem = plugin.schematicManager().restoreSnapshot(def);

        placedByArena.remove(id);
        dueMillisByArena.remove(id);
        placedCrystalsByArena.remove(id);

        // Se restaurou via WorldEdit, a arena já está 100% como nova!
        if (restoredBySchem) {
            if (after != null) {
                after.run();
            }
            return;
        }

        // 2. FALLBACK (caso a arena não tenha schematic salvo)
        Map<ArenaBlockPos, UUID> blocksSnap = placedByArena.containsKey(id) ? new HashMap<>(placedByArena.get(id)) : null;
        World w = Bukkit.getWorld(def.cuboid().worldId());
        List<Consumer<Runnable>> sequence = new ArrayList<>();
        if (blocksSnap != null && !blocksSnap.isEmpty()) {
            sequence.add(cb -> scheduleClearByChunkBatch(blocksSnap, decay, cb));
        }
        if (w != null && tracks(def)) {
            sequence.add(cb -> sweepUntrackedCrystalsInCuboid(w, def.cuboid(), cb));
        }
        runSequence(sequence, after);
    }

    private static void runSequence(List<Consumer<Runnable>> parts, @Nullable Runnable finalAfter) {
        if (parts.isEmpty()) {
            if (finalAfter != null) {
                finalAfter.run();
            }
            return;
        }
        AtomicInteger idx = new AtomicInteger();
        Runnable[] chain = new Runnable[1];
        chain[0] = () -> {
            int i = idx.getAndIncrement();
            if (i >= parts.size()) {
                if (finalAfter != null) {
                    finalAfter.run();
                }
                return;
            }
            parts.get(i).accept(chain[0]);
        };
        chain[0].run();
    }

    private void removeTrackedCrystals(Set<UUID> uuids, @Nullable Runnable after) {
        if (uuids.isEmpty()) {
            if (after != null) {
                after.run();
            }
            return;
        }
        AtomicInteger pending = new AtomicInteger(uuids.size());
        Runnable doneOne = () -> {
            if (pending.decrementAndGet() == 0 && after != null) {
                after.run();
            }
        };

        for (UUID id : uuids) {
            boolean found = false;
            for (World world : Bukkit.getWorlds()) {
                Entity ent = world.getEntity(id);
                if (ent instanceof EnderCrystal crystal) {
                    found = true;
                    crystal.getScheduler().run(plugin, task -> {
                        try {
                            crystal.remove();
                        } finally {
                            doneOne.run();
                        }
                    }, null);
                    break;
                }
            }
            if (!found) {
                doneOne.run();
            }
        }
    }

    private void sweepUntrackedCrystalsInCuboid(World world, Cuboid cuboid, @Nullable Runnable after) {
        int minCx = cuboid.minX() >> 4;
        int maxCx = cuboid.maxX() >> 4;
        int minCz = cuboid.minZ() >> 4;
        int maxCz = cuboid.maxZ() >> 4;
        int chunks = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks++;
            }
        }
        if (chunks == 0) {
            if (after != null) {
                after.run();
            }
            return;
        }
        AtomicInteger pending = new AtomicInteger(chunks);
        Runnable doneChunk = () -> {
            if (pending.decrementAndGet() == 0 && after != null) {
                after.run();
            }
        };
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                int fcx = cx;
                int fcz = cz;
                Bukkit.getRegionScheduler().execute(plugin, world, fcx, fcz, () -> {
                    try {
                        if (!world.isChunkLoaded(fcx, fcz)) {
                            return;
                        }
                        for (Entity entity : world.getChunkAt(fcx, fcz).getEntities()) {
                            if (!(entity instanceof EnderCrystal crystal)) {
                                continue;
                            }
                            if (!cuboid.contains(crystal.getLocation())) {
                                continue;
                            }
                            crystal.getScheduler().run(plugin, task -> crystal.remove(), null);
                        }
                    } finally {
                        doneChunk.run();
                    }
                });
            }
        }
    }

    /**
     * Limpeza segura em lote agrupada por Chunks no RegionScheduler.
     */
    private void scheduleClearByChunkBatch(Map<ArenaBlockPos, UUID> worldPositions, boolean decay, @Nullable Runnable after) {
        FileConfiguration cfg = plugin.getConfig();
        Sound snd = parseSound(cfg.getString("block-decay-sound", "BLOCK_FIRE_EXTINGUISH"));
        float vol = (float) cfg.getDouble("block-decay-volume", 0.35);
        float pitch = (float) cfg.getDouble("block-decay-pitch", 1.15);
        Particle particle = parseParticle(cfg.getString("block-decay-particle", "CLOUD"));
        int pcount = cfg.getInt("block-decay-particle-count", 12);

        Map<ChunkBatchKey, List<ArenaBlockPos>> byChunk = new HashMap<>();
        for (Map.Entry<ArenaBlockPos, UUID> e : worldPositions.entrySet()) {
            World w = Bukkit.getWorld(e.getValue());
            if (w == null) {
                continue;
            }
            ArenaBlockPos pos = e.getKey();
            int cx = pos.x() >> 4;
            int cz = pos.z() >> 4;
            byChunk.computeIfAbsent(new ChunkBatchKey(e.getValue(), cx, cz), k -> new ArrayList<>()).add(pos);
        }
        if (byChunk.isEmpty()) {
            if (after != null) {
                after.run();
            }
            return;
        }

        AtomicInteger pending = new AtomicInteger(byChunk.size());
        Runnable doneChunk = () -> {
            if (pending.decrementAndGet() == 0 && after != null) {
                after.run();
            }
        };

        for (Map.Entry<ChunkBatchKey, List<ArenaBlockPos>> e : byChunk.entrySet()) {
            ChunkBatchKey key = e.getKey();
            List<ArenaBlockPos> chunkPositions = e.getValue();
            World w = Bukkit.getWorld(key.worldId);
            if (w == null) {
                doneChunk.run();
                continue;
            }

            Bukkit.getRegionScheduler().execute(plugin, w, key.chunkX, key.chunkZ, () -> {
                try {
                    // Garante que o chunk está carregado para não ignorar blocos
                    if (!w.isChunkLoaded(key.chunkX, key.chunkZ)) {
                        w.getChunkAt(key.chunkX, key.chunkZ);
                    }
                    for (ArenaBlockPos pos : chunkPositions) {
                        try {
                            Block b = w.getBlockAt(pos.x(), pos.y(), pos.z());
                            if (decay && b.getType() != Material.AIR) {
                                decayBlockVisual(b, particle, pcount, snd, vol, pitch);
                            }
                            // Transforma qualquer bloco ou fluido em ar sem disparar physics em cadeia
                            b.setType(Material.AIR, false);
                        } catch (Throwable ignored) {
                            // Erro isolado em um bloco não cancela a remoção dos demais
                        }
                    }
                } finally {
                    doneChunk.run();
                }
            });
        }
    }

    private record ChunkBatchKey(UUID worldId, int chunkX, int chunkZ) {
    }

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
        markDirty();
    }

    private static void decayBlockVisual(Block block, Particle particle, int count, Sound sound, float volume, float pitch) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        World w = block.getWorld();
        w.spawnParticle(particle, center, count, 0.25, 0.25, 0.25, 0.01);
        w.playSound(center, sound, volume, pitch);
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