package com.epictrails.data.database;

import com.epictrails.EpicTrails;
import com.epictrails.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * MySQL database backend using HikariCP connection pooling.
 *
 * <p>All configuration is read from the plugin's {@code config.yml} via
 * {@link ConfigManager}.  The pool is sized according to the configured
 * {@code pool-size} and {@code min-idle} values.</p>
 */
public final class MySQLDatabase extends Database {

    private HikariDataSource dataSource;
    private final ConfigManager configManager;

    /**
     * @param plugin        the owning plugin instance
     * @param configManager the active {@link ConfigManager} (provides MySQL credentials)
     */
    public MySQLDatabase(EpicTrails plugin, ConfigManager configManager) {
        super(plugin);
        this.configManager = configManager;
    }

    @Override
    public void connect() throws SQLException {
        String host     = configManager.getMySQLHost();
        int    port     = configManager.getMySQLPort();
        String database = configManager.getMySQLDatabase();
        String username = configManager.getMySQLUsername();
        String password = configManager.getMySQLPassword();

        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EpicTrails-MySQL");
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(configManager.getMySQLPoolSize());
        cfg.setMinimumIdle(configManager.getMySQLMinIdle());
        cfg.setConnectionTimeout(configManager.getMySQLConnectionTimeout());
        cfg.setIdleTimeout(configManager.getMySQLIdleTimeout());
        cfg.setMaxLifetime(configManager.getMySQLMaxLifetime());

        // Performance properties
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");
        cfg.addDataSourceProperty("useLocalSessionState", "true");
        cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
        cfg.addDataSourceProperty("cacheResultSetMetadata", "true");
        cfg.addDataSourceProperty("cacheServerConfiguration", "true");
        cfg.addDataSourceProperty("elideSetAutoCommits", "true");
        cfg.addDataSourceProperty("maintainTimeStats", "false");

        // Overlay any custom properties from config
        Map<?, ?> customProps = (configManager.getConfig().isConfigurationSection("storage.mysql.properties"))
                ? configManager.getConfig().getConfigurationSection("storage.mysql.properties").getValues(false)
                : java.util.Collections.emptyMap();

        for (Map.Entry<?, ?> entry : customProps.entrySet()) {
            cfg.addDataSourceProperty(entry.getKey().toString(), entry.getValue().toString());
        }

        try {
            dataSource = new HikariDataSource(cfg);
        } catch (Exception e) {
            throw new SQLException("Failed to initialise MySQL HikariCP pool: " + e.getMessage(), e);
        }

        createTables();
        plugin.getLogger().info("Connected to MySQL database: " + host + ":" + port + "/" + database);
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("MySQL data source is not initialised.");
        }
        return dataSource.getConnection();
    }

    /**
     * MySQL {@code INSERT … ON DUPLICATE KEY UPDATE} upsert.
     * Binds: (1) uuid, (2) trail_style, (3) particle_key, (4) trail_enabled.
     */
    @Override
    protected String getUpsertSql() {
        return "INSERT INTO player_trails (uuid, trail_style, particle_key, trail_enabled) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "trail_style   = VALUES(trail_style), "
                + "particle_key  = VALUES(particle_key), "
                + "trail_enabled = VALUES(trail_enabled), "
                + "updated_at    = CURRENT_TIMESTAMP;";
    }
}
