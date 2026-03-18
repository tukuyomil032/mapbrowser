package com.tukuyomil032.mapbrowser.screen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
    private static final Color[] PALETTE_COLORS = buildPaletteColors();

    private final MapBrowserPlugin plugin;
    private final Map<UUID, byte[]> lastFrames = new ConcurrentHashMap<>();
    private final Map<UUID, int[]> tileRevisionsByScreen = new ConcurrentHashMap<>();
    private final Map<UUID, DirtyRect[]> tileDirtyRectsByScreen = new ConcurrentHashMap<>();
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

        final int fullSize = screen.getWidth() * 128 * screen.getHeight() * 128;
        if (colorData.length < fullSize) {
            plugin.getLogger().log(Level.WARNING,
                    "Skipping invalid FRAME: expected>={0} actual={1}",
                    new Object[]{fullSize, colorData.length});
            return;
        }

        lastFrames.put(screen.getId(), colorData);
        bumpAllTiles(screen);
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
        if (w <= 0 || h <= 0) {
            return;
        }

        final long expectedLength = (long) w * (long) h;
        if (expectedLength > Integer.MAX_VALUE || deltaData.length < expectedLength) {
            plugin.getLogger().log(Level.WARNING,
                    "Skipping invalid DELTA_FRAME: expected={0} actual={1}",
                    new Object[]{expectedLength, deltaData.length});
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

        bumpDirtyTiles(screen, x, y, w, h);
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
        ensureTileRevisions(screen);
        ensureTileDirtyRects(screen);
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
        tileRevisionsByScreen.remove(screenId);
        tileDirtyRectsByScreen.remove(screenId);
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
        tileRevisionsByScreen.clear();
        tileDirtyRectsByScreen.clear();
        plugin.getLogger().info("FrameRenderer shutdown completed.");
    }

    private int[] ensureTileRevisions(final Screen screen) {
        final int tileCount = screen.getWidth() * screen.getHeight();
        return tileRevisionsByScreen.compute(screen.getId(), (ignored, existing) -> {
            if (existing == null || existing.length != tileCount) {
                return new int[tileCount];
            }
            return existing;
        });
    }

    private DirtyRect[] ensureTileDirtyRects(final Screen screen) {
        final int tileCount = screen.getWidth() * screen.getHeight();
        return tileDirtyRectsByScreen.compute(screen.getId(), (ignored, existing) -> {
            if (existing == null || existing.length != tileCount) {
                return new DirtyRect[tileCount];
            }
            return existing;
        });
    }

    private void bumpAllTiles(final Screen screen) {
        final int[] tileRevisions = ensureTileRevisions(screen);
        synchronized (tileRevisions) {
            for (int i = 0; i < tileRevisions.length; i++) {
                tileRevisions[i] = tileRevisions[i] + 1;
            }
        }
        // Full frame invalidates per-tile dirty regions; next draw should render full tile.
        tileDirtyRectsByScreen.remove(screen.getId());
    }

    private void bumpDirtyTiles(final Screen screen, final int x, final int y, final int w, final int h) {
        final int fullWidth = screen.getWidth() * 128;
        final int fullHeight = screen.getHeight() * 128;

        final int minX = Math.max(0, x);
        final int minY = Math.max(0, y);
        final int maxX = Math.min(fullWidth - 1, x + w - 1);
        final int maxY = Math.min(fullHeight - 1, y + h - 1);
        if (maxX < minX || maxY < minY) {
            return;
        }

        final int minTileX = minX / 128;
        final int maxTileX = maxX / 128;
        final int minTileY = minY / 128;
        final int maxTileY = maxY / 128;
        final int[] tileRevisions = ensureTileRevisions(screen);
        final DirtyRect[] dirtyRects = ensureTileDirtyRects(screen);
        synchronized (tileRevisions) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                for (int tx = minTileX; tx <= maxTileX; tx++) {
                    final int tileIndex = (ty * screen.getWidth()) + tx;
                    if (tileIndex >= 0 && tileIndex < tileRevisions.length) {
                        tileRevisions[tileIndex] = tileRevisions[tileIndex] + 1;

                        final int tileStartX = tx * 128;
                        final int tileStartY = ty * 128;
                        final int localMinX = Math.max(0, minX - tileStartX);
                        final int localMinY = Math.max(0, minY - tileStartY);
                        final int localMaxX = Math.min(127, maxX - tileStartX);
                        final int localMaxY = Math.min(127, maxY - tileStartY);
                        final DirtyRect incoming = new DirtyRect(localMinX, localMinY, localMaxX, localMaxY);
                        final DirtyRect existing = dirtyRects[tileIndex];
                        dirtyRects[tileIndex] = existing == null ? incoming : existing.merge(incoming);
                    }
                }
            }
        }
    }

    private int tileRevision(final Screen screen, final int tileIndex) {
        final int[] tileRevisions = ensureTileRevisions(screen);
        if (tileIndex < 0 || tileIndex >= tileRevisions.length) {
            return 0;
        }
        return tileRevisions[tileIndex];
    }

    private boolean isWithinRenderDistance(final Player player, final Screen screen) {
        if (!player.getWorld().getName().equals(screen.getWorldName())) {
            return false;
        }

        final int configured = plugin.getConfig().getInt("screen.render-distance", 64);
        if (configured <= 0) {
            return true;
        }

        final double dx = player.getLocation().getX() - screen.getOriginX();
        final double dy = player.getLocation().getY() - screen.getOriginY();
        final double dz = player.getLocation().getZ() - screen.getOriginZ();
        final double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
        final double maxDistanceSquared = (double) configured * (double) configured;
        return distanceSquared <= maxDistanceSquared;
    }

    private DirtyRect tileDirtyRect(final Screen screen, final int tileIndex) {
        final DirtyRect[] dirtyRects = ensureTileDirtyRects(screen);
        if (tileIndex < 0 || tileIndex >= dirtyRects.length) {
            return null;
        }
        return dirtyRects[tileIndex];
    }

    private static Color[] buildPaletteColors() {
        final int[] palette = MapColorUtil.MAP_COLORS_RGB;
        final Color[] colors = new Color[palette.length];
        for (int i = 0; i < palette.length; i++) {
            colors[i] = new Color(palette[i]);
        }
        return colors;
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

            final UUID playerId = player.getUniqueId();
            if (!isWithinRenderDistance(player, screen)) {
                renderedRevisionByPlayer.remove(playerId);
                return;
            }

            final int revision = tileRevision(screen, tileIndex);
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

            final boolean drawFull = renderedRevision == null || renderedRevision < (revision - 1);
            final DirtyRect dirtyRect = drawFull ? DirtyRect.fullTile() : tileDirtyRect(screen, tileIndex);
            final int drawMinX = dirtyRect == null ? 0 : dirtyRect.minX();
            final int drawMinY = dirtyRect == null ? 0 : dirtyRect.minY();
            final int drawMaxX = dirtyRect == null ? 127 : dirtyRect.maxX();
            final int drawMaxY = dirtyRect == null ? 127 : dirtyRect.maxY();

            for (int y = drawMinY; y <= drawMaxY; y++) {
                final int srcRow = (startY + y) * fullWidth;
                for (int x = drawMinX; x <= drawMaxX; x++) {
                    final int srcIndex = srcRow + startX + x;
                    if (srcIndex < 0 || srcIndex >= frame.length) {
                        continue;
                    }
                    final int paletteIndex = Byte.toUnsignedInt(frame[srcIndex]);
                    final int safeIndex = paletteIndex < PALETTE_COLORS.length ? paletteIndex : 0;
                    canvas.setPixelColor(x, y, PALETTE_COLORS[safeIndex]);
                }
            }

            renderedRevisionByPlayer.put(playerId, revision);
        }
    }

    private record DirtyRect(int minX, int minY, int maxX, int maxY) {
        private DirtyRect merge(final DirtyRect other) {
            if (other == null) {
                return this;
            }
            return new DirtyRect(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY)
            );
        }

        private static DirtyRect fullTile() {
            return new DirtyRect(0, 0, 127, 127);
        }
    }
}
