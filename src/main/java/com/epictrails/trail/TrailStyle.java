package com.epictrails.trail;

/**
 * Represents the nine distinct visual styles that an EpicTrails trail can adopt.
 * Each style defines a unique particle-motion algorithm applied every render tick.
 *
 * <p>Style permissions follow the pattern {@code epictrails.style.<name_lowercase>}.</p>
 */
public enum TrailStyle {

    /**
     * Particles form a graceful single-helix spiral that rises along the player's
     * movement axis. Radius and pitch are configurable per-render tick.
     */
    SPIRAL("Spiral", "epictrails.style.spiral"),

    /**
     * Two interleaved helices twist around the player's travel path, producing a
     * DNA-like double-helix effect.
     */
    DOUBLE_HELIX("Double Helix", "epictrails.style.double_helix"),

    /**
     * Particles revolve around the player in a horizontal ring at a fixed radius,
     * creating a continuous orbit regardless of player movement.
     */
    ORBIT("Orbit", "epictrails.style.orbit"),

    /**
     * An expanding-then-contracting spherical shell of particles pulses outward
     * from the player's centre on each render tick, like a heartbeat.
     */
    PULSE("Pulse", "epictrails.style.pulse"),

    /**
     * Hot embers and rising flame particles erupt upward from directly beneath the
     * player's feet, simulating a jet of fire.
     */
    FLAME("Flame", "epictrails.style.flame"),

    /**
     * Sweeping arcs of particles fan out symmetrically from both of the player's
     * shoulders, resembling outstretched wings.
     */
    WING("Wing", "epictrails.style.wing"),

    /**
     * A dense streak of particles streams behind the player in the direction
     * opposite to their velocity, like the luminous tail of a comet.
     */
    COMET("Comet", "epictrails.style.comet"),

    /**
     * Enchantment-glyph-style particles swirl and shimmer in a loose cloud around
     * the player, evoking magical energy.
     */
    ENCHANT("Enchant", "epictrails.style.enchant"),

    /**
     * A cyclonic vortex of particles whirls around the player's vertical axis,
     * tightening at the base and flaring at the top.
     */
    VORTEX("Vortex", "epictrails.style.vortex");

    private final String displayName;
    private final String permission;

    TrailStyle(String displayName, String permission) {
        this.displayName = displayName;
        this.permission = permission;
    }

    /** Human-readable name shown in GUIs and messages. */
    public String getDisplayName() {
        return displayName;
    }

    /** Bukkit permission node required to use this style. */
    public String getPermission() {
        return permission;
    }

    /**
     * Resolves a {@link TrailStyle} by its enum name (case-insensitive).
     *
     * @param name the raw name string (e.g. {@code "spiral"}, {@code "DOUBLE_HELIX"})
     * @return the matching {@link TrailStyle}, or {@code null} if not found
     */
    public static TrailStyle fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
