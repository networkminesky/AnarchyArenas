package anarchy.minesky.arenas.arena;

import org.jetbrains.annotations.Nullable;

/**
 * Como blocos colocados são restaurados/removidos.
 */
public enum RestoreMode {
    PER_BLOCK_TIMER,
    RESET_ON_EMPTY;

    public static @Nullable RestoreMode fromConfig(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "PER_BLOCK_TIMER", "TIMER" -> PER_BLOCK_TIMER;
            case "RESET_ON_EMPTY", "EMPTY" -> RESET_ON_EMPTY;
            default -> null;
        };
    }
}
