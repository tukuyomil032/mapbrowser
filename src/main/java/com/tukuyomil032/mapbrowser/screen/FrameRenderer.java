package com.tukuyomil032.mapbrowser.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.util.MapColorUtil;

/**
 * Handles incoming frame bytes and keeps per-screen frame cache.
 */
public final class FrameRenderer {
    private final MapBrowserPlugin plugin;
    private final Map<UUID, byte[]> lastFrames = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> revisions = new ConcurrentHashMap<>();
    private final Map<UUID, List<Integer>> mapIdsByScreen = new ConcurrentHashMap<>();
    private final Map<Integer, MapRenderer> renderersByMapId = new ConcurrentHashMap<>();

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
        revisions.merge(screen.getId(), 1, (left, right) -> left + right);
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

        revisions.merge(screen.getId(), 1, (left, right) -> left + right);
        screen.setState(ScreenState.PLAYING);
    }

    /**
     * Registers map renderers for all map tiles in a screen.
     */
    public void registerScreen(final Screen screen) {
        if (screen == null) {
            return;
        }

        final int[] mapIds = screen.getMapIds();
        final ArrayList<Integer> ids = new ArrayList<>(mapIds.length);
        for (int tileIndex = 0; tileIndex < mapIds.length; tileIndex++) {
            final int mapId = mapIds[tileIndex];
            final MapView mapView = Bukkit.getMap(mapId);
            if (mapView == null) {
                continue;
            }

            final MapRenderer oldRenderer = renderersByMapId.remove(mapId);
            if (oldRenderer != null) {
                mapView.removeRenderer(oldRenderer);
            }

            // Remove default terrain renderers so only browser output is drawn.
            for (final MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
                mapView.removeRenderer(renderer);
            }

            final ScreenTileRenderer tileRenderer = new ScreenTileRenderer(screen.getId(), tileIndex);
            mapView.addRenderer(tileRenderer);
            renderersByMapId.put(mapId, tileRenderer);
            ids.add(mapId);
        }

        mapIdsByScreen.put(screen.getId(), ids);
    }

    /**
     * Unregisters map renderers for a screen.
     */
    public void unregisterScreen(final UUID screenId) {
        final List<Integer> mapIds = mapIdsByScreen.remove(screenId);
        if (mapIds == null) {
            return;
        }
        for (final Integer mapId : mapIds) {
            final MapRenderer renderer = renderersByMapId.remove(mapId);
            if (renderer == null) {
                continue;
            }
            final MapView view = Bukkit.getMap(mapId);
            if (view != null) {
                view.removeRenderer(renderer);
            }
        }
    }

    /**
     * Removes cached frame for a screen.
     */
    public void clear(final UUID screenId) {
        lastFrames.remove(screenId);
        revisions.remove(screenId);
        unregisterScreen(screenId);
    }

    /**
     * Clears all cached frames.
     */
    public void shutdown() {
        for (final UUID screenId : new ArrayList<>(mapIdsByScreen.keySet())) {
            unregisterScreen(screenId);
        }
        lastFrames.clear();
        revisions.clear();
        plugin.getLogger().info("FrameRenderer shutdown completed.");
    }

    private final class ScreenTileRenderer extends MapRenderer {
        private final UUID screenId;
        private final int tileIndex;
        private final Map<UUID, Integer> renderedRevisionByPlayer = new ConcurrentHashMap<>();

        private ScreenTileRenderer(final UUID screenId, final int tileIndex) {
            super(false);
            this.screenId = screenId;
            this.tileIndex = tileIndex;
        }

        @Override
        public void render(final MapView map, final MapCanvas canvas, final Player player) {
            final Screen screen = plugin.getScreenManager().getScreen(screenId).orElse(null);
            if (screen == null) {
                return;
            }

            final int revision = revisions.getOrDefault(screenId, 0);
            final UUID playerId = player.getUniqueId();
            final Integer renderedRevision = renderedRevisionByPlayer.get(playerId);
            if (renderedRevision != null && renderedRevision == revision) {
                return;
            }

            final byte[] frame = lastFrames.get(screenId);
            if (frame == null) {
                return;
            }

            final int fullWidth = screen.getWidth() * 128;
            final int tileX = tileIndex % screen.getWidth();
            final int tileY = tileIndex / screen.getWidth();
            final int startX = tileX * 128;
            final int startY = tileY * 128;

            for (int y = 0; y < 128; y++) {
                final int srcRow = (startY + y) * fullWidth;
                for (int x = 0; x < 128; x++) {
                    final int srcIndex = srcRow + startX + x;
                    if (srcIndex < 0 || srcIndex >= frame.length) {
                        continue;
                    }
                    final int paletteIndex = Byte.toUnsignedInt(frame[srcIndex]);
                    final int rgb = MapColorUtil.MAP_COLORS_RGB[paletteIndex % MapColorUtil.MAP_COLORS_RGB.length];
                    final int r = (rgb >> 16) & 0xFF;
                    final int g = (rgb >> 8) & 0xFF;
                    final int b = rgb & 0xFF;
                    canvas.setPixelColor(x, y, new Color(r, g, b));
                }
            }

            renderedRevisionByPlayer.put(playerId, revision);
        }
    }
}
