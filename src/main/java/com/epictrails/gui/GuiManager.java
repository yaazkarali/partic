package com.epictrails.gui;

import com.epictrails.EpicTrails;
import com.epictrails.data.PlayerData;
import com.epictrails.trail.Trail;
import com.epictrails.trail.TrailStyle;
import com.epictrails.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all EpicTrails GUI inventories (main selector, style browser, particle browser).
 *
 * <p>Each open GUI is tracked so that click events can be attributed to the correct
 * player and page.  This class is registered as a Bukkit {@link Listener} for
 * {@link InventoryClickEvent} and {@link InventoryCloseEvent}.</p>
 */
public final class GuiManager implements Listener {

    private final EpicTrails plugin;

    /** Maps player UUID → the type of GUI they have open. */
    private final Map<UUID, GuiType> openGuis = new ConcurrentHashMap<>();

    /** Maps player UUID → current page number (0-indexed) in paged GUIs. */
    private final Map<UUID, Integer> currentPage = new ConcurrentHashMap<>();

    /** Number of item slots per page in paged GUIs (rows 1-5 = 45 slots, minus 9 for nav row). */
    private static final int PAGE_SIZE = 45;

    private enum GuiType { MAIN, STYLE, PARTICLE }

    public GuiManager(EpicTrails plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Open GUI methods
    // -------------------------------------------------------------------------

    /** Opens the main trail selector GUI for {@code player}. */
    public void openMain(Player player) {
        Inventory inv = buildMainGui(player);
        openGuis.put(player.getUniqueId(), GuiType.MAIN);
        player.openInventory(inv);
    }

    /** Opens the trail style selection GUI for {@code player}. */
    public void openStyleGui(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        Inventory inv = buildStyleGui(player, 0);
        openGuis.put(player.getUniqueId(), GuiType.STYLE);
        player.openInventory(inv);
    }

    /** Opens the particle type selection GUI for {@code player}. */
    public void openParticleGui(Player player) {
        currentPage.put(player.getUniqueId(), 0);
        Inventory inv = buildParticleGui(player, 0);
        openGuis.put(player.getUniqueId(), GuiType.PARTICLE);
        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // GUI builders
    // -------------------------------------------------------------------------

    private Inventory buildMainGui(Player player) {
        String titleRaw = plugin.getConfigManager().getMessageNoPrefix("gui-title");
        Component title = ColorUtil.parse(titleRaw);
        int rows = plugin.getConfigManager().getConfig().getInt("gui.selector-rows", 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        boolean fillEmpty = plugin.getConfigManager().getConfig()
                .getBoolean("gui.fill-empty-slots", true);
        if (fillEmpty) {
            String matName = plugin.getConfigManager().getConfig()
                    .getString("gui.fill-material", "GRAY_STAINED_GLASS_PANE");
            Material filler = parseMaterial(matName, Material.GRAY_STAINED_GLASS_PANE);
            ItemStack pane = buildItem(filler, " ", List.of());
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        }

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());

        // Style selector button (slot 20)
        inv.setItem(20, buildItem(Material.BLAZE_ROD,
                plugin.getConfigManager().getMessageNoPrefix("gui-style-title"),
                Arrays.asList("<gray>Browse and select your", "<gray>preferred trail style.", "",
                        "<yellow>Click to open!")));

        // Particle selector button (slot 24)
        inv.setItem(24, buildItem(Material.NETHER_STAR,
                plugin.getConfigManager().getMessageNoPrefix("gui-particle-title"),
                Arrays.asList("<gray>Choose the particle that", "<gray>makes up your trail.", "",
                        "<yellow>Click to open!")));

        // Toggle button (slot 40)
        boolean enabled = data != null && data.isTrailEnabled();
        Material toggleMat = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String toggleName = plugin.getConfigManager().getMessageNoPrefix(
                enabled ? "gui-toggle-on" : "gui-toggle-off");
        List<String> toggleLore = enabled
                ? Arrays.asList("<gray>Your trail is currently <green>enabled</green>.", "", "<yellow>Click to disable.")
                : Arrays.asList("<gray>Your trail is currently <red>disabled</red>.", "", "<yellow>Click to enable.");
        inv.setItem(40, buildItem(toggleMat, toggleName, toggleLore));

        // Current settings display (slot 13)
        String styleName = data != null ? data.getTrailStyle().getDisplayName() : "SPIRAL";
        String particleName = data != null ? data.getParticleKey() : "FLAME";
        inv.setItem(13, buildItem(Material.PAPER,
                "<gradient:#ff6b35:#ffd700>Current Settings</gradient>",
                Arrays.asList(
                        plugin.getConfigManager().getMessageNoPrefix("gui-current-style")
                                .replace("<style>", styleName),
                        plugin.getConfigManager().getMessageNoPrefix("gui-current-particle")
                                .replace("<particle>", particleName)
                )));

        // Close button (slot 49)
        inv.setItem(49, buildItem(Material.BARRIER,
                plugin.getConfigManager().getMessageNoPrefix("gui-back"),
                List.of("<gray>Close this menu.")));

        return inv;
    }

    private Inventory buildStyleGui(Player player, int page) {
        String titleRaw = plugin.getConfigManager().getMessageNoPrefix("gui-style-title");
        Component title = ColorUtil.parse(titleRaw);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        fillBorder(inv);

        TrailStyle[] styles = TrailStyle.values();
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, styles.length);

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        TrailStyle current = data != null ? data.getTrailStyle() : TrailStyle.SPIRAL;

        int slot = 0;
        for (int i = start; i < end; i++) {
            TrailStyle style = styles[i];
            boolean selected    = style == current;
            boolean hasPermission = !plugin.getConfigManager().isUseStylePermissions()
                    || player.hasPermission(style.getPermission());

            String name = style.getDisplayName();
            List<String> lore = new ArrayList<>();
            if (!hasPermission) {
                name = "<red>✘ " + style.getDisplayName();
                lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-no-permission"));
            } else if (selected) {
                lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-selected"));
            } else {
                lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-click-select"));
            }

            Material icon = resolveStyleIcon(style);
            inv.setItem(slot++, buildItem(icon, name, lore));
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-prev-page"), List.of()));
        }
        if (end < styles.length) {
            inv.setItem(53, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-next-page"), List.of()));
        }
        inv.setItem(49, buildItem(Material.BARRIER,
                plugin.getConfigManager().getMessageNoPrefix("gui-back"), List.of()));

        return inv;
    }

    private Inventory buildParticleGui(Player player, int page) {
        String titleRaw = plugin.getConfigManager().getMessageNoPrefix("gui-particle-title");
        Component title = ColorUtil.parse(titleRaw);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        fillBorder(inv);

        List<String> keys = plugin.getConfigManager().getTrailKeys();
        Map<String, Trail> registry = plugin.getConfigManager().getTrailRegistry();
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, keys.size());

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        String currentKey = data != null ? data.getParticleKey() : "FLAME";

        int slot = 0;
        for (int i = start; i < end; i++) {
            String key   = keys.get(i);
            Trail  trail = registry.get(key);
            if (trail == null) continue;

            boolean selected = key.equalsIgnoreCase(currentKey);

            String name = trail.getDisplayName();
            List<String> lore = new ArrayList<>();
            if (selected) {
                lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-selected"));
            } else {
                lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-click-select"));
            }

            Material icon = parseMaterial(trail.getIconMaterial(), Material.PAPER);
            inv.setItem(slot++, buildItem(icon, name, lore));
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-prev-page"), List.of()));
        }
        if (end < keys.size()) {
            inv.setItem(53, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-next-page"), List.of()));
        }
        inv.setItem(49, buildItem(Material.BARRIER,
                plugin.getConfigManager().getMessageNoPrefix("gui-back"), List.of()));

        return inv;
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        GuiType type = openGuis.get(uuid);
        if (type == null) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        switch (type) {
            case MAIN -> handleMainClick(player, slot);
            case STYLE -> handleStyleClick(player, slot, clicked);
            case PARTICLE -> handleParticleClick(player, slot, clicked);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openGuis.remove(player.getUniqueId());
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 20 -> openStyleGui(player);
            case 24 -> openParticleGui(player);
            case 40 -> {
                PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
                if (data != null) {
                    data.setTrailEnabled(!data.isTrailEnabled());
                    plugin.getDatabaseManager().getDatabase().savePlayer(data);
                    player.closeInventory();
                    String msg = data.isTrailEnabled()
                            ? plugin.getConfigManager().getMessage("trail-enabled")
                            : plugin.getConfigManager().getMessage("trail-disabled");
                    player.sendMessage(ColorUtil.parse(msg));
                }
            }
            case 49 -> player.closeInventory();
        }
    }

    private void handleStyleClick(Player player, int slot, ItemStack clicked) {
        // Navigation slots
        if (slot == 45) {
            int page = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (page > 0) {
                currentPage.put(player.getUniqueId(), page - 1);
                player.openInventory(buildStyleGui(player, page - 1));
            }
            return;
        }
        if (slot == 53) {
            int page = currentPage.getOrDefault(player.getUniqueId(), 0);
            currentPage.put(player.getUniqueId(), page + 1);
            player.openInventory(buildStyleGui(player, page + 1));
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }

        // Style slot: map slot index back to TrailStyle
        int page  = currentPage.getOrDefault(player.getUniqueId(), 0);
        int index = page * PAGE_SIZE + slot;
        TrailStyle[] styles = TrailStyle.values();
        if (index < 0 || index >= styles.length) return;

        TrailStyle chosen = styles[index];
        if (plugin.getConfigManager().isUseStylePermissions()
                && !player.hasPermission(chosen.getPermission())) {
            player.sendMessage(ColorUtil.parse(
                    plugin.getConfigManager().getMessage("style-no-permission")
                            .replace("<style>", chosen.getDisplayName())));
            return;
        }

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data != null) {
            data.setTrailStyle(chosen);
            plugin.getDatabaseManager().getDatabase().savePlayer(data);
        }
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("style-changed")
                        .replace("<style>", chosen.getDisplayName())));
        openStyleGui(player);
    }

    private void handleParticleClick(Player player, int slot, ItemStack clicked) {
        if (slot == 45) {
            int page = currentPage.getOrDefault(player.getUniqueId(), 0);
            if (page > 0) {
                currentPage.put(player.getUniqueId(), page - 1);
                player.openInventory(buildParticleGui(player, page - 1));
            }
            return;
        }
        if (slot == 53) {
            int page = currentPage.getOrDefault(player.getUniqueId(), 0);
            currentPage.put(player.getUniqueId(), page + 1);
            player.openInventory(buildParticleGui(player, page + 1));
            return;
        }
        if (slot == 49) {
            openMain(player);
            return;
        }

        int page = currentPage.getOrDefault(player.getUniqueId(), 0);
        int index = page * PAGE_SIZE + slot;
        List<String> keys = plugin.getConfigManager().getTrailKeys();
        if (index < 0 || index >= keys.size()) return;

        String key = keys.get(index);
        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        if (data != null) {
            data.setParticleKey(key);
            plugin.getDatabaseManager().getDatabase().savePlayer(data);
        }
        player.sendMessage(ColorUtil.parse(
                plugin.getConfigManager().getMessage("particle-changed")
                        .replace("<particle>", key)));
        openParticleGui(player);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack buildItem(Material material, String rawName, List<String> rawLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.parse(rawName));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : rawLore) {
                loreComponents.add(ColorUtil.parse(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack pane = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private Material resolveStyleIcon(TrailStyle style) {
        String path = "styles." + style.name() + ".icon";
        String mat  = plugin.getConfigManager().getTrailsConfig().getString(path, "PAPER");
        return parseMaterial(mat, Material.PAPER);
    }
}
