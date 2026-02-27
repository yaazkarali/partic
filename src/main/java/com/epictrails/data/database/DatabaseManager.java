package com.epictrails.data.database;

import com.epictrails.EpicTrails;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Selects, initialises, and manages the lifecycle of the configured database backend.
 *
 * <p>On {@link #enable()} the manager reads the {@code storage.type} value from
 * {@code config.yml} and instantiates either a {@link SQLiteDatabase} or a
 * {@link MySQLDatabase}.  On {@link #disable()} the active backend is cleanly
 * shut down.</p>
 */
public final class DatabaseManager {

    private final EpicTrails plugin;
    private Database database;

    /**
     * @param plugin the owning plugin instance
     */
    public DatabaseManager(EpicTrails plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialises the configured database backend.
     *
     * <p>Exits the server if the connection fails, because the plugin cannot
     * function without persistent storage.</p>
     */
    public void enable() {
        String type = plugin.getConfigManager().getStorageType();

        switch (type) {
            case "mysql" -> {
                plugin.getLogger().info("Using MySQL database backend.");
                database = new MySQLDatabase(plugin, plugin.getConfigManager());
            }
            default -> {
                if (!"sqlite".equals(type)) {
                    plugin.getLogger().warning("Unknown storage type '" + type + "'. Defaulting to SQLite.");
                }
                plugin.getLogger().info("Using SQLite database backend.");
                String fileName = plugin.getConfigManager().getSQLiteFile();
                File dbFile = new File(plugin.getDataFolder(), fileName);
                database = new SQLiteDatabase(plugin, dbFile);
            }
        }

        try {
            database.connect();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to connect to the database. The plugin will disable itself.", e);
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    /**
     * Shuts down the active database backend gracefully.
     * Pending async tasks on the executor are given up to 5 seconds to complete.
     */
    public void disable() {
        if (database != null) {
            database.shutdownExecutor();
            database.disconnect();
        }
    }

    /**
     * Returns the active {@link Database} instance.
     *
     * @return the active database, or {@code null} if {@link #enable()} has not been called
     */
    public Database getDatabase() {
        return database;
    }
}
