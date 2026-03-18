package com.tukuyomil032.mapbrowser.service;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.tukuyomil032.mapbrowser.screen.Screen;

/**
 * Public service boundary for future API exposure.
 */
public interface MapBrowserService {
    /**
     * Returns all screens.
     */
    Collection<Screen> getAllScreens();

    /**
     * Returns screen by id.
     */
    Optional<Screen> getScreen(UUID screenId);

    /**
     * Requests URL navigation for a screen.
     */
    void openUrl(UUID screenId, String url);

    /**
     * Requests page reload for a screen.
     */
    boolean reload(UUID screenId);

    /**
     * Requests FPS update for a screen.
     */
    boolean setFps(UUID screenId, int fps);

    /**
     * Requests browser close for a screen.
     */
    boolean close(UUID screenId);

    /**
     * Requests browser history back for a screen.
     */
    boolean goBack(UUID screenId);

    /**
     * Requests browser history forward for a screen.
     */
    boolean goForward(UUID screenId);

    /**
     * Returns public status snapshot.
     */
    ServiceStatus status();

    /**
     * Immutable public status snapshot.
     */
    record ServiceStatus(boolean ipcConnected, int screenCount) {
    }
}
