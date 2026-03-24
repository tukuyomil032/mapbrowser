package com.tukuyomil032.mapbrowser.input;

import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import com.tukuyomil032.mapbrowser.screen.Screen;
import com.tukuyomil032.mapbrowser.util.RaycastUtil;

/**
 * Converts item-frame hit vectors into browser coordinates.
 */
final class FrameClickResolver {
    private FrameClickResolver() {
    }

    static Optional<RaycastUtil.Vector2i> resolveClickPosition(
            final ItemFrame frame,
            final Screen screen,
            final Vector clickedPosition,
            final NamespacedKey tileIndexKey
    ) {
        final Optional<Integer> tileIndex = resolveTileIndexFromFrame(frame, tileIndexKey);
        if (tileIndex.isEmpty()) {
            return Optional.empty();
        }

        final int frameIndex = tileIndex.get();
        final int tileX = frameIndex % screen.getWidth();
        final int tileY = frameIndex / screen.getWidth();
        final Vector worldHit = frame.getLocation().toVector().add(clickedPosition);
        final Vector local = normalizeFrameHit(frame, worldHit);
        final RaycastUtil.Vector2i pixel = RaycastUtil.toBrowserCoords(local, 1, 1);
        final int x = (tileX * 128) + pixel.x();
        final int y = (tileY * 128) + pixel.y();
        return Optional.of(new RaycastUtil.Vector2i(x, y));
    }

    private static Vector normalizeFrameHit(final ItemFrame frame, final Vector worldHit) {
        final BoundingBox box = frame.getBoundingBox();
        final double nx = normalizeAxis(worldHit.getX(), box.getMinX(), box.getMaxX());
        final double ny = normalizeAxis(worldHit.getY(), box.getMinY(), box.getMaxY());
        final double nz = normalizeAxis(worldHit.getZ(), box.getMinZ(), box.getMaxZ());

        final double u = switch (frame.getFacing()) {
            case NORTH -> nx;
            case SOUTH -> 1.0 - nx;
            case EAST -> nz;
            case WEST -> 1.0 - nz;
            default -> nx;
        };
        final double v = 1.0 - ny;
        return new Vector(clamp01(u), clamp01(v), 0.0);
    }

    private static double normalizeAxis(final double value, final double min, final double max) {
        if (max <= min) {
            return 0.5;
        }
        return (value - min) / (max - min);
    }

    private static double clamp01(final double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Optional<Integer> resolveTileIndexFromFrame(final ItemFrame frame, final NamespacedKey tileIndexKey) {
        final ItemStack displayed = frame.getItem();
        if (!displayed.hasItemMeta()) {
            return Optional.empty();
        }
        final ItemMeta displayedMeta = displayed.getItemMeta();
        if (displayedMeta == null) {
            return Optional.empty();
        }
        final Integer tileIndex = displayedMeta.getPersistentDataContainer().get(tileIndexKey, PersistentDataType.INTEGER);
        if (tileIndex == null || tileIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(tileIndex);
    }
}
