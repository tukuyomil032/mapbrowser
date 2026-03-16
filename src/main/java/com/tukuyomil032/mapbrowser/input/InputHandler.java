package com.tukuyomil032.mapbrowser.input;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles basic player interaction routing to selected browser screen.
 */
public final class InputHandler implements Listener {
    private final MapBrowserPlugin plugin;
    private final Map<UUID, UUID> anvilSessions;
    private final HashSet<UUID> assembledScreens;
    private final NamespacedKey toolKey;
    private final NamespacedKey screenIdKey;
    private final NamespacedKey tileIndexKey;
    private final NamespacedKey autofillKey;

    /**
     * Creates input handler.
     */
    public InputHandler(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
        this.anvilSessions = new HashMap<>();
        this.assembledScreens = new HashSet<>();
        this.toolKey = new NamespacedKey(plugin, "tool");
        this.screenIdKey = new NamespacedKey(plugin, "screen-id");
        this.tileIndexKey = new NamespacedKey(plugin, "tile-index");
        this.autofillKey = new NamespacedKey(plugin, "autofill-enabled");
    }

    /**
     * Handles right-click interactions for quick left-click forwarding.
     */
    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        final Player previewPlayer = event.getPlayer();
        final ItemStack previewHeld = previewPlayer.getInventory().getItemInMainHand();
        final Optional<Screen> previewScreen = resolveStarterMapScreen(previewHeld);
        if (previewScreen.isPresent() && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            final BlockFace clickedFace = event.getBlockFace();
            if (clickedFace == BlockFace.UP || clickedFace == BlockFace.DOWN) {
                previewPlayer.sendMessage("Starter map preview works on wall faces only.");
                return;
            }
            showSimulationPreview(previewPlayer, previewScreen.get(), event.getClickedBlock().getLocation(), clickedFace);
            event.setCancelled(true);
            return;
        }

