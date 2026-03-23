package com.tukuyomil032.mapbrowser.util;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MessageLocalizerTest {

    @Test
    void translateKeyUsesLocaleFallbackJaJpToJaThenEn() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("greeting", "Hello"),
                Map.of("greeting", "こんにちは")
        );

        assertEquals("こんにちは", localizer.translateKey("ja-JP", "greeting", "fallback"));
        assertEquals("Hello", localizer.translateKey("en-US", "greeting", "fallback"));
    }

    @Test
    void translateKeyFallsBackToEnWhenJaKeyMissing() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("only_en", "Only EN"),
                Map.of()
        );

        assertEquals("Only EN", localizer.translateKey("ja-JP", "only_en", "fallback"));
    }

    @Test
    void translateKeyAppliesPlaceholders() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("msg", "Screen {screen} FPS {fps}"),
                Map.of("msg", "スクリーン {screen} FPS {fps}")
        );

        final String value = localizer.translateKey(
                "ja-JP",
                "msg",
                "fallback",
                Map.of("screen", "alpha", "fps", 15)
        );

        assertEquals("スクリーン alpha FPS 15", value);
    }

    @Test
    void translateRawSupportsPrefixRules() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of(),
                Map.of(),
                Map.of("Navigating: ", "Navigating: "),
                Map.of("Navigating: ", "移動先: "),
                Map.of(),
                Map.of()
        );

        assertEquals("移動先: https://example.com", localizer.translateRaw("ja-JP", "Navigating: https://example.com"));
        assertEquals("Navigating: https://example.com", localizer.translateRaw("en-US", "Navigating: https://example.com"));
    }

    @Test
    void translateRawPrefersExactBeforePrefix() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of("Status: OK", "Status exact"),
                Map.of("Status: OK", "状態: 厳密"),
                Map.of("Status: ", "Status prefix: "),
                Map.of("Status: ", "状態: "),
                Map.of(),
                Map.of()
        );

        assertEquals("状態: 厳密", localizer.translateRaw("ja-JP", "Status: OK"));
        assertEquals("状態: NG", localizer.translateRaw("ja-JP", "Status: NG"));
    }

    @Test
    void translateKeyAppliesPlaceholdersToDefaultTextWhenKeyMissing() {
        final MessageLocalizer localizer = new MessageLocalizer(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        final String value = localizer.translateKey(
                "ja-JP",
                "missing.key",
                "Fallback {name}",
                Map.of("name", "beta")
        );

        assertEquals("Fallback beta", value);
    }
}
