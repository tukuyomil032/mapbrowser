package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
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
    private final NamespacedKey toolKey;
    private final NamespacedKey menuActionKey;

    /**
     * Creates command handler.
     */
    public MapBrowserCommand(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.menuActionKey = new NamespacedKey(plugin, "menu-action");
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
            sendInfo(sender, "/mb create <w> <h> [name]");
            sendInfo(sender, "/mb menu");
            sendInfo(sender, "/mb select <screen-id|screen-name>");
            sendInfo(sender, "/mb list, /mb info, /mb destroy, /mb exit");
            sendInfo(sender, "/mb open <url>, /mb back, /mb forward, /mb reload, /mb fps <value>");
            sendInfo(sender, "/mb give <pointer|back|forward|reload|url-bar|scroll-up|scroll-down>");
            sendInfo(sender, "/mb admin status|deps|stop <screenId>");
            sendLine(sender);
            return true;
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "menu" -> handleMenu(sender);
            case "select" -> handleSelect(sender, args);
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
            return Arrays.asList("create", "menu", "select", "open", "back", "forward", "reload", "fps", "list", "info", "destroy", "give", "exit", "admin");
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
            return Arrays.asList("pointer", "back", "forward", "reload", "url-bar", "scroll-up", "scroll-down");
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("status", "deps", "stop");
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
            sendError(sender, "Usage: /mb create <w> <h> [name]");
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

        final String name = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "screen-" + System.currentTimeMillis();
        final Screen screen = plugin.getScreenManager().createScreen(player, BlockFace.NORTH, width, height, name);
        plugin.getBrowserIPCClient().sendOpen(screen.getId(), width, height, screen.getFps());
        final int delivered = giveScreenMaps(player, screen);

        sendHeader(sender, "SCREEN CREATED");
        sendOk(sender, "Name: " + screen.getName());
        sendInfo(sender, "ID: " + screen.getId());
        sendInfo(sender, "Size: " + width + "x" + height + " maps");
        sendInfo(sender, "Given maps: " + delivered + "/" + (width * height));
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

        menu.setItem(19, createMenuItem(Material.FEATHER, "Give Pointer", "give-pointer", "Click helper"));
        menu.setItem(20, createMenuItem(Material.BOW, "Give Back", "give-back", "Browser back"));
        menu.setItem(21, createMenuItem(Material.ARROW, "Give Forward", "give-forward", "Browser forward"));
        menu.setItem(22, createMenuItem(Material.COMPASS, "Give Reload", "give-reload", "Browser reload"));
        menu.setItem(23, createMenuItem(Material.SLIME_BALL, "Give Scroll Up", "give-scroll-up", "Scroll up"));
        menu.setItem(24, createMenuItem(Material.MAGMA_CREAM, "Give Scroll Down", "give-scroll-down", "Scroll down"));
        menu.setItem(25, createMenuItem(Material.WRITABLE_BOOK, "Give URL Bar", "give-url-bar", "Anvil URL input"));

        menu.setItem(31, createMenuItem(Material.REDSTONE, "FPS 5", "fps-5", "Low load mode"));
        menu.setItem(32, createMenuItem(Material.GLOWSTONE_DUST, "FPS 10", "fps-10", "Balanced mode"));
        menu.setItem(33, createMenuItem(Material.BLAZE_POWDER, "FPS 15", "fps-15", "High refresh mode"));

        menu.setItem(40, createMenuItem(Material.BARRIER, "Destroy Selected", "destroy", "Delete selected screen"));
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
        screen.setCurrentUrl(result.valueOrReason());
        plugin.getBrowserIPCClient().sendNavigate(screen.getId(), result.valueOrReason());
        sendOk(sender, "Navigating: " + result.valueOrReason());
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

        final int maxFps = plugin.getConfig().getInt("screen.max-fps", 20);
        if (fps < 1 || fps > maxFps) {
            sendError(sender, "FPS must be 1.." + maxFps);
            return true;
        }

        final Screen screen = selected.get();
        screen.setFps(fps);
        plugin.getBrowserIPCClient().sendSetFps(screen.getId(), fps);
        sendOk(sender, "FPS updated: " + fps);
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

    private boolean handleDestroy(final CommandSender sender) {
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

        final boolean ok = plugin.getScreenManager().destroyScreen(selected.get().getId());
        if (ok) {
            sendOk(sender, "Screen destroyed.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
        } else {
            sendError(sender, "Destroy failed.");
        }
        return true;
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
            sendError(sender, "Usage: /mb give <pointer|back|forward|reload|url-bar|scroll-up|scroll-down>");
            return true;
        }

        final String key = java.util.Objects.requireNonNull(args[1], "item type").toLowerCase(Locale.ROOT);
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
            sendError(sender, "Unknown item type.");
            return true;
        }

        final String materialName = Objects.requireNonNullElse(plugin.getConfig().getString(path, "FEATHER"), "FEATHER");
        final Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            sendError(sender, "Config has invalid material for " + key + ": " + materialName);
            return true;
        }

        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = java.util.Objects.requireNonNull(item.getItemMeta(), "ItemMeta unavailable");
        meta.displayName(Component.text("MapBrowser " + material.name(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Control item for browser screen", NamedTextColor.GRAY),
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

    private boolean handleAdmin(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            sendError(sender, "No permission.");
            return true;
        }
        if (args.length < 2) {
            sendError(sender, "Usage: /mb admin status|deps|stop <screenId>");
            return true;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            sendHeader(sender, "MAPBROWSER STATUS");
            sendInfo(sender, "IPC connected: " + plugin.getBrowserIPCClient().isConnected());
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

    private int giveScreenMaps(final Player player, final Screen screen) {
        final int[] mapIds = screen.getMapIds();
        int delivered = 0;
        for (int index = 0; index < mapIds.length; index++) {
            final ItemStack mapItem = new ItemStack(Material.FILLED_MAP, 1);
            if (!(mapItem.getItemMeta() instanceof MapMeta meta)) {
                continue;
            }
            final MapView mapView = Bukkit.getMap(mapIds[index]);
            if (mapView != null) {
                meta.setMapView(mapView);
            }
            meta.displayName(Component.text("MapBrowser Display Tile", NamedTextColor.AQUA));
            final int row = (index / screen.getWidth()) + 1;
            final int col = (index % screen.getWidth()) + 1;
            meta.lore(List.of(
                    Component.text("Screen: " + screen.getName(), NamedTextColor.GRAY),
                    Component.text("Tile: " + col + "," + row + " / " + screen.getWidth() + "x" + screen.getHeight(), NamedTextColor.DARK_GRAY),
                    Component.text("ID: " + mapIds[index], NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "screen-id"), PersistentDataType.STRING, screen.getId().toString());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "tile-index"), PersistentDataType.INTEGER, index);
            mapItem.setItemMeta(meta);

            final HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(mapItem);
            if (overflow.isEmpty()) {
                delivered++;
                continue;
            }
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            delivered++;
        }
        return delivered;
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
            case "give-pointer" -> "mb give pointer";
            case "give-back" -> "mb give back";
            case "give-forward" -> "mb give forward";
            case "give-reload" -> "mb give reload";
            case "give-scroll-up" -> "mb give scroll-up";
            case "give-scroll-down" -> "mb give scroll-down";
            case "give-url-bar" -> "mb give url-bar";
            case "fps-5" -> "mb fps 5";
            case "fps-10" -> "mb fps 10";
            case "fps-15" -> "mb fps 15";
            case "destroy" -> "mb destroy";
            case "exit" -> "mb exit";
            default -> "mb";
        };
    }

    private void runAsPlayerCommand(final Player player, final String commandLine) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(commandLine));
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
