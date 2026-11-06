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

    public ArenaDefinition(String id, Cuboid cuboid, ArenaMode mode, RestoreMode restoreMode, int perBlockSeconds) {
        this.id = id;
        this.cuboid = cuboid;
        this.mode = mode;
        this.restoreMode = restoreMode;
        this.perBlockSeconds = Math.max(1, perBlockSeconds);
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

    public ArenaDefinition withMode(ArenaMode newMode) {
        return new ArenaDefinition(id, cuboid, newMode, restoreMode, perBlockSeconds);
    }
}
