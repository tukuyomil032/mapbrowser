package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;
import com.tukuyomil032.mapbrowser.util.UrlSecurityValidator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles /mapbrowser and /mb command set.
 */
public final class MapBrowserCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String MENU_TITLE = "MapBrowser Menu";

    private final MapBrowserPlugin plugin;
    private final HashMap<UUID, Integer> perfBenchTaskIds;
    private final NamespacedKey toolKey;
    private final NamespacedKey menuActionKey;
    private final NamespacedKey screenIdKey;
    private final NamespacedKey tileIndexKey;
    private final NamespacedKey autofillKey;

    /**
     * Creates command handler.
     */
    public MapBrowserCommand(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
        this.perfBenchTaskIds = new HashMap<>();
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.menuActionKey = new NamespacedKey(plugin, "menu-action");
        this.screenIdKey = new NamespacedKey(plugin, "screen-id");
        this.tileIndexKey = new NamespacedKey(plugin, "tile-index");
        this.autofillKey = new NamespacedKey(plugin, "autofill-enabled");
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
            sendHeader(sender, "MAPBROWSER COMMANDS");
            sendInfo(sender, "/mb create <w> <h> [name] [--autofill]");
            sendInfo(sender, "/mb menu|gui");
            sendInfo(sender, "/mb select <screen-id|screen-name>");
            sendInfo(sender, "/mb list, /mb info, /mb load [screen], /mb unload [screen], /mb delete|destroy [screen], /mb exit");
            sendInfo(sender, "/mb give-frame|gif <screen> <tile-range>, /mb resize <screen> <w> <h>");
            sendInfo(sender, "/mb config simulate_particle <end_rod|flame>");
            sendInfo(sender, "/mb config language <en|ja>");
            sendInfo(sender, "/mb open <url>, /mb type <text>, /mb back, /mb forward, /mb reload, /mb fps <value>");
            sendInfo(sender, "/mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>");
            sendInfo(sender, "/mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>");
            sendLine(sender);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "menu", "gui" -> handleMenu(sender);
            case "select" -> handleSelect(sender, args);
            case "open" -> handleOpen(sender, args);
            case "type" -> handleType(sender, args);
            case "back" -> handleSimpleBrowserCommand(sender, "GO_BACK");
            case "forward" -> handleSimpleBrowserCommand(sender, "GO_FORWARD");
            case "reload" -> handleSimpleBrowserCommand(sender, "RELOAD");
            case "fps" -> handleFps(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender);
            case "load" -> handleLoad(sender, args);
            case "unload" -> handleUnload(sender, args);
            case "delete", "remove", "destroy" -> handleDestroy(sender, args);
            case "give-frame", "gif" -> handleGiveFrame(sender, args);
            case "resize" -> handleResize(sender, args);
            case "config" -> handleConfig(sender, args);
            case "give" -> handleGive(sender, args);
            case "exit" -> handleExit(sender);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendError(sender, "Unknown subcommand. Use /mb");
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
            return Arrays.asList("create", "menu", "gui", "select", "open", "type", "back", "forward", "reload", "fps", "list", "info", "load", "unload", "delete", "destroy", "give-frame", "gif", "resize", "config", "give", "exit", "admin");
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return rangeValues(plugin.getConfig().getInt("screen.max-width", 8));
        }
        if (args.length == 3 && "create".equalsIgnoreCase(args[0])) {
            return rangeValues(plugin.getConfig().getInt("screen.max-height", 8));
        }
        if (args.length >= 4 && "create".equalsIgnoreCase(args[0])) {
            return List.of("--autofill");
        }
        if (args.length == 2 && ("delete".equalsIgnoreCase(args[0]) || "destroy".equalsIgnoreCase(args[0]) || "resize".equalsIgnoreCase(args[0]) || "load".equalsIgnoreCase(args[0]) || "unload".equalsIgnoreCase(args[0]) || "give-frame".equalsIgnoreCase(args[0]) || "gif".equalsIgnoreCase(args[0]))) {
            return screenNameSuggestions();
        }
        if (args.length == 3 && ("give-frame".equalsIgnoreCase(args[0]) || "gif".equalsIgnoreCase(args[0])) ) {
            if (sender instanceof Player player) {
                final Optional<Screen> target = resolveScreen(args[1], player);
                if (target.isPresent()) {
                    final int total = target.get().getMapIds().length;
                    return List.of("all", "odd", "even", "1", "1-3", "1-" + total);
                }
            }
            return List.of("all", "odd", "even", "1", "1-3");
        }
        if (args.length == 3 && "resize".equalsIgnoreCase(args[0])) {
            return rangeValues(plugin.getConfig().getInt("screen.max-width", 8));
        }
        if (args.length == 4 && "resize".equalsIgnoreCase(args[0])) {
            return rangeValues(plugin.getConfig().getInt("screen.max-height", 8));
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return List.of("simulate_particle", "language");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "simulate_particle".equalsIgnoreCase(args[1])) {
            return List.of("end_rod", "flame");
        }
        if (args.length == 3 && "config".equalsIgnoreCase(args[0]) && "language".equalsIgnoreCase(args[1])) {
            return List.of("en", "ja");
        }
        if (args.length == 2 && "select".equalsIgnoreCase(args[0])) {
            final List<String> names = plugin.getScreenManager().getAllScreens().stream()
                    .sorted(Comparator.comparing(Screen::getCreatedAt).reversed())
                    .limit(20)
                    .map(screen -> screen.getName())
                    .toList();
            final ArrayList<String> values = new ArrayList<>();
            values.add("latest");
            values.addAll(names);
            return values;
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return Arrays.asList("pointer-left", "pointer-right", "pointer", "back", "forward", "reload", "url-bar", "text-input", "text-delete", "text-enter", "scroll", "scroll-up", "scroll-down");
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("status", "deps", "reload", "perf", "perfbench", "stop");
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && ("stop".equalsIgnoreCase(args[1]) || "perf".equalsIgnoreCase(args[1]))) {
            return screenNameSuggestions();
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(args[0]) && "perfbench".equalsIgnoreCase(args[1])) {
            return List.of("30", "60", "120");
        }
        return List.of();
    }

    private boolean handleCreate(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.create")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 3) {
            sendError(sender, "Usage: /mb create <w> <h> [name] [--autofill]");
            return true;
        }

        final int width;
        final int height;
        try {
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);
        } catch (final NumberFormatException ex) {
            sendError(sender, "Width/height must be integer.");
            return true;
        }

        final int maxWidth = plugin.getConfig().getInt("screen.max-width", 8);
        final int maxHeight = plugin.getConfig().getInt("screen.max-height", 8);
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            sendError(sender, "Screen size must be 1.." + maxWidth + " x 1.." + maxHeight);
            return true;
        }

        final int maxScreensPerWorld = plugin.getConfig().getInt("screen.max-screens-per-world", 8);
        final long screensInWorld = plugin.getScreenManager().getAllScreens().stream()
                .filter(screen -> screen.getWorldName().equals(player.getWorld().getName()))
                .count();
        if (screensInWorld >= maxScreensPerWorld) {
            sendError(sender, "Screen limit reached in this world (max=" + maxScreensPerWorld + ").");
            return true;
        }

        boolean autoFillEnabled = false;
        final ArrayList<String> nameParts = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            final String token = args[i];
            if ("--autofill".equalsIgnoreCase(token)) {
                autoFillEnabled = true;
                continue;
            }
            nameParts.add(token);
        }

        final String name = nameParts.isEmpty() ? "screen-" + System.currentTimeMillis() : String.join(" ", nameParts);
        final boolean duplicateName = plugin.getScreenManager().getAllScreens().stream()
            .anyMatch(screen -> screen.getName().equalsIgnoreCase(name));
        if (duplicateName) {
            sendError(sender, "Screen name already exists: " + name);
            return true;
        }

        final Screen screen = plugin.getScreenManager().createScreen(player, BlockFace.NORTH, width, height, name);
        plugin.getScreenManager().setSelected(player.getUniqueId(), screen.getId());
        plugin.getBrowserIPCClient().sendOpen(screen.getId(), width, height, screen.getFps());
        final MapDeliverySummary summary = giveScreenMaps(player, screen, autoFillEnabled);

        sendHeader(sender, "SCREEN CREATED");
        sendOk(sender, "Name: " + screen.getName());
        sendInfo(sender, "ID: " + screen.getId());
        sendInfo(sender, "Size: " + width + "x" + height + " maps");
        sendInfo(sender, "Map total: " + summary.totalMaps() + " (direct=" + summary.directMaps() + ", bundles=" + summary.bundleBoxes() + ")");
        sendInfo(sender, "Autofill: " + (autoFillEnabled ? "enabled" : "disabled") + " (use --autofill to enable)");
        sendInfo(sender, "Starter frame given: place 1 frame + starter map.");
        sendInfo(sender, "Use /mb select " + screen.getName() + " to re-select later.");
        sendInfo(sender, "Placement guide: " + (width * height) + " item frames, no map rotation.");
        sendInfo(sender, "Tile order: left->right, then top->bottom.");
        sendLine(sender);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        return true;
    }

    private boolean handleMenu(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Inventory menu = Bukkit.createInventory(player, 54, Component.text(MENU_TITLE, NamedTextColor.AQUA));

        menu.setItem(10, createMenuItem(Material.ITEM_FRAME, "Create 1x1", "create-1x1", "Quick test screen"));
        menu.setItem(11, createMenuItem(Material.ITEM_FRAME, "Create 2x2", "create-2x2", "Default display size"));
        menu.setItem(12, createMenuItem(Material.COMPASS, "Select Latest", "select-latest", "Pick newest screen"));
        menu.setItem(13, createMenuItem(Material.BOOK, "List Screens", "list", "Show all screens"));
        menu.setItem(14, createMenuItem(Material.WRITABLE_BOOK, "Open URL", "open-url", "Gives URL tool"));
        menu.setItem(15, createMenuItem(Material.COMPASS, "Reload", "reload", "Reload selected page"));
        menu.setItem(16, createMenuItem(Material.CLOCK, "Info", "info", "Show selected screen info"));

        menu.setItem(19, createMenuItem(Material.FEATHER, "Give Left Click", "give-pointer-left", "Click position: left click"));
        menu.setItem(20, createMenuItem(Material.FLINT, "Give Right Click", "give-pointer-right", "Click position: right click"));
        menu.setItem(21, createMenuItem(Material.MAGMA_CREAM, "Give Scroll", "give-scroll", "Right-click down / Shift right-click up"));
        menu.setItem(22, createMenuItem(Material.WRITABLE_BOOK, "Give Text Input", "give-text-input", "Open text input dialog"));
        menu.setItem(23, createMenuItem(Material.WRITABLE_BOOK, "Give URL Bar", "give-url-bar", "Open URL input dialog"));
        menu.setItem(24, createMenuItem(Material.SHEARS, "Give Text Delete", "give-text-delete", "Backspace / clear input"));
        menu.setItem(25, createMenuItem(Material.BOW, "Give Back", "give-back", "Browser back"));
        menu.setItem(26, createMenuItem(Material.ARROW, "Give Forward", "give-forward", "Browser forward"));
        menu.setItem(27, createMenuItem(Material.COMPASS, "Give Reload", "give-reload", "Browser reload"));
        menu.setItem(28, createMenuItem(Material.PRISMARINE_SHARD, "Give Enter", "give-text-enter", "Send Enter key"));

        menu.setItem(31, createMenuItem(Material.REDSTONE, "FPS 5", "fps-5", "Low load mode"));
        menu.setItem(32, createMenuItem(Material.GLOWSTONE_DUST, "FPS 10", "fps-10", "Balanced mode"));
        menu.setItem(33, createMenuItem(Material.BLAZE_POWDER, "FPS 15", "fps-15", "High refresh mode"));

        menu.setItem(40, createMenuItem(Material.BARRIER, "Delete Selected", "delete", "Delete selected screen"));
        menu.setItem(49, createMenuItem(Material.OAK_DOOR, "Exit Selection", "exit", "Clear selected screen"));

        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        sendOk(sender, "Opened MapBrowser menu.");
        return true;
    }

    private boolean handleSelect(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb select <screen-id|screen-name>");
            return true;
        }

        final String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if ("latest".equalsIgnoreCase(query)) {
            final Optional<Screen> latest = plugin.getScreenManager().getAllScreens().stream()
                    .max(Comparator.comparing(Screen::getCreatedAt));
            if (latest.isEmpty()) {
                sendError(sender, "No screens available.");
                return true;
            }
            plugin.getScreenManager().setSelected(player.getUniqueId(), latest.get().getId());
            sendOk(sender, "Selected latest screen: " + latest.get().getName() + " (" + latest.get().getId() + ")");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            return true;
        }

        Optional<Screen> byId = Optional.empty();
        try {
            byId = plugin.getScreenManager().getScreen(UUID.fromString(query));
        } catch (final IllegalArgumentException ignored) {
            // Treat as a name query.
        }

        final Optional<Screen> selected = byId.isPresent()
                ? byId
                : plugin.getScreenManager().getAllScreens().stream()
                .filter(screen -> screen.getName().equalsIgnoreCase(query))
                .findFirst();

        if (selected.isEmpty()) {
            sendError(sender, "Screen not found: " + query);
            return true;
        }

        plugin.getScreenManager().setSelected(player.getUniqueId(), selected.get().getId());
        sendOk(sender, "Selected screen: " + selected.get().getName() + " (" + selected.get().getId() + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        return true;
    }

    private boolean handleOpen(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb open <url>");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, "No selected screen. Create/select one first.");
            return true;
        }

        final String url = args[1];
        final UrlSecurityValidator.ValidationResult result = UrlSecurityValidator.validate(url, plugin.getConfig());
        if (!result.allowed()) {
            sendError(sender, result.valueOrReason());
            return true;
        }

        final Screen screen = selected.get();
        plugin.getScreenManager().ensureLoaded(screen.getId());
        screen.setCurrentUrl(result.valueOrReason());
        plugin.getBrowserIPCClient().sendNavigate(screen.getId(), result.valueOrReason());
        sendOk(sender, "Navigating: " + result.valueOrReason());
        return true;
    }

    private boolean handleType(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb type <text>");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, "No selected screen.");
            return true;
        }

        final String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isBlank()) {
            sendError(sender, "Text is empty.");
            return true;
        }

        final Screen screen = selected.get();
        plugin.getScreenManager().ensureLoaded(screen.getId());
        plugin.getBrowserIPCClient().sendTextInput(screen.getId(), text);
        sendOk(sender, "Typed text into browser.");
        return true;
    }

    private boolean handleSimpleBrowserCommand(final CommandSender sender, final String type) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, "No selected screen.");
            return true;
        }

        final Screen screen = selected.get();
        plugin.getScreenManager().ensureLoaded(screen.getId());
        switch (type) {
            case "GO_BACK" -> plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            case "GO_FORWARD" -> plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            case "RELOAD" -> plugin.getBrowserIPCClient().sendReload(screen.getId());
            default -> {
                sendError(sender, "Unsupported command.");
                return true;
            }
        }

        sendOk(sender, "Sent command: " + type);
        return true;
    }

    private boolean handleFps(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb fps <value>");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, "No selected screen.");
            return true;
        }

        final int fps;
        try {
            fps = Integer.parseInt(args[1]);
        } catch (final NumberFormatException ex) {
            sendError(sender, "FPS must be integer.");
            return true;
        }

        final int maxFps = plugin.getConfig().getInt("screen.max-fps", 30);
        if (fps < 1 || fps > maxFps) {
            sendError(sender, "FPS must be 1.." + maxFps);
            return true;
        }

        final Screen screen = selected.get();
        screen.setFps(fps);
        plugin.getScreenManager().ensureLoaded(screen.getId());
        plugin.getBrowserIPCClient().sendSetFps(screen.getId(), fps);
        sendOk(sender, "FPS updated: " + fps);
        return true;
    }

    private boolean handleLoad(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Optional<Screen> target = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (target.isEmpty()) {
            sendError(sender, args.length >= 2 ? "Screen not found." : "No selected screen.");
            return true;
        }

        final Screen screen = target.get();
        if (plugin.getScreenManager().loadScreen(screen.getId())) {
            sendOk(sender, "Screen loaded: " + screen.getName());
            return true;
        }

        sendError(sender, "Load failed.");
        return true;
    }

    private boolean handleUnload(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Optional<Screen> target = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (target.isEmpty()) {
            sendError(sender, args.length >= 2 ? "Screen not found." : "No selected screen.");
            return true;
        }

        final Screen screen = target.get();
        if (plugin.getScreenManager().unloadScreen(screen.getId())) {
            sendOk(sender, "Screen unloaded: " + screen.getName());
            return true;
        }

        sendError(sender, "Unload failed.");
        return true;
    }

    private boolean handleList(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        final List<Screen> screens = new ArrayList<>(plugin.getScreenManager().getAllScreens());
        sendHeader(sender, "SCREEN LIST (" + screens.size() + ")");
        UUID selectedId = null;
        if (sender instanceof Player player) {
            selectedId = plugin.getScreenManager().getSelected(player.getUniqueId())
                    .map(Screen::getId)
                    .orElse(null);
        }
        for (final Screen screen : screens.stream().sorted(Comparator.comparing(Screen::getCreatedAt)).toList()) {
            final boolean selected = selectedId != null && selectedId.equals(screen.getId());
            sender.sendMessage(Component.text()
                    .append(Component.text(selected ? "[*] " : "[ ] ", selected ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                    .append(Component.text(screen.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" :: " + screen.getId(), NamedTextColor.GRAY))
                    .build());
        }
        sendLine(sender);
        return true;
    }

    private boolean handleInfo(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, "No selected screen.");
            return true;
        }

        final Screen screen = selected.get();
        sendHeader(sender, "SELECTED SCREEN");
        sendInfo(sender, "Name: " + screen.getName());
        sendInfo(sender, "ID: " + screen.getId());
        sendInfo(sender, "URL: " + screen.getCurrentUrl());
        sendInfo(sender, "State: " + screen.getState());
        sendInfo(sender, "Size: " + screen.getWidth() + "x" + screen.getHeight());
        sendInfo(sender, "FPS: " + screen.getFps());
        sendLine(sender);
        return true;
    }

    private boolean handleDestroy(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }

        final Optional<Screen> selected = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, args.length >= 2 ? "Screen not found." : "No selected screen.");
            return true;
        }

        final boolean ok = plugin.getScreenManager().destroyScreen(selected.get().getId());
        if (ok) {
            sendOk(sender, "Screen destroyed.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
        } else {
            sendError(sender, "Destroy failed.");
        }
        return true;
    }

    private boolean handleGiveFrame(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, "No permission.");
            return true;
        }

        if (args.length < 2) {
            sendError(sender, "Usage: /mb give-frame <screen-id|screen-name> <tile-range>");
            sendInfo(sender, "Example: /mb gif test 1-3");
            sendInfo(sender, "Range format: all, odd, even, 1, 1-3, 1,3,5-8");
            return true;
        }

        final Optional<Screen> target;
        final String rangeExpr;
        if (args.length == 2) {
            target = plugin.getScreenManager().getSelected(player.getUniqueId());
            rangeExpr = args[1];
        } else {
            target = resolveScreen(args[1], player);
            rangeExpr = args[2];
        }

        if (target.isEmpty()) {
            sendError(sender, args.length == 2 ? "No selected screen." : "Screen not found.");
            return true;
        }

        final Screen screen = target.get();
        final int tileCount = screen.getMapIds().length;
        if (tileCount <= 0) {
            sendError(sender, "Screen has no map tiles.");
            return true;
        }

        final Optional<List<Integer>> parsed = parseTileRange(rangeExpr, tileCount);
        if (parsed.isEmpty()) {
            sendError(sender, "Invalid tile range: " + rangeExpr);
            sendInfo(sender, "Use all/odd/even or 1-based indexes. Example: all, 1-3, 1,4,6-8");
            sendInfo(sender, "Valid tile range: 1-" + tileCount);
            return true;
        }

        final byte autoFillFlag = detectAutoFillPreference(player, screen);
        int supplied = 0;
        for (final int tileIndex : parsed.get()) {
            giveItemOrDrop(player, createScreenMapItem(screen, tileIndex, autoFillFlag));
            supplied++;
        }

        sendOk(sender, "Frame tiles supplied: " + supplied);
        sendInfo(sender, "Screen: " + screen.getName() + " (" + screen.getId() + ")");
        sendInfo(sender, "Tile range: " + rangeExpr + " (valid 1-" + tileCount + ")");
        return true;
    }

    private Optional<List<Integer>> parseTileRange(final String rangeExpr, final int tileCount) {
        if (rangeExpr == null || rangeExpr.isBlank() || tileCount <= 0) {
            return Optional.empty();
        }

        final String normalized = rangeExpr.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            final ArrayList<Integer> all = new ArrayList<>(tileCount);
            for (int i = 0; i < tileCount; i++) {
                all.add(i);
            }
            return Optional.of(all);
        }
        if ("odd".equals(normalized) || "even".equals(normalized)) {
            final boolean odd = "odd".equals(normalized);
            final ArrayList<Integer> picked = new ArrayList<>();
            for (int i = 1; i <= tileCount; i++) {
                if (odd && (i % 2 == 1)) {
                    picked.add(i - 1);
                }
                if (!odd && (i % 2 == 0)) {
                    picked.add(i - 1);
                }
            }
            return picked.isEmpty() ? Optional.empty() : Optional.of(picked);
        }

        final Set<Integer> selected = new TreeSet<>();
        final String[] tokens = rangeExpr.replace(" ", "").split(",");
        for (final String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            final int dash = token.indexOf('-');
            if (dash >= 0) {
                final String startRaw = token.substring(0, dash);
                final String endRaw = token.substring(dash + 1);
                if (startRaw.isBlank() || endRaw.isBlank()) {
                    return Optional.empty();
                }

                final int start;
                final int end;
                try {
                    start = Integer.parseInt(startRaw);
                    end = Integer.parseInt(endRaw);
                } catch (final NumberFormatException ex) {
                    return Optional.empty();
                }
                if (start < 1 || end < 1 || start > tileCount || end > tileCount) {
                    return Optional.empty();
                }

                final int from = Math.min(start, end);
                final int to = Math.max(start, end);
                for (int index = from; index <= to; index++) {
                    selected.add(index - 1);
                }
                continue;
            }

            final int single;
            try {
                single = Integer.parseInt(token);
            } catch (final NumberFormatException ex) {
                return Optional.empty();
            }
            if (single < 1 || single > tileCount) {
                return Optional.empty();
            }
            selected.add(single - 1);
        }

        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ArrayList<>(selected));
    }

    private boolean handleResize(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.create")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 4) {
            sendError(sender, "Usage: /mb resize <screen-id|screen-name> <w> <h>");
            return true;
        }

        final Optional<Screen> target = resolveScreen(args[1], player);
        if (target.isEmpty()) {
            sendError(sender, "Screen not found.");
            return true;
        }

        final int width;
        final int height;
        try {
            width = Integer.parseInt(args[2]);
            height = Integer.parseInt(args[3]);
        } catch (final NumberFormatException ex) {
            sendError(sender, "Width/height must be integer.");
            return true;
        }

        final int maxWidth = plugin.getConfig().getInt("screen.max-width", 8);
        final int maxHeight = plugin.getConfig().getInt("screen.max-height", 8);
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            sendError(sender, "Screen size must be 1.." + maxWidth + " x 1.." + maxHeight);
            return true;
        }

        final Optional<Screen> resized = plugin.getScreenManager().resizeScreen(target.get().getId(), width, height);
        if (resized.isEmpty()) {
            sendError(sender, "Resize failed.");
            return true;
        }

        plugin.getBrowserIPCClient().sendClose(target.get().getId());
        plugin.getBrowserIPCClient().sendOpen(resized.get().getId(), width, height, resized.get().getFps());
        if (resized.get().getCurrentUrl() != null && !resized.get().getCurrentUrl().isBlank()) {
            plugin.getBrowserIPCClient().sendNavigate(resized.get().getId(), resized.get().getCurrentUrl());
        }

        final MapDeliverySummary summary = giveScreenMaps(player, resized.get(), detectAutoFillPreference(player, resized.get()) == (byte) 1);
        sendOk(sender, "Screen resized to " + width + "x" + height + ".");
        sendInfo(sender, "Map total: " + summary.totalMaps() + " (direct=" + summary.directMaps() + ", bundles=" + summary.bundleBoxes() + ")");
        return true;
    }

    private boolean handleConfig(final CommandSender sender, final String[] args) {
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 3) {
            sendError(sender, "Usage: /mb config <simulate_particle|language> <value>");
            return true;
        }

        if ("language".equalsIgnoreCase(args[1])) {
            final String language = args[2].toLowerCase(Locale.ROOT);
            if (!"en".equals(language) && !"ja".equals(language)) {
                sendError(sender, "Value must be en or ja.");
                return true;
            }
            plugin.getConfig().set("ui.language", language);
            plugin.saveConfig();
            sendOk(sender, "language updated: " + language);
            return true;
        }

        if (!"simulate_particle".equalsIgnoreCase(args[1])) {
            sendError(sender, "Unknown config key: " + args[1]);
            return true;
        }

        final String value = args[2].toLowerCase(Locale.ROOT);
        if (!"end_rod".equals(value) && !"flame".equals(value)) {
            sendError(sender, "Value must be end_rod or flame.");
            return true;
        }

        plugin.getConfig().set("ui.simulate-particle", value);
        plugin.saveConfig();
        sendOk(sender, "simulate_particle updated: " + value);
        return true;
    }

    private Optional<Screen> resolveScreen(final String query, final Player player) {
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

    private List<String> screenNameSuggestions() {
        return plugin.getScreenManager().getAllScreens().stream()
                .sorted(Comparator.comparing(Screen::getCreatedAt).reversed())
                .limit(20)
                .map(Screen::getName)
                .toList();
    }

    private boolean handleGive(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>");
            return true;
        }

        final String key = java.util.Objects.requireNonNull(args[1], "item type").toLowerCase(Locale.ROOT);
        final String path = switch (key) {
            case "pointer-left" -> "items.pointer-left";
            case "pointer-right" -> "items.pointer-right";
            case "pointer" -> "items.pointer";
            case "back" -> "items.back";
            case "forward" -> "items.forward";
            case "reload" -> "items.reload";
            case "url-bar" -> "items.url-bar";
            case "text-input" -> "items.text-input";
            case "text-delete" -> "items.text-delete";
            case "text-enter" -> "items.text-enter";
            case "scroll" -> "items.scroll";
            case "scroll-up" -> "items.scroll-up";
            case "scroll-down" -> "items.scroll-down";
            default -> null;
        };

        if (path == null) {
            sendError(sender, "Unknown item type.");
            return true;
        }

        final Material fallback = fallbackMaterialForTool(key);
        final String materialName = Objects.requireNonNullElse(plugin.getConfig().getString(path, fallback.name()), fallback.name());
        final Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            sendError(sender, "Config has invalid material for " + key + ": " + materialName);
            return true;
        }

        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = java.util.Objects.requireNonNull(item.getItemMeta(), "ItemMeta unavailable");
    final String language = resolveLanguage();
    meta.displayName(Component.text(localizedToolName(language, key), NamedTextColor.GOLD));
        meta.lore(List.of(
        Component.text(localizedToolDescription(language, key), NamedTextColor.GRAY),
                Component.text("Type: " + key, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(
            toolKey,
            PersistentDataType.STRING,
            java.util.Objects.requireNonNull(key, "tool key")
        );
        item.setItemMeta(meta);

        final HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            sendInfo(sender, "Inventory full. Dropped item on ground.");
        }
        sendOk(sender, "Given item: " + key + " (" + material.name() + ")");
        return true;
    }

    private Material fallbackMaterialForTool(final String key) {
        return switch (key) {
            case "pointer-left", "pointer" -> Material.FEATHER;
            case "pointer-right" -> Material.FLINT;
            case "back" -> Material.BOW;
            case "forward" -> Material.ARROW;
            case "reload" -> Material.COMPASS;
            case "url-bar" -> Material.WRITABLE_BOOK;
            case "text-input" -> Material.WRITABLE_BOOK;
            case "text-delete" -> Material.SHEARS;
            case "text-enter" -> Material.PRISMARINE_SHARD;
            case "scroll", "scroll-down" -> Material.MAGMA_CREAM;
            case "scroll-up" -> Material.SLIME_BALL;
            default -> Material.FEATHER;
        };
    }

    private String resolveLanguage() {
        final String configured = plugin.getConfig().getString("ui.language", "en");
        if (configured == null) {
            return "en";
        }
        final String normalized = configured.toLowerCase(Locale.ROOT);
        return "ja".equals(normalized) ? "ja" : "en";
    }

    private String localizedToolName(final String language, final String key) {
        if ("ja".equals(language)) {
            return switch (key) {
                case "pointer-left", "pointer" -> "ブラウザ左クリック";
                case "pointer-right" -> "ブラウザ右クリック";
                case "back" -> "戻る";
                case "forward" -> "進む";
                case "reload" -> "リロード";
                case "url-bar" -> "URL入力";
                case "text-input" -> "テキスト入力";
                case "text-delete" -> "テキスト削除";
                case "text-enter" -> "Enter入力";
                case "scroll", "scroll-up", "scroll-down" -> "スクロール";
                default -> "ブラウザ操作";
            };
        }
        return switch (key) {
            case "pointer-left", "pointer" -> "Browser Left Click";
            case "pointer-right" -> "Browser Right Click";
            case "back" -> "Browser Back";
            case "forward" -> "Browser Forward";
            case "reload" -> "Browser Reload";
            case "url-bar" -> "Browser URL Bar";
            case "text-input" -> "Browser Text Input";
            case "text-delete" -> "Browser Text Delete";
            case "text-enter" -> "Browser Enter";
            case "scroll", "scroll-up", "scroll-down" -> "Browser Scroll";
            default -> "Browser Control";
        };
    }

    private String localizedToolDescription(final String language, final String key) {
        if ("ja".equals(language)) {
            return switch (key) {
                case "pointer-left", "pointer" -> "選択中スクリーンのクリック位置へ左クリックを送信";
                case "pointer-right" -> "選択中スクリーンのクリック位置へ右クリックを送信";
                case "back" -> "選択中スクリーンで戻る";
                case "forward" -> "選択中スクリーンで進む";
                case "reload" -> "選択中スクリーンを再読み込み";
                case "url-bar" -> "右クリックでURL入力を開く";
                case "text-input" -> "右クリックで文字入力を開く";
                case "text-delete" -> "右クリックで1文字削除、Shift+右クリックで全削除";
                case "text-enter" -> "右クリックでEnterキーを送信";
                case "scroll" -> "右クリックで下へ、Shift+右クリックで上へスクロール";
                case "scroll-up" -> "右クリックで上へスクロール";
                case "scroll-down" -> "右クリックで下へスクロール";
                default -> "選択中スクリーンに対する操作アイテム";
            };
        }
        return switch (key) {
            case "pointer-left", "pointer" -> "Right-click a selected screen frame to send left click";
            case "pointer-right" -> "Right-click a selected screen frame to send right click";
            case "back" -> "Go back on selected screen";
            case "forward" -> "Go forward on selected screen";
            case "reload" -> "Reload selected screen";
            case "url-bar" -> "Right-click to open URL input";
            case "text-input" -> "Right-click to open text input";
            case "text-delete" -> "Right-click to backspace, Shift+right-click to clear all";
            case "text-enter" -> "Right-click to send Enter key";
            case "scroll" -> "Right-click scroll down, Shift+right-click scroll up";
            case "scroll-up" -> "Right-click to scroll up";
            case "scroll-down" -> "Right-click to scroll down";
            default -> "Control item for selected browser screen";
        };
    }

    private boolean handleAdmin(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>");
            return true;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            sendHeader(sender, "MAPBROWSER STATUS");
            sendInfo(sender, "IPC connected: " + plugin.getBrowserIPCClient().isConnected());
            sendInfo(sender, "IPC health: " + plugin.getBrowserIPCClient().healthSummary());
            final long readyAge = plugin.getBrowserIPCClient().secondsSinceReady();
            sendInfo(sender, "READY age: " + (readyAge >= 0 ? readyAge + "s" : "never"));
            sendInfo(sender, "Screens: " + plugin.getScreenManager().getAllScreens().size());
            sendLine(sender);
            return true;
        }

        if ("deps".equalsIgnoreCase(args[1])) {
            sendHeader(sender, "DEPENDENCY CHECK");
            sendInfo(sender, "PacketEvents (softdepend): " + pluginState("PacketEvents"));
            sendInfo(sender, "AnvilGUI (softdepend): " + pluginState("AnvilGUI"));
            sendInfo(sender, "spark (optional): " + pluginState("spark"));
            sendLine(sender);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[1])) {
            plugin.reloadConfig();
            sendOk(sender, "Config reloaded.");
            sendInfo(sender, "storage=" + plugin.getConfig().getString("storage", "yaml"));
            sendInfo(sender, "render-distance=" + plugin.getConfig().getInt("screen.render-distance", 64));
            return true;
        }

        if ("perf".equalsIgnoreCase(args[1])) {
            final var ipcStats = plugin.getBrowserIPCClient().snapshotStats();
            final var screenStats = plugin.getBrowserIPCClient().snapshotScreenStats();
            final long uptime = Math.max(1L, ipcStats.uptimeSeconds());
            final long totalInbound = ipcStats.inboundTotal();
            final long perSecond = totalInbound / uptime;
            final Runtime runtime = Runtime.getRuntime();
            final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
            final long maxMb = runtime.maxMemory() / (1024L * 1024L);

            sendHeader(sender, "MAPBROWSER PERF");
            sendInfo(sender, "uptime=" + uptime + "s inbound=" + totalInbound + " msg(" + perSecond + "/s)");
            sendInfo(sender, "frames(full=" + ipcStats.inboundFrame() + ", delta=" + ipcStats.inboundDelta() + ") errors=" + ipcStats.inboundErrorEvent());
            sendInfo(sender, "tps=" + readCurrentTpsText());
            sendInfo(sender, "memory=" + usedMb + "MB/" + maxMb + "MB screens=" + plugin.getScreenManager().getAllScreens().size());
            sendInfo(sender, "audio=" + plugin.getAudioBridge().diagnostics());

            if (args.length >= 3 && sender instanceof Player player) {
                final Optional<Screen> target = resolveScreen(args[2], player);
                if (target.isEmpty()) {
                    sendError(sender, "Screen not found for perf detail: " + args[2]);
                    sendLine(sender);
                    return true;
                }
                final Screen screen = target.get();
                final var detail = screenStats.get(screen.getId());
                sendInfo(sender, "screen=" + screen.getName() + " id=" + screen.getId());
                sendInfo(sender, "state=" + screen.getState() + " size=" + screen.getWidth() + "x" + screen.getHeight() + " fps=" + screen.getFps());
                if (detail == null) {
                    sendInfo(sender, "ipc(per-screen)=no data yet");
                } else {
                    final long since = detail.lastInboundAtEpochMillis() <= 0L
                            ? -1L
                            : Math.max(0L, (System.currentTimeMillis() - detail.lastInboundAtEpochMillis()) / 1000L);
                    sendInfo(sender, "ipc(per-screen) full=" + detail.frameCount() + " delta=" + detail.deltaCount() + " err=" + detail.errorCount() + " last=" + (since >= 0 ? since + "s" : "never"));
                }
                sendLine(sender);
                return true;
            }

            final List<Screen> topScreens = plugin.getScreenManager().getAllScreens().stream()
                    .sorted((left, right) -> Long.compare(
                            totalFrames(screenStats.get(right.getId())),
                            totalFrames(screenStats.get(left.getId()))
                    ))
                    .limit(5)
                    .toList();
            for (final Screen screen : topScreens) {
                final var detail = screenStats.get(screen.getId());
                final long full = detail == null ? 0L : detail.frameCount();
                final long delta = detail == null ? 0L : detail.deltaCount();
                final long errors = detail == null ? 0L : detail.errorCount();
                sendInfo(sender, "screen=" + screen.getName() + " frames=" + (full + delta) + " (f=" + full + " d=" + delta + ") err=" + errors);
            }
            sendLine(sender);
            return true;
        }

        if ("perfbench".equalsIgnoreCase(args[1])) {
            if (!(sender instanceof Player player)) {
                sendError(sender, "Player only command.");
                return true;
            }
            final int durationSec;
            if (args.length >= 3) {
                try {
                    durationSec = Integer.parseInt(args[2]);
                } catch (final NumberFormatException ex) {
                    sendError(sender, "Duration must be integer seconds.");
                    return true;
                }
            } else {
                durationSec = 30;
            }
            if (durationSec < 5 || durationSec > 600) {
                sendError(sender, "Duration must be 5..600 seconds.");
                return true;
            }
            startPerfBench(player, durationSec);
            return true;
        }

        if ("stop".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sendError(sender, "Usage: /mb admin stop <screenId>");
                return true;
            }
            try {
                final UUID screenId = UUID.fromString(args[2]);
                plugin.getBrowserIPCClient().sendClose(screenId);
                sendOk(sender, "Sent CLOSE for " + screenId);
                return true;
            } catch (final IllegalArgumentException ex) {
                sendError(sender, "Invalid UUID format.");
                return true;
            }
        }

        sendError(sender, "Unknown admin command.");
        return true;
    }

    private boolean handleExit(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Player only command.");
            return true;
        }
        plugin.getScreenManager().clearSelected(player.getUniqueId());
        sendOk(sender, "Exited browser operation mode.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        return true;
    }

    private MapDeliverySummary giveScreenMaps(final Player player, final Screen screen, final boolean autoFillEnabled) {
        final int totalMaps = screen.getMapIds().length;
        int directMaps = 0;
        int bundleBoxes = 0;
        final byte autoFillFlag = autoFillEnabled ? (byte) 1 : (byte) 0;

        giveItemOrDrop(player, createStarterFrameItem(screen));
        if (totalMaps > 0) {
            giveItemOrDrop(player, createScreenMapItem(screen, 0, autoFillFlag));
            directMaps++;
        }

        if (totalMaps <= 36) {
            for (int index = 1; index < totalMaps; index++) {
                giveItemOrDrop(player, createScreenMapItem(screen, index, autoFillFlag));
                directMaps++;
            }
            return new MapDeliverySummary(totalMaps, directMaps, bundleBoxes);
        }

        int index = 1;
        while (index < totalMaps) {
            final int endExclusive = Math.min(index + 27, totalMaps);
            giveItemOrDrop(player, createMapBundleBox(screen, index, endExclusive, autoFillFlag));
            bundleBoxes++;
            index = endExclusive;
        }

        return new MapDeliverySummary(totalMaps, directMaps, bundleBoxes);
    }

    private ItemStack createScreenMapItem(final Screen screen, final int tileIndex, final byte autoFillFlag) {
        final int[] mapIds = screen.getMapIds();
        final ItemStack mapItem = new ItemStack(Material.FILLED_MAP, 1);
        if (!(mapItem.getItemMeta() instanceof MapMeta meta)) {
            return mapItem;
        }

        final int mapId = mapIds[tileIndex];
        final MapView mapView = Bukkit.getMap(mapId);
        if (mapView != null) {
            meta.setMapView(mapView);
        }
        meta.displayName(Component.text("MapBrowser Display Tile", NamedTextColor.AQUA));
        final int row = (tileIndex / screen.getWidth()) + 1;
        final int col = (tileIndex % screen.getWidth()) + 1;
        meta.lore(List.of(
                Component.text("Screen: " + screen.getName(), NamedTextColor.GRAY),
                Component.text("Tile: " + col + "," + row + " / " + screen.getWidth() + "x" + screen.getHeight(), NamedTextColor.DARK_GRAY),
                Component.text("ID: " + mapId, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(screenIdKey, PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(tileIndexKey, PersistentDataType.INTEGER, tileIndex);
        if (autoFillFlag == (byte) 1) {
            meta.getPersistentDataContainer().set(autofillKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(autofillKey);
        }
        mapItem.setItemMeta(meta);
        return mapItem;
    }

    private ItemStack createStarterFrameItem(final Screen screen) {
        final ItemStack frame = new ItemStack(Material.ITEM_FRAME, 1);
        final ItemMeta meta = java.util.Objects.requireNonNull(frame.getItemMeta(), "Frame meta unavailable");
        meta.displayName(Component.text("MapBrowser Starter Frame", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Place 1 frame, insert starter map, then auto-fill.", NamedTextColor.GRAY),
                Component.text("Screen: " + screen.getName(), NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "screen-id"), PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "starter-frame"), PersistentDataType.BYTE, (byte) 1);
        frame.setItemMeta(meta);
        return frame;
    }

    private ItemStack createMapBundleBox(final Screen screen, final int startInclusive, final int endExclusive, final byte autoFillFlag) {
        final ItemStack shulkerItem = new ItemStack(Material.SHULKER_BOX, 1);
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockMeta)) {
            return shulkerItem;
        }
        if (!(blockMeta.getBlockState() instanceof ShulkerBox shulker)) {
            return shulkerItem;
        }

        int slot = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            shulker.getInventory().setItem(slot++, createScreenMapItem(screen, i, autoFillFlag));
        }
        blockMeta.setBlockState(shulker);
        blockMeta.displayName(Component.text("MapBrowser Map Bundle", NamedTextColor.LIGHT_PURPLE));
        blockMeta.lore(List.of(
                Component.text("Screen: " + screen.getName(), NamedTextColor.GRAY),
                Component.text("Tiles: " + (startInclusive + 1) + ".." + endExclusive, NamedTextColor.DARK_GRAY)
        ));
        shulkerItem.setItemMeta(blockMeta);
        return shulkerItem;
    }

    private void giveItemOrDrop(final Player player, final ItemStack item) {
        final HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private List<String> rangeValues(final int maxValue) {
        final int max = Math.max(1, maxValue);
        final ArrayList<String> values = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            values.add(String.valueOf(i));
        }
        return values;
    }

    private byte detectAutoFillPreference(final Player player, final Screen screen) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.FILLED_MAP || !stack.hasItemMeta()) {
                continue;
            }
            if (!(stack.getItemMeta() instanceof MapMeta meta)) {
                continue;
            }
            final String sid = meta.getPersistentDataContainer().get(screenIdKey, PersistentDataType.STRING);
            if (!screen.getId().toString().equals(sid)) {
                continue;
            }
            final Byte enabled = meta.getPersistentDataContainer().get(autofillKey, PersistentDataType.BYTE);
            return enabled != null && enabled == (byte) 1 ? (byte) 1 : (byte) 0;
        }
        return 0;
    }

    private record MapDeliverySummary(int totalMaps, int directMaps, int bundleBoxes) {
    }

    @EventHandler
    public void onMenuClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!MENU_TITLE.equals(title)) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        final ItemMeta meta = clicked.getItemMeta();
        final String action = meta.getPersistentDataContainer().get(menuActionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) {
            return;
        }

        if ("open-url".equals(action)) {
            sendInfo(player, "Use URL bar item and right-click block to open anvil input.");
        }

        player.closeInventory();
        runAsPlayerCommand(player, actionToCommand(action));
    }

    private ItemStack createMenuItem(
            final Material material,
            final String title,
            final String action,
            final String description
    ) {
        final ItemStack stack = new ItemStack(material, 1);
        final ItemMeta meta = java.util.Objects.requireNonNull(stack.getItemMeta(), "Menu item meta unavailable");
        meta.displayName(Component.text(title, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(menuActionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private String actionToCommand(final String action) {
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        return switch (action) {
            case "create-1x1" -> "mb create 1 1 quick-" + suffix;
            case "create-2x2" -> "mb create 2 2 quick-" + suffix;
            case "select-latest" -> "mb select latest";
            case "list" -> "mb list";
            case "open-url" -> "mb give url-bar";
            case "reload" -> "mb reload";
            case "info" -> "mb info";
            case "give-pointer", "give-pointer-left" -> "mb give pointer-left";
            case "give-pointer-right" -> "mb give pointer-right";
            case "give-back" -> "mb give back";
            case "give-forward" -> "mb give forward";
            case "give-reload" -> "mb give reload";
            case "give-text-input" -> "mb give text-input";
            case "give-text-delete" -> "mb give text-delete";
            case "give-text-enter" -> "mb give text-enter";
            case "give-scroll" -> "mb give scroll";
            case "give-scroll-up" -> "mb give scroll-up";
            case "give-scroll-down" -> "mb give scroll-down";
            case "give-url-bar" -> "mb give url-bar";
            case "fps-5" -> "mb fps 5";
            case "fps-10" -> "mb fps 10";
            case "fps-15" -> "mb fps 15";
            case "delete" -> "mb delete";
            case "exit" -> "mb exit";
            default -> "mb";
        };
    }

    private void runAsPlayerCommand(final Player player, final String commandLine) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(commandLine));
    }

    private static long totalFrames(final com.tukuyomil032.mapbrowser.ipc.BrowserIPCClient.ScreenIpcStatsSnapshot snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        return snapshot.frameCount() + snapshot.deltaCount();
    }

    private String readCurrentTpsText() {
        try {
            final double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) {
                return "N/A";
            }
            return String.format(java.util.Locale.ROOT, "%.2f", tps[0]);
        } catch (final Throwable ignored) {
            return "N/A";
        }
    }

    private void startPerfBench(final Player player, final int durationSec) {
        final UUID playerId = player.getUniqueId();
        final Integer existingTaskId = perfBenchTaskIds.remove(playerId);
        if (existingTaskId != null) {
            Bukkit.getScheduler().cancelTask(existingTaskId);
        }

        final var startStats = plugin.getBrowserIPCClient().snapshotStats();
        final long startedAt = System.currentTimeMillis();
        final double[] samples = new double[durationSec];
        final int[] sampleIndex = {0};

        sendHeader(player, "MAPBROWSER PERFBENCH");
        sendInfo(player, "Duration: " + durationSec + "s");
        sendInfo(player, "Collecting TPS samples...");
        sendLine(player);

        final int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                final Integer removed = perfBenchTaskIds.remove(playerId);
                if (removed != null) {
                    Bukkit.getScheduler().cancelTask(removed);
                }
                return;
            }

            final int index = sampleIndex[0];
            if (index >= durationSec) {
                final Integer removed = perfBenchTaskIds.remove(playerId);
                if (removed != null) {
                    Bukkit.getScheduler().cancelTask(removed);
                }

                final var endStats = plugin.getBrowserIPCClient().snapshotStats();
                final long elapsedSec = Math.max(1L, (System.currentTimeMillis() - startedAt) / 1000L);
                double min = 20.0;
                double max = 0.0;
                double sum = 0.0;
                for (final double sample : samples) {
                    min = Math.min(min, sample);
                    max = Math.max(max, sample);
                    sum += sample;
                }
                final double avg = sum / samples.length;

                final long frameDelta = Math.max(0L, endStats.inboundFrame() - startStats.inboundFrame());
                final long deltaDelta = Math.max(0L, endStats.inboundDelta() - startStats.inboundDelta());

                sendHeader(player, "MAPBROWSER PERFBENCH RESULT");
                sendInfo(player, String.format(java.util.Locale.ROOT, "tps avg=%.2f min=%.2f max=%.2f", avg, min, max));
                sendInfo(player, "elapsed=" + elapsedSec + "s fullFrames=" + frameDelta + " deltaFrames=" + deltaDelta);
                sendInfo(player, "frames/sec=" + ((frameDelta + deltaDelta) / elapsedSec));
                sendLine(player);
                return;
            }

            samples[index] = readCurrentTpsValue();
            sampleIndex[0] = index + 1;
        }, 20L, 20L);

        perfBenchTaskIds.put(playerId, taskId);
    }

    private double readCurrentTpsValue() {
        try {
            final double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) {
                return 20.0;
            }
            return Math.max(0.0, tps[0]);
        } catch (final Throwable ignored) {
            return 20.0;
        }
    }

    private String pluginState(final String pluginName) {
        final var found = Bukkit.getPluginManager().getPlugin(pluginName);
        if (found == null) {
            return "not installed";
        }
        return found.isEnabled()
                ? "enabled v" + found.getPluginMeta().getVersion()
                : "installed but disabled";
    }

    private void sendHeader(final CommandSender sender, final String title) {
        sender.sendMessage(Component.text("+----------------------------------------+", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("| ", NamedTextColor.AQUA).append(Component.text(title, NamedTextColor.WHITE)));
    }

    private void sendLine(final CommandSender sender) {
        sender.sendMessage(Component.text("+----------------------------------------+", NamedTextColor.DARK_GRAY));
    }

    private void sendOk(final CommandSender sender, final String message) {
        sender.sendMessage(Component.text("[OK] ", NamedTextColor.GREEN).append(Component.text(message, NamedTextColor.WHITE)));
    }

    private void sendError(final CommandSender sender, final String message) {
        sender.sendMessage(Component.text("[ERR] ", NamedTextColor.RED).append(Component.text(message, NamedTextColor.WHITE)));
    }

    private void sendInfo(final CommandSender sender, final String message) {
        sender.sendMessage(Component.text("[i] ", NamedTextColor.GRAY).append(Component.text(message, NamedTextColor.WHITE)));
    }
}
