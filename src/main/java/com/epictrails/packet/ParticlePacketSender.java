package com.epictrails.packet;

import com.epictrails.EpicTrails;
import com.epictrails.trail.Trail;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.particle.Particle;
import com.github.retrooper.packetevents.protocol.particle.data.ParticleDustData;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleType;
import com.github.retrooper.packetevents.protocol.particle.type.ParticleTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerParticle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.logging.Level;

/**
 * Sends PacketEvents-based particle packets to a collection of viewers.
 *
 * <p>All particle rendering in EpicTrails is packet-based — {@code World#spawnParticle}
 * is never called.  This ensures full control over visibility (respecting vanish/PvP
 * flags) and avoids Paper's particle throttling.</p>
 *
 * <p>Special handling is provided for:</p>
 * <ul>
 *   <li><b>DUST</b> particles – wrapped in {@link ParticleDustData} with the trail's
 *       configured colour and size.</li>
 *   <li>All other particle types – wrapped in a generic {@link Particle} with no
 *       extra data.</li>
 * </ul>
 */
public final class ParticlePacketSender {

    private final EpicTrails plugin;

    /**
     * Default packet parameters for non-dust particles.
     * {@code longDistance=true} ensures the packet reaches viewers up to 512 blocks away;
     * EpicTrails performs its own distance culling before sending.
     */
    private static final boolean LONG_DISTANCE = true;
    private static final float   OFFSET_X       = 0f;
    private static final float   OFFSET_Y       = 0f;
    private static final float   OFFSET_Z       = 0f;
    private static final float   MAX_SPEED      = 0f;
    private static final int     PARTICLE_COUNT = 1;

    public ParticlePacketSender(EpicTrails plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a single particle packet at {@code location} to every player in
     * {@code viewers} using the particle configuration from {@code trail}.
     *
     * @param trail    the {@link Trail} whose particle type and colour to use
     * @param location the world position at which to spawn the particle
     * @param viewers  the players who should receive the packet
     */
    public void sendParticle(Trail trail, Location location, Collection<? extends Player> viewers) {
        if (trail == null || location == null || viewers == null || viewers.isEmpty()) return;

        WrapperPlayServerParticle packet = buildPacket(trail, location);
        if (packet == null) return;

        for (Player viewer : viewers) {
            if (!viewer.isOnline()) continue;
            try {
                PacketEvents.getAPI()
                        .getPlayerManager()
                        .getUser(viewer)
                        .sendPacket(packet);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE,
                        "Failed to send particle packet to " + viewer.getName(), e);
            }
        }
    }

    /**
     * Sends a single particle packet at {@code location} to a single viewer.
     *
     * @param trail    the {@link Trail} configuration
     * @param location the world position
     * @param viewer   the recipient player
     */
    public void sendParticle(Trail trail, Location location, Player viewer) {
        if (trail == null || location == null || viewer == null || !viewer.isOnline()) return;

        WrapperPlayServerParticle packet = buildPacket(trail, location);
        if (packet == null) return;

        try {
            PacketEvents.getAPI()
                    .getPlayerManager()
                    .getUser(viewer)
                    .sendPacket(packet);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to send particle packet to " + viewer.getName(), e);
        }
    }

    /**
     * Sends a particle packet at {@code particleLocation} to all players within the
     * configured view distance, using {@code owner} to determine self-visibility.
     *
     * @param trail            the {@link Trail} configuration
     * @param particleLocation the world position at which to spawn the particle
     * @param owner            the player who owns the trail (used for self-visibility check)
     * @param showToSelf       whether the owner should receive the packet
     */
    public void sendToNearby(Trail trail, Location particleLocation, Player owner, boolean showToSelf) {
        World world = particleLocation.getWorld();
        if (world == null) return;

        double viewDistSq = plugin.getConfigManager().getViewDistanceSquared();

        WrapperPlayServerParticle packet = buildPacket(trail, particleLocation);
        if (packet == null) return;

        for (Player viewer : world.getPlayers()) {
            if (!showToSelf && viewer.getUniqueId().equals(owner.getUniqueId())) continue;
            if (viewer.getLocation().distanceSquared(particleLocation) > viewDistSq) continue;

            try {
                PacketEvents.getAPI()
                        .getPlayerManager()
                        .getUser(viewer)
                        .sendPacket(packet);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE,
                        "Failed to send particle packet to " + viewer.getName(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Packet builder
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link WrapperPlayServerParticle} from the given trail and location.
     *
     * @param trail    source trail configuration
     * @param location world position
     * @return the constructed packet, or {@code null} if the particle type cannot be resolved
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private WrapperPlayServerParticle buildPacket(Trail trail, Location location) {
        Particle<?> particle;

        if (trail.isDustParticle()) {
            // Build a DUST particle with the configured colour and size
            org.bukkit.Color bukkit = trail.getDustColor();
            if (bukkit == null) bukkit = org.bukkit.Color.WHITE;

            float r = bukkit.getRed()   / 255f;
            float g = bukkit.getGreen() / 255f;
            float b = bukkit.getBlue()  / 255f;

            ParticleDustData dustData = new ParticleDustData(r, g, b, trail.getDustSize());
            particle = new Particle<>(ParticleTypes.DUST, dustData);

        } else {
            // Resolve the particle type by name via reflection on ParticleTypes
            ParticleType<?> type = resolveParticleType(trail.getParticleTypeName());
            if (type == null) {
                plugin.getLogger().warning("Unknown particle type '" + trail.getParticleTypeName()
                        + "' for trail '" + trail.getKey() + "'. Falling back to FLAME.");
                type = ParticleTypes.FLAME;
            }
            particle = new Particle(type);
        }

        return new WrapperPlayServerParticle(
                particle,
                LONG_DISTANCE,
                location.getX(),
                location.getY(),
                location.getZ(),
                OFFSET_X,
                OFFSET_Y,
                OFFSET_Z,
                MAX_SPEED,
                PARTICLE_COUNT
        );
    }

    /**
     * Resolves a {@link ParticleType} from its field name on the {@link ParticleTypes} class.
     *
     * @param name the field name, e.g. {@code "FLAME"}, {@code "SOUL_FIRE_FLAME"}
     * @return the matching {@link ParticleType}, or {@code null} if not found
     */
    private ParticleType<?> resolveParticleType(String name) {
        if (name == null) return null;
        try {
            Field field = ParticleTypes.class.getField(name.toUpperCase());
            return (ParticleType<?>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }
}
