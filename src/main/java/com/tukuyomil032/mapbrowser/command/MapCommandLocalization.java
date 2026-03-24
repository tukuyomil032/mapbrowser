package com.tukuyomil032.mapbrowser.command;

import java.util.Locale;
import java.util.Map;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

/**
 * Resolves language and localized command messages.
 */
final class MapCommandLocalization {
    private final MapBrowserPlugin plugin;

    MapCommandLocalization(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    String resolveLanguage() {
        final String configured = plugin.getConfig().getString("ui.language", "en");
        if (configured == null) {
            return "en";
        }
        final String normalized = configured.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.isBlank() ? "en" : normalized;
    }

    String localizeMessage(final String source) {
        if (source == null) {
            return "";
        }
        return plugin.getMessageLocalizer().translateRaw(resolveLanguage(), source);
    }

    String tk(final String key, final String en, final String ja) {
        final String language = resolveLanguage();
        final String fallback = language.startsWith("ja") ? ja : en;
        return plugin.getMessageLocalizer().translateKey(language, key, fallback);
    }

    String tkp(final String key, final String en, final String ja, final Map<String, ?> placeholders) {
        final String language = resolveLanguage();
        final String fallback = language.startsWith("ja") ? ja : en;
        return plugin.getMessageLocalizer().translateKey(language, key, fallback, placeholders);
    }
}
