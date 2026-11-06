package anarchy.minesky.arenas.arena;

/**
 * Definição persistida de uma arena.
 */
public final class ArenaDefinition {

    private final String id;
    private final Cuboid cuboid;
    private final ArenaMode mode;
    private final RestoreMode restoreMode;
    private final int perBlockSeconds;
    private final boolean keepInventory;

    public ArenaDefinition(String id, Cuboid cuboid, ArenaMode mode, RestoreMode restoreMode, int perBlockSeconds, boolean keepInventory) {
        this.id = id;
        this.cuboid = cuboid;
        this.mode = mode;
        this.restoreMode = restoreMode;
        this.perBlockSeconds = Math.max(1, perBlockSeconds);
        this.keepInventory = keepInventory;
    }

    public String id() {
        return id;
    }

    public Cuboid cuboid() {
        return cuboid;
    }

    public ArenaMode mode() {
        return mode;
    }

    public RestoreMode restoreMode() {
        return restoreMode;
    }

    public int perBlockSeconds() {
        return perBlockSeconds;
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public ArenaDefinition withMode(ArenaMode newMode) {
        return new ArenaDefinition(id, cuboid, newMode, restoreMode, perBlockSeconds, keepInventory);
    }

    public ArenaDefinition withKeepInventory(boolean v) {
        return new ArenaDefinition(id, cuboid, mode, restoreMode, perBlockSeconds, v);
    }
}
