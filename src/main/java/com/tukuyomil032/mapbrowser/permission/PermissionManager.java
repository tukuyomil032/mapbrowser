package com.tukuyomil032.mapbrowser.permission;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

/**
 * Permission resolver with LuckPerms/Vault/Bukkit fallback.
 */
public final class PermissionManager {
    private final MapBrowserPlugin plugin;
    private final PermissionBackend backend;
    private final Object vaultPermissionProvider;
    private final Method vaultHasMethod;

    /**
     * Creates permission resolver with auto-detected backend.
     */
    public PermissionManager(final MapBrowserPlugin plugin) {
        this.plugin = plugin;

        final VaultResolver vault = resolveVault();
        if (isPluginEnabled("LuckPerms")) {
            this.backend = PermissionBackend.LUCKPERMS;
            this.vaultPermissionProvider = null;
            this.vaultHasMethod = null;
        } else if (vault.available()) {
            this.backend = PermissionBackend.VAULT;
            this.vaultPermissionProvider = vault.provider();
            this.vaultHasMethod = vault.hasMethod();
        } else {
            this.backend = PermissionBackend.BUKKIT;
            this.vaultPermissionProvider = null;
            this.vaultHasMethod = null;
        }

        plugin.getLogger().log(Level.INFO, "Permission backend: {0}", backend.name());
    }

    /**
     * Checks whether the sender owns a permission.
     */
    public boolean has(final CommandSender sender, final String permission) {
        if (sender == null) {
            return false;
        }

        if (sender.hasPermission("mapbrowser.admin")) {
            return true;
        }

        return switch (backend) {
            case LUCKPERMS -> sender.hasPermission(permission);
            case VAULT -> hasViaVault(sender, permission);
            case BUKKIT -> sender.hasPermission(permission);
        };
    }

    private boolean hasViaVault(final CommandSender sender, final String permission) {
        if (vaultPermissionProvider == null || vaultHasMethod == null) {
            return sender.hasPermission(permission);
        }
        try {
            final Object result = vaultHasMethod.invoke(vaultPermissionProvider, sender, permission);
            if (result instanceof Boolean allowed) {
                return allowed;
            }
        } catch (final IllegalAccessException | InvocationTargetException ex) {
            plugin.getLogger().log(Level.WARNING, "Vault permission check failed: {0}", ex.getMessage());
        }
        return sender.hasPermission(permission);
    }

    private boolean isPluginEnabled(final String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @SuppressWarnings({"DataFlowIssue", "ConstantConditions"})
    private VaultResolver resolveVault() {
        if (!isPluginEnabled("Vault")) {
            return VaultResolver.unavailable();
        }

        try {
            final Class<?> permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            @SuppressWarnings("unchecked")
            final Class<Object> rawPermissionClass = (Class<Object>) permissionClass;
            final var registration = Bukkit.getServicesManager().getRegistration(rawPermissionClass);
            if (registration == null) {
                return VaultResolver.unavailable();
            }
            final Object provider = java.util.Objects.requireNonNull(
                    registration.getProvider(),
                    "Vault permission provider unavailable"
            );

            final Method hasMethod = provider.getClass().getMethod("has", CommandSender.class, String.class);
            return VaultResolver.available(provider, hasMethod);
        } catch (final ClassNotFoundException | NoSuchMethodException | NullPointerException ex) {
            plugin.getLogger().log(Level.WARNING, "Vault API not available: {0}", ex.getMessage());
            return VaultResolver.unavailable();
        }
    }

    private enum PermissionBackend {
        LUCKPERMS,
        VAULT,
        BUKKIT
    }

    private record VaultResolver(boolean available, Object provider, Method hasMethod) {
        static VaultResolver available(final Object provider, final Method hasMethod) {
            return new VaultResolver(true, provider, hasMethod);
        }

        static VaultResolver unavailable() {
            return new VaultResolver(false, null, null);
        }
    }
}
