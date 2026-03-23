package com.tukuyomil032.mapbrowser.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

/**
 * Provides language-aware message translation with exact/prefix rules.
 */
public final class MessageLocalizer {
    private final Map<String, String> enExact;
    private final Map<String, String> jaExact;
    private final Map<String, String> enPrefix;
    private final Map<String, String> jaPrefix;
    private final Map<String, String> enKeys;
    private final Map<String, String> jaKeys;

    /**
     * Creates and loads message catalogs from bundled yaml resources.
     */
    public MessageLocalizer(final MapBrowserPlugin plugin) {
        this.enExact = new LinkedHashMap<>();
        this.jaExact = new LinkedHashMap<>();
        this.enPrefix = new LinkedHashMap<>();
        this.jaPrefix = new LinkedHashMap<>();
        this.enKeys = new LinkedHashMap<>();
        this.jaKeys = new LinkedHashMap<>();
        load(plugin, "messages_en.yml", enExact, enPrefix);
        load(plugin, "messages_input_en.yml", enExact, enPrefix);
        load(plugin, "messages_admin_en.yml", enExact, enPrefix);
        load(plugin, "messages_ja.yml", jaExact, jaPrefix);
        load(plugin, "messages_input_ja.yml", jaExact, jaPrefix);
        load(plugin, "messages_admin_ja.yml", jaExact, jaPrefix);
        loadKeys(plugin, "messages_keys_en.yml", enKeys);
        loadKeys(plugin, "messages_keys_ja.yml", jaKeys);
    }

    /**
     * Translates a free-form message according to current language setting.
     */
    public String translateRaw(final String language, final String source) {
        if (source == null) {
            return "";
        }

        final boolean ja = "ja".equalsIgnoreCase(language);
        final Map<String, String> exact = ja ? jaExact : enExact;
        final Map<String, String> prefix = ja ? jaPrefix : enPrefix;

        final String exactMatch = exact.get(source);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (final Map.Entry<String, String> entry : prefix.entrySet()) {
            final String from = entry.getKey();
            if (source.startsWith(from)) {
                return entry.getValue() + source.substring(from.length());
            }
        }

        return source;
    }

    /**
     * Translates a stable message key. Falls back to defaultText when key is missing.
     */
    public String translateKey(final String language, final String key, final String defaultText) {
        if (key == null || key.isBlank()) {
            return defaultText == null ? "" : defaultText;
        }
        final Map<String, String> keys = "ja".equalsIgnoreCase(language) ? jaKeys : enKeys;
        final String value = keys.get(key);
        if (value != null) {
            return value;
        }
        return defaultText == null ? key : defaultText;
    }

    private void load(
            final MapBrowserPlugin plugin,
            final String resource,
            final Map<String, String> exactOut,
            final Map<String, String> prefixOut
    ) {
        final InputStream stream = plugin.getResource(resource);
        if (stream == null) {
            plugin.getLogger().log(Level.WARNING, "Missing message resource: {0}", resource);
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        final ConfigurationSection exactSection = yaml.getConfigurationSection("messages.exact");
        if (exactSection != null) {
            for (final String key : exactSection.getKeys(false)) {
                final String value = exactSection.getString(key, key);
                exactOut.put(key, value);
            }
        }

        final ConfigurationSection prefixSection = yaml.getConfigurationSection("messages.prefix");
        if (prefixSection != null) {
            for (final String key : prefixSection.getKeys(false)) {
                final String value = prefixSection.getString(key, key);
                prefixOut.put(key, value);
            }
        }
    }

    private void loadKeys(
            final MapBrowserPlugin plugin,
            final String resource,
            final Map<String, String> keyOut
    ) {
        final InputStream stream = plugin.getResource(resource);
        if (stream == null) {
            plugin.getLogger().log(Level.WARNING, "Missing message resource: {0}", resource);
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        );

        final ConfigurationSection keySection = yaml.getConfigurationSection("messages.keys");
        if (keySection != null) {
            for (final String key : keySection.getKeys(false)) {
                final String value = keySection.getString(key, key);
                keyOut.put(key, value);
            }
        }
    }
}
