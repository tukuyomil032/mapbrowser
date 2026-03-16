package com.tukuyomil032.mapbrowser.util;

import org.bukkit.util.Vector;

/**
 * Converts normalized hit positions to browser pixel coordinates.
 */
public final class RaycastUtil {
    private RaycastUtil() {
    }

    /**
     * Converts a hit vector inside a frame into browser pixel coordinates.
     */
    public static Vector2i toBrowserCoords(final Vector normalizedHit, final int screenWidthMaps, final int screenHeightMaps) {
        final double clampedX = Math.max(0.0, Math.min(1.0, normalizedHit.getX()));
        final double clampedY = Math.max(0.0, Math.min(1.0, normalizedHit.getY()));

        final int widthPx = screenWidthMaps * 128;
        final int heightPx = screenHeightMaps * 128;
        final int x = Math.min(widthPx - 1, (int) Math.floor(clampedX * widthPx));
        final int y = Math.min(heightPx - 1, (int) Math.floor(clampedY * heightPx));
        return new Vector2i(x, y);
    }

    /**
     * Integer 2D vector.
     */
    public record Vector2i(int x, int y) {
    }
}
