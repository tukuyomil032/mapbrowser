package com.tukuyomil032.mapbrowser.command;

import java.util.Locale;

import org.bukkit.Material;

/**
 * Provides tool metadata and menu action mapping.
 */
final class MapToolCatalog {
    private MapToolCatalog() {
    }

    static Material fallbackMaterialForTool(final String key) {
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

    static String localizedToolName(final String language, final String key) {
        if (language.startsWith("ja")) {
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

    static String localizedToolDescription(final String language, final String key) {
        if (language.startsWith("ja")) {
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

    static String actionToCommand(final String action) {
        final String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
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

    static String normalizeToolKey(final String rawKey) {
        return java.util.Objects.requireNonNull(rawKey, "item type").toLowerCase(Locale.ROOT);
    }
}
