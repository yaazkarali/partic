package com.epictrails.config;

import com.epictrails.EpicTrails;
import com.epictrails.trail.Trail;
import com.epictrails.trail.TrailStyle;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages loading and hot-reloading of all YAML configuration files:
 * {@code config.yml}, {@code messages.yml}, {@code trails.yml}, and {@code guis.yml}.
 *
 * <p>On first boot the bundled resource files are copied to the plugin data folder.
 * The singleton instance is accessible via {@link EpicTrails#getConfigManager()}.</p>
 */
public final class ConfigManager {

    private final EpicTrails plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration trailsConfig;
    private FileConfiguration guisConfig;

    // Parsed trail registry: key → Trail
    private final Map<String, Trail> trailRegistry = new LinkedHashMap<>();

    public ConfigManager(EpicTrails plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) all configuration files from the plugin data folder,
     * copying default resources when files are missing.
     */
    public void load() {
        saveDefaultIfAbsent("config.yml");
        saveDefaultIfAbsent("messages.yml");
        saveDefaultIfAbsent("trails.yml");
        saveDefaultIfAbsent("guis.yml");

        plugin.reloadConfig();
        config = plugin.getConfig();

        messages = loadYaml("messages.yml");
        trailsConfig = loadYaml("trails.yml");
        guisConfig = loadYaml("guis.yml");

        parseTrails();
    }

    // -------------------------------------------------------------------------
    // Config accessors
    // -------------------------------------------------------------------------

    /** Returns the main {@code config.yml} configuration. */
    public FileConfiguration getConfig() {
        return config;
    }

    /** Returns the loaded {@code messages.yml} configuration. */
    public FileConfiguration getMessages() {
        return messages;
    }

    /** Returns the loaded {@code trails.yml} configuration. */
    public FileConfiguration getTrailsConfig() {
        return trailsConfig;
    }

    /** Returns the loaded {@code guis.yml} configuration. */
    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    // -------------------------------------------------------------------------
    // Typed config getters
    // -------------------------------------------------------------------------

    /** Storage backend: {@code "sqlite"} or {@code "mysql"}. */
    public String getStorageType() {
        return config.getString("storage.type", "sqlite").toLowerCase();
    }

    /** SQLite database file name relative to the plugin data folder. */
    public String getSQLiteFile() {
        return config.getString("storage.sqlite.file", "trails.db");
    }

    /** MySQL host. */
    public String getMySQLHost() {
        return config.getString("storage.mysql.host", "localhost");
    }

    /** MySQL port. */
    public int getMySQLPort() {
        return config.getInt("storage.mysql.port", 3306);
    }

    /** MySQL database name. */
    public String getMySQLDatabase() {
        return config.getString("storage.mysql.database", "epictrails");
    }

    /** MySQL username. */
    public String getMySQLUsername() {
        return config.getString("storage.mysql.username", "root");
    }

    /** MySQL password. */
    public String getMySQLPassword() {
        return config.getString("storage.mysql.password", "");
    }

    /** HikariCP maximum pool size for MySQL. */
    public int getMySQLPoolSize() {
        return config.getInt("storage.mysql.pool-size", 10);
    }

    /** HikariCP minimum idle connections for MySQL. */
    public int getMySQLMinIdle() {
        return config.getInt("storage.mysql.min-idle", 2);
    }

    /** HikariCP connection timeout (ms) for MySQL. */
    public long getMySQLConnectionTimeout() {
        return config.getLong("storage.mysql.connection-timeout", 30000L);
    }

    /** HikariCP idle timeout (ms) for MySQL. */
    public long getMySQLIdleTimeout() {
        return config.getLong("storage.mysql.idle-timeout", 600000L);
    }

    /** HikariCP max lifetime (ms) for MySQL. */
    public long getMySQLMaxLifetime() {
        return config.getLong("storage.mysql.max-lifetime", 1800000L);
    }

    /** Tick interval between particle spawns. */
    public int getTickInterval() {
        return Math.max(1, config.getInt("trails.tick-interval", 2));
    }

    /** Maximum squared view distance (squared to avoid sqrt). */
    public double getViewDistanceSquared() {
        int dist = config.getInt("trails.view-distance", 48);
        return (double) dist * dist;
    }

    /** Whether a player can see their own trail. */
    public boolean isShowToSelf() {
        return config.getBoolean("trails.show-to-self", true);
    }

    /** Whether trails are hidden in PvP zones. */
    public boolean isDisableInPvp() {
        return config.getBoolean("trails.disable-in-pvp", false);
    }

    /** Whether trails are hidden for vanished players. */
    public boolean isDisableForVanished() {
        return config.getBoolean("trails.disable-for-vanished", true);
    }

    /** Whether trails are hidden during combat. */
    public boolean isDisableInCombat() {
        return config.getBoolean("trails.disable-in-combat", false);
    }

    /** Duration in seconds of a combat tag. */
    public int getCombatTagSeconds() {
        return config.getInt("trails.combat-tag-seconds", 10);
    }

    /** Whether style permissions are enforced. */
    public boolean isUseStylePermissions() {
        return config.getBoolean("permissions.use-style-permissions", true);
    }

    /** Whether particle permissions are enforced. */
    public boolean isUseParticlePermissions() {
        return config.getBoolean("permissions.use-particle-permissions", false);
    }

    /** Default trail style key for new players. */
    public TrailStyle getDefaultStyle() {
        String raw = config.getString("permissions.default-style", "SPIRAL");
        TrailStyle ts = TrailStyle.fromString(raw);
        return ts != null ? ts : TrailStyle.SPIRAL;
    }

    /** Default particle key for new players. */
    public String getDefaultParticle() {
        return config.getString("permissions.default-particle", "FLAME");
    }

    // -------------------------------------------------------------------------
    // Message accessors
    // -------------------------------------------------------------------------

    /**
     * Retrieves a raw message string from {@code messages.yml}.
     * Falls back to the key itself if not found.
     *
     * @param key the YAML path
     * @return raw message string (may include MiniMessage / legacy codes)
     */
    public String getMessage(String key) {
        String prefix = messages.getString("prefix", "");
        String msg = messages.getString(key, "<red>" + key);
        return prefix + msg;
    }

    /**
     * Retrieves a raw message string from {@code messages.yml} without the prefix.
     *
     * @param key the YAML path
     * @return raw message string
     */
    public String getMessageNoPrefix(String key) {
        return messages.getString(key, "<red>" + key);
    }

    // -------------------------------------------------------------------------
    // Trail registry
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of all registered {@link Trail} objects. */
    public Map<String, Trail> getTrailRegistry() {
        return Collections.unmodifiableMap(trailRegistry);
    }

    /**
     * Looks up a {@link Trail} by its YAML key (case-insensitive).
     *
     * @param key particle key
     * @return the matching {@link Trail}, or {@code null} if not found
     */
    public Trail getTrail(String key) {
        if (key == null) return null;
        return trailRegistry.get(key.toUpperCase());
    }

    /** Returns an ordered list of all trail keys. */
    public List<String> getTrailKeys() {
        return new ArrayList<>(trailRegistry.keySet());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Copies the bundled resource to the data folder if the file doesn't exist. */
    private void saveDefaultIfAbsent(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    /** Loads a YAML file from the plugin data folder, merging defaults from the jar. */
    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        // Merge defaults from the bundled resource
        InputStream defaultStream = plugin.getResource(name);
        if (defaultStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                yaml.setDefaults(defaults);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load defaults for " + name, e);
            }
        }
        return yaml;
    }

    /** Parses the {@code particles} section of {@code trails.yml} into the trail registry. */
    private void parseTrails() {
        trailRegistry.clear();
        if (trailsConfig == null || !trailsConfig.isConfigurationSection("particles")) {
            plugin.getLogger().warning("trails.yml is missing the 'particles' section.");
            return;
        }

        for (String key : trailsConfig.getConfigurationSection("particles").getKeys(false)) {
            String path = "particles." + key;
            String displayName = trailsConfig.getString(path + ".display-name", key);
            String icon = trailsConfig.getString(path + ".icon", "PAPER");
            String type = trailsConfig.getString(path + ".type", key);

            Trail trail;
            if ("DUST".equalsIgnoreCase(type) && trailsConfig.contains(path + ".color")) {
                String colorStr = trailsConfig.getString(path + ".color", "255,255,255");
                float size = (float) trailsConfig.getDouble(path + ".size", 1.5);
                Color color = parseColor(colorStr);
                trail = new Trail(key.toUpperCase(), displayName, icon, color, size);
            } else {
                trail = new Trail(key.toUpperCase(), displayName, icon, type);
            }

            trailRegistry.put(key.toUpperCase(), trail);
        }

        plugin.getLogger().info("Loaded " + trailRegistry.size() + " particle trail(s).");
    }

    /**
     * Parses a comma-separated {@code "R,G,B"} string into a Bukkit {@link Color}.
     * Defaults to white if parsing fails.
     */
    private Color parseColor(String colorStr) {
        try {
            String[] parts = colorStr.split(",");
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return Color.fromRGB(
                    Math.max(0, Math.min(255, r)),
                    Math.max(0, Math.min(255, g)),
                    Math.max(0, Math.min(255, b))
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse color '" + colorStr + "', defaulting to white.");
            return Color.WHITE;
        }
    }
}
