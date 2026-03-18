package com.tukuyomil032.mapbrowser.audio;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default audio bridge that keeps audio disabled.
 */
public final class NoopAudioBridge implements AudioBridge {
    private final AtomicLong droppedFrames = new AtomicLong(0L);

    /**
     * Ignores all audio frames.
     */
    @Override
    public void publishFrame(final UUID screenId, final byte[] opusFrame, final int sampleRate) {
        // Phase 4 implementation will forward packets to companion mod clients.
        droppedFrames.incrementAndGet();
    }

    /**
     * Returns diagnostics summary.
     */
    @Override
    public String diagnostics() {
        return "audio=noop dropped=" + droppedFrames.get();
    }
}
