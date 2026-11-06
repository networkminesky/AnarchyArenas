package anarchy.minesky.arenas.arena;

import org.jetbrains.annotations.Nullable;

/**
 * Modos de operação das arenas.
 */
public enum ArenaMode {
    CPVP,
    TRADITIONAL,
    ALL_BLOCKS;

    public static @Nullable ArenaMode fromArg(String raw) {
        if (raw == null) {
            return null;
        }
        String u = raw.trim().toUpperCase();
        return switch (u) {
            case "CPVP", "MODE_CPVP" -> CPVP;
            case "TRADITIONAL", "TRAD", "PVP", "MODE_TRADITIONAL" -> TRADITIONAL;
            case "ALL_BLOCKS", "ANARCHY", "BUILD", "MODE_ALL_BLOCKS" -> ALL_BLOCKS;
            default -> null;
        };
    }

    public String configKey() {
        return name();
    }

    public String displayMini() {
        return switch (this) {
            case CPVP -> "<gradient:#FF5555:#FF8833>CPvP";
            case TRADITIONAL -> "<gradient:#55AAFF:#88DDFF>PvP";
            case ALL_BLOCKS -> "<gradient:#55FF55:#AAFF88>Build";
        };
    }

    /** Texto curto para placeholders (%mode%) sem formatação. */
    public String plainLabel() {
        return switch (this) {
            case CPVP -> "CPvP";
            case TRADITIONAL -> "PvP";
            case ALL_BLOCKS -> "Build";
        };
    }

    public boolean isCpvp() {
        return this == CPVP;
    }

    public boolean isTraditional() {
        return this == TRADITIONAL;
    }

    public boolean isAllBlocks() {
        return this == ALL_BLOCKS;
    }
}
