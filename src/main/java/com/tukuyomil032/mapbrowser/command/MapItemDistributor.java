package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles screen-map item creation and delivery.
 */
final class MapItemDistributor {
    private final MapBrowserPlugin plugin;
    private final NamespacedKey screenIdKey;
    private final NamespacedKey tileIndexKey;
    private final NamespacedKey autofillKey;

    MapItemDistributor(
            final MapBrowserPlugin plugin,
            final NamespacedKey screenIdKey,
            final NamespacedKey tileIndexKey,
            final NamespacedKey autofillKey
    ) {
        this.plugin = plugin;
        this.screenIdKey = screenIdKey;
        this.tileIndexKey = tileIndexKey;
        this.autofillKey = autofillKey;
    }

    MapDeliverySummary giveScreenMaps(final Player player, final Screen screen, final boolean autoFillEnabled) {
        final int totalMaps = screen.getMapIds().length;
        int directMaps = 0;
        int bundleBoxes = 0;
        final byte autoFillFlag = autoFillEnabled ? (byte) 1 : (byte) 0;

        giveItemOrDrop(player, createStarterFrameItem(screen));
        if (totalMaps > 0) {
            giveItemOrDrop(player, createScreenMapItem(screen, 0, autoFillFlag));
            directMaps++;
        }

        if (totalMaps <= 36) {
            for (int index = 1; index < totalMaps; index++) {
                giveItemOrDrop(player, createScreenMapItem(screen, index, autoFillFlag));
                directMaps++;
            }
            return new MapDeliverySummary(totalMaps, directMaps, bundleBoxes);
        }

        int index = 1;
        while (index < totalMaps) {
            final int endExclusive = Math.min(index + 27, totalMaps);
            giveItemOrDrop(player, createMapBundleBox(screen, index, endExclusive, autoFillFlag));
            bundleBoxes++;
            index = endExclusive;
        }

        return new MapDeliverySummary(totalMaps, directMaps, bundleBoxes);
    }

    ItemStack createScreenMapItem(final Screen screen, final int tileIndex, final byte autoFillFlag) {
        final int[] mapIds = screen.getMapIds();
        final ItemStack mapItem = new ItemStack(Material.FILLED_MAP, 1);
        if (!(mapItem.getItemMeta() instanceof MapMeta meta)) {
            return mapItem;
        }

        final int mapId = mapIds[tileIndex];
        final MapView mapView = Bukkit.getMap(mapId);
        if (mapView != null) {
            meta.setMapView(mapView);
        }
        meta.displayName(Component.text("MapBrowser Display Tile", NamedTextColor.AQUA));
        final int row = (tileIndex / screen.getWidth()) + 1;
        final int col = (tileIndex % screen.getWidth()) + 1;
        meta.lore(List.of(
                Component.text("Screen: " + screen.getName(), NamedTextColor.GRAY),
                Component.text("Tile: " + col + "," + row + " / " + screen.getWidth() + "x" + screen.getHeight(), NamedTextColor.DARK_GRAY),
                Component.text("ID: " + mapId, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(screenIdKey, PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(tileIndexKey, PersistentDataType.INTEGER, tileIndex);
        if (autoFillFlag == (byte) 1) {
            meta.getPersistentDataContainer().set(autofillKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(autofillKey);
        }
        mapItem.setItemMeta(meta);
        return mapItem;
    }

    void giveItemOrDrop(final Player player, final ItemStack item) {
        final HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    List<String> rangeValues(final int maxValue) {
        final int max = Math.max(1, maxValue);
        final ArrayList<String> values = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            values.add(String.valueOf(i));
        }
        return values;
    }

    byte detectAutoFillPreference(final Player player, final Screen screen) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.FILLED_MAP || !stack.hasItemMeta()) {
                continue;
            }
            if (!(stack.getItemMeta() instanceof MapMeta meta)) {
                continue;
            }
            final String sid = meta.getPersistentDataContainer().get(screenIdKey, PersistentDataType.STRING);
            if (!screen.getId().toString().equals(sid)) {
                continue;
            }
            final Byte enabled = meta.getPersistentDataContainer().get(autofillKey, PersistentDataType.BYTE);
            return enabled != null && enabled == (byte) 1 ? (byte) 1 : (byte) 0;
        }
        return 0;
    }

    private ItemStack createStarterFrameItem(final Screen screen) {
        final ItemStack frame = new ItemStack(Material.ITEM_FRAME, 1);
        final ItemMeta meta = java.util.Objects.requireNonNull(frame.getItemMeta(), "Frame meta unavailable");
        meta.displayName(Component.text("MapBrowser Starter Frame", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Place 1 frame, insert starter map, then auto-fill.", NamedTextColor.GRAY),
                Component.text("Screen: " + screen.getName(), NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "screen-id"), PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "starter-frame"), PersistentDataType.BYTE, (byte) 1);
        frame.setItemMeta(meta);
        return frame;
    }

    private ItemStack createMapBundleBox(final Screen screen, final int startInclusive, final int endExclusive, final byte autoFillFlag) {
        final ItemStack shulkerItem = new ItemStack(Material.SHULKER_BOX, 1);
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockMeta)) {
            return shulkerItem;
        }
        if (!(blockMeta.getBlockState() instanceof ShulkerBox shulker)) {
            return shulkerItem;
        }

        int slot = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            shulker.getInventory().setItem(slot++, createScreenMapItem(screen, i, autoFillFlag));
        }
        blockMeta.setBlockState(shulker);
        blockMeta.displayName(Component.text("MapBrowser Map Bundle", NamedTextColor.LIGHT_PURPLE));
        blockMeta.lore(List.of(
                Component.text("Screen: " + screen.getName(), NamedTextColor.GRAY),
                Component.text("Tiles: " + (startInclusive + 1) + ".." + endExclusive, NamedTextColor.DARK_GRAY)
        ));
        shulkerItem.setItemMeta(blockMeta);
        return shulkerItem;
    }

    record MapDeliverySummary(int totalMaps, int directMaps, int bundleBoxes) {
    }
}
