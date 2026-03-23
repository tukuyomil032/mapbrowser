package com.tukuyomil032.mapbrowser.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    MessageLocalizer(
            final Map<String, String> enExact,
            final Map<String, String> jaExact,
            final Map<String, String> enPrefix,
            final Map<String, String> jaPrefix,
            final Map<String, String> enKeys,
            final Map<String, String> jaKeys
    ) {
        this.enExact = new LinkedHashMap<>(enExact);
        this.jaExact = new LinkedHashMap<>(jaExact);
        this.enPrefix = new LinkedHashMap<>(enPrefix);
        this.jaPrefix = new LinkedHashMap<>(jaPrefix);
        this.enKeys = new LinkedHashMap<>(enKeys);
        this.jaKeys = new LinkedHashMap<>(jaKeys);
    }

    /**
     * Translates a free-form message according to current language setting.
     */
    public String translateRaw(final String language, final String source) {
        if (source == null) {
            return "";
        }

        for (final String candidate : localeCandidates(language)) {
            final Map<String, String> exact = exactCatalog(candidate);
            final Map<String, String> prefix = prefixCatalog(candidate);

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
        }

        return source;
    }

    /**
     * Translates a stable message key. Falls back to defaultText when key is missing.
     */
    public String translateKey(final String language, final String key, final String defaultText) {
        return translateKey(language, key, defaultText, Map.of());
    }

    /**
     * Translates a stable message key with placeholder values.
     */
    public String translateKey(
            final String language,
            final String key,
            final String defaultText,
            final Map<String, ?> placeholders
    ) {
        if (key == null || key.isBlank()) {
            return defaultText == null ? "" : defaultText;
        }

        String resolved = null;
        for (final String candidate : localeCandidates(language)) {
            final Map<String, String> keys = keyCatalog(candidate);
            final String value = keys.get(key);
            if (value != null) {
                resolved = value;
                break;
            }
        }

        if (resolved == null) {
            resolved = defaultText == null ? key : defaultText;
        }
        return applyPlaceholders(resolved, placeholders);
    }

    private String applyPlaceholders(final String template, final Map<String, ?> placeholders) {
        if (template == null || template.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return template == null ? "" : template;
        }

        String out = template;
        for (final Map.Entry<String, ?> entry : placeholders.entrySet()) {
            final String token = "{" + entry.getKey() + "}";
            final String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            out = out.replace(token, value);
        }
        return out;
    }

    private List<String> localeCandidates(final String language) {
        final String normalized = language == null
                ? ""
                : language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("ja")) {
            return List.of("ja", "en");
        }
        return List.of("en");
    }

    private Map<String, String> exactCatalog(final String locale) {
        return "ja".equals(locale) ? jaExact : enExact;
    }

    private Map<String, String> prefixCatalog(final String locale) {
        return "ja".equals(locale) ? jaPrefix : enPrefix;
    }

    private Map<String, String> keyCatalog(final String locale) {
        return "ja".equals(locale) ? jaKeys : enKeys;
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
