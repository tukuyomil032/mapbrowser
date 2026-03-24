package com.tukuyomil032.mapbrowser.input;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import com.tukuyomil032.mapbrowser.screen.Screen;

/**
 * Creates screen-bound map items used by auto-assemble flow.
 */
final class InputScreenMapItemFactory {
    private InputScreenMapItemFactory() {
    }

    static ItemStack createScreenMapItem(final Screen screen, final int tileIndex, final NamespacedKey screenIdKey, final NamespacedKey tileIndexKey) {
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
        meta.getPersistentDataContainer().set(screenIdKey, PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(tileIndexKey, PersistentDataType.INTEGER, tileIndex);
        mapItem.setItemMeta(meta);
        return mapItem;
    }
}
