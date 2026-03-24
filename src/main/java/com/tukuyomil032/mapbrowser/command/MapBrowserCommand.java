package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;
import com.tukuyomil032.mapbrowser.util.UrlSecurityValidator;

/**
 * Handles /mapbrowser and /mb command set.
 */
public final class MapBrowserCommand implements
    CommandExecutor,
    TabCompleter,
    Listener,
    MapToolItemSupport.MessageBridge,
    MapToolItemSupport.LanguageResolver,
    MapMenuSupport.MessageBridge,
    MapScreenInfoSupport.MessageBridge,
    MapScreenInfoSupport.LanguageResolver,
    MapAdminSupport.MessageBridge,
    MapAdminSupport.ScreenResolver {
    private static final String MENU_TITLE = "MapBrowser Menu";
    private static final Map<String, String> SUBCOMMAND_ALIASES = Map.of(
            "gui", "menu",
            "remove", "delete",
            "destroy", "delete",
            "gif", "give-frame"
    );

    private final MapBrowserPlugin plugin;
    private final HashMap<UUID, Integer> perfBenchTaskIds;
    private final MapTileRangeParser tileRangeParser;
    private final MapItemDistributor itemDistributor;
    private final MapAdminSupport adminSupport;
    private final MapToolItemSupport toolItemSupport;
    private final MapMenuSupport menuSupport;
    private final MapScreenInfoSupport screenInfoSupport;
    private final MapCommandMessageRenderer messageRenderer;
    private final MapCommandLocalization localization;
    private final MapScreenQueryResolver screenQueryResolver;
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
        this.tileRangeParser = new MapTileRangeParser();
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.menuActionKey = new NamespacedKey(plugin, "menu-action");
        this.screenIdKey = new NamespacedKey(plugin, "screen-id");
        this.tileIndexKey = new NamespacedKey(plugin, "tile-index");
        this.autofillKey = new NamespacedKey(plugin, "autofill-enabled");
        this.localization = new MapCommandLocalization(plugin);
        this.screenQueryResolver = new MapScreenQueryResolver(plugin);
        this.messageRenderer = new MapCommandMessageRenderer(this::localizeMessage);
        this.itemDistributor = new MapItemDistributor(plugin, screenIdKey, tileIndexKey, autofillKey);
        this.toolItemSupport = new MapToolItemSupport(plugin, toolKey, this, this);
        this.menuSupport = new MapMenuSupport(plugin, MENU_TITLE, menuActionKey, this);
        this.screenInfoSupport = new MapScreenInfoSupport(plugin, this, this);
        this.adminSupport = new MapAdminSupport(plugin, perfBenchTaskIds, this, this);
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
            sendHeader(sender, tk("command.help.title", "MAPBROWSER COMMANDS", "MapBrowser コマンド一覧"));
            sendInfo(sender, tk("command.help.create", "/mb create <w> <h> [name] [--autofill]", "/mb create <w> <h> [name] [--autofill]"));
            sendInfo(sender, tk("command.help.menu", "/mb menu (alias: gui)", "/mb menu (alias: gui)"));
            sendInfo(sender, tk("command.help.select", "/mb select <screen-id|screen-name>", "/mb select <screen-id|screen-name>"));
            sendInfo(sender, tk("command.help.core", "/mb list, /mb info, /mb load [screen], /mb unload [screen], /mb delete [screen] (alias: remove, destroy), /mb exit", "/mb list, /mb info, /mb load [screen], /mb unload [screen], /mb delete [screen] (alias: remove, destroy), /mb exit"));
            sendInfo(sender, tk("command.help.frame", "/mb give-frame <screen> <tile-range> (alias: gif), /mb resize <screen> <w> <h>", "/mb give-frame <screen> <tile-range> (alias: gif), /mb resize <screen> <w> <h>"));
            sendInfo(sender, tk("command.help.config-particle", "/mb config simulate_particle <end_rod|flame>", "/mb config simulate_particle <end_rod|flame>"));
            sendInfo(sender, tk("command.help.config-language", "/mb config language <en|ja|ja-JP>", "/mb config language <en|ja|ja-JP>"));
            sendInfo(sender, tk("command.help.browser", "/mb open <url>, /mb type <text>, /mb back, /mb forward, /mb reload, /mb fps <value>", "/mb open <url>, /mb type <text>, /mb back, /mb forward, /mb reload, /mb fps <value>"));
            sendInfo(sender, tk("command.help.tools", "/mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>", "/mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>"));
            sendInfo(sender, tk("command.help.admin", "/mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>", "/mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>"));
            sendLine(sender);
            return true;
        }

        final String sub = canonicalSubcommand(args[0]);
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "menu" -> handleMenu(sender);
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
            case "delete" -> handleDestroy(sender, args);
            case "give-frame" -> handleGiveFrame(sender, args);
            case "resize" -> handleResize(sender, args);
            case "config" -> handleConfig(sender, args);
            case "give" -> handleGive(sender, args);
            case "exit" -> handleExit(sender);
            case "admin" -> handleAdmin(sender, args);
            default -> {
                sendError(sender, tk("command.error.unknown-subcommand", "Unknown subcommand. Use /mb", "不明なサブコマンドです。/mb を使用してください。"));
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
        final String sub = args.length > 0 ? canonicalSubcommand(args[0]) : "";
        if (args.length == 1) {
            return Arrays.asList("create", "menu", "select", "open", "type", "back", "forward", "reload", "fps", "list", "info", "load", "unload", "delete", "give-frame", "resize", "config", "give", "exit", "admin");
        }
        if (args.length == 2 && "create".equals(sub)) {
            return itemDistributor.rangeValues(plugin.getConfig().getInt("screen.max-width", 8));
        }
        if (args.length == 3 && "create".equals(sub)) {
            return itemDistributor.rangeValues(plugin.getConfig().getInt("screen.max-height", 8));
        }
        if (args.length >= 4 && "create".equals(sub)) {
            return List.of("--autofill");
        }
        if (args.length == 2 && ("delete".equals(sub) || "resize".equals(sub) || "load".equals(sub) || "unload".equals(sub) || "give-frame".equals(sub))) {
            return screenNameSuggestions();
        }
        if (args.length == 3 && "give-frame".equals(sub)) {
            if (sender instanceof Player player) {
                final Optional<Screen> target = resolveScreen(args[1], player);
                if (target.isPresent()) {
                    final Screen screen = target.get();
                    final int total = screen.getMapIds().length;
                    return List.of("all", "odd", "even", "1-1", "1-2", "1-1:2-2", "1..3", "1.." + total, "1");
                }
            }
            return List.of("all", "odd", "even", "1-1", "1-2", "1-1:2-2", "1..3", "1");
        }
        if (args.length == 3 && "resize".equals(sub)) {
            return itemDistributor.rangeValues(plugin.getConfig().getInt("screen.max-width", 8));
        }
        if (args.length == 4 && "resize".equals(sub)) {
            return itemDistributor.rangeValues(plugin.getConfig().getInt("screen.max-height", 8));
        }
        if (args.length == 2 && "config".equals(sub)) {
            return List.of("simulate_particle", "language");
        }
        if (args.length == 3 && "config".equals(sub) && "simulate_particle".equalsIgnoreCase(args[1])) {
            return List.of("end_rod", "flame");
        }
        if (args.length == 3 && "config".equals(sub) && "language".equalsIgnoreCase(args[1])) {
            return List.of("en", "ja", "ja-JP");
        }
        if (args.length == 2 && "select".equals(sub)) {
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
        if (args.length == 2 && "give".equals(sub)) {
            return Arrays.asList("pointer-left", "pointer-right", "pointer", "back", "forward", "reload", "url-bar", "text-input", "text-delete", "text-enter", "scroll", "scroll-up", "scroll-down");
        }
        if (args.length == 2 && "admin".equals(sub)) {
            return List.of("status", "deps", "reload", "perf", "perfbench", "stop");
        }
        if (args.length == 3 && "admin".equals(sub) && ("stop".equalsIgnoreCase(args[1]) || "perf".equalsIgnoreCase(args[1]))) {
            return screenNameSuggestions();
        }
        if (args.length == 3 && "admin".equals(sub) && "perfbench".equalsIgnoreCase(args[1])) {
            return List.of("30", "60", "120");
        }
        return List.of();
    }

    private String canonicalSubcommand(final String raw) {
        final String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        return SUBCOMMAND_ALIASES.getOrDefault(normalized, normalized);
    }

    private boolean handleCreate(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.create")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 3) {
            sendError(sender, tk("command.usage.create", "Usage: /mb create <w> <h> [name] [--autofill]", "使用法: /mb create <w> <h> [name] [--autofill]"));
            return true;
        }

        final int width;
        final int height;
        try {
            width = Integer.parseInt(args[1]);
            height = Integer.parseInt(args[2]);
        } catch (final NumberFormatException ex) {
            sendError(sender, tk("command.error.size-integer", "Width/height must be integer.", "幅と高さは整数で入力してください。"));
            return true;
        }

        final int maxWidth = plugin.getConfig().getInt("screen.max-width", 8);
        final int maxHeight = plugin.getConfig().getInt("screen.max-height", 8);
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            sendError(sender, tkp(
                    "command.error.screen-size-range",
                    "Screen size must be 1..{maxWidth} x 1..{maxHeight}",
                    "スクリーンサイズは 1..{maxWidth} x 1..{maxHeight} の範囲で指定してください。",
                    Map.of("maxWidth", maxWidth, "maxHeight", maxHeight)
            ));
            return true;
        }

        final int maxScreensPerWorld = plugin.getConfig().getInt("screen.max-screens-per-world", 8);
        final long screensInWorld = plugin.getScreenManager().getAllScreens().stream()
                .filter(screen -> screen.getWorldName().equals(player.getWorld().getName()))
                .count();
        if (screensInWorld >= maxScreensPerWorld) {
            sendError(sender, tkp(
                    "command.error.screen-limit-world",
                    "Screen limit reached in this world (max={max}).",
                    "このワールドのスクリーン上限に達しています (max={max})。",
                    Map.of("max", maxScreensPerWorld)
            ));
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
            sendError(sender, tkp(
                    "command.error.screen-name-exists",
                    "Screen name already exists: {screen}",
                    "スクリーン名は既に存在します: {screen}",
                    Map.of("screen", name)
            ));
            return true;
        }

        final Screen screen = plugin.getScreenManager().createScreen(player, BlockFace.NORTH, width, height, name);
        plugin.getScreenManager().setSelected(player.getUniqueId(), screen.getId());
        plugin.getBrowserIPCClient().sendOpen(screen.getId(), width, height, screen.getFps());
        final MapItemDistributor.MapDeliverySummary summary = itemDistributor.giveScreenMaps(player, screen, autoFillEnabled);

        sendHeader(sender, tk("command.ok.screen-created-header", "SCREEN CREATED", "スクリーン作成完了"));
        sendOk(sender, tkp("command.ok.created.name", "Name: {screen}", "名前: {screen}", Map.of("screen", screen.getName())));
        sendInfo(sender, tkp("command.ok.created.id", "ID: {id}", "ID: {id}", Map.of("id", screen.getId())));
        sendInfo(sender, tkp("command.ok.created.size", "Size: {width}x{height} maps", "サイズ: {width}x{height} マップ", Map.of("width", width, "height", height)));
        sendInfo(sender, tkp("command.ok.created.map-total", "Map total: {total} (direct={direct}, bundles={bundles})", "マップ総数: {total} (直接={direct}, バンドル={bundles})", Map.of("total", summary.totalMaps(), "direct", summary.directMaps(), "bundles", summary.bundleBoxes())));
        sendInfo(sender, tkp("command.ok.created.autofill", "Autofill: {state} (use --autofill to enable)", "自動配置: {state} (--autofill で有効)", Map.of("state", autoFillEnabled ? tk("common.enabled", "enabled", "有効") : tk("common.disabled", "disabled", "無効"))));
        sendInfo(sender, tk("command.ok.created.starter-frame", "Starter frame given: place 1 frame + starter map.", "スターターフレームを付与しました: 額縁1つとスターターマップを設置してください。"));
        sendInfo(sender, tkp("command.ok.created.reselect", "Use /mb select {screen} to re-select later.", "後で再選択するには /mb select {screen} を使用してください。", Map.of("screen", screen.getName())));
        sendInfo(sender, tkp("command.ok.created.placement-guide", "Placement guide: {frames} item frames, no map rotation.", "配置ガイド: 額縁 {frames} 個、マップ回転なし。", Map.of("frames", width * height)));
        sendInfo(sender, tk("command.ok.created.tile-order", "Tile order: left->right, then top->bottom.", "タイル順: 左→右、次に上→下。"));
        sendLine(sender);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        return true;
    }

    private boolean handleMenu(final CommandSender sender) {
        return menuSupport.handleMenu(sender);
    }

    private boolean handleSelect(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (args.length < 2) {
            sendError(sender, tk("command.usage.select", "Usage: /mb select <screen-id|screen-name>", "使用法: /mb select <screen-id|screen-name>"));
            return true;
        }

        final String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if ("latest".equalsIgnoreCase(query)) {
            final Optional<Screen> latest = plugin.getScreenManager().getAllScreens().stream()
                    .max(Comparator.comparing(Screen::getCreatedAt));
            if (latest.isEmpty()) {
                sendError(sender, tk("command.error.no-screens", "No screens available.", "利用可能なスクリーンがありません。"));
                return true;
            }
            plugin.getScreenManager().setSelected(player.getUniqueId(), latest.get().getId());
            sendOk(sender, tkp(
                    "command.ok.selected-latest",
                    "Selected latest screen: {screen} ({id})",
                    "最新スクリーンを選択: {screen} ({id})",
                    Map.of("screen", latest.get().getName(), "id", latest.get().getId())
            ));
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
            sendError(sender, tkp(
                "command.error.screen-not-found-name",
                "Screen not found: {screen}",
                "スクリーンが見つかりません: {screen}",
                Map.of("screen", query)
            ));
            return true;
        }

        plugin.getScreenManager().setSelected(player.getUniqueId(), selected.get().getId());
        sendOk(sender, tkp(
            "command.ok.selected",
            "Selected screen: {screen} ({id})",
            "選択したスクリーン: {screen} ({id})",
            Map.of("screen", selected.get().getName(), "id", selected.get().getId())
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        return true;
    }

    private boolean handleOpen(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 2) {
            sendError(sender, tk("command.usage.open", "Usage: /mb open <url>", "使用法: /mb open <url>"));
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, tk("command.error.no-selected-create", "No selected screen. Create/select one first.", "スクリーンが選択されていません。先に作成または選択してください。"));
            return true;
        }

        final String url = args[1];
        final UrlSecurityValidator.ValidationResult result = UrlSecurityValidator.validate(url, plugin.getConfig());
        if (!result.allowed()) {
            sendError(sender, result.valueOrReason());
            return true;
        }

        final Screen screen = selected.get();
        if (!plugin.getScreenManager().ensureLoaded(screen.getId())) {
            sendError(sender, tk("command.error.unloaded", "Screen is unloaded. Use /mb load first.", "スクリーンはアンロード状態です。先に /mb load を実行してください。"));
            return true;
        }
        screen.setCurrentUrl(result.valueOrReason());
        plugin.getBrowserIPCClient().sendNavigate(screen.getId(), result.valueOrReason());
        sendOk(sender, tkp(
            "command.ok.navigating",
            "Navigating: {url}",
            "移動先: {url}",
            Map.of("url", result.valueOrReason())
        ));
        return true;
    }

    private boolean handleType(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 2) {
            sendError(sender, tk("command.usage.type", "Usage: /mb type <text>", "使用法: /mb type <text>"));
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (text.isBlank()) {
            sendError(sender, tk("command.error.text-empty", "Text is empty.", "テキストが空です。"));
            return true;
        }

        final Screen screen = selected.get();
        if (!plugin.getScreenManager().ensureLoaded(screen.getId())) {
            sendError(sender, tk("command.error.unloaded", "Screen is unloaded. Use /mb load first.", "スクリーンはアンロード状態です。先に /mb load を実行してください。"));
            return true;
        }
        plugin.getBrowserIPCClient().sendTextInput(screen.getId(), text);
        sendOk(sender, tk("command.ok.typed", "Typed text into browser.", "ブラウザにテキストを入力しました。"));
        return true;
    }

    private boolean handleSimpleBrowserCommand(final CommandSender sender, final String type) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final Screen screen = selected.get();
        if (!plugin.getScreenManager().ensureLoaded(screen.getId())) {
            sendError(sender, tk("command.error.unloaded", "Screen is unloaded. Use /mb load first.", "スクリーンはアンロード状態です。先に /mb load を実行してください。"));
            return true;
        }
        switch (type) {
            case "GO_BACK" -> plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            case "GO_FORWARD" -> plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            case "RELOAD" -> plugin.getBrowserIPCClient().sendReload(screen.getId());
            default -> {
                sendError(sender, tk("command.error.unsupported", "Unsupported command.", "未対応のコマンドです。"));
                return true;
            }
        }

        sendOk(sender, tkp("command.ok.sent-command", "Sent command: {command}", "コマンド送信: {command}", Map.of("command", type)));
        return true;
    }

    private boolean handleFps(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (args.length < 2) {
            sendError(sender, tk("command.usage.fps", "Usage: /mb fps <value>", "使用法: /mb fps <value>"));
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final int fps;
        try {
            fps = Integer.parseInt(args[1]);
        } catch (final NumberFormatException ex) {
            sendError(sender, tk("command.error.fps-integer", "FPS must be integer.", "FPSは整数で入力してください。"));
            return true;
        }

        final int maxFps = plugin.getConfig().getInt("screen.max-fps", 30);
        if (fps < 1 || fps > maxFps) {
            sendError(sender, tkp("command.error.fps-range", "FPS must be 1..{maxFps}", "FPSは 1..{maxFps} の範囲で指定してください。", Map.of("maxFps", maxFps)));
            return true;
        }

        final Screen screen = selected.get();
        screen.setFps(fps);
        if (!plugin.getScreenManager().ensureLoaded(screen.getId())) {
            sendError(sender, tk("command.error.unloaded", "Screen is unloaded. Use /mb load first.", "スクリーンはアンロード状態です。先に /mb load を実行してください。"));
            return true;
        }
        plugin.getBrowserIPCClient().sendSetFps(screen.getId(), fps);
        sendOk(sender, tkp(
            "command.ok.fps-updated",
            "FPS updated: {fps}",
            "FPSを更新: {fps}",
            Map.of("fps", fps)
        ));
        return true;
    }

    private boolean handleLoad(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Optional<Screen> target = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (target.isEmpty()) {
            sendError(sender, args.length >= 2
                ? tk("command.error.screen-not-found", "Screen not found.", "スクリーンが見つかりません。")
                : tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final Screen screen = target.get();
        if (plugin.getScreenManager().loadScreen(screen.getId())) {
            sendOk(sender, tkp(
                    "command.ok.screen-loaded",
                    "Screen loaded: {screen}",
                    "スクリーンをロード: {screen}",
                    Map.of("screen", screen.getName())
            ));
            return true;
        }

        sendError(sender, tk("command.error.load-failed", "Load failed.", "ロードに失敗しました。"));
        return true;
    }

    private boolean handleUnload(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Optional<Screen> target = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (target.isEmpty()) {
            sendError(sender, args.length >= 2
                ? tk("command.error.screen-not-found", "Screen not found.", "スクリーンが見つかりません。")
                : tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final Screen screen = target.get();
        if (plugin.getScreenManager().unloadScreen(screen.getId())) {
            sendOk(sender, tkp(
                    "command.ok.screen-unloaded",
                    "Screen unloaded: {screen}",
                    "スクリーンをアンロード: {screen}",
                    Map.of("screen", screen.getName())
            ));
            return true;
        }

        sendError(sender, tk("command.error.unload-failed", "Unload failed.", "アンロードに失敗しました。"));
        return true;
    }

    private boolean handleList(final CommandSender sender) {
        return screenInfoSupport.handleList(sender);
    }

    private boolean handleInfo(final CommandSender sender) {
        return screenInfoSupport.handleInfo(sender);
    }

    private boolean handleDestroy(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Optional<Screen> selected = args.length >= 2
                ? resolveScreen(args[1], player)
                : plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            sendError(sender, args.length >= 2
                ? tk("command.error.screen-not-found", "Screen not found.", "スクリーンが見つかりません。")
                : tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final boolean ok = plugin.getScreenManager().destroyScreen(selected.get().getId());
        if (ok) {
            sendOk(sender, tk("command.ok.screen-destroyed", "Screen destroyed.", "スクリーンを削除しました。"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
        } else {
            sendError(sender, tk("command.error.destroy-failed", "Destroy failed.", "削除に失敗しました。"));
        }
        return true;
    }

    private boolean handleGiveFrame(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }

        if (args.length < 2) {
            sendError(sender, tk("command.usage.give-frame", "Usage: /mb give-frame <screen-id|screen-name> <tile-range>", "使用法: /mb give-frame <screen-id|screen-name> <tile-range>"));
            sendInfo(sender, tk("command.help.give-frame-example", "Example: /mb gif test 1-2", "例: /mb gif test 1-2"));
            sendInfo(sender, tk("command.help.give-frame-format", "Format: all, odd, even, x-y, x1-y1:x2-y2, n, n..m, n,m,p..q", "形式: all, odd, even, x-y, x1-y1:x2-y2, n, n..m, n,m,p..q"));
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
            sendError(sender, args.length == 2
                    ? tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。")
                    : tk("command.error.screen-not-found", "Screen not found.", "スクリーンが見つかりません。"));
            return true;
        }

        final Screen screen = target.get();
        final int tileCount = screen.getMapIds().length;
        if (tileCount <= 0) {
            sendError(sender, tk("command.error.no-map-tiles", "Screen has no map tiles.", "スクリーンにマップタイルがありません。"));
            return true;
        }

        final Optional<List<Integer>> parsed = tileRangeParser.parseTileRange(rangeExpr, screen.getWidth(), screen.getHeight());
        if (parsed.isEmpty()) {
            sendError(sender, tkp("command.error.invalid-tile-range", "Invalid tile range: {range}", "タイル範囲が不正です: {range}", Map.of("range", rangeExpr)));
            sendInfo(sender, tk("command.help.tile-range-usage", "Use all/odd/even, x-y coordinates, or 1-based linear indexes.", "all/odd/even、x-y座標、または1始まりの線形インデックスを使用してください。"));
            sendInfo(sender, tk("command.help.tile-range-examples", "Examples: 1-2, 1-1:3-2, 1..3, 1,4,6..8", "例: 1-2, 1-1:3-2, 1..3, 1,4,6..8"));
            sendInfo(sender, tkp("command.help.tile-range-bounds", "Coordinate bounds: x=1-{width}, y=1-{height}", "座標範囲: x=1-{width}, y=1-{height}", Map.of("width", screen.getWidth(), "height", screen.getHeight())));
            return true;
        }

        final byte autoFillFlag = itemDistributor.detectAutoFillPreference(player, screen);
        int supplied = 0;
        for (final int tileIndex : parsed.get()) {
            itemDistributor.giveItemOrDrop(player, itemDistributor.createScreenMapItem(screen, tileIndex, autoFillFlag));
            supplied++;
        }

        sendOk(sender, tkp("command.ok.frame-tiles-supplied", "Frame tiles supplied: {count}", "フレームタイルを配布しました: {count}", Map.of("count", supplied)));
        sendInfo(sender, tkp("command.ok.frame-target", "Screen: {screen} ({id})", "スクリーン: {screen} ({id})", Map.of("screen", screen.getName(), "id", screen.getId())));
        sendInfo(sender, tkp("command.ok.frame-range", "Tile range: {range} (valid 1-{max})", "タイル範囲: {range} (有効 1-{max})", Map.of("range", rangeExpr, "max", tileCount)));
        return true;
    }


    private boolean handleResize(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.create")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 4) {
            sendError(sender, tk("command.usage.resize", "Usage: /mb resize <screen-id|screen-name> <w> <h>", "使用法: /mb resize <screen-id|screen-name> <w> <h>"));
            return true;
        }

        final Optional<Screen> target = resolveScreen(args[1], player);
        if (target.isEmpty()) {
            sendError(sender, tk("command.error.screen-not-found", "Screen not found.", "スクリーンが見つかりません。"));
            return true;
        }

        final int width;
        final int height;
        try {
            width = Integer.parseInt(args[2]);
            height = Integer.parseInt(args[3]);
        } catch (final NumberFormatException ex) {
            sendError(sender, tk("command.error.size-integer", "Width/height must be integer.", "幅と高さは整数で入力してください。"));
            return true;
        }

        final int maxWidth = plugin.getConfig().getInt("screen.max-width", 8);
        final int maxHeight = plugin.getConfig().getInt("screen.max-height", 8);
        if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
            sendError(sender, tkp(
                    "command.error.screen-size-range",
                    "Screen size must be 1..{maxWidth} x 1..{maxHeight}",
                    "スクリーンサイズは 1..{maxWidth} x 1..{maxHeight} の範囲で指定してください。",
                    Map.of("maxWidth", maxWidth, "maxHeight", maxHeight)
            ));
            return true;
        }

        final Optional<Screen> resized = plugin.getScreenManager().resizeScreen(target.get().getId(), width, height);
        if (resized.isEmpty()) {
            sendError(sender, tk("command.error.resize-failed", "Resize failed.", "リサイズに失敗しました。"));
            return true;
        }

        plugin.getBrowserIPCClient().sendClose(target.get().getId());
        plugin.getBrowserIPCClient().sendOpen(resized.get().getId(), width, height, resized.get().getFps());
        if (resized.get().getCurrentUrl() != null && !resized.get().getCurrentUrl().isBlank()) {
            plugin.getBrowserIPCClient().sendNavigate(resized.get().getId(), resized.get().getCurrentUrl());
        }

        final MapItemDistributor.MapDeliverySummary summary = itemDistributor.giveScreenMaps(player, resized.get(), itemDistributor.detectAutoFillPreference(player, resized.get()) == (byte) 1);
        sendOk(sender, tkp("command.ok.screen-resized", "Screen resized to {width}x{height}.", "スクリーンを {width}x{height} にリサイズしました。", Map.of("width", width, "height", height)));
        sendInfo(sender, tkp("command.ok.created.map-total", "Map total: {total} (direct={direct}, bundles={bundles})", "マップ総数: {total} (直接={direct}, バンドル={bundles})", Map.of("total", summary.totalMaps(), "direct", summary.directMaps(), "bundles", summary.bundleBoxes())));
        return true;
    }

    private boolean handleConfig(final CommandSender sender, final String[] args) {
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            sendError(sender, tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 3) {
            sendError(sender, tk("command.usage.config", "Usage: /mb config <simulate_particle|language> <value>", "使用法: /mb config <simulate_particle|language> <value>"));
            return true;
        }

        if ("language".equalsIgnoreCase(args[1])) {
            final String language = args[2].toLowerCase(Locale.ROOT).replace('_', '-');
            if (!"en".equals(language) && !"ja".equals(language) && !"ja-jp".equals(language)) {
                sendError(sender, tk("command.error.language-value", "Value must be en, ja or ja-JP.", "値は en, ja, ja-JP のいずれかで指定してください。"));
                return true;
            }
            plugin.getConfig().set("ui.language", language);
            plugin.saveConfig();
            sendOk(sender, tkp("command.ok.language-updated", "language updated: {language}", "language を更新しました: {language}", Map.of("language", language)));
            return true;
        }

        if (!"simulate_particle".equalsIgnoreCase(args[1])) {
            sendError(sender, tkp("command.error.unknown-config", "Unknown config key: {key}", "不明な設定キーです: {key}", Map.of("key", args[1])));
            return true;
        }

        final String value = args[2].toLowerCase(Locale.ROOT);
        if (!"end_rod".equals(value) && !"flame".equals(value)) {
            sendError(sender, tk("command.error.simulate-particle-value", "Value must be end_rod or flame.", "値は end_rod または flame を指定してください。"));
            return true;
        }

        plugin.getConfig().set("ui.simulate-particle", value);
        plugin.saveConfig();
        sendOk(sender, tkp("command.ok.simulate-particle-updated", "simulate_particle updated: {value}", "simulate_particle を更新しました: {value}", Map.of("value", value)));
        return true;
    }

    @Override
    public Optional<Screen> resolve(final String query, final Player player) {
        return screenQueryResolver.resolveScreen(query, player);
    }

    private Optional<Screen> resolveScreen(final String query, final Player player) {
        return resolve(query, player);
    }

    private List<String> screenNameSuggestions() {
        return screenQueryResolver.screenNameSuggestions();
    }

    private boolean handleGive(final CommandSender sender, final String[] args) {
        return toolItemSupport.handleGive(sender, args);
    }

    @Override
    public String resolveLanguage() {
        return localization.resolveLanguage();
    }


    private boolean handleAdmin(final CommandSender sender, final String[] args) {
        return adminSupport.handleAdmin(sender, args);
    }

    private boolean handleExit(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        plugin.getScreenManager().clearSelected(player.getUniqueId());
        sendOk(sender, tk("command.ok.exited", "Exited browser operation mode.", "ブラウザ操作モードを終了しました。"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        return true;
    }


    @EventHandler
    public void onMenuClick(final InventoryClickEvent event) {
        menuSupport.onMenuClick(event);
    }

    @Override
    public void sendHeader(final CommandSender sender, final String title) {
        messageRenderer.sendHeader(sender, title);
    }

    @Override
    public void sendLine(final CommandSender sender) {
        messageRenderer.sendLine(sender);
    }

    @Override
    public void sendOk(final CommandSender sender, final String message) {
        messageRenderer.sendOk(sender, message);
    }

    @Override
    public void sendError(final CommandSender sender, final String message) {
        messageRenderer.sendError(sender, message);
    }

    @Override
    public void sendInfo(final CommandSender sender, final String message) {
        messageRenderer.sendInfo(sender, message);
    }

    private String localizeMessage(final String source) {
        return localization.localizeMessage(source);
    }

    @Override
    public String tk(final String key, final String en, final String ja) {
        return localization.tk(key, en, ja);
    }

    @Override
    public String tkp(final String key, final String en, final String ja, final Map<String, ?> placeholders) {
        return localization.tkp(key, en, ja, placeholders);
    }
}
