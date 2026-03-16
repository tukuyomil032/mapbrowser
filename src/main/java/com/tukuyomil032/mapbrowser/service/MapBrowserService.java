package com.tukuyomil032.mapbrowser.service;

import com.tukuyomil032.mapbrowser.screen.Screen;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

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
}
