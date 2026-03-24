package com.tukuyomil032.mapbrowser.command;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Renders styled command messages.
 */
final class MapCommandMessageRenderer {
    private final Localizer localizer;

    MapCommandMessageRenderer(final Localizer localizer) {
        this.localizer = localizer;
    }

    void sendHeader(final CommandSender sender, final String title) {
        final String localizedTitle = localizer.localize(title);
        sender.sendMessage(gradientLine(50, TextColor.color(0x1E293B), TextColor.color(0x334155)));
        sender.sendMessage(Component.text("  ")
                .append(gradientText(localizedTitle, TextColor.color(0x38BDF8), TextColor.color(0x22D3EE)))
                .decoration(TextDecoration.BOLD, true));
        sender.sendMessage(gradientLine(50, TextColor.color(0x1E293B), TextColor.color(0x334155)));
    }

    void sendLine(final CommandSender sender) {
        sender.sendMessage(gradientLine(50, TextColor.color(0x1E293B), TextColor.color(0x334155)));
    }

    void sendOk(final CommandSender sender, final String message) {
        final String localized = localizer.localize(message);
        sender.sendMessage(
                Component.text("✔ ", TextColor.color(0x22C55E)).decoration(TextDecoration.BOLD, true)
                        .append(Component.text(localized, TextColor.color(0xF8FAFC)).decoration(TextDecoration.BOLD, true))
        );
    }

    void sendError(final CommandSender sender, final String message) {
        final String localized = localizer.localize(message);
        sender.sendMessage(
                Component.text("✖ ", TextColor.color(0xEF4444)).decoration(TextDecoration.BOLD, true)
                        .append(Component.text(localized, TextColor.color(0xF8FAFC)).decoration(TextDecoration.BOLD, true))
        );
    }

    void sendInfo(final CommandSender sender, final String message) {
        final String localized = localizer.localize(message);
        sender.sendMessage(
                Component.text("• ", TextColor.color(0x60A5FA)).decoration(TextDecoration.BOLD, true)
                        .append(Component.text(localized, TextColor.color(0xE2E8F0)))
        );
    }

    private Component gradientLine(final int length, final TextColor from, final TextColor to) {
        final String line = "─".repeat(Math.max(1, length));
        return gradientText(line, from, to);
    }

    private Component gradientText(final String text, final TextColor from, final TextColor to) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        final int n = text.length();
        if (n == 1) {
            return Component.text(text, from);
        }
        final int fr = from.red();
        final int fg = from.green();
        final int fb = from.blue();
        final int tr = to.red();
        final int tg = to.green();
        final int tb = to.blue();

        Component out = Component.empty();
        for (int i = 0; i < n; i++) {
            final float t = (float) i / (float) (n - 1);
            final int r = Math.round(fr + (tr - fr) * t);
            final int g = Math.round(fg + (tg - fg) * t);
            final int b = Math.round(fb + (tb - fb) * t);
            out = out.append(Component.text(String.valueOf(text.charAt(i)), TextColor.color(r, g, b)));
        }
        return out;
    }

    interface Localizer {
        String localize(String source);
    }
}
