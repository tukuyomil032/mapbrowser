package com.tukuyomil032.mapbrowser.util;

/**
 * Holds an approximate 144-color palette and nearest-color lookup.
 */
public final class MapColorUtil {
    public static final int[] MAP_COLORS_RGB = buildPalette();

    private MapColorUtil() {
    }

    /**
     * Converts RGB to nearest palette index.
     */
    public static byte toMapColor(final int r, final int g, final int b) {
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < MAP_COLORS_RGB.length; i++) {
            final int rgb = MAP_COLORS_RGB[i];
            final int pr = (rgb >> 16) & 0xFF;
            final int pg = (rgb >> 8) & 0xFF;
            final int pb = rgb & 0xFF;
            final int dr = r - pr;
            final int dg = g - pg;
            final int db = b - pb;
            final int distance = (dr * dr) + (dg * dg) + (db * db);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return (byte) bestIndex;
    }

    private static int[] buildPalette() {
        final int[] palette = new int[144];
        int idx = 0;

        // A compact synthetic palette used for server-side validation.
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 4; b++) {
                    if (idx >= palette.length) {
                        break;
                    }
                    final int rr = Math.min(255, r * 51);
                    final int gg = Math.min(255, g * 51);
                    final int bb = Math.min(255, b * 85);
                    palette[idx++] = (rr << 16) | (gg << 8) | bb;
                }
            }
        }

        return palette;
    }
}
