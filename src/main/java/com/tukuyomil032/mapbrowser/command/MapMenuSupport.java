package com.tukuyomil032.mapbrowser.command;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Handles menu rendering and click actions for /mb menu.
 */
final class MapMenuSupport {
    private final MapBrowserPlugin plugin;
    private final String menuTitle;
    private final NamespacedKey menuActionKey;
    private final MessageBridge messages;

    MapMenuSupport(
            final MapBrowserPlugin plugin,
            final String menuTitle,
            final NamespacedKey menuActionKey,
            final MessageBridge messages
    ) {
        this.plugin = plugin;
        this.menuTitle = menuTitle;
        this.menuActionKey = menuActionKey;
        this.messages = messages;
    }

    boolean handleMenu(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, messages.tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Inventory menu = Bukkit.createInventory(player, 54, Component.text(menuTitle, NamedTextColor.AQUA));

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
        messages.sendOk(sender, messages.tk("command.ok.menu-opened", "Opened MapBrowser menu.", "MapBrowserメニューを開きました。"));
        return true;
    }

    void onMenuClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!menuTitle.equals(title)) {
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
            messages.sendInfo(player, messages.tk("command.info.menu-open-url", "Use URL bar item and right-click block to open anvil input.", "URLバーアイテムを使い、ブロック右クリックでAnvil入力を開いてください。"));
        }

        player.closeInventory();
        final String commandLine = MapToolCatalog.actionToCommand(action);
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(commandLine));
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

    interface MessageBridge {
        void sendInfo(CommandSender sender, String message);

        void sendOk(CommandSender sender, String message);

        void sendError(CommandSender sender, String message);

        String tk(String key, String en, String ja);
    }
}
