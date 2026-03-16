package com.tukuyomil032.mapbrowser.audio;

import java.util.UUID;

/**
 * Default audio bridge that keeps audio disabled.
 */
public final class NoopAudioBridge implements AudioBridge {
    /**
     * Ignores all audio frames.
     */
    @Override
    public void publishFrame(final UUID screenId, final byte[] opusFrame, final int sampleRate) {
        // Phase 4 implementation will forward packets to companion mod clients.
    }
}
