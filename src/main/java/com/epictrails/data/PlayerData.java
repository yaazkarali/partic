package com.epictrails.data;

import com.epictrails.trail.TrailStyle;

import java.util.UUID;

/**
 * Holds the mutable trail preferences and runtime state for a single online player.
 * Instances are created on join and discarded on quit after persistence.
 */
public final class PlayerData {

    private final UUID uuid;
    private TrailStyle trailStyle;
    private String particleKey;
    private boolean trailEnabled;
    private boolean vanished;
    private boolean inPvp;
    private long lastCombatTag;

    /**
     * Creates a new {@code PlayerData} with sensible defaults.
     *
     * @param uuid         the player's unique ID
     * @param defaultStyle the default {@link TrailStyle} from config
     * @param defaultParticle the default particle key from config
     */
    public PlayerData(UUID uuid, TrailStyle defaultStyle, String defaultParticle) {
        this.uuid = uuid;
        this.trailStyle = defaultStyle;
        this.particleKey = defaultParticle;
        this.trailEnabled = true;
        this.vanished = false;
        this.inPvp = false;
        this.lastCombatTag = 0L;
    }

    /** The player's unique identifier. */
    public UUID getUuid() {
        return uuid;
    }

    /** The currently selected {@link TrailStyle}. */
    public TrailStyle getTrailStyle() {
        return trailStyle;
    }

    /** Sets the trail style. */
    public void setTrailStyle(TrailStyle trailStyle) {
        this.trailStyle = trailStyle;
    }

    /** The key of the currently selected particle (matches a key in {@code trails.yml}). */
    public String getParticleKey() {
        return particleKey;
    }

    /** Sets the particle key. */
    public void setParticleKey(String particleKey) {
        this.particleKey = particleKey;
    }

    /** Whether the player's trail is enabled. */
    public boolean isTrailEnabled() {
        return trailEnabled;
    }

    /** Sets trail enabled state. */
    public void setTrailEnabled(boolean trailEnabled) {
        this.trailEnabled = trailEnabled;
    }

    /** Whether the player is currently considered vanished. */
    public boolean isVanished() {
        return vanished;
    }

    /** Updates the vanished flag. */
    public void setVanished(boolean vanished) {
        this.vanished = vanished;
    }

    /** Whether the player is currently in a PvP zone. */
    public boolean isInPvp() {
        return inPvp;
    }

    /** Updates the PvP zone flag. */
    public void setInPvp(boolean inPvp) {
        this.inPvp = inPvp;
    }

    /**
     * Returns the system time (ms) of the last combat-tag event.
     * Zero means the player has never been combat-tagged this session.
     */
    public long getLastCombatTag() {
        return lastCombatTag;
    }

    /** Updates the combat-tag timestamp to the current system time. */
    public void markCombat() {
        this.lastCombatTag = System.currentTimeMillis();
    }

    /**
     * Returns {@code true} if the player is within the configured combat-tag window.
     *
     * @param combatTagSeconds seconds after the last tag during which combat is active
     */
    public boolean isInCombat(int combatTagSeconds) {
        if (lastCombatTag == 0L) return false;
        return (System.currentTimeMillis() - lastCombatTag) < (combatTagSeconds * 1000L);
    }

    /**
     * Determines whether the trail should be rendered right now, considering all
     * integration flags.
     *
     * @param disableForVanished plugin config flag
     * @param disableInPvp       plugin config flag
     * @param disableInCombat    plugin config flag
     * @param combatTagSeconds   plugin config value
     * @return {@code true} if the trail should spawn particles this tick
     */
    public boolean shouldRender(boolean disableForVanished, boolean disableInPvp,
                                boolean disableInCombat, int combatTagSeconds) {
        if (!trailEnabled) return false;
        if (disableForVanished && vanished) return false;
        if (disableInPvp && inPvp) return false;
        if (disableInCombat && isInCombat(combatTagSeconds)) return false;
        return true;
    }
}
