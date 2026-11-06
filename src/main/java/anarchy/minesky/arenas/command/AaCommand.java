package anarchy.minesky.arenas.command;

import anarchy.minesky.arenas.AnarchyArenas;
import anarchy.minesky.arenas.arena.ArenaDefinition;
import anarchy.minesky.arenas.arena.ArenaMode;
import anarchy.minesky.arenas.arena.Cuboid;
import anarchy.minesky.arenas.arena.RestoreMode;
import anarchy.minesky.arenas.hook.WorldEditSelections;
import anarchy.minesky.core.api.MineSkyAPI;
import anarchy.minesky.core.api.dialogapi.DialogAPI;
import anarchy.minesky.core.api.dialogapi.DialogButton;
import anarchy.minesky.core.api.dialogapi.DialogResponse;
import anarchy.minesky.core.api.dialogapi.MineSkyDialog;
import anarchy.minesky.core.api.feedback.UiSoundMode;
import anarchy.minesky.core.api.sounds.UiSoundsAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /aa — admin de arenas.
 */
public final class AaCommand implements CommandExecutor, TabCompleter {

    private final AnarchyArenas plugin;

    public AaCommand(AnarchyArenas plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.messages().message("player-only", Map.of()));
                    return true;
                }
                if (!player.hasPermission("anarchyarenas.admin")) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("no-permission", Map.of()));
                    return true;
                }
                if (args.length == 1) {
                    openCreateDialog(player);
                    return true;
                }
                if (args.length < 3) {
                    playDeny(player);
                    sendUsage(sender, "/aa create <nome> <CPVP|TRADITIONAL|ALL_BLOCKS>");
                    return true;
                }
                String name = args[1];
                ArenaMode mode = ArenaMode.fromArg(args[2]);
                if (mode == null) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("invalid-mode", Map.of()));
                    return true;
                }
                return handleCreate(player, name, mode);
            }
            case "setmode" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.messages().message("player-only", Map.of()));
                    return true;
                }
                if (!player.hasPermission("anarchyarenas.admin")) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("no-permission", Map.of()));
                    return true;
                }
                if (args.length < 3) {
                    playDeny(player);
                    sendUsage(sender, "/aa setmode <nome> <CPVP|TRADITIONAL|ALL_BLOCKS>");
                    return true;
                }
                ArenaDefinition existing = plugin.registry().get(args[1]);
                if (existing == null) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("arena-not-found", Map.of()));
                    return true;
                }
                ArenaMode mode = ArenaMode.fromArg(args[2]);
                if (mode == null) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("invalid-mode", Map.of()));
                    return true;
                }
                ArenaDefinition updated = existing.withMode(mode);
                plugin.registry().register(updated);
                plugin.blockTrack().clearArenaSplitWorlds(updated.id(), true, () -> Bukkit.getGlobalRegionScheduler().execute(plugin, plugin.blockTrack()::flushSync));
                playSuccess(player);
                player.sendMessage(plugin.messages().prefixed("setmode-success", Map.of("arena", updated.id(), "mode", mode.plainLabel())));
                return true;
            }
            case "delete" -> {
                if (!(sender instanceof Player player)) {
                    if (!sender.hasPermission("anarchyarenas.admin")) {
                        sender.sendMessage(plugin.messages().message("no-permission", Map.of()));
                        return true;
                    }
                    return handleDeleteConsole(sender, args);
                }
                if (!player.hasPermission("anarchyarenas.admin")) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("no-permission", Map.of()));
                    return true;
                }
                if (args.length < 2) {
                    playDeny(player);
                    sendUsage(sender, "/aa delete <nome>");
                    return true;
                }
                ArenaDefinition removed = plugin.registry().remove(args[1]);
                if (removed == null) {
                    playDeny(player);
                    player.sendMessage(plugin.messages().message("arena-not-found", Map.of()));
                    return true;
                }
                plugin.blockTrack().clearArenaSplitWorlds(removed.id(), false, () -> Bukkit.getGlobalRegionScheduler().execute(plugin, plugin.blockTrack()::flushSync));
                playSuccess(player);
                player.sendMessage(plugin.messages().prefixed("delete-success", Map.of("arena", removed.id())));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("anarchyarenas.admin")) {
                    if (sender instanceof Player p) {
                        playDeny(p);
                    }
                    sender.sendMessage(plugin.messages().message("no-permission", Map.of()));
                    return true;
                }
                plugin.reloadAll();
                if (sender instanceof Player p) {
                    playSuccess(p);
                }
                sender.sendMessage(plugin.messages().prefixed("reload-success", Map.of()));
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private boolean handleDeleteConsole(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender, "/aa delete <nome>");
            return true;
        }
        ArenaDefinition removed = plugin.registry().remove(args[1]);
        if (removed == null) {
            sender.sendMessage(plugin.messages().message("arena-not-found", Map.of()));
            return true;
        }
        plugin.blockTrack().clearArenaSplitWorlds(removed.id(), false, () -> Bukkit.getGlobalRegionScheduler().execute(plugin, plugin.blockTrack()::flushSync));
        sender.sendMessage(plugin.messages().prefixed("delete-success", Map.of("arena", removed.id())));
        return true;
    }

    private boolean handleCreate(Player player, String name, ArenaMode mode) {
        if (plugin.registry().get(name) != null) {
            playDeny(player);
            player.sendMessage(plugin.messages().message("arena-exists", Map.of()));
            return true;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            playDeny(player);
            player.sendMessage(plugin.messages().message("worldedit-required", Map.of()));
            return true;
        }
        WorldEditSelections.SelectionBounds sel = WorldEditSelections.getCuboid(player);
        if (sel == null) {
            playDeny(player);
            player.sendMessage(plugin.messages().message("no-selection", Map.of()));
            return true;
        }
        org.bukkit.World bw = sel.bukkitWorld(player);
        Cuboid cuboid = new Cuboid(bw.getUID(), sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ());
        if (plugin.registry().intersects(cuboid)) {
            playDeny(player);
            player.sendMessage(plugin.messages().message("arena-overlap", Map.of()));
            return true;
        }
        RestoreMode restore = RestoreMode.fromConfig(plugin.defaultRestoreMode());
        if (restore == null) {
            restore = RestoreMode.RESET_ON_EMPTY;
        }
        int timerSec = plugin.defaultPerBlockSeconds();
        ArenaDefinition def = new ArenaDefinition(name, cuboid, mode, restore, timerSec);
        plugin.registry().register(def);
        plugin.blockTrack().flushSync();
        playSuccess(player);
        Map<String, String> r = new HashMap<>();
        r.put("arena", def.id());
        r.put("mode", mode.plainLabel());
        player.sendMessage(plugin.messages().prefixed("create-success", r));
        return true;
    }

    private void openCreateDialog(Player player) {
        if (!MineSkyAPI.isAvailable()) {
            UiSoundsAPI.error(player);
            player.sendMessage(plugin.messages().message("core-dialog-unavailable", Map.of()));
            return;
        }
        DialogAPI dialogApi = MineSkyAPI.dialog();
        MineSkyDialog.Builder b = dialogApi.createDialog("&#FFDD77Criar arena")
                .body("&#C7C7C7Use a seleção ativa do &#FFDD77WorldEdit&#C7C7C7 (//pos1/2) e preencha abaixo.")
                .multiActionColumns(1)
                .boolInput("block_if_overlap", "Bloquear se a seleção cruzar outra arena", true)
                .textInput("name", "Nome da arena", "ex: spawn_cpvp", "")
                .dropdownInput("mode", "Modo", dd -> dd
                        .option("CPVP", "&#FF6655CPvP / Âncora")
                        .option("TRADITIONAL", "&#66AAFFPvP tradicional")
                        .option("ALL_BLOCKS", "&#66FF77Anarchy build")
                        .initial("CPVP"))
                .dropdownInput("restore", "Restauração", dd -> dd
                        .option("PER_BLOCK_TIMER", "&#AAAAAATimer por bloco")
                        .option("RESET_ON_EMPTY", "&#AAAAAALimpar ao esvaziar")
                        .initial(plugin.defaultRestoreMode()))
                .sliderInput("timer_sec", "Segundos por bloco (timer)", 5.0F, 300.0F, 1.0F, plugin.defaultPerBlockSeconds())
                .onSubmit(this::onCreateDialogSubmit)
                .button(DialogButton.of("&#66FF88Criar Arena", (p, r) -> onCreateDialogSubmit(p, r)))
                .button(DialogButton.cancel("&#AAAAAACancelar", pl -> UiSoundsAPI.playMode(pl, UiSoundMode.ERROR)));
        dialogApi.show(player, b.build());
    }

    private void onCreateDialogSubmit(Player player, DialogResponse response) {
        if (!player.hasPermission("anarchyarenas.admin")) {
            UiSoundsAPI.error(player);
            return;
        }
        String name = response.getText("name", "").trim();
        if (name.isEmpty()) {
            UiSoundsAPI.error(player);
            return;
        }
        String modeKey = response.getSelectedOption("mode", "CPVP");
        ArenaMode mode = ArenaMode.fromArg(modeKey);
        if (mode == null) {
            UiSoundsAPI.error(player);
            return;
        }
        String restoreKey = response.getSelectedOption("restore", plugin.defaultRestoreMode());
        RestoreMode restore = RestoreMode.fromConfig(restoreKey);
        if (restore == null) {
            restore = RestoreMode.RESET_ON_EMPTY;
        }
        int timer = Math.max(1, Math.round(response.getFloat("timer_sec", plugin.defaultPerBlockSeconds())));
        boolean checkOverlap = response.getBoolean("block_if_overlap", true);

        if (plugin.registry().get(name) != null) {
            UiSoundsAPI.error(player);
            player.sendMessage(plugin.messages().message("arena-exists", Map.of()));
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            UiSoundsAPI.error(player);
            player.sendMessage(plugin.messages().message("worldedit-required", Map.of()));
            return;
        }
        WorldEditSelections.SelectionBounds sel = WorldEditSelections.getCuboid(player);
        if (sel == null) {
            UiSoundsAPI.error(player);
            player.sendMessage(plugin.messages().message("no-selection", Map.of()));
            return;
        }
        org.bukkit.World bw = sel.bukkitWorld(player);
        Cuboid cuboid = new Cuboid(bw.getUID(), sel.minX(), sel.minY(), sel.minZ(), sel.maxX(), sel.maxY(), sel.maxZ());
        if (checkOverlap && plugin.registry().intersects(cuboid)) {
            UiSoundsAPI.error(player);
            player.sendMessage(plugin.messages().message("arena-overlap", Map.of()));
            return;
        }
        ArenaDefinition def = new ArenaDefinition(name, cuboid, mode, restore, timer);
        plugin.registry().register(def);
        plugin.blockTrack().flushSync();
        UiSoundsAPI.success(player);
        Map<String, String> r = new HashMap<>();
        r.put("arena", def.id());
        r.put("mode", mode.plainLabel());
        player.sendMessage(plugin.messages().prefixed("create-success", r));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("§e/aa create [nome] [modo] §7| §e/aa setmode §7| §e/aa delete §7| §e/aa reload"));
    }

    private void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage(plugin.messages().message("invalid-usage", Map.of("usage", usage)));
    }

    private void playSuccess(Player player) {
        if (MineSkyAPI.isAvailable()) {
            UiSoundsAPI.success(player);
        }
    }

    private void playDeny(Player player) {
        if (MineSkyAPI.isAvailable()) {
            UiSoundsAPI.error(player);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("anarchyarenas.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return partial(List.of("create", "setmode", "delete", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("setmode") || args[0].equalsIgnoreCase("delete"))) {
            List<String> names = new ArrayList<>();
            for (ArenaDefinition def : plugin.registry().all()) {
                names.add(def.id());
            }
            return partial(names, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("setmode"))) {
            return partial(List.of("CPVP", "TRADITIONAL", "ALL_BLOCKS"), args[2]);
        }
        return List.of();
    }

    private static List<String> partial(List<String> opts, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
