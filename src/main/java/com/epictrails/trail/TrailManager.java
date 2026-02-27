package com.epictrails.trail;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;
import com.epictrails.packet.ParticlePacketSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Schedules the repeating task that renders cosmetic trails for all online players.
 *
 * <p>A single BukkitRunnable ticks on the main thread at the configured interval
 * ({@code trails.tick-interval}).  For each online player it checks whether the
 * trail should render (enabled, not vanished, not in PvP/combat), then delegates
 * to {@link ParticlePacketSender} with positions computed by the active
 * {@link TrailStyle} algorithm.</p>
 *
 * <p>A per-player <em>tick counter</em> is maintained so that styles that use
 * multi-step animations (e.g. SPIRAL, VORTEX) can advance their phase each tick.</p>
 */
public final class TrailManager {

    private final EpicTrails plugin;
    private final ParticlePacketSender sender;

    /** Per-player animation tick counter (continuously increments). */
    private final ConcurrentMap<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    private BukkitTask trailTask;

    public TrailManager(EpicTrails plugin) {
        this.plugin = plugin;
        this.sender = plugin.getParticlePacketSender();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Starts the global trail rendering task. */
    public void start() {
        if (trailTask != null) {
            trailTask.cancel();
        }
        int interval = plugin.getConfigManager().getTickInterval();
        trailTask = new TrailRenderTask().runTaskTimer(plugin, interval, interval);
        plugin.getLogger().info("Trail render task started (interval=" + interval + " ticks).");
    }

    /** Cancels the global trail rendering task. */
    public void stop() {
        if (trailTask != null) {
            trailTask.cancel();
            trailTask = null;
        }
        tickCounters.clear();
    }

    // -------------------------------------------------------------------------
    // Player counter management
    // -------------------------------------------------------------------------

    /** Registers a player's tick counter when they join. */
    public void registerPlayer(UUID uuid) {
        tickCounters.put(uuid, 0);
    }

    /** Removes a player's tick counter when they leave. */
    public void unregisterPlayer(UUID uuid) {
        tickCounters.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Inner render task
    // -------------------------------------------------------------------------

    private final class TrailRenderTask extends BukkitRunnable {

        @Override
        public void run() {
            boolean showToSelf   = plugin.getConfigManager().isShowToSelf();
            boolean disableVanish = plugin.getConfigManager().isDisableForVanished();
            boolean disablePvp   = plugin.getConfigManager().isDisableInPvp();
            boolean disableCombat = plugin.getConfigManager().isDisableInCombat();
            int combatSec        = plugin.getConfigManager().getCombatTagSeconds();

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                PlayerData data = plugin.getPlayerDataMap().get(uuid);

                if (data == null) continue;
                if (!data.shouldRender(disableVanish, disablePvp, disableCombat, combatSec)) continue;

                int tick = tickCounters.merge(uuid, 1, Integer::sum);

                Trail trail = plugin.getConfigManager().getTrail(data.getParticleKey());
                if (trail == null) continue;

                renderTrail(player, data.getTrailStyle(), trail, tick, showToSelf);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Style rendering algorithms
    // -------------------------------------------------------------------------

    /**
     * Dispatches to the appropriate style algorithm and sends the resulting
     * particle positions via the packet sender.
     */
    private void renderTrail(Player player, TrailStyle style, Trail trail, int tick, boolean showToSelf) {
        Location base = player.getLocation();

        switch (style) {
            case SPIRAL       -> renderSpiral(player, trail, base, tick, showToSelf);
            case DOUBLE_HELIX -> renderDoubleHelix(player, trail, base, tick, showToSelf);
            case ORBIT        -> renderOrbit(player, trail, base, tick, showToSelf);
            case PULSE        -> renderPulse(player, trail, base, tick, showToSelf);
            case FLAME        -> renderFlame(player, trail, base, showToSelf);
            case WING         -> renderWing(player, trail, base, tick, showToSelf);
            case COMET        -> renderComet(player, trail, base, showToSelf);
            case ENCHANT      -> renderEnchant(player, trail, base, tick, showToSelf);
            case VORTEX       -> renderVortex(player, trail, base, tick, showToSelf);
        }
    }

    // --- SPIRAL ---

    private void renderSpiral(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        double angle  = tick * 0.4;
        double radius = 0.6;

        for (int i = 0; i < 3; i++) {
            double a = angle + (i * Math.PI * 2.0 / 3.0);
            double x = base.getX() + Math.cos(a) * radius;
            double y = base.getY() + 0.1 + (i * 0.3);
            double z = base.getZ() + Math.sin(a) * radius;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- DOUBLE HELIX ---

    private void renderDoubleHelix(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        double angle  = tick * 0.35;
        double radius = 0.7;

        for (int strand = 0; strand < 2; strand++) {
            double strandOffset = strand * Math.PI;
            for (int i = 0; i < 2; i++) {
                double a = angle + strandOffset + (i * 0.5);
                double yOff = (tick % 20) * 0.05;
                double x = base.getX() + Math.cos(a) * radius;
                double y = base.getY() + 0.5 + yOff % 2.0 - 1.0;
                double z = base.getZ() + Math.sin(a) * radius;
                sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
            }
        }
    }

    // --- ORBIT ---

    private void renderOrbit(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        double angle  = tick * 0.3;
        double radius = 1.2;
        int    points = 6;

        for (int i = 0; i < points; i++) {
            double a = angle + (i * Math.PI * 2.0 / points);
            double x = base.getX() + Math.cos(a) * radius;
            double y = base.getY() + 1.0;
            double z = base.getZ() + Math.sin(a) * radius;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- PULSE ---

    private void renderPulse(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        double radius = 0.3 + ((tick % 15) / 15.0) * 1.5;
        int    points = 8;

        for (int i = 0; i < points; i++) {
            double theta = (Math.PI * 2.0 / points) * i;
            double phi   = Math.PI / 2.0;
            double x = base.getX() + radius * Math.sin(phi) * Math.cos(theta);
            double y = base.getY() + 1.0 + radius * Math.cos(phi);
            double z = base.getZ() + radius * Math.sin(phi) * Math.sin(theta);
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- FLAME ---

    private void renderFlame(Player player, Trail trail, Location base, boolean showToSelf) {
        for (int i = 0; i < 5; i++) {
            double x = base.getX() + (Math.random() - 0.5) * 0.5;
            double y = base.getY() + Math.random() * 0.3;
            double z = base.getZ() + (Math.random() - 0.5) * 0.5;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- WING ---

    private void renderWing(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        double yaw   = Math.toRadians(base.getYaw() + 90);
        double spread = 1.5;

        for (int side = -1; side <= 1; side += 2) {
            for (int j = 1; j <= 3; j++) {
                double dist = j * 0.5;
                double x = base.getX() + side * Math.cos(yaw) * dist * spread;
                double y = base.getY() + 1.2 + (j - 2) * 0.3 + Math.sin(tick * 0.2) * 0.1;
                double z = base.getZ() + side * Math.sin(yaw) * dist * spread;
                sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
            }
        }
    }

    // --- COMET ---

    private void renderComet(Player player, Trail trail, Location base, boolean showToSelf) {
        double yaw   = Math.toRadians(base.getYaw());
        double pitch = Math.toRadians(base.getPitch());

        // Direction vector opposite to where the player faces
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy =  Math.sin(pitch);
        double dz =  Math.cos(yaw) * Math.cos(pitch);

        for (int i = 1; i <= 6; i++) {
            double spread = i * 0.1;
            double x = base.getX() - dx * i * 0.25 + (Math.random() - 0.5) * spread;
            double y = base.getY() + 1.0 - dy * i * 0.25 + (Math.random() - 0.5) * spread;
            double z = base.getZ() - dz * i * 0.25 + (Math.random() - 0.5) * spread;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- ENCHANT ---

    private void renderEnchant(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        for (int i = 0; i < 4; i++) {
            double angle = tick * 0.5 + i * (Math.PI / 2.0);
            double r     = 0.5 + Math.random() * 0.5;
            double x = base.getX() + Math.cos(angle) * r;
            double y = base.getY() + 0.5 + Math.random() * 1.5;
            double z = base.getZ() + Math.sin(angle) * r;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }

    // --- VORTEX ---

    private void renderVortex(Player player, Trail trail, Location base, int tick, boolean showToSelf) {
        int   layers  = 4;
        double radius = 1.0;

        for (int l = 0; l < layers; l++) {
            double heightFraction = l / (double) layers;
            double angle = tick * 0.45 + l * (Math.PI / layers);
            double r = radius * (1.0 - heightFraction * 0.6);

            double x = base.getX() + Math.cos(angle) * r;
            double y = base.getY() + 0.1 + heightFraction * 2.0;
            double z = base.getZ() + Math.sin(angle) * r;
            sender.sendToNearby(trail, base.clone().set(x, y, z), player, showToSelf);
        }
    }
}
