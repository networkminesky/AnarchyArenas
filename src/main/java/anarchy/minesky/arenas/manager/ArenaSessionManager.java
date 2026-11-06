package anarchy.minesky.arenas.manager;

import anarchy.minesky.arenas.arena.ArenaDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arena atual do jogador (após última checagem de bloco).
 */
public final class ArenaSessionManager {

    private final Map<UUID, ArenaDefinition> session = new ConcurrentHashMap<>();

    public @Nullable ArenaDefinition get(UUID id) {
        return session.get(id);
    }

    public @Nullable ArenaDefinition put(UUID id, @Nullable ArenaDefinition def) {
        if (def == null) {
            return session.remove(id);
        }
        return session.put(id, def);
    }

    public void remove(UUID id) {
        session.remove(id);
    }

    public void clear() {
        session.clear();
    }
}
