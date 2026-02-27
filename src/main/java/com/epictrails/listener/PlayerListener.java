package com.epictrails.listener;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;
import com.epictrails.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player-lifecycle events: join, quit, and combat-tagging.
 *
 * <p>On join, the player's persisted data is loaded asynchronously from the database
 * and stored in the in-memory map.  Vanish detection is attempted via well-known
 * plugin metadata keys so that the trail is hidden for vanished players immediately.</p>
 *
 * <p>On quit, data is saved asynchronously and then removed from memory.</p>
 */
public final class PlayerListener implements Listener {

    private final EpicTrails plugin;

    public PlayerListener(EpicTrails plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager().getDatabase()
                .loadPlayer(player.getUniqueId())
                .thenAccept(loaded -> {
                    PlayerData data;
                    if (loaded != null) {
                        data = loaded;
                    } else {
                        data = new PlayerData(
                                player.getUniqueId(),
                                plugin.getConfigManager().getDefaultStyle(),
                                plugin.getConfigManager().getDefaultParticle()
                        );
                    }

                    // Detect vanish status from common vanish plugins
                    data.setVanished(isVanished(player));

                    final PlayerData finalData = data;
                    // Switch back to main thread for map insertion
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getPlayerDataMap().put(player.getUniqueId(), finalData);
                        plugin.getTrailManager().registerPlayer(player.getUniqueId());
                    });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Failed to load data for "
                            + player.getName() + ": " + ex.getMessage());
                    // Fall back to defaults
                    PlayerData data = new PlayerData(
                            player.getUniqueId(),
                            plugin.getConfigManager().getDefaultStyle(),
                            plugin.getConfigManager().getDefaultParticle()
                    );
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.getPlayerDataMap().put(player.getUniqueId(), data);
                        plugin.getTrailManager().registerPlayer(player.getUniqueId());
                    });
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        PlayerData data = plugin.getPlayerDataMap().remove(player.getUniqueId());
        plugin.getTrailManager().unregisterPlayer(player.getUniqueId());

        if (data != null) {
            plugin.getDatabaseManager().getDatabase().savePlayer(data);
        }
    }

    /**
     * Tags the attacked player as "in combat" so trails can be suppressed during PvP.
     * Only fires when two players are involved.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isDisableInCombat()) return;

        if (event.getDamager() instanceof Player attacker
                && event.getEntity() instanceof Player victim) {

            PlayerData attackerData = plugin.getPlayerDataMap().get(attacker.getUniqueId());
            PlayerData victimData   = plugin.getPlayerDataMap().get(victim.getUniqueId());

            if (attackerData != null) attackerData.markCombat();
            if (victimData   != null) victimData.markCombat();
        }
    }

    // -------------------------------------------------------------------------
    // Vanish detection
    // -------------------------------------------------------------------------

    /**
     * Checks whether {@code player} is currently vanished according to one of
     * the supported vanish plugins.
     *
     * <p>Supported plugins (checked in order):
     * <ul>
     *   <li>SuperVanish / PremiumVanish – metadata key {@code "vanished"}</li>
     *   <li>CMI – metadata key {@code "CMIVanished"}</li>
     *   <li>Essentials – metadata key {@code "isVanished"}</li>
     * </ul>
     * </p>
     */
    public boolean isVanished(Player player) {
        // SuperVanish / PremiumVanish
        if (player.hasMetadata("vanished")) {
            return player.getMetadata("vanished").stream()
                    .anyMatch(v -> v.asBoolean());
        }
        // CMI
        if (player.hasMetadata("CMIVanished")) {
            return player.getMetadata("CMIVanished").stream()
                    .anyMatch(v -> v.asBoolean());
        }
        // Essentials
        if (player.hasMetadata("isVanished")) {
            return player.getMetadata("isVanished").stream()
                    .anyMatch(v -> v.asBoolean());
        }
        return false;
    }
}
