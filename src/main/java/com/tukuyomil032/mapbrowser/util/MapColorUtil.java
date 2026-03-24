package com.tukuyomil032.mapbrowser.util;

/**
 * Holds an approximate 144-color palette and nearest-color lookup.
 */
public final class MapColorUtil {
    public static final int[] MAP_COLORS_RGB = buildPalette();
    private static final byte[] COLOR_LUT = buildLut();

    private MapColorUtil() {
    }

    /**
     * Converts RGB to nearest palette index.
     */
    public static byte toMapColor(final int r, final int g, final int b) {
        final int rr = r & 0xFF;
        final int gg = g & 0xFF;
        final int bb = b & 0xFF;
        final int rgb = (rr << 16) | (gg << 8) | bb;
        return toMapColor(rgb);
    }

    /**
     * Converts packed RGB to nearest palette index.
     */
    public static byte toMapColor(final int rgb) {
        return COLOR_LUT[rgb & 0x00FFFFFF];
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

    private static byte[] buildLut() {
        final byte[] lut = new byte[1 << 24];
        for (int rgb = 0; rgb < lut.length; rgb++) {
            lut[rgb] = slowNearest(rgb);
        }
        return lut;
    }

    private static byte slowNearest(final int rgb) {
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;

        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < MAP_COLORS_RGB.length; i++) {
            final int p = MAP_COLORS_RGB[i];
            final int pr = (p >> 16) & 0xFF;
            final int pg = (p >> 8) & 0xFF;
            final int pb = p & 0xFF;
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
}
