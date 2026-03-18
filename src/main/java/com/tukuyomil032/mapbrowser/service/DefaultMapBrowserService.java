package com.tukuyomil032.mapbrowser.service;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

/**
 * Default implementation of service boundary.
 */
public final class DefaultMapBrowserService implements MapBrowserService {
    private final MapBrowserPlugin plugin;

    /**
     * Creates default service.
     */
    public DefaultMapBrowserService(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns all screens.
     */
    @Override
    public Collection<Screen> getAllScreens() {
        return plugin.getScreenManager().getAllScreens();
    }

    /**
     * Returns screen by id.
     */
    @Override
    public Optional<Screen> getScreen(final UUID screenId) {
        return plugin.getScreenManager().getScreen(screenId);
    }

    /**
     * Requests URL navigation for a screen.
     */
    @Override
    public void openUrl(final UUID screenId, final String url) {
        plugin.getScreenManager().getScreen(screenId).ifPresent(screen -> {
            screen.setCurrentUrl(url);
            plugin.getBrowserIPCClient().sendNavigate(screen.getId(), url);
        });
    }

    /**
     * Requests page reload for a screen.
     */
    @Override
    public boolean reload(final UUID screenId) {
        return plugin.getScreenManager().getScreen(screenId).map(screen -> {
            plugin.getScreenManager().ensureLoaded(screen.getId());
            plugin.getBrowserIPCClient().sendReload(screen.getId());
            return true;
        }).orElse(false);
    }

    /**
     * Requests FPS update for a screen.
     */
    @Override
    public boolean setFps(final UUID screenId, final int fps) {
        return plugin.getScreenManager().getScreen(screenId).map(screen -> {
            screen.setFps(fps);
            plugin.getScreenManager().ensureLoaded(screen.getId());
            plugin.getBrowserIPCClient().sendSetFps(screen.getId(), fps);
            return true;
        }).orElse(false);
    }

    /**
     * Requests browser close for a screen.
     */
    @Override
    public boolean close(final UUID screenId) {
        return plugin.getScreenManager().getScreen(screenId).map(screen -> {
            plugin.getBrowserIPCClient().sendClose(screen.getId());
            return true;
        }).orElse(false);
    }

    /**
     * Returns public status snapshot.
     */
    @Override
    public ServiceStatus status() {
        return new ServiceStatus(
                plugin.getBrowserIPCClient().isConnected(),
                plugin.getScreenManager().getAllScreens().size()
        );
    }
}
