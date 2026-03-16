package com.tukuyomil032.mapbrowser.audio;

import java.util.UUID;

/**
 * Extension point for companion-mod audio transport.
 */
public interface AudioBridge {
    /**
     * Publishes encoded audio frame for a screen.
     */
    void publishFrame(UUID screenId, byte[] opusFrame, int sampleRate);
}