        final Player player = event.getPlayer();
        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            return;
        }

        if (!player.hasPermission("mapbrowser.use")) {
            return;
        }

        final Screen screen = selected.get();
        final ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (matchesTool(heldItem, "pointer", "items.pointer", Material.FEATHER)) {
            final int x = screen.getWidth() * 64;
            final int y = screen.getHeight() * 64;
            plugin.getBrowserIPCClient().sendMouseClick(screen.getId(), x, y, "left");
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "back", "items.back", Material.BOW)) {
            plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "forward", "items.forward", Material.ARROW)) {
            plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "reload", "items.reload", Material.COMPASS)) {
            plugin.getBrowserIPCClient().sendReload(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "scroll-up", "items.scroll-up", Material.SLIME_BALL)) {
            plugin.getBrowserIPCClient().sendScroll(screen.getId(), -300);
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "scroll-down", "items.scroll-down", Material.MAGMA_CREAM)) {
            plugin.getBrowserIPCClient().sendScroll(screen.getId(), 300);
            event.setCancelled(true);
            return;
        }

        if (matchesTool(heldItem, "url-bar", "items.url-bar", Material.WRITABLE_BOOK)) {
            openUrlInput(player, screen);
            event.setCancelled(true);
        }
    }

    /**
     * Handles URL confirmation from anvil output slot.
     */
    @EventHandler
    public void onAnvilClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        final UUID screenId = anvilSessions.get(player.getUniqueId());
        if (screenId == null) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }

        event.setCancelled(true);
        final Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.ANVIL) {
            player.closeInventory();
            return;
        }

        final ItemStack outputItem = event.getCurrentItem();
        final String input;
        if (outputItem != null && outputItem.hasItemMeta() && outputItem.getItemMeta() != null) {
            final Component displayName = outputItem.getItemMeta().displayName();
            input = displayName == null ? null : PlainTextComponentSerializer.plainText().serialize(displayName);
        } else {
            input = null;
        }
        if (input == null || input.isBlank()) {
            player.sendMessage("URL is empty.");
            player.closeInventory();
            return;
        }

        final Optional<Screen> target = plugin.getScreenManager().getScreen(screenId);
        if (target.isEmpty()) {
            player.sendMessage("Target screen not found.");
            player.closeInventory();
            return;
        }

        final UrlSecurityValidator.ValidationResult validated = UrlSecurityValidator.validate(input.trim(), plugin.getConfig());
        if (!validated.allowed()) {
            player.sendMessage(validated.valueOrReason());
            player.closeInventory();
            return;
        }

        final Screen screen = target.get();
        screen.setCurrentUrl(validated.valueOrReason());
        plugin.getBrowserIPCClient().sendNavigate(screen.getId(), validated.valueOrReason());
        player.sendMessage("Navigating: " + validated.valueOrReason());
        player.closeInventory();
    }

    /**
     * Clears temporary anvil state when inventory closes.
     */
    @EventHandler
    public void onAnvilClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }
        anvilSessions.remove(player.getUniqueId());
    }

    /**
     * Clears player state on quit.
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        plugin.getScreenManager().clearSelected(event.getPlayer().getUniqueId());
        anvilSessions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Auto-fills remaining frames and map tiles after the player inserts the first map tile.
     */
    @EventHandler
    public void onItemFrameInteract(final PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame anchorFrame)) {
            return;
        }

        final ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (held.getType() != Material.FILLED_MAP || !held.hasItemMeta()) {
            return;
        }

        final ItemMeta meta = held.getItemMeta();
        final String screenIdRaw = meta.getPersistentDataContainer().get(screenIdKey, PersistentDataType.STRING);
        final Integer tileIndex = meta.getPersistentDataContainer().get(tileIndexKey, PersistentDataType.INTEGER);
        if (screenIdRaw == null || tileIndex == null) {
            return;
        }

        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ignored) {
            return;
        }

        final Optional<Screen> target = plugin.getScreenManager().getScreen(screenId);
        if (target.isEmpty()) {
            return;
        }

        final Byte autoFillEnabled = meta.getPersistentDataContainer().get(autofillKey, PersistentDataType.BYTE);
        if (autoFillEnabled == null || autoFillEnabled != (byte) 1) {
            return;
        }

        if (tileIndex != 0) {
            event.getPlayer().sendMessage("Use the first map tile (top-left) to auto-assemble.");
            return;
        }

        if (assembledScreens.contains(screenId)) {
            return;
        }

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> autoAssembleScreen(event.getPlayer(), target.get(), anchorFrame, tileIndex));
    }

    /**
     * Routes tool use directly on item frames.
     */
    @EventHandler
    public void onToolFrameInteract(final PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }

        final ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        final Optional<ToolAction> action = resolveToolAction(held);
        if (action.isEmpty()) {
            return;
        }

        final Optional<Screen> screen = resolveScreenFromFrame(event.getPlayer(), frame);
        if (screen.isEmpty()) {
            event.getPlayer().sendMessage("No selected screen. Use /mb select <screen>.");
            event.setCancelled(true);
            return;
        }

        executeToolAction(event.getPlayer(), screen.get(), action.get());
        event.setCancelled(true);
    }

    /**
     * Prevents map extraction when left-clicking frames with control tools.
     */
    @EventHandler
    public void onToolFrameDamage(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        final Optional<ToolAction> action = resolveToolAction(held);
        if (action.isEmpty()) {
            return;
        }

        final Optional<Screen> screen = resolveScreenFromFrame(player, frame);
        if (screen.isEmpty()) {
            player.sendMessage("No selected screen. Use /mb select <screen>.");
            event.setCancelled(true);
            return;
        }

        if (action.get() == ToolAction.POINTER) {
            executeToolAction(player, screen.get(), action.get());
        }
        event.setCancelled(true);
    }

    private void openUrlInput(final Player player, final Screen screen) {
        final Inventory anvil = Bukkit.createInventory(player, InventoryType.ANVIL, Component.text("MapBrowser URL"));
        final ItemStack paper = new ItemStack(Material.PAPER);
        final ItemMeta meta = paper.getItemMeta();
        final String current = screen.getCurrentUrl() == null ? "https://" : screen.getCurrentUrl();
        final String trimmed = current.length() > 45 ? current.substring(0, 45) : current;
        meta.displayName(Component.text(trimmed));
        paper.setItemMeta(meta);
        anvil.setItem(0, paper);
        anvilSessions.put(player.getUniqueId(), screen.getId());
        player.openInventory(anvil);
        player.sendMessage("Enter URL and click result slot to confirm.");
    }

    private boolean isConfigured(final Material held, final String path, final Material fallback) {
        final String configured = plugin.getConfig().getString(path, fallback.name());
        if (configured == null || configured.isBlank()) {
            return held == fallback;
        }
        try {
            return held == Material.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            return held == fallback;
        }
    }

    private boolean matchesTool(
            final ItemStack heldItem,
            final String expectedTool,
            final String path,
            final Material fallback
    ) {
        if (heldItem != null && heldItem.hasItemMeta()) {
            final ItemMeta meta = heldItem.getItemMeta();
            if (meta != null) {
                final String tagged = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
                if (tagged != null) {
                    return tagged.equalsIgnoreCase(expectedTool);
                }
            }
        }
        return isConfigured(heldItem == null ? Material.AIR : heldItem.getType(), path, fallback);
    }

    private void autoAssembleScreen(
            final Player player,
            final Screen screen,
            final ItemFrame anchorFrame,
            final int tileIndex
    ) {
        if (assembledScreens.contains(screen.getId())) {
            return;
        }

        final BlockFace facing = anchorFrame.getFacing();
        final int[] right = rightVector(facing);
        if (right == null) {
            player.sendMessage("This frame direction is not supported for auto-assembly.");
            return;
        }

        final int width = screen.getWidth();
        final int height = screen.getHeight();
        final int anchorCol = Math.floorMod(tileIndex, width);
        final int anchorRow = tileIndex / width;

        if (anchorCol != 0 || anchorRow != 0) {
            player.sendMessage("Starter map must be tile 0 (top-left).");
            return;
        }

        final Location anchorLoc = anchorFrame.getLocation();
        final Location topLeft = anchorLoc.clone();

        final World world = anchorLoc.getWorld();
        if (world == null) {
            return;
        }

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                final int index = row * width + col;
                final Location targetLoc = topLeft.clone().add(right[0] * col, -row, right[2] * col);
                final BlockFace backing = facing.getOppositeFace();
                final Location backingLoc = targetLoc.clone().add(backing.getModX(), backing.getModY(), backing.getModZ());
                if (backingLoc.getBlock().getType().isAir()) {
                    backingLoc.getBlock().setType(Material.SMOOTH_STONE, false);
                }
                final ItemFrame frame = findOrCreateFrame(world, targetLoc, facing);
                if (frame == null) {
                    continue;
                }
                frame.setItem(createScreenMapItem(screen, index), false);
            }
        }

        assembledScreens.add(screen.getId());
        player.sendMessage("MapBrowser auto-assembly completed: " + width + "x" + height);
    }

    private ItemFrame findOrCreateFrame(final World world, final Location loc, final BlockFace facing) {
        final Collection<Entity> nearby = world.getNearbyEntities(loc, 0.6, 0.6, 0.6);
        for (final Entity entity : nearby) {
            if (entity instanceof ItemFrame frame && frame.getFacing() == facing) {
                return frame;
            }
        }

        return world.spawn(loc, ItemFrame.class, spawned -> spawned.setFacingDirection(facing, true));
    }

    private int[] rightVector(final BlockFace facing) {
        return switch (facing) {
            case NORTH -> new int[]{1, 0, 0};
            case SOUTH -> new int[]{-1, 0, 0};
            case EAST -> new int[]{0, 0, 1};
            case WEST -> new int[]{0, 0, -1};
            default -> null;
        };
    }

    private Optional<Screen> resolveStarterMapScreen(final ItemStack held) {
        if (held == null || held.getType() != Material.FILLED_MAP || !held.hasItemMeta()) {
            return Optional.empty();
        }

        final ItemMeta meta = held.getItemMeta();
        final String screenIdRaw = meta.getPersistentDataContainer().get(screenIdKey, PersistentDataType.STRING);
        final Integer tileIndex = meta.getPersistentDataContainer().get(tileIndexKey, PersistentDataType.INTEGER);
        final Byte autoFillEnabled = meta.getPersistentDataContainer().get(autofillKey, PersistentDataType.BYTE);
        if (screenIdRaw == null || tileIndex == null || tileIndex != 0 || autoFillEnabled == null || autoFillEnabled != (byte) 1) {
            return Optional.empty();
        }

        try {
            return plugin.getScreenManager().getScreen(UUID.fromString(screenIdRaw));
        } catch (final IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void showSimulationPreview(
            final Player player,
            final Screen screen,
            final Location clickedBlock,
            final BlockFace face
    ) {
        final Particle particle = resolveSimulationParticle();
        if (particle == null) {
            return;
        }

        final int[] right = rightVector(face);
        if (right == null) {
            return;
        }

        final Location base = clickedBlock.clone()
                .add(0.5 + (face.getModX() * 0.46875), 0.5 + (face.getModY() * 0.46875), 0.5 + (face.getModZ() * 0.46875));
        final Location topLeft = base.clone().add(0, screen.getHeight() - 1, 0);

        for (int row = 0; row < screen.getHeight(); row++) {
            for (int col = 0; col < screen.getWidth(); col++) {
                final Location loc = topLeft.clone().add(right[0] * col, -row, right[2] * col);
                player.spawnParticle(particle, loc, 8, 0.16, 0.16, 0.16, 0.0);
            }
        }
    }

    private Particle resolveSimulationParticle() {
        final String configured = plugin.getConfig().getString("ui.simulate-particle", "END_ROD");
        if (configured == null) {
            return Particle.END_ROD;
        }

        final String normalized = configured.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("NONE") || normalized.equals("OFF")) {
            return null;
        }

        try {
            return Particle.valueOf(normalized);
        } catch (final IllegalArgumentException ignored) {
            return Particle.END_ROD;
        }
    }

    private Optional<Screen> resolveScreenFromFrame(final Player player, final ItemFrame frame) {
        final ItemStack displayed = frame.getItem();
        if (displayed != null && displayed.hasItemMeta()) {
            final ItemMeta displayedMeta = displayed.getItemMeta();
            if (displayedMeta != null) {
                final String sid = displayedMeta.getPersistentDataContainer().get(screenIdKey, PersistentDataType.STRING);
                if (sid != null) {
                    try {
                        final Optional<Screen> byMap = plugin.getScreenManager().getScreen(UUID.fromString(sid));
                        if (byMap.isPresent()) {
                            return byMap;
                        }
                    } catch (final IllegalArgumentException ignored) {
                        // Ignore invalid uuid and fallback.
                    }
                }
            }
        }

        return plugin.getScreenManager().getSelected(player.getUniqueId());
    }

    private Optional<ToolAction> resolveToolAction(final ItemStack held) {
        if (matchesTool(held, "pointer", "items.pointer", Material.FEATHER)) {
            return Optional.of(ToolAction.POINTER);
        }
        if (matchesTool(held, "back", "items.back", Material.BOW)) {
            return Optional.of(ToolAction.BACK);
        }
        if (matchesTool(held, "forward", "items.forward", Material.ARROW)) {
            return Optional.of(ToolAction.FORWARD);
        }
        if (matchesTool(held, "reload", "items.reload", Material.COMPASS)) {
            return Optional.of(ToolAction.RELOAD);
        }
        if (matchesTool(held, "scroll-up", "items.scroll-up", Material.SLIME_BALL)) {
            return Optional.of(ToolAction.SCROLL_UP);
        }
        if (matchesTool(held, "scroll-down", "items.scroll-down", Material.MAGMA_CREAM)) {
            return Optional.of(ToolAction.SCROLL_DOWN);
        }
        if (matchesTool(held, "url-bar", "items.url-bar", Material.WRITABLE_BOOK)) {
            return Optional.of(ToolAction.URL_BAR);
        }
        return Optional.empty();
    }

    private void executeToolAction(final Player player, final Screen screen, final ToolAction action) {
        switch (action) {
            case POINTER -> {
                final int x = screen.getWidth() * 64;
                final int y = screen.getHeight() * 64;
                plugin.getBrowserIPCClient().sendMouseClick(screen.getId(), x, y, "left");
            }
            case BACK -> plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            case FORWARD -> plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            case RELOAD -> plugin.getBrowserIPCClient().sendReload(screen.getId());
            case SCROLL_UP -> plugin.getBrowserIPCClient().sendScroll(screen.getId(), -300);
            case SCROLL_DOWN -> plugin.getBrowserIPCClient().sendScroll(screen.getId(), 300);
            case URL_BAR -> openUrlInput(player, screen);
        }
    }

    private enum ToolAction {
        POINTER,
        BACK,
        FORWARD,
        RELOAD,
        SCROLL_UP,
        SCROLL_DOWN,
        URL_BAR
    }

    private ItemStack createScreenMapItem(final Screen screen, final int tileIndex) {
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
        meta.getPersistentDataContainer().set(screenIdKey, PersistentDataType.STRING, screen.getId().toString());
        meta.getPersistentDataContainer().set(tileIndexKey, PersistentDataType.INTEGER, tileIndex);
        mapItem.setItemMeta(meta);
        return mapItem;
    }
}
