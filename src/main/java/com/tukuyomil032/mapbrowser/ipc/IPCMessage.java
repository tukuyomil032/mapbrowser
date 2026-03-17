package com.tukuyomil032.mapbrowser.ipc;

import java.util.UUID;

import com.google.gson.JsonObject;

/**
 * Utility factory for IPC JSON messages.
 */
public final class IPCMessage {
    public static final String OPEN = "OPEN";
    public static final String NAVIGATE = "NAVIGATE";
    public static final String MOUSE_CLICK = "MOUSE_CLICK";
    public static final String SCROLL = "SCROLL";
    public static final String GO_BACK = "GO_BACK";
    public static final String GO_FORWARD = "GO_FORWARD";
    public static final String RELOAD = "RELOAD";
    public static final String CLOSE = "CLOSE";
    public static final String SET_FPS = "SET_FPS";
    public static final String TEXT_INPUT = "TEXT_INPUT";

    public static final String READY = "READY";
    public static final String FRAME = "FRAME";
    public static final String DELTA_FRAME = "DELTA_FRAME";
    public static final String URL_CHANGED = "URL_CHANGED";
    public static final String PAGE_LOADED = "PAGE_LOADED";
    public static final String AUDIO_FRAME = "AUDIO_FRAME";
    public static final String ERROR = "ERROR";

    private IPCMessage() {
    }

    /**
     * Builds a screen-scoped message.
     */
    public static JsonObject screenMessage(final String type, final UUID screenId) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("screenId", screenId.toString());
        return obj;
    }
}
