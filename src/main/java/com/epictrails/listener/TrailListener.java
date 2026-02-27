package com.epictrails.listener;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;
import com.epictrails.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Listens to player movement to trigger trail suppression/activation based on
 * vanish and PvP zone state changes.
 *
 * <p>The actual particle rendering is handled by the scheduled task in
 * {@link com.epictrails.trail.TrailManager}.  This listener is responsible for
 * maintaining the correctness of the {@link PlayerData#isVanished()} and
 * {@link PlayerData#isInPvp()} flags that the render task reads.</p>
 *
 * <p>WorldGuard PvP integration: if WorldGuard is present, the PvP flag is checked
 * on every non-trivial movement event.  Otherwise the flag defaults to {@code false}.</p>
 */
public final class TrailListener implements Listener {

    private final EpicTrails plugin;

    /** Whether WorldGuard is available on this server. */
    private final boolean worldGuardPresent;

    public TrailListener(EpicTrails plugin) {
        this.plugin = plugin;
        this.worldGuardPresent = plugin.getServer().getPluginManager()
                .getPlugin("WorldGuard") != null;
    }

    /**
     * On meaningful player movement (position change, not just head rotation),
     * updates the PvP-zone flag in the player's {@link PlayerData}.
     *
     * <p>The check is only performed when {@code trails.disable-in-pvp} is enabled
     * in the configuration to avoid unnecessary overhead.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if only the head direction changed (no position delta)
        if (!event.hasChangedPosition()) return;

        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data == null) return;

        // Update vanish status every move tick (cheap metadata check)
        if (plugin.getConfigManager().isDisableForVanished()) {
            boolean vanished = isVanished(player);
            if (data.isVanished() != vanished) {
                data.setVanished(vanished);
                notifyVanishChange(player, vanished);
            }
        }

        // Update PvP zone status if WorldGuard is present and feature enabled
        if (plugin.getConfigManager().isDisableInPvp() && worldGuardPresent) {
            boolean inPvp = checkWorldGuardPvp(player);
            if (data.isInPvp() != inPvp) {
                data.setInPvp(inPvp);
                notifyPvpChange(player, inPvp);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Integration helpers
    // -------------------------------------------------------------------------

    /**
     * Detects whether the player is currently vanished via common vanish plugins.
     */
    private boolean isVanished(Player player) {
        if (player.hasMetadata("vanished")) {
            return player.getMetadata("vanished").stream().anyMatch(m -> m.asBoolean());
        }
        if (player.hasMetadata("CMIVanished")) {
            return player.getMetadata("CMIVanished").stream().anyMatch(m -> m.asBoolean());
        }
        if (player.hasMetadata("isVanished")) {
            return player.getMetadata("isVanished").stream().anyMatch(m -> m.asBoolean());
        }
        return false;
    }

    /**
     * Checks whether the player's current location is inside a WorldGuard region
     * where the PvP flag is set to {@code deny}.
     *
     * <p>Uses the WorldGuard API reflectively to avoid a hard compile-time dependency.
     * If the flag cannot be determined (WorldGuard absent or API mismatch),
     * returns {@code false} so that trails remain visible.</p>
     */
    private boolean checkWorldGuardPvp(Player player) {
        try {
            // WorldGuard.getInstance()
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);

            // wg.getPlatform().getRegionContainer().createQuery()
            Object platform = wgClass.getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);

            // BukkitAdapter.adapt(location) → com.sk89q.worldedit.util.Location
            Class<?> adapterClass = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter");
            Object weLocation = adapterClass
                    .getMethod("adapt", org.bukkit.Location.class)
                    .invoke(null, player.getLocation());

            // Flags.PVP (a StateFlag)
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object pvpFlag = flagsClass.getField("PVP").get(null);

            // Build a StateFlag[] vararg array
            Class<?> stateFlagClass = Class.forName(
                    "com.sk89q.worldguard.protection.flags.StateFlag");
            Object flagsArray = java.lang.reflect.Array.newInstance(stateFlagClass, 1);
            java.lang.reflect.Array.set(flagsArray, 0, pvpFlag);

            // query.queryState(weLocation, null, Flags.PVP)
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer");
            Object state = query.getClass()
                    .getMethod("queryState", weLocClass, localPlayerClass,
                            java.lang.reflect.Array.newInstance(stateFlagClass, 0).getClass())
                    .invoke(query, weLocation, null, flagsArray);

            if (state == null) return false;

            // DENY means PvP is disabled → trail should be hidden
            Class<?> stateEnum = Class.forName(
                    "com.sk89q.worldguard.protection.flags.StateFlag$State");
            return state == stateEnum.getField("DENY").get(null);

        } catch (Exception e) {
            // WorldGuard absent or API incompatibility: fail open (trails stay visible)
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private void notifyVanishChange(Player player, boolean nowVanished) {
        if (nowVanished) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("vanish-hidden")));
        }
    }

    private void notifyPvpChange(Player player, boolean nowInPvp) {
        if (nowInPvp) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("pvp-disabled")));
        } else {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("pvp-enabled")));
        }
    }
}
