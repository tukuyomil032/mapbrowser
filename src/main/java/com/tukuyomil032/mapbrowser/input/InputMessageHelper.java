package com.tukuyomil032.mapbrowser.input;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles localized user-facing messages for input interactions.
 */
final class InputMessageHelper {
    private final MapBrowserPlugin plugin;

    InputMessageHelper(final MapBrowserPlugin plugin) {
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

    String t(final String en, final String ja) {
        final String source = resolveLanguage().startsWith("ja") ? ja : en;
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

    void sendError(final Player player, final String en, final String ja) {
        player.sendMessage(Component.text("[ERR] ", NamedTextColor.RED).append(Component.text(t(en, ja), NamedTextColor.WHITE)));
    }

    void sendInfoKey(final Player player, final String key, final String en, final String ja) {
        player.sendMessage(Component.text("• ", NamedTextColor.GRAY).append(Component.text(tk(key, en, ja), NamedTextColor.WHITE)));
    }

    void sendErrorKey(final Player player, final String key, final String en, final String ja) {
        player.sendMessage(Component.text("[ERR] ", NamedTextColor.RED).append(Component.text(tk(key, en, ja), NamedTextColor.WHITE)));
    }

    void sendInfoKey(
            final Player player,
            final String key,
            final String en,
            final String ja,
            final Map<String, ?> placeholders
    ) {
        player.sendMessage(Component.text("• ", NamedTextColor.GRAY).append(Component.text(tkp(key, en, ja, placeholders), NamedTextColor.WHITE)));
    }
}
