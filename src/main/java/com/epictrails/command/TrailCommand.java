package com.epictrails.command;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;
import com.epictrails.trail.Trail;
import com.epictrails.trail.TrailStyle;
import com.epictrails.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Handles the {@code /trail} command and all its subcommands.
 *
 * <p>Registered subcommands:</p>
 * <ul>
 *   <li>{@code /trail} – opens the GUI selector</li>
 *   <li>{@code /trail style <name>} – sets the trail style</li>
 *   <li>{@code /trail particle <name>} – sets the trail particle</li>
 *   <li>{@code /trail toggle} – enables or disables the trail</li>
 *   <li>{@code /trail reset} – resets to defaults</li>
 *   <li>{@code /trail reload} – reloads config (admin)</li>
 *   <li>{@code /trail help} – shows help</li>
 * </ul>
 */
public final class TrailCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS =
            Arrays.asList("style", "particle", "toggle", "reset", "reload", "help");

    private final EpicTrails plugin;

    public TrailCommand(EpicTrails plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.parseLegacy(
                    plugin.getConfigManager().getMessage("player-only")));
            return true;
        }

        if (!player.hasPermission("epictrails.use")) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "style"    -> handleStyle(player, args);
            case "particle" -> handleParticle(player, args);
            case "toggle"   -> handleToggle(player);
            case "reset"    -> handleReset(player);
            case "reload"   -> handleReload(player);
            case "help"     -> sendHelp(player);
            default -> player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("unknown-command")));
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Subcommand handlers
    // -------------------------------------------------------------------------

    private void handleStyle(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getGuiManager().openStyleGui(player);
            return;
        }

        String name = args[1].toUpperCase(Locale.ROOT);
        TrailStyle style = TrailStyle.fromString(name);

        if (style == null) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("style-not-found")
                            .replace("<style>", args[1])));
            return;
        }

        if (plugin.getConfigManager().isUseStylePermissions()
                && !player.hasPermission(style.getPermission())) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("style-no-permission")
                            .replace("<style>", style.getDisplayName())));
            return;
        }

        PlayerData data = getOrCreate(player);
        data.setTrailStyle(style);
        plugin.getDatabaseManager().getDatabase().savePlayer(data);

        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("style-changed")
                        .replace("<style>", style.getDisplayName())));
    }

    private void handleParticle(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getGuiManager().openParticleGui(player);
            return;
        }

        String key = args[1].toUpperCase(Locale.ROOT);
        Trail trail = plugin.getConfigManager().getTrail(key);

        if (trail == null) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("particle-not-found")
                            .replace("<particle>", args[1])));
            return;
        }

        PlayerData data = getOrCreate(player);
        data.setParticleKey(key);
        plugin.getDatabaseManager().getDatabase().savePlayer(data);

        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("particle-changed")
                        .replace("<particle>", key)));
    }

    private void handleToggle(Player player) {
        PlayerData data = getOrCreate(player);
        data.setTrailEnabled(!data.isTrailEnabled());
        plugin.getDatabaseManager().getDatabase().savePlayer(data);

        String msg = data.isTrailEnabled()
                ? plugin.getConfigManager().getMessage("trail-enabled")
                : plugin.getConfigManager().getMessage("trail-disabled");
        player.sendMessage(ColorUtil.parse(msg));
    }

    private void handleReset(Player player) {
        PlayerData data = getOrCreate(player);
        data.setTrailStyle(plugin.getConfigManager().getDefaultStyle());
        data.setParticleKey(plugin.getConfigManager().getDefaultParticle());
        data.setTrailEnabled(true);
        plugin.getDatabaseManager().getDatabase().savePlayer(data);
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("trail-reset")));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("epictrails.reload")) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("no-permission")));
            return;
        }
        plugin.getConfigManager().load();
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("config-reloaded")));
    }

    private void sendHelp(Player player) {
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-header")));
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-gui")));
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-style")));
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-particle")));
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-toggle")));
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("help-reset")));
        if (player.hasPermission("epictrails.reload")) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessageNoPrefix("help-reload")));
        }
    }

    // -------------------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            return ROOT_SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("style".equals(sub)) {
                return Arrays.stream(TrailStyle.values())
                        .map(TrailStyle::name)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if ("particle".equals(sub)) {
                return plugin.getConfigManager().getTrailKeys().stream()
                        .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Returns the existing PlayerData for the player, or creates a default instance. */
    private PlayerData getOrCreate(Player player) {
        return plugin.getPlayerDataMap().computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerData(uuid,
                        plugin.getConfigManager().getDefaultStyle(),
                        plugin.getConfigManager().getDefaultParticle()));
    }
}
