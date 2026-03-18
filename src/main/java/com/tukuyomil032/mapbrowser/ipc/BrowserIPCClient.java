package com.tukuyomil032.mapbrowser.ipc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.ScreenState;

/**
 * Handles websocket IPC communication with the browser renderer process.
 */
public final class BrowserIPCClient {
    private static final Gson GSON = new Gson();
    private static final int FRAME_MAGIC = 0x4D424652; // MBFR
    private static final int FRAME_VERSION = 1;
    private static final int FRAME_TYPE_FULL = 1;
    private static final int FRAME_TYPE_DELTA = 2;

    private final MapBrowserPlugin plugin;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger restartAttempts = new AtomicInteger(0);
    private final AtomicInteger connectFailures = new AtomicInteger(0);

    private volatile WebSocket socket;
    private volatile Process rendererProcess;
    private volatile boolean stopping;
    private volatile long rendererStartEpochMillis;
    private volatile long lastInboundEpochMillis;
    private volatile long lastReadyEpochMillis;
    private final long statsStartedEpochMillis = System.currentTimeMillis();
    private final AtomicLong inboundTextCount = new AtomicLong(0L);
    private final AtomicLong inboundBinaryCount = new AtomicLong(0L);
    private final AtomicLong inboundFrameCount = new AtomicLong(0L);
    private final AtomicLong inboundDeltaCount = new AtomicLong(0L);
    private final AtomicLong inboundErrorEventCount = new AtomicLong(0L);
    private final Map<UUID, ScreenIpcStatsCounter> screenStats = new ConcurrentHashMap<>();

