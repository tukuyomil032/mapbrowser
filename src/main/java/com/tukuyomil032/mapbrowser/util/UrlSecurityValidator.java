package com.tukuyomil032.mapbrowser.util;

import java.net.URI;
import java.util.regex.Pattern;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Validates URLs against plugin security configuration.
 */
public final class UrlSecurityValidator {
    private static final Pattern PRIVATE_CIDR_A = Pattern.compile("^10\\..*");
    private static final Pattern PRIVATE_CIDR_B = Pattern.compile("^192\\.168\\..*");
    private static final Pattern PRIVATE_CIDR_C = Pattern.compile("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");

    private UrlSecurityValidator() {
    }

    /**
     * Validates whether a URL is allowed by config.
     */
    public static ValidationResult validate(final String rawUrl, final FileConfiguration config) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (final Exception ex) {
            return ValidationResult.invalid("Invalid URL format.");
        }

        final String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return ValidationResult.invalid("Only http/https URL is allowed.");
        }

        if (!config.getBoolean("security.allow-http", false) && scheme.equalsIgnoreCase("http")) {
            return ValidationResult.invalid("HTTP is disabled by server config.");
        }

        final String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return ValidationResult.invalid("URL host is missing.");
        }

        if (config.getBoolean("security.block-local-network", true) && isPrivateAddressHost(host)) {
            return ValidationResult.invalid("Access to local/private network is blocked.");
        }

        for (final String blocked : config.getStringList("security.url-blacklist")) {
            if (matchesWildcard(host, blocked)) {
                return ValidationResult.invalid("Host is blocked by blacklist.");
            }
        }

        final var whitelist = config.getStringList("security.url-whitelist");
        if (!whitelist.isEmpty()) {
            boolean allowed = false;
            for (final String allowedHost : whitelist) {
                if (matchesWildcard(host, allowedHost)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return ValidationResult.invalid("Host is not included in whitelist.");
            }
        }

        return ValidationResult.valid(uri.toString());
    }

    private static boolean matchesWildcard(final String host, final String pattern) {
        final String escaped = pattern.replace(".", "\\.").replace("*", ".*");
        return host.matches("(?i)^" + escaped + "$");
    }

    private static boolean isPrivateAddressHost(final String host) {
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
            return true;
        }

        if (PRIVATE_CIDR_A.matcher(host).matches()) {
            return true;
        }
        if (PRIVATE_CIDR_B.matcher(host).matches()) {
            return true;
        }
        return PRIVATE_CIDR_C.matcher(host).matches();
    }

    /**
     * Immutable validation result.
     */
    public record ValidationResult(boolean allowed, String valueOrReason) {
        /**
         * Builds allowed result.
         */
        public static ValidationResult valid(final String value) {
            return new ValidationResult(true, value);
        }

        /**
         * Builds rejected result.
         */
        public static ValidationResult invalid(final String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
