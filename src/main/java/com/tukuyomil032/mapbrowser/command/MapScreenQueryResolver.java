package com.tukuyomil032.mapbrowser.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

/**
 * Resolves screen query inputs and suggestions.
 */
final class MapScreenQueryResolver {
    private final MapBrowserPlugin plugin;

    MapScreenQueryResolver(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    Optional<Screen> resolveScreen(final String query, final Player player) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        if ("latest".equalsIgnoreCase(query)) {
            return plugin.getScreenManager().getAllScreens().stream()
                    .max(Comparator.comparing(Screen::getCreatedAt));
        }

        try {
            final Optional<Screen> byId = plugin.getScreenManager().getScreen(UUID.fromString(query));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (final IllegalArgumentException ignored) {
            // Ignore and fallback to name search.
        }

        final Optional<Screen> byName = plugin.getScreenManager().getAllScreens().stream()
                .filter(screen -> screen.getName().equalsIgnoreCase(query))
                .findFirst();
        if (byName.isPresent()) {
            return byName;
        }

        return plugin.getScreenManager().getSelected(player.getUniqueId());
    }

    List<String> screenNameSuggestions() {
        return plugin.getScreenManager().getAllScreens().stream()
                .sorted(Comparator.comparing(Screen::getCreatedAt).reversed())
                .limit(20)
                .map(Screen::getName)
                .toList();
    }
}