    /**
     * Creates IPC client.
     */
    public BrowserIPCClient(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts renderer process and connects websocket.
     */
    public void start() {
        stopping = false;
        startRendererProcess();
        connect();
    }

    /**
     * Stops websocket and child process.
     */
    public void stop() {
        stopping = true;
        final WebSocket ws = socket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin shutdown");
        }

        final Process process = rendererProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    /**
     * Sends OPEN command.
     */
    public void sendOpen(final UUID screenId, final int width, final int height, final int fps) {
        final JsonObject obj = IPCMessage.screenMessage("OPEN", screenId);
        obj.addProperty("width", width);
        obj.addProperty("height", height);
        obj.addProperty("fps", fps);
        sendJson(obj);
    }

    /**
     * Sends NAVIGATE command.
     */
    public void sendNavigate(final UUID screenId, final String url) {
        final JsonObject obj = IPCMessage.screenMessage("NAVIGATE", screenId);
        obj.addProperty("url", url);
        sendJson(obj);
    }

    /**
     * Sends click command.
     */
    public void sendMouseClick(final UUID screenId, final int x, final int y, final String button) {
        final JsonObject obj = IPCMessage.screenMessage("MOUSE_CLICK", screenId);
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("button", button);
        sendJson(obj);
    }

    /**
     * Sends scroll command.
     */
    public void sendScroll(final UUID screenId, final int deltaY) {
        final JsonObject obj = IPCMessage.screenMessage("SCROLL", screenId);
        obj.addProperty("deltaY", deltaY);
        sendJson(obj);
    }

    /**
     * Sends back command.
     */
    public void sendGoBack(final UUID screenId) {
        sendJson(IPCMessage.screenMessage("GO_BACK", screenId));
    }

    /**
     * Sends forward command.
     */
    public void sendGoForward(final UUID screenId) {
        sendJson(IPCMessage.screenMessage("GO_FORWARD", screenId));
    }

    /**
     * Sends reload command.
     */
    public void sendReload(final UUID screenId) {
        sendJson(IPCMessage.screenMessage("RELOAD", screenId));
    }

    /**
     * Sends close command.
     */
    public void sendClose(final UUID screenId) {
        sendJson(IPCMessage.screenMessage("CLOSE", screenId));
    }

    /**
     * Sends fps command.
     */
    public void sendSetFps(final UUID screenId, final int fps) {
        final JsonObject obj = IPCMessage.screenMessage("SET_FPS", screenId);
        obj.addProperty("fps", fps);
        sendJson(obj);
    }

    /**
     * Sends text input command.
     */
    public void sendTextInput(final UUID screenId, final String text) {
        final JsonObject obj = IPCMessage.screenMessage("TEXT_INPUT", screenId);
        obj.addProperty("text", text);
        sendJson(obj);
    }

    /**
     * Sends key press command.
     */
    public void sendKeyPress(final UUID screenId, final String key) {
        final JsonObject obj = IPCMessage.screenMessage("KEY_PRESS", screenId);
        obj.addProperty("key", key);
        sendJson(obj);
    }

    /**
     * Returns current websocket connectivity state.
     */
    public boolean isConnected() {
        return socket != null && !socket.isOutputClosed();
    }

    /**
     * Returns IPC health summary for admin diagnostics.
     */
    public String healthSummary() {
        if (!isConnected()) {
            return "disconnected";
        }

        final long ageSec = secondsSince(lastInboundEpochMillis);
        if (ageSec < 0) {
            return "connected (no inbound message yet)";
        }
        if (ageSec <= 30) {
            return "healthy (last inbound " + ageSec + "s ago)";
        }
        return "stale (last inbound " + ageSec + "s ago)";
    }

    /**
     * Returns seconds since last READY message, or -1 if never received.
     */
    public long secondsSinceReady() {
        return secondsSince(lastReadyEpochMillis);
    }

    /**
     * Returns immutable IPC stats snapshot.
     */
    public IpcStatsSnapshot snapshotStats() {
        return new IpcStatsSnapshot(
                statsStartedEpochMillis,
                inboundTextCount.get(),
                inboundBinaryCount.get(),
                inboundFrameCount.get(),
                inboundDeltaCount.get(),
                inboundErrorEventCount.get(),
                lastInboundEpochMillis,
                lastReadyEpochMillis
        );
    }

    /**
     * Returns immutable per-screen IPC stats snapshot.
     */
    public Map<UUID, ScreenIpcStatsSnapshot> snapshotScreenStats() {
        final HashMap<UUID, ScreenIpcStatsSnapshot> snapshot = new HashMap<>();
        for (final Map.Entry<UUID, ScreenIpcStatsCounter> entry : screenStats.entrySet()) {
            final ScreenIpcStatsCounter counter = entry.getValue();
            snapshot.put(entry.getKey(), new ScreenIpcStatsSnapshot(
                    counter.frameCount.get(),
                    counter.deltaCount.get(),
                    counter.errorCount.get(),
                    counter.lastInboundAtEpochMillis.get()
            ));
        }
        return Map.copyOf(snapshot);
    }

    private long secondsSince(final long timestamp) {
        if (timestamp <= 0L) {
            return -1L;
        }
        return Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
    }

    private ScreenIpcStatsCounter screenStats(final UUID screenId) {
        return screenStats.computeIfAbsent(screenId, ignored -> new ScreenIpcStatsCounter());
    }

    private void startRendererProcess() {
        if (stopping) {
            return;
        }
        final String nodePath = plugin.getConfig().getString("browser.node-path", "");
        final String executable = nodePath == null || nodePath.isBlank() ? "node" : nodePath;
        final String rendererDir = plugin.getConfig().getString("browser.renderer-dir", "browser-renderer");
        final java.io.File configuredDir = rendererDir == null ? new java.io.File("browser-renderer") : new java.io.File(rendererDir);
        final java.io.File workingDir = configuredDir.isAbsolute()
                ? configuredDir
                : new java.io.File(Bukkit.getWorldContainer(), rendererDir).getAbsoluteFile();

        try {
            final ProcessBuilder pb = new ProcessBuilder(executable, "dist/index.js");
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            final Map<String, String> env = pb.environment();
                final String captureMode = plugin.getConfig().getString("audio.capture-mode", "none");
                env.put("MAPBROWSER_AUDIO_CAPTURE_MODE", captureMode == null ? "none" : captureMode);
                env.put("MAPBROWSER_AUDIO_MEDIARECORDER_TIMESLICE_MS",
                    String.valueOf(plugin.getConfig().getInt("audio.media-recorder-timeslice-ms", 200)));
            final String testOpusBase64 = plugin.getConfig().getString("audio.test-opus-base64", "");
            if (testOpusBase64 != null && !testOpusBase64.isBlank()) {
                env.put("MAPBROWSER_AUDIO_TEST_OPUS_BASE64", testOpusBase64);
                env.put("MAPBROWSER_AUDIO_SAMPLE_RATE",
                        String.valueOf(plugin.getConfig().getInt("audio.sample-rate", 48000)));
                env.put("MAPBROWSER_AUDIO_FRAME_INTERVAL_MS",
                        String.valueOf(plugin.getConfig().getInt("audio.frame-interval-ms", 100)));
            }
            rendererProcess = pb.start();
            rendererStartEpochMillis = System.currentTimeMillis();
            connectFailures.set(0);
            watchRendererExit(rendererProcess);
                plugin.getLogger().log(Level.INFO,
                    "Started browser-renderer process: node={0} dir={1}",
                    new Object[]{executable, workingDir.getAbsolutePath()});
        } catch (final IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to start browser-renderer process: {0}", ex.getMessage());
            plugin.getLogger().log(Level.WARNING, "Renderer startup context: node={0}, dir={1}, dirExists={2}", new Object[]{
                    executable,
                    workingDir.getAbsolutePath(),
                    workingDir.exists()
            });
        }
    }

    private void connect() {
        if (stopping) {
            return;
        }

        final Process process = rendererProcess;
        if (process == null || !process.isAlive()) {
            startRendererProcess();
        }

        final int port = plugin.getConfig().getInt("browser.ipc-port", 25600);
        final URI uri = URI.create("ws://127.0.0.1:" + port);

        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new Listener())
                .thenAccept(ws -> {
                    socket = ws;
                    reconnecting.set(false);
                    restartAttempts.set(0);
                    connectFailures.set(0);
                    plugin.getLogger().log(Level.INFO, "Connected to browser-renderer: {0}", uri);
                })
                .exceptionally(ex -> {
                    final Throwable cause = rootCause(ex);
                    final int failures = connectFailures.incrementAndGet();
                    if (isTransientBootConnectFailure(cause)) {
                        plugin.getLogger().log(Level.INFO,
                                "IPC endpoint is not ready yet (attempt {0}): {1}",
                                new Object[]{failures, describeThrowable(cause)});
                    } else {
                        plugin.getLogger().log(Level.WARNING, "IPC connect failed: {0}", describeThrowable(cause));
                    }
                    scheduleReconnect();
                    return null;
                });
    }

