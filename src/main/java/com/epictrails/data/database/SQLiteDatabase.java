package com.epictrails.data.database;

import com.epictrails.EpicTrails;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * SQLite database backend.
 *
 * <p>Uses HikariCP with a single connection (SQLite does not support concurrent
 * writes).  The database file is created in the plugin's data folder.</p>
 */
public final class SQLiteDatabase extends Database {

    private HikariDataSource dataSource;
    private final File databaseFile;

    /**
     * @param plugin       the owning plugin instance
     * @param databaseFile path to the {@code .db} file (may not exist yet)
     */
    public SQLiteDatabase(EpicTrails plugin, File databaseFile) {
        super(plugin);
        this.databaseFile = databaseFile;
    }

    @Override
    public void connect() throws SQLException {
        // Ensure the parent directory exists
        if (!databaseFile.getParentFile().exists()) {
            databaseFile.getParentFile().mkdirs();
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EpicTrails-SQLite");
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        cfg.setMaximumPoolSize(1);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30000L);
        // SQLite performance pragmas
        cfg.addDataSourceProperty("journal_mode", "WAL");
        cfg.addDataSourceProperty("synchronous", "NORMAL");
        cfg.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");

        try {
            dataSource = new HikariDataSource(cfg);
        } catch (Exception e) {
            throw new SQLException("Failed to initialise SQLite HikariCP pool: " + e.getMessage(), e);
        }

        createTables();
        plugin.getLogger().info("Connected to SQLite database: " + databaseFile.getName());
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("SQLite connection pool closed.");
        }
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("SQLite data source is not initialised.");
        }
        return dataSource.getConnection();
    }

    /**
     * SQLite 3.24+ UPSERT syntax: inserts or updates on primary-key conflict.
     * Binds: (1) uuid, (2) trail_style, (3) particle_key, (4) trail_enabled.
     */
    @Override
    protected String getUpsertSql() {
        return "INSERT INTO player_trails (uuid, trail_style, particle_key, trail_enabled) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET "
                + "trail_style   = excluded.trail_style, "
                + "particle_key  = excluded.particle_key, "
                + "trail_enabled = excluded.trail_enabled, "
                + "updated_at    = CURRENT_TIMESTAMP;";
    }
}
