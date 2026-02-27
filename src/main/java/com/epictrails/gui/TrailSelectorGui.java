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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the inventory for the pageable trail selector popup.
 *
 * <p>This class is a focused view builder used by {@link GuiManager} to render the
 * combined style+particle selection screen. It is separate from {@link GuiManager}
 * to keep each class under a manageable size and responsibility scope.</p>
 *
 * <p>The inventory is a 6-row (54-slot) chest whose first 45 slots display trail
 * style or particle items, and the bottom row holds navigation controls.</p>
 */
public final class TrailSelectorGui {

    private final EpicTrails plugin;

    /** Number of item cells available per page (54 slots − 9 nav slots). */
    public static final int PAGE_SIZE = 45;

    public TrailSelectorGui(EpicTrails plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Style page builder
    // -------------------------------------------------------------------------

    /**
     * Builds a page of the style-selector inventory.
     *
     * @param player the viewing player (used to check permissions and current selection)
     * @param page   0-indexed page number
     * @return the constructed {@link Inventory}
     */
    public Inventory buildStylePage(Player player, int page) {
        Component title = ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("gui-style-title"));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        TrailStyle currentStyle = data != null ? data.getTrailStyle() : TrailStyle.SPIRAL;

        TrailStyle[] styles = TrailStyle.values();
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, styles.length);

        for (int i = start; i < end; i++) {
            TrailStyle style    = styles[i];
            int        slot     = i - start;
            boolean    selected = style == currentStyle;
            boolean    hasPerm  = !plugin.getConfigManager().isUseStylePermissions()
                    || player.hasPermission(style.getPermission());

            String iconPath = "styles." + style.name() + ".icon";
            String matName  = plugin.getConfigManager().getTrailsConfig().getString(iconPath, "PAPER");
            Material icon   = safeMaterial(matName, Material.PAPER);

            String displayName = hasPerm
                    ? style.getDisplayName()
                    : "<red>✘ " + style.getDisplayName();

            List<String> lore = buildStyleLore(style, selected, hasPerm);
            inv.setItem(slot, buildItem(icon, displayName, lore));
        }

        addNavRow(inv, page, end < styles.length);
        return inv;
    }

    // -------------------------------------------------------------------------
    // Particle page builder
    // -------------------------------------------------------------------------

    /**
     * Builds a page of the particle-selector inventory.
     *
     * @param player the viewing player
     * @param page   0-indexed page number
     * @return the constructed {@link Inventory}
     */
    public Inventory buildParticlePage(Player player, int page) {
        Component title = ColorUtil.parse(
                plugin.getConfigManager().getMessageNoPrefix("gui-particle-title"));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
        String currentKey = data != null ? data.getParticleKey() : "FLAME";

        List<String> keys = plugin.getConfigManager().getTrailKeys();
        int start = page * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, keys.size());

        for (int i = start; i < end; i++) {
            String key   = keys.get(i);
            Trail  trail = plugin.getConfigManager().getTrail(key);
            if (trail == null) continue;

            int     slot     = i - start;
            boolean selected = key.equalsIgnoreCase(currentKey);
            Material icon    = safeMaterial(trail.getIconMaterial(), Material.PAPER);

            List<String> lore = new ArrayList<>();
            lore.add(selected
                    ? plugin.getConfigManager().getMessageNoPrefix("gui-selected")
                    : plugin.getConfigManager().getMessageNoPrefix("gui-click-select"));

            inv.setItem(slot, buildItem(icon, trail.getDisplayName(), lore));
        }

        addNavRow(inv, page, end < keys.size());
        return inv;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<String> buildStyleLore(TrailStyle style, boolean selected, boolean hasPerm) {
        List<String> lore = new ArrayList<>();
        // Description from trails.yml
        String descPath = "styles." + style.name() + ".description";
        if (plugin.getConfigManager().getTrailsConfig().contains(descPath)) {
            lore.addAll(plugin.getConfigManager().getTrailsConfig().getStringList(descPath));
            lore.add("");
        }
        if (!hasPerm) {
            lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-no-permission"));
        } else if (selected) {
            lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-selected"));
        } else {
            lore.add(plugin.getConfigManager().getMessageNoPrefix("gui-click-select"));
        }
        return lore;
    }

    private void addNavRow(Inventory inv, int page, boolean hasNext) {
        ItemStack pane = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        if (page > 0) {
            inv.setItem(45, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-prev-page"), List.of()));
        }
        if (hasNext) {
            inv.setItem(53, buildItem(Material.ARROW,
                    plugin.getConfigManager().getMessageNoPrefix("gui-next-page"), List.of()));
        }
        inv.setItem(49, buildItem(Material.BARRIER,
                plugin.getConfigManager().getMessageNoPrefix("gui-back"), List.of()));
    }

    private ItemStack buildItem(Material material, String rawName, List<String> rawLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.parse(rawName));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : rawLore) loreComponents.add(ColorUtil.parse(line));
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material safeMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
