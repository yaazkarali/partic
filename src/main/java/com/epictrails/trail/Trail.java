package com.epictrails.trail;

import org.bukkit.Color;

/**
 * Immutable value-object that describes a single configured particle entry
 * from {@code trails.yml}.  A {@code Trail} captures the display name, the
 * raw PacketEvents particle-type key, and optional DUST colour / size data.
 */
public final class Trail {

    private final String key;
    private final String displayName;
    private final String iconMaterial;
    private final String particleTypeName;
    private final boolean dustParticle;
    private final Color dustColor;
    private final float dustSize;

    /**
     * Constructs a non-dust trail entry.
     *
     * @param key              YAML key (e.g. {@code "FLAME"})
     * @param displayName      MiniMessage/legacy display name
     * @param iconMaterial     Bukkit {@link org.bukkit.Material} name for the GUI icon
     * @param particleTypeName PacketEvents {@code ParticleTypes} field name
     */
    public Trail(String key, String displayName, String iconMaterial, String particleTypeName) {
        this.key = key;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.particleTypeName = particleTypeName;
        this.dustParticle = false;
        this.dustColor = null;
        this.dustSize = 1.0f;
    }

    /**
     * Constructs a dust trail entry (type {@code DUST}) with explicit colour and size.
     *
     * @param key              YAML key
     * @param displayName      MiniMessage/legacy display name
     * @param iconMaterial     Bukkit {@link org.bukkit.Material} name for the GUI icon
     * @param dustColor        {@link Color} of the dust particle
     * @param dustSize         Size scalar of the dust particle
     */
    public Trail(String key, String displayName, String iconMaterial, Color dustColor, float dustSize) {
        this.key = key;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.particleTypeName = "DUST";
        this.dustParticle = true;
        this.dustColor = dustColor;
        this.dustSize = dustSize;
    }

    /** YAML configuration key. */
    public String getKey() {
        return key;
    }

    /** MiniMessage-formatted display name for GUIs. */
    public String getDisplayName() {
        return displayName;
    }

    /** Bukkit Material name for the GUI icon item. */
    public String getIconMaterial() {
        return iconMaterial;
    }

    /**
     * The PacketEvents {@code ParticleTypes} field name that this trail uses.
     * Always {@code "DUST"} for dust trails.
     */
    public String getParticleTypeName() {
        return particleTypeName;
    }

    /** {@code true} if this trail uses the DUST particle type (has colour data). */
    public boolean isDustParticle() {
        return dustParticle;
    }

    /**
     * The dust particle colour.  Only meaningful when {@link #isDustParticle()} is {@code true}.
     */
    public Color getDustColor() {
        return dustColor;
    }

    /**
     * The dust particle size scalar.  Only meaningful when {@link #isDustParticle()} is {@code true}.
     */
    public float getDustSize() {
        return dustSize;
    }

    @Override
    public String toString() {
        return "Trail{key='" + key + "', particle='" + particleTypeName + "'}";
    }
}
