package com.tukuyomil032.mapbrowser.screen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

/**
 * Manages screen lifecycle and lookup.
 */
public final class ScreenManager {
    private final MapBrowserPlugin plugin;
    private final Map<UUID, Screen> screens = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> selectedByPlayer = new ConcurrentHashMap<>();

    /**
     * Creates a manager.
     */
    public ScreenManager(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads screens from datastore.
     */
    public void init() {
        for (final Screen screen : plugin.getDataStore().loadScreens()) {
            screens.put(screen.getId(), screen);
            plugin.getFrameRenderer().registerScreen(screen);
        }
        plugin.getLogger().log(Level.INFO, "Loaded screens: {0}", screens.size());
    }

    /**
     * Loads a screen into browser-renderer process.
     */
    public boolean loadScreen(final UUID screenId) {
        final Screen screen = screens.get(screenId);
        if (screen == null) {
            return false;
        }
        plugin.getBrowserIPCClient().sendOpen(screen.getId(), screen.getWidth(), screen.getHeight(), screen.getFps());
        final String url = screen.getCurrentUrl();
        if (url != null && !url.isBlank() && !"about:blank".equalsIgnoreCase(url)) {
            plugin.getBrowserIPCClient().sendNavigate(screen.getId(), url);
        }
        screen.setState(ScreenState.LOADING);
        return true;
    }

    /**
     * Unloads a screen from browser-renderer process.
     */
    public boolean unloadScreen(final UUID screenId) {
        final Screen screen = screens.get(screenId);
        if (screen == null) {
            return false;
        }
        plugin.getBrowserIPCClient().sendClose(screen.getId());
        screen.setState(ScreenState.PAUSED);
        return true;
    }

    /**
     * Ensures a screen is loaded before browser commands are sent.
     */
    public boolean ensureLoaded(final UUID screenId) {
        final Screen screen = screens.get(screenId);
        if (screen == null) {
            return false;
        }
        if (screen.getState() == ScreenState.PLAYING || screen.getState() == ScreenState.LOADING) {
            return true;
        }
        return loadScreen(screenId);
    }

    /**
     * Unloads all screens and marks them as paused.
     */
    public void unloadAll() {
        for (final Screen screen : screens.values()) {
            plugin.getBrowserIPCClient().sendClose(screen.getId());
            screen.setState(ScreenState.PAUSED);
        }
    }

    /**
     * Creates a screen from player context.
     */
    public Screen createScreen(
            final Player player,
            final BlockFace face,
            final int width,
            final int height,
            final String name
    ) {
        final World world = player.getWorld();
        final var targetBlock = player.getTargetBlockExact(6);
        final Location loc = targetBlock != null ? targetBlock.getLocation() : player.getLocation();

        final int[] mapIds = allocateMapIds(world, width * height);
        final int fps = plugin.getConfig().getInt("screen.default-fps", 10);
        final Screen screen = new Screen(
                UUID.randomUUID(),
                name,
                world.getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                face,
                width,
                height,
                player.getUniqueId(),
                System.currentTimeMillis(),
                mapIds,
                "about:blank",
                fps,
                ScreenState.LOADING
        );

        screens.put(screen.getId(), screen);
        plugin.getFrameRenderer().registerScreen(screen);
        setSelected(player.getUniqueId(), screen.getId());
        return screen;
    }

    /**
     * Destroys a screen and removes caches.
     */
    public boolean destroyScreen(final UUID screenId) {
        final Screen removed = screens.remove(screenId);
        if (removed != null) {
            plugin.getFrameRenderer().clear(screenId);
            plugin.getBrowserIPCClient().sendClose(screenId);
            return true;
        }
        return false;
    }

    /**
     * Resizes an existing screen while keeping id/name/url/owner.
     */
    public Optional<Screen> resizeScreen(final UUID screenId, final int width, final int height) {
        final Screen old = screens.get(screenId);
        if (old == null) {
            return Optional.empty();
        }

        World world = Bukkit.getWorld(old.getWorldName());
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return Optional.empty();
        }

        final int[] mapIds = allocateMapIds(world, width * height);
        final Screen resized = new Screen(
                old.getId(),
                old.getName(),
                old.getWorldName(),
                old.getOriginX(),
                old.getOriginY(),
                old.getOriginZ(),
                old.getFace(),
                width,
                height,
                old.getOwnerUuid(),
                old.getCreatedAt(),
                mapIds,
                old.getCurrentUrl(),
                old.getFps(),
                ScreenState.LOADING
        );

        plugin.getFrameRenderer().clear(screenId);
        screens.put(screenId, resized);
        plugin.getFrameRenderer().registerScreen(resized);
        return Optional.of(resized);
    }

    /**
     * Finds a screen by id.
     */
    public Optional<Screen> getScreen(final UUID screenId) {
        return Optional.ofNullable(screens.get(screenId));
    }

    /**
     * Returns all screens.
     */
    public Collection<Screen> getAllScreens() {
        return List.copyOf(screens.values());
    }

    /**
     * Gets nearest screen from location.
     */
    public Optional<Screen> getNearestScreen(final Location center, final double range) {
        return screens.values().stream()
                .filter(screen -> screen.getWorldName().equals(center.getWorld() != null ? center.getWorld().getName() : ""))
                .filter(screen -> distanceSquared(center, screen) <= range * range)
                .min(Comparator.comparingDouble(screen -> distanceSquared(center, screen)));
    }

    /**
     * Returns selected screen for a player.
     */
    public Optional<Screen> getSelected(final UUID playerUuid) {
        final UUID selected = selectedByPlayer.get(playerUuid);
        if (selected == null) {
            return Optional.empty();
        }
        return getScreen(selected);
    }

    /**
     * Sets selected screen for a player.
     */
    public void setSelected(final UUID playerUuid, final UUID screenId) {
        selectedByPlayer.put(playerUuid, screenId);
    }

    /**
     * Removes selected screen binding for a player.
     */
    public void clearSelected(final UUID playerUuid) {
        selectedByPlayer.remove(playerUuid);
    }

    /**
     * Persists screens to storage.
     */
    public void saveAll() {
        plugin.getDataStore().saveScreens(getAllScreens());
    }

    /**
     * Shuts down manager.
     */
    public void shutdown() {
        unloadAll();
        saveAll();
        screens.clear();
        selectedByPlayer.clear();
    }

    private int[] allocateMapIds(final World world, final int count) {
        final List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final MapView map = Bukkit.createMap(world);
            ids.add(map.getId());
        }
        final int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    private double distanceSquared(final Location center, final Screen screen) {
        final double dx = center.getX() - screen.getOriginX();
        final double dy = center.getY() - screen.getOriginY();
        final double dz = center.getZ() - screen.getOriginZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}
