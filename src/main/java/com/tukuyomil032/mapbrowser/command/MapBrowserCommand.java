package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;
import com.tukuyomil032.mapbrowser.util.UrlSecurityValidator;

/**
 * Handles /mapbrowser and /mb command set.
 */
public final class MapBrowserCommand implements CommandExecutor, TabCompleter {
    private final MapBrowserPlugin plugin;

    /**
     * Creates command handler.
     */
    public MapBrowserCommand(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes command.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/mb create <w> <h> [name]");
            sender.sendMessage("/mb open <url>");
            sender.sendMessage("/mb back|forward|reload|fps <value>|list|info");
            sender.sendMessage("/mb give <pointer|back|forward|reload|url-bar|scroll-up|scroll-down>");
            sender.sendMessage("/mb admin status");
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "open" -> handleOpen(sender, args);
            case "back" -> handleSimpleBrowserCommand(sender, "GO_BACK");
            case "forward" -> handleSimpleBrowserCommand(sender, "GO_FORWARD");
            case "reload" -> handleSimpleBrowserCommand(sender, "RELOAD");
            case "fps" -> handleFps(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "destroy" -> handleDestroy(sender);
            case "give" -> handleGive(sender, args);
            case "exit" -> handleExit(sender);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sender.sendMessage("Unknown subcommand.");
                yield true;
            }
        };
    }

    /**
     * Provides command tab completion.
     */
    @Override
    public List<String> onTabComplete(
            final CommandSender sender,
            final Command command,
            final String alias,
            final String[] args
    ) {
        if (args.length == 1) {
            return Arrays.asList("create", "open", "back", "forward", "reload", "fps", "list", "info", "destroy", "give", "exit", "admin");
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return Arrays.asList("pointer", "back", "forward", "reload", "url-bar", "scroll-up", "scroll-down");
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("status", "stop");
        }
        return List.of();
    }

    private boolean handleCreate(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.create")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /mb create <w> <h> [name]");
            return true;
        }

        final int width;
        final int height;
        try {
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Width/height must be integer.");
            return true;
        }

        final int maxWidth = plugin.getConfig().getInt("screen.max-width", 8);
        final int maxHeight = plugin.getConfig().getInt("screen.max-height", 8);
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            sender.sendMessage("Screen size must be 1.." + maxWidth + " x 1.." + maxHeight);
            return true;
        }

        final String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "screen-" + System.currentTimeMillis();
        final Screen screen = plugin.getScreenManager().createScreen(player, BlockFace.NORTH, width, height, name);
        plugin.getBrowserIPCClient().sendOpen(screen.getId(), width, height, screen.getFps());
        sender.sendMessage("Created screen: " + screen.getName() + " (" + screen.getId() + ")");
        return true;
    }

    private boolean handleOpen(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /mb open <url>");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sender.sendMessage("No selected screen. Create one first.");
            return true;
        }

        final String url = args[1];
        final UrlSecurityValidator.ValidationResult result = UrlSecurityValidator.validate(url, plugin.getConfig());
        if (!result.allowed()) {
            sender.sendMessage(result.valueOrReason());
            return true;
        }

        final Screen screen = selected.get();
        screen.setCurrentUrl(result.valueOrReason());
        plugin.getBrowserIPCClient().sendNavigate(screen.getId(), result.valueOrReason());
        sender.sendMessage("Navigating: " + result.valueOrReason());
        return true;
    }

    private boolean handleSimpleBrowserCommand(final CommandSender sender, final String type) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sender.sendMessage("No selected screen.");
            return true;
        }

        final Screen screen = selected.get();
        switch (type) {
            case "GO_BACK" -> plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            case "GO_FORWARD" -> plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            case "RELOAD" -> plugin.getBrowserIPCClient().sendReload(screen.getId());
            default -> {
                sender.sendMessage("Unsupported command.");
                return true;
            }
        }

        sender.sendMessage("Sent command: " + type);
        return true;
    }

    private boolean handleFps(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /mb fps <value>");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sender.sendMessage("No selected screen.");
            return true;
        }

        final int fps;
        try {
            fps = Integer.parseInt(args[1]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage("FPS must be integer.");
            return true;
        }

        final int maxFps = plugin.getConfig().getInt("screen.max-fps", 20);
        if (fps < 1 || fps > maxFps) {
            sender.sendMessage("FPS must be 1.." + maxFps);
            return true;
        }

        final Screen screen = selected.get();
        screen.setFps(fps);
        plugin.getBrowserIPCClient().sendSetFps(screen.getId(), fps);
        sender.sendMessage("FPS updated: " + fps);
        return true;
    }

    private boolean handleList(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        final List<Screen> screens = new ArrayList<>(plugin.getScreenManager().getAllScreens());
        sender.sendMessage("Screens: " + screens.size());
        for (final Screen screen : screens) {
            sender.sendMessage("- " + screen.getName() + " " + screen.getId());
        }
        return true;
    }

    private boolean handleInfo(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sender.sendMessage("No selected screen.");
            return true;
        }

        final Screen screen = selected.get();
        sender.sendMessage("Screen: " + screen.getName());
        sender.sendMessage("ID: " + screen.getId());
        sender.sendMessage("URL: " + screen.getCurrentUrl());
        sender.sendMessage("State: " + screen.getState());
        sender.sendMessage("Size: " + screen.getWidth() + "x" + screen.getHeight());
        return true;
    }

    private boolean handleDestroy(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sender.sendMessage("No selected screen.");
            return true;
        }

        final boolean ok = plugin.getScreenManager().destroyScreen(selected.get().getId());
        sender.sendMessage(ok ? "Screen destroyed." : "Destroy failed.");
        return true;
    }

    private boolean handleGive(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /mb give <pointer|back|forward|reload|url-bar|scroll-up|scroll-down>");
            return true;
        }

        final String key = args[1].toLowerCase(Locale.ROOT);
        final String path = switch (key) {
            case "pointer" -> "items.pointer";
            case "back" -> "items.back";
            case "forward" -> "items.forward";
            case "reload" -> "items.reload";
            case "url-bar" -> "items.url-bar";
            case "scroll-up" -> "items.scroll-up";
            case "scroll-down" -> "items.scroll-down";
            default -> null;
        };

        if (path == null) {
            sender.sendMessage("Unknown item type.");
            return true;
        }

        final String materialName = Objects.requireNonNullElse(plugin.getConfig().getString(path, "FEATHER"), "FEATHER");
        final Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            sender.sendMessage("Config has invalid material for " + key + ": " + materialName);
            return true;
        }

        player.getInventory().addItem(new ItemStack(material, 1));
        sender.sendMessage("Given item: " + key + " (" + material.name() + ")");
        return true;
    }

    private boolean handleAdmin(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /mb admin status");
            return true;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            sender.sendMessage("IPC connected: " + plugin.getBrowserIPCClient().isConnected());
            sender.sendMessage("Screens: " + plugin.getScreenManager().getAllScreens().size());
            return true;
        }

        if ("stop".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage("Usage: /mb admin stop <screenId>");
                return true;
            }
            try {
                final UUID screenId = UUID.fromString(args[2]);
                plugin.getBrowserIPCClient().sendClose(screenId);
                sender.sendMessage("Sent CLOSE for " + screenId);
                return true;
            } catch (final IllegalArgumentException ex) {
                sender.sendMessage("Invalid UUID format.");
                return true;
            }
        }

        sender.sendMessage("Unknown admin command.");
        return true;
    }

    private boolean handleExit(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only command.");
            return true;
        }
        plugin.getScreenManager().clearSelected(player.getUniqueId());
        sender.sendMessage("Exited browser operation mode.");
        return true;
    }
}
