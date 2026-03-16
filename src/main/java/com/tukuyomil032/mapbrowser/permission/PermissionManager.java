package com.tukuyomil032.mapbrowser.permission;

import org.bukkit.command.CommandSender;

/**
 * Permission resolver with simple Bukkit fallback.
 */
public final class PermissionManager {

    /**
     * Checks whether the sender owns a permission.
     */
    public boolean has(final CommandSender sender, final String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("mapbrowser.admin");
    }
}
