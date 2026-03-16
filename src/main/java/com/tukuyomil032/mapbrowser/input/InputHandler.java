package com.tukuyomil032.mapbrowser.input;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

    /**
     * Creates input handler.
     */
    public InputHandler(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
        this.anvilSessions = new HashMap<>();
    }

    /**
     * Handles right-click interactions for quick left-click forwarding.
     */
    @EventHandler
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
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
        final Material held = player.getInventory().getItemInMainHand().getType();

        if (isConfigured(held, "items.pointer", Material.FEATHER)) {
            final int x = screen.getWidth() * 64;
            final int y = screen.getHeight() * 64;
            plugin.getBrowserIPCClient().sendMouseClick(screen.getId(), x, y, "left");
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.back", Material.BOW)) {
            plugin.getBrowserIPCClient().sendGoBack(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.forward", Material.ARROW)) {
            plugin.getBrowserIPCClient().sendGoForward(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.reload", Material.COMPASS)) {
            plugin.getBrowserIPCClient().sendReload(screen.getId());
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.scroll-up", Material.SLIME_BALL)) {
            plugin.getBrowserIPCClient().sendScroll(screen.getId(), -300);
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.scroll-down", Material.MAGMA_CREAM)) {
            plugin.getBrowserIPCClient().sendScroll(screen.getId(), 300);
            event.setCancelled(true);
            return;
        }

        if (isConfigured(held, "items.url-bar", Material.WRITABLE_BOOK)) {
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
}
