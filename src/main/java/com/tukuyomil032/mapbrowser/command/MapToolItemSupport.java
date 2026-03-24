package com.tukuyomil032.mapbrowser.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles /mb give tool item command.
 */
final class MapToolItemSupport {
    private final MapBrowserPlugin plugin;
    private final NamespacedKey toolKey;
    private final MessageBridge messages;
    private final LanguageResolver languageResolver;

    MapToolItemSupport(
            final MapBrowserPlugin plugin,
            final NamespacedKey toolKey,
            final MessageBridge messages,
            final LanguageResolver languageResolver
    ) {
        this.plugin = plugin;
        this.toolKey = toolKey;
        this.messages = messages;
        this.languageResolver = languageResolver;
    }

    boolean handleGive(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, messages.tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.use")) {
            messages.sendError(sender, messages.tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 2) {
            messages.sendError(sender, messages.tk("command.usage.give", "Usage: /mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>", "使用法: /mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll>"));
            return true;
        }

        final String key = MapToolCatalog.normalizeToolKey(args[1]);
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
            messages.sendError(sender, messages.tk("command.error.unknown-item", "Unknown item type.", "不明なアイテム種別です。"));
            return true;
        }

        final Material fallback = MapToolCatalog.fallbackMaterialForTool(key);
        final String materialName = Objects.requireNonNullElse(plugin.getConfig().getString(path, fallback.name()), fallback.name());
        final Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            messages.sendError(sender, messages.tkp(
                    "command.error.invalid-material-config",
                    "Config has invalid material for {key}: {material}",
                    "設定に不正なマテリアルがあります {key}: {material}",
                    Map.of("key", key, "material", materialName)
            ));
            return true;
        }

        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = Objects.requireNonNull(item.getItemMeta(), "ItemMeta unavailable");
        final String language = languageResolver.resolveLanguage();
        meta.displayName(Component.text(MapToolCatalog.localizedToolName(language, key), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text(MapToolCatalog.localizedToolDescription(language, key), NamedTextColor.GRAY),
                Component.text("Type: " + key, NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, Objects.requireNonNull(key, "tool key"));
        item.setItemMeta(meta);

        final java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            messages.sendInfo(sender, messages.tk("command.info.inventory-full-drop", "Inventory full. Dropped item on ground.", "インベントリが満杯のため地面にドロップしました。"));
        }
        messages.sendOk(sender, messages.tkp("command.ok.given-item", "Given item: {key} ({material})", "アイテムを付与しました: {key} ({material})", Map.of("key", key, "material", material.name())));
        return true;
    }

    interface MessageBridge {
        void sendInfo(CommandSender sender, String message);

        void sendOk(CommandSender sender, String message);

        void sendError(CommandSender sender, String message);

        String tk(String key, String en, String ja);

        String tkp(String key, String en, String ja, Map<String, ?> placeholders);
    }

    interface LanguageResolver {
        String resolveLanguage();
    }
}
