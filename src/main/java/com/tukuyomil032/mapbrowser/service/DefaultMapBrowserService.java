package com.tukuyomil032.mapbrowser.service;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
}
