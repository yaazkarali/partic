package com.epictrails;

import com.epictrails.command.TrailCommand;
import com.epictrails.config.ConfigManager;
import com.epictrails.data.PlayerData;
import com.epictrails.data.database.DatabaseManager;
import com.epictrails.gui.GuiManager;
import com.epictrails.listener.PlayerListener;
import com.epictrails.listener.TrailListener;
import com.epictrails.packet.ParticlePacketSender;
import com.epictrails.trail.TrailManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * EpicTrails – Production-ready cosmetic trails plugin for Paper 1.21.1.
 *
 * <p><b>Subsystem overview:</b></p>
 * <ul>
 *   <li>{@link ConfigManager} – loads/reloads all YAML configurations</li>
 *   <li>{@link DatabaseManager} – manages SQLite or MySQL persistence</li>
 *   <li>{@link ParticlePacketSender} – PacketEvents-based particle dispatch</li>
 *   <li>{@link TrailManager} – scheduled trail rendering across all trail styles</li>
 *   <li>{@link GuiManager} – in-game chest GUI selectors</li>
 *   <li>{@link PlayerListener} / {@link TrailListener} – event integrations</li>
 * </ul>
 *
 * <p>PacketEvents is bootstrapped in {@link #onLoad()} as required by the library's
 * lifecycle contract.  All subsystems are initialised in {@link #onEnable()} in
 * strict dependency order, and torn down safely in {@link #onDisable()}.</p>
 */
public final class EpicTrails extends JavaPlugin {

    private static EpicTrails instance;

    /** In-memory store of per-player data, keyed by UUID. */
    private final ConcurrentMap<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();

    // Subsystems
    private ConfigManager         configManager;
    private DatabaseManager       databaseManager;
    private ParticlePacketSender  particlePacketSender;
    private TrailManager          trailManager;
    private GuiManager            guiManager;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onLoad() {
        // PacketEvents MUST be bootstrapped during onLoad before any other
        // plugin interacts with the Minecraft protocol layer.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
        getLogger().info("PacketEvents bootstrapped successfully.");
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. Load all YAML configurations
        configManager = new ConfigManager(this);
        configManager.load();

        // 2. Initialise database (may disable plugin on failure)
        databaseManager = new DatabaseManager(this);
        databaseManager.enable();
        if (!isEnabled()) return; // DB init failed

        // 3. Initialise PacketEvents API (terminal init after load-phase)
        PacketEvents.getAPI().init();

        // 4. Create shared packet sender
        particlePacketSender = new ParticlePacketSender(this);

        // 5. Start trail rendering scheduler
        trailManager = new TrailManager(this);
        trailManager.start();

        // 6. Build GUI manager and register its listener
        guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        // 7. Register player & trail listeners
        PlayerListener playerListener = new PlayerListener(this);
        TrailListener  trailListener  = new TrailListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(trailListener, this);

        // 8. Register the /trail command
        PluginCommand trailCmd = getCommand("trail");
        if (trailCmd != null) {
            TrailCommand executor = new TrailCommand(this);
            trailCmd.setExecutor(executor);
            trailCmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Could not find 'trail' command in plugin.yml – check configuration!");
        }

        getLogger().info("EpicTrails v" + getDescription().getVersion() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Stop trail task first to prevent concurrent map access during shutdown
        if (trailManager != null) {
            trailManager.stop();
        }

        // Save all online player data synchronously before shutting down the DB
        for (PlayerData data : playerDataMap.values()) {
            if (databaseManager != null && databaseManager.getDatabase() != null) {
                databaseManager.getDatabase().savePlayer(data);
            }
        }
        playerDataMap.clear();

        // Shut down database (waits for pending async tasks)
        if (databaseManager != null) {
            databaseManager.disable();
        }

        // Terminate PacketEvents
        PacketEvents.getAPI().terminate();

        getLogger().info("EpicTrails disabled.");
    }

    // -------------------------------------------------------------------------
    // Subsystem accessors
    // -------------------------------------------------------------------------

    /** Returns the plugin singleton. */
    public static EpicTrails getInstance() {
        return instance;
    }

    /** Returns the configuration manager. */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /** Returns the database manager. */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /** Returns the PacketEvents-based particle packet sender. */
    public ParticlePacketSender getParticlePacketSender() {
        return particlePacketSender;
    }

    /** Returns the trail rendering manager. */
    public TrailManager getTrailManager() {
        return trailManager;
    }

    /** Returns the GUI manager. */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Returns the live in-memory player-data map.
     *
     * <p>This map is populated on player join and cleared on player quit.  It is
     * safe for concurrent read/write from both the main thread and the database
     * executor thread (via {@link ConcurrentHashMap}).</p>
     */
    public ConcurrentMap<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }
}
