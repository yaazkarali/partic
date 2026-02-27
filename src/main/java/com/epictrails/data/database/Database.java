package com.epictrails.data.database;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Abstract base class for all EpicTrails database backends.
 *
 * <p>Provides a shared single-threaded {@link ExecutorService} for off-main-thread
 * database operations, a template for schema initialisation, and common CRUD
 * operations that concrete implementations can reuse.  Subclasses only need to
 * provide a live {@link Connection} by implementing {@link #getConnection()}.</p>
 *
 * <p><b>Schema</b> (created on first boot):</p>
 * <pre>
 * player_trails (uuid PK, trail_style, particle_key, trail_enabled)
 * </pre>
 */
public abstract class Database {

    protected final EpicTrails plugin;

    /** Dedicated off-main-thread executor for all DB work. */
    protected final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EpicTrails-DB");
        t.setDaemon(true);
        return t;
    });

    protected Database(EpicTrails plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Opens the underlying connection (or pool) and creates the schema if needed.
     *
     * @throws SQLException if a connection or DDL error occurs
     */
    public abstract void connect() throws SQLException;

    /**
     * Closes the underlying connection (or pool).  Must be safe to call even if
     * {@link #connect()} was never called or threw.
     */
    public abstract void disconnect();

    /**
     * Returns a live {@link Connection} from the underlying pool or driver.
     * Callers are responsible for closing the connection (or returning it to the pool).
     *
     * @return an open {@link Connection}
     * @throws SQLException if a connection cannot be obtained
     */
    protected abstract Connection getConnection() throws SQLException;

    // -------------------------------------------------------------------------
    // Schema
    // -------------------------------------------------------------------------

    /**
     * Creates the {@code player_trails} table if it does not already exist.
     * Called from {@link #connect()} by each subclass.
     *
     * @throws SQLException if DDL execution fails
     */
    protected void createTables() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS player_trails ("
                + "uuid         VARCHAR(36)  NOT NULL PRIMARY KEY,"
                + "trail_style  VARCHAR(50)  NOT NULL DEFAULT 'SPIRAL',"
                + "particle_key VARCHAR(100) NOT NULL DEFAULT 'FLAME',"
                + "trail_enabled TINYINT(1)  NOT NULL DEFAULT 1,"
                + "created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        }
    }

    // -------------------------------------------------------------------------
    // CRUD – async public API
    // -------------------------------------------------------------------------

    /**
     * Loads a player's persisted data asynchronously.
     *
     * @param uuid the player's UUID
     * @return a future that completes with the stored {@link PlayerData}, or
     *         {@code null} if no record exists
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> loadPlayerSync(uuid), executor);
    }

    /**
     * Persists a player's current data asynchronously (upsert).
     *
     * @param data the {@link PlayerData} to persist
     * @return a future that completes when the upsert finishes
     */
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        return CompletableFuture.runAsync(() -> savePlayerSync(data), executor);
    }

    /**
     * Deletes all stored data for a player asynchronously.
     *
     * @param uuid the player's UUID
     * @return a future that completes when deletion finishes
     */
    public CompletableFuture<Void> deletePlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> deletePlayerSync(uuid), executor);
    }

    // -------------------------------------------------------------------------
    // CRUD – synchronous internals (run on executor thread)
    // -------------------------------------------------------------------------

    private PlayerData loadPlayerSync(UUID uuid) {
        String sql = "SELECT trail_style, particle_key, trail_enabled "
                + "FROM player_trails WHERE uuid = ?;";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String styleRaw = rs.getString("trail_style");
                    String particleKey = rs.getString("particle_key");
                    boolean enabled = rs.getInt("trail_enabled") == 1;

                    com.epictrails.trail.TrailStyle style =
                            com.epictrails.trail.TrailStyle.fromString(styleRaw);
                    if (style == null) style = plugin.getConfigManager().getDefaultStyle();

                    PlayerData pd = new PlayerData(uuid, style, particleKey);
                    pd.setTrailEnabled(enabled);
                    return pd;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
        }
        return null;
    }

    private void savePlayerSync(PlayerData data) {
        String sql = getUpsertSql();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getTrailStyle().name());
            ps.setString(3, data.getParticleKey());
            ps.setInt(4, data.isTrailEnabled() ? 1 : 0);
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + data.getUuid(), e);
        }
    }

    private void deletePlayerSync(UUID uuid) {
        String sql = "DELETE FROM player_trails WHERE uuid = ?;";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete player data for " + uuid, e);
        }
    }

    /**
     * Returns the database-specific UPSERT SQL.  Overridden by each subclass to use
     * the correct syntax ({@code INSERT OR REPLACE} vs {@code INSERT … ON DUPLICATE KEY UPDATE}).
     */
    protected abstract String getUpsertSql();

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    /**
     * Shuts down the background executor, waiting up to 5 s for pending tasks.
     */
    public void shutdownExecutor() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