    private void scheduleReconnect() {
        if (stopping) {
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            reconnecting.set(false);
            connect();
        }, isRendererBootingWindow() ? 20L : 20L * 3L);
    }

    private boolean isRendererBootingWindow() {
        final long startedAt = rendererStartEpochMillis;
        if (startedAt <= 0L) {
            return false;
        }
        return (System.currentTimeMillis() - startedAt) <= 15_000L;
    }

    private static Throwable rootCause(final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isTransientBootConnectFailure(final Throwable throwable) {
        if (!isRendererBootingWindow()) {
            return false;
        }
        return throwable instanceof ConnectException || throwable instanceof ClosedChannelException;
    }

    private static String describeThrowable(final Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        final String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.toString();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private void watchRendererExit(final Process process) {
        Thread.startVirtualThread(() -> {
            try {
                final int exitCode = process.waitFor();
                if (stopping) {
                    return;
                }

                plugin.getLogger().log(Level.WARNING, "browser-renderer exited with code: {0}", exitCode);
                final boolean autoRestart = plugin.getConfig().getBoolean("browser.auto-restart", true);
                final int maxRestarts = plugin.getConfig().getInt("browser.max-restart-attempts", 3);
                final int delaySec = plugin.getConfig().getInt("browser.restart-delay", 30);
                final int attempt = restartAttempts.incrementAndGet();

                if (!autoRestart || attempt > maxRestarts) {
                    plugin.getLogger().warning("Auto-restart disabled or max restart attempts reached.");
                    return;
                }

                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    if (stopping) {
                        return;
                    }
                    plugin.getLogger().log(Level.WARNING, "Restarting browser-renderer (attempt {0})", attempt);
                    startRendererProcess();
                    connect();
                }, 20L * Math.max(1, delaySec));
            } catch (final InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void sendJson(final JsonObject obj) {
        final WebSocket ws = socket;
        if (ws == null) {
            return;
        }
        ws.sendText(GSON.toJson(obj), true);
    }

    private void onMessage(final String message) {
        lastInboundEpochMillis = System.currentTimeMillis();
        inboundTextCount.incrementAndGet();
        try {
            final JsonObject obj = JsonParser.parseString(message).getAsJsonObject();
            final String type = obj.get("type").getAsString();

            switch (type) {
                case "READY" -> {
                    lastReadyEpochMillis = System.currentTimeMillis();
                    plugin.getLogger().info("browser-renderer is READY");
                }
                case "FRAME" -> {
                    inboundFrameCount.incrementAndGet();
                    handleFrame(obj);
                }
                case "DELTA_FRAME" -> {
                    inboundDeltaCount.incrementAndGet();
                    handleDeltaFrame(obj);
                }
                case "URL_CHANGED" -> handleUrlChanged(obj);
                case "PAGE_LOADED" -> handlePageLoaded(obj);
                case "AUDIO_FRAME" -> handleAudioFrame(obj);
                case "ERROR" -> {
                    inboundErrorEventCount.incrementAndGet();
                    handleError(obj);
                }
                default -> plugin.getLogger().log(Level.WARNING, "Unknown IPC message type: {0}", type);
            }
        } catch (final IllegalStateException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle IPC message: {0}", ex.getMessage());
        }
    }

    private void onBinaryMessage(final byte[] payload) {
        if (payload == null || payload.length < 40) {
            return;
        }

        lastInboundEpochMillis = System.currentTimeMillis();
        inboundBinaryCount.incrementAndGet();

        try {
            final ByteBuffer buffer = ByteBuffer.wrap(payload);
            final int magic = buffer.getInt();
            if (magic != FRAME_MAGIC) {
                return;
            }

            final int version = Byte.toUnsignedInt(buffer.get());
            if (version != FRAME_VERSION) {
                plugin.getLogger().log(Level.WARNING, "Unsupported binary frame version: {0}", version);
                return;
            }

            final int type = Byte.toUnsignedInt(buffer.get());
            buffer.getShort(); // reserved

            final long mostSigBits = buffer.getLong();
            final long leastSigBits = buffer.getLong();
            final UUID screenId = new UUID(mostSigBits, leastSigBits);

            final int a = buffer.getInt();
            final int b = buffer.getInt();
            final int c = buffer.getInt();
            final int d = buffer.getInt();

            final byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            if (type == FRAME_TYPE_FULL) {
                inboundFrameCount.incrementAndGet();
                final int width = a & 0xFFFF;
                final int height = (a >>> 16) & 0xFFFF;
                if (width <= 0 || height <= 0) {
                    return;
                }
                plugin.getScreenManager().getScreen(screenId)
                        .ifPresent(screen -> plugin.getFrameRenderer().renderFrame(screen, data));
                return;
            }

            if (type == FRAME_TYPE_DELTA) {
                inboundDeltaCount.incrementAndGet();
                final int x = a;
                final int y = b;
                final int w = c;
                final int h = d;
                plugin.getScreenManager().getScreen(screenId)
                        .ifPresent(screen -> plugin.getFrameRenderer().renderDeltaFrame(screen, data, x, y, w, h));
            }
        } catch (final IllegalStateException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle binary IPC message: {0}", ex.getMessage());
        }
    }

    private void handleFrame(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        final ScreenIpcStatsCounter stats = screenStats(screenId);
        stats.frameCount.incrementAndGet();
        stats.lastInboundAtEpochMillis.set(System.currentTimeMillis());
        final byte[] data = Base64.getDecoder().decode(obj.get("data").getAsString().getBytes(StandardCharsets.UTF_8));
        plugin.getScreenManager().getScreen(screenId)
                .ifPresent(screen -> plugin.getFrameRenderer().renderFrame(screen, data));
    }

    private void handleDeltaFrame(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        final ScreenIpcStatsCounter stats = screenStats(screenId);
        stats.deltaCount.incrementAndGet();
        stats.lastInboundAtEpochMillis.set(System.currentTimeMillis());
        final byte[] data = Base64.getDecoder().decode(obj.get("data").getAsString().getBytes(StandardCharsets.UTF_8));
        final int x = obj.get("x").getAsInt();
        final int y = obj.get("y").getAsInt();
        final int w = obj.get("w").getAsInt();
        final int h = obj.get("h").getAsInt();

        plugin.getScreenManager().getScreen(screenId)
                .ifPresent(screen -> plugin.getFrameRenderer().renderDeltaFrame(screen, data, x, y, w, h));
    }

    private void handleUrlChanged(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        final String url = obj.get("url").getAsString();
        plugin.getScreenManager().getScreen(screenId)
                .ifPresent(screen -> screen.setCurrentUrl(url));
    }

    private void handlePageLoaded(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        plugin.getScreenManager().getScreen(screenId)
                .ifPresent(screen -> screen.setState(ScreenState.PLAYING));
    }

    private void handleError(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        final ScreenIpcStatsCounter stats = screenStats(screenId);
        stats.errorCount.incrementAndGet();
        stats.lastInboundAtEpochMillis.set(System.currentTimeMillis());
        final String message = obj.get("message").getAsString();
        plugin.getScreenManager().getScreen(screenId)
                .ifPresent(screen -> screen.setState(ScreenState.ERROR));
        plugin.getLogger().log(Level.WARNING, "Renderer error [{0}]: {1}", new Object[]{screenId, message});
    }

    private void handleAudioFrame(final JsonObject obj) {
        final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
        final int sampleRate = obj.has("sampleRate") ? obj.get("sampleRate").getAsInt() : 48000;
        final byte[] opus = Base64.getDecoder().decode(obj.get("data").getAsString().getBytes(StandardCharsets.UTF_8));
        if (sampleRate <= 0 || opus.length == 0) {
            return;
        }
        plugin.getAudioBridge().publishFrame(screenId, opus, sampleRate);
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

        @Override
        public void onOpen(final WebSocket webSocket) {
            webSocket.request(1);
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(final WebSocket webSocket, final CharSequence data, final boolean last) {
            textBuffer.append(data);
            if (last) {
                onMessage(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(final WebSocket webSocket, final ByteBuffer data, final boolean last) {
            final byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryBuffer.write(chunk, 0, chunk.length);
            if (last) {
                onBinaryMessage(binaryBuffer.toByteArray());
                binaryBuffer.reset();
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(final WebSocket webSocket, final Throwable error) {
            plugin.getLogger().log(Level.WARNING, "IPC websocket error: {0}", error.getMessage());
            scheduleReconnect();
            WebSocket.Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onClose(final WebSocket webSocket, final int statusCode, final String reason) {
            plugin.getLogger().log(Level.WARNING, "IPC websocket closed: {0} {1}", new Object[]{statusCode, reason});
            if (!stopping) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Immutable stats snapshot for diagnostics.
     */
    public record IpcStatsSnapshot(
            long startedAtEpochMillis,
            long inboundText,
            long inboundBinary,
            long inboundFrame,
            long inboundDelta,
            long inboundErrorEvent,
            long lastInboundAtEpochMillis,
            long lastReadyAtEpochMillis
    ) {
        /**
         * Returns elapsed seconds since stats collection started.
         */
        public long uptimeSeconds() {
            return Math.max(0L, (System.currentTimeMillis() - startedAtEpochMillis) / 1000L);
        }

        /**
         * Returns total inbound message count.
         */
        public long inboundTotal() {
            return inboundText + inboundBinary;
        }
    }

    /**
     * Immutable per-screen IPC stats snapshot.
     */
    public record ScreenIpcStatsSnapshot(
            long frameCount,
            long deltaCount,
            long errorCount,
            long lastInboundAtEpochMillis
    ) {
    }

    private static final class ScreenIpcStatsCounter {
        private final AtomicLong frameCount = new AtomicLong(0L);
        private final AtomicLong deltaCount = new AtomicLong(0L);
        private final AtomicLong errorCount = new AtomicLong(0L);
        private final AtomicLong lastInboundAtEpochMillis = new AtomicLong(0L);
    }
}
