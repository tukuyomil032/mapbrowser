package com.tukuyomil032.mapbrowser.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Handles /mb list and /mb info rendering.
 */
final class MapScreenInfoSupport {
    private final MapBrowserPlugin plugin;
    private final MessageBridge messages;
    private final LanguageResolver languageResolver;

    MapScreenInfoSupport(
            final MapBrowserPlugin plugin,
            final MessageBridge messages,
            final LanguageResolver languageResolver
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.languageResolver = languageResolver;
    }

    boolean handleList(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        final List<Screen> screens = new ArrayList<>(plugin.getScreenManager().getAllScreens());
        messages.sendHeader(sender, messages.tkp("command.list.header", "SCREEN LIST ({count})", "スクリーン一覧 ({count})", Map.of("count", screens.size())));
        UUID selectedId = null;
        if (sender instanceof Player player) {
            selectedId = plugin.getScreenManager().getSelected(player.getUniqueId())
                    .map(Screen::getId)
                    .orElse(null);
        }
        for (final Screen screen : screens.stream().sorted(Comparator.comparing(Screen::getCreatedAt)).toList()) {
            final boolean selected = selectedId != null && selectedId.equals(screen.getId());
            final String selectedLabel = languageResolver.resolveLanguage().startsWith("ja") ? "選択中" : "SELECTED";
            final String stateLabel = languageResolver.resolveLanguage().startsWith("ja") ? "状態" : "STATE";
            sender.sendMessage(Component.text()
                .append(Component.text(selected ? "◆ " : "◇ ", selected ? TextColor.color(0x22C55E) : NamedTextColor.DARK_GRAY))
                .append(Component.text(screen.getName(), TextColor.color(0x67E8F9)).decoration(TextDecoration.BOLD, true))
                .append(Component.text("  " + stateLabel + ": " + screen.getState(), TextColor.color(0x94A3B8)))
                .append(Component.text("  ID: " + screen.getId(), NamedTextColor.GRAY))
                .append(selected ? Component.text("  [" + selectedLabel + "]", TextColor.color(0x22C55E)).decoration(TextDecoration.BOLD, true) : Component.empty())
                    .build());
        }
        messages.sendLine(sender);
        return true;
    }

    boolean handleInfo(final CommandSender sender) {
        if (sender == null) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.sendError(sender, messages.tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
            return true;
        }

        final Optional<Screen> selected = plugin.getScreenManager().getSelected(player.getUniqueId());
        if (selected.isEmpty()) {
            messages.sendError(sender, messages.tk("command.error.no-selected", "No selected screen.", "スクリーンが選択されていません。"));
            return true;
        }

        final Screen screen = selected.get();
        messages.sendHeader(sender, messages.tk("command.info.header", "SELECTED SCREEN", "選択中スクリーン"));
        messages.sendInfo(sender, messages.tkp("command.info.name", "Name: {screen}", "名前: {screen}", Map.of("screen", screen.getName())));
        messages.sendInfo(sender, messages.tkp("command.info.id", "ID: {id}", "ID: {id}", Map.of("id", screen.getId())));
        messages.sendInfo(sender, messages.tkp("command.info.url", "URL: {url}", "URL: {url}", Map.of("url", screen.getCurrentUrl())));
        messages.sendInfo(sender, messages.tkp("command.info.state", "State: {state}", "状態: {state}", Map.of("state", screen.getState())));
        messages.sendInfo(sender, messages.tkp("command.info.size", "Size: {width}x{height}", "サイズ: {width}x{height}", Map.of("width", screen.getWidth(), "height", screen.getHeight())));
        messages.sendInfo(sender, messages.tkp("command.info.fps", "FPS: {fps}", "FPS: {fps}", Map.of("fps", screen.getFps())));
        messages.sendLine(sender);
        return true;
    }

    interface MessageBridge {
        void sendHeader(CommandSender sender, String message);

        void sendLine(CommandSender sender);

        void sendInfo(CommandSender sender, String message);

        void sendError(CommandSender sender, String message);

        String tk(String key, String en, String ja);

        String tkp(String key, String en, String ja, Map<String, ?> placeholders);
    }

    interface LanguageResolver {
        String resolveLanguage();
    }
}
