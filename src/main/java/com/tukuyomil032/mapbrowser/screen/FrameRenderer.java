package com.tukuyomil032.mapbrowser.screen;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles incoming frame bytes and keeps per-screen frame cache.
 */
public final class FrameRenderer {
    private final MapBrowserPlugin plugin;
    private final Map<UUID, byte[]> lastFrames = new ConcurrentHashMap<>();

    /**
     * Creates a frame renderer.
     */
    public FrameRenderer(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies a full frame.
     */
    public void renderFrame(final Screen screen, final byte[] colorData) {
        if (screen == null || colorData == null) {
            return;
        }
        lastFrames.put(screen.getId(), colorData);
        screen.setState(ScreenState.PLAYING);
    }

    /**
     * Applies a delta frame.
     */
    public void renderDeltaFrame(
            final Screen screen,
            final byte[] deltaData,
            final int x,
            final int y,
            final int w,
            final int h
    ) {
        if (screen == null || deltaData == null) {
            return;
        }

        final int fullWidth = screen.getWidth() * 128;
        final int fullHeight = screen.getHeight() * 128;
        final int fullSize = fullWidth * fullHeight;
        final byte[] frame = lastFrames.computeIfAbsent(screen.getId(), ignored -> new byte[fullSize]);
        if (frame.length != fullSize) {
            lastFrames.put(screen.getId(), new byte[fullSize]);
        }

        byte[] target = lastFrames.get(screen.getId());
        if (target == null || target.length != fullSize) {
            target = new byte[fullSize];
            lastFrames.put(screen.getId(), target);
        }

        int srcOffset = 0;
        for (int row = 0; row < h; row++) {
            final int yy = y + row;
            if (yy < 0 || yy >= fullHeight) {
                srcOffset += w;
                continue;
            }

            if (x >= 0 && x + w <= fullWidth && srcOffset + w <= deltaData.length) {
                System.arraycopy(deltaData, srcOffset, target, (yy * fullWidth) + x, w);
                srcOffset += w;
                continue;
            }

            for (int col = 0; col < w; col++) {
                final int xx = x + col;
                if (srcOffset >= deltaData.length) {
                    break;
                }
                if (xx >= 0 && xx < fullWidth) {
                    target[(yy * fullWidth) + xx] = deltaData[srcOffset];
                }
                srcOffset++;
            }
        }

        screen.setState(ScreenState.PLAYING);
    }

    /**
     * Removes cached frame for a screen.
     */
    public void clear(final UUID screenId) {
        lastFrames.remove(screenId);
    }

    /**
     * Clears all cached frames.
     */
    public void shutdown() {
        lastFrames.clear();
        plugin.getLogger().info("FrameRenderer shutdown completed.");
    }
}
