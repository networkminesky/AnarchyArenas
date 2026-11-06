package anarchy.minesky.arenas;

import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.command.AaCommand;
import anarchy.minesky.arenas.listener.BlockArenaListener;
import anarchy.minesky.arenas.listener.PlayerMoveArenaListener;
import anarchy.minesky.arenas.manager.ArenaRegistry;
import anarchy.minesky.arenas.manager.ArenaSessionManager;
import anarchy.minesky.arenas.manager.ArenaYamlIO;
import anarchy.minesky.arenas.manager.BlockTrackManager;
import anarchy.minesky.arenas.manager.MaterialLists;
import anarchy.minesky.arenas.manager.Messages;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AnarchyArenas extends JavaPlugin {

    private Messages messages;
    private MaterialLists materialLists;
    private final ArenaRegistry registry = new ArenaRegistry();
    private BlockTrackManager blockTrack;
    private final ArenaSessionManager session = new ArenaSessionManager();
    private PlayerMoveArenaListener moveListener;
    private ScheduledTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.materialLists = new MaterialLists();
        materialLists.reload(getConfig());

        File af = new File(getDataFolder(), "arenas.yml");
        if (!af.exists()) {
            saveResource("arenas.yml", false);
        }
        this.blockTrack = new BlockTrackManager(this, af);

        reloadAll();

        AaCommand cmd = new AaCommand(this);
        if (getCommand("aa") != null) {
            getCommand("aa").setExecutor(cmd);
            getCommand("aa").setTabCompleter(cmd);
        }

        this.moveListener = new PlayerMoveArenaListener(this);
        Bukkit.getPluginManager().registerEvents(moveListener, this);
        Bukkit.getPluginManager().registerEvents(new BlockArenaListener(this), this);

        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> blockTrack.tick(), 1L, 1L);

        getLogger().info("AnarchyArenas habilitado — Folia/regiões.");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (blockTrack != null) {
            blockTrack.flushSync();
        }
    }

    /**
     * Recarrega config e arenas.yml; limpa sessões.
     */
    public void reloadAll() {
        reloadConfig();
        materialLists.reload(getConfig());

        ArenaYamlIO.LoadResult lr = ArenaYamlIO.load(blockTrack != null ? blockTrack.arenasFile()
                : new File(getDataFolder(), "arenas.yml"));
        registry.clear();
        for (ArenaDefinition def : lr.definitions().values()) {
            registry.register(def);
        }
        session.clear();
        if (blockTrack != null) {
            blockTrack.applyLoaded(lr, registry);
        }
        if (moveListener != null) {
            moveListener.reconcileAllOnline();
        }
    }

    public Messages messages() {
        return messages;
    }

    public MaterialLists materialLists() {
        return materialLists;
    }

    public ArenaRegistry registry() {
        return registry;
    }

    public BlockTrackManager blockTrack() {
        return blockTrack;
    }

    public ArenaSessionManager session() {
        return session;
    }

    public String defaultRestoreMode() {
        return getConfig().getString("default-restore-mode", "RESET_ON_EMPTY");
    }

    public int defaultPerBlockSeconds() {
        return getConfig().getInt("default-per-block-seconds", 30);
    }
}
