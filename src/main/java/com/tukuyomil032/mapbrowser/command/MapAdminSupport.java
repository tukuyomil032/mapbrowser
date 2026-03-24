package com.tukuyomil032.mapbrowser.command;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;

/**
 * Encapsulates /mb admin command and perf bench logic.
 */
final class MapAdminSupport {
    private final MapBrowserPlugin plugin;
    private final HashMap<UUID, Integer> perfBenchTaskIds;
    private final MessageBridge messages;
    private final ScreenResolver screenResolver;

    MapAdminSupport(
            final MapBrowserPlugin plugin,
            final HashMap<UUID, Integer> perfBenchTaskIds,
            final MessageBridge messages,
            final ScreenResolver screenResolver
    ) {
        this.plugin = plugin;
        this.perfBenchTaskIds = perfBenchTaskIds;
        this.messages = messages;
        this.screenResolver = screenResolver;
    }

    boolean handleAdmin(final CommandSender sender, final String[] args) {
        if (sender == null) {
            return true;
        }
        if (!plugin.getPermissionManager().has(sender, "mapbrowser.admin")) {
            messages.sendError(sender, messages.tk("command.error.no-permission", "No permission.", "権限がありません。"));
            return true;
        }
        if (args.length < 2) {
            messages.sendError(sender, messages.tk("command.usage.admin", "Usage: /mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>", "使用法: /mb admin status|deps|reload|perf [screen]|perfbench <sec>|stop <screenId>"));
            return true;
        }

        if ("status".equalsIgnoreCase(args[1])) {
            messages.sendHeader(sender, messages.tk("command.admin.status.header", "MAPBROWSER STATUS", "MAPBROWSER ステータス"));
            messages.sendInfo(sender, messages.tkp("command.admin.status.ipc-connected", "IPC connected: {value}", "IPC 接続: {value}", Map.of("value", plugin.getBrowserIPCClient().isConnected())));
            messages.sendInfo(sender, messages.tkp("command.admin.status.ipc-health", "IPC health: {value}", "IPC ヘルス: {value}", Map.of("value", plugin.getBrowserIPCClient().healthSummary())));
            final long readyAge = plugin.getBrowserIPCClient().secondsSinceReady();
            messages.sendInfo(sender, messages.tkp("command.admin.status.ready-age", "READY age: {value}", "READY経過: {value}", Map.of("value", readyAge >= 0 ? readyAge + "s" : "never")));
            messages.sendInfo(sender, messages.tkp("command.admin.status.screens", "Screens: {count}", "スクリーン数: {count}", Map.of("count", plugin.getScreenManager().getAllScreens().size())));
            messages.sendLine(sender);
            return true;
        }

        if ("deps".equalsIgnoreCase(args[1])) {
            messages.sendHeader(sender, messages.tk("command.admin.deps.header", "DEPENDENCY CHECK", "依存関係チェック"));
            messages.sendInfo(sender, messages.tkp("command.admin.deps.packetevents", "PacketEvents (softdepend): {state}", "PacketEvents (softdepend): {state}", Map.of("state", pluginState("PacketEvents"))));
            messages.sendInfo(sender, messages.tkp("command.admin.deps.anvilgui", "AnvilGUI (softdepend): {state}", "AnvilGUI (softdepend): {state}", Map.of("state", pluginState("AnvilGUI"))));
            messages.sendInfo(sender, messages.tkp("command.admin.deps.spark", "spark (optional): {state}", "spark (optional): {state}", Map.of("state", pluginState("spark"))));
            messages.sendLine(sender);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[1])) {
            plugin.reloadConfig();
            messages.sendOk(sender, messages.tk("command.admin.reload.ok", "Config reloaded.", "設定を再読み込みしました。"));
            messages.sendInfo(sender, messages.tkp("command.admin.reload.storage", "storage={value}", "storage={value}", Map.of("value", plugin.getConfig().getString("storage", "yaml"))));
            messages.sendInfo(sender, messages.tkp("command.admin.reload.render-distance", "render-distance={value}", "render-distance={value}", Map.of("value", plugin.getConfig().getInt("screen.render-distance", 64))));
            return true;
        }

        if ("perf".equalsIgnoreCase(args[1])) {
            final var ipcStats = plugin.getBrowserIPCClient().snapshotStats();
            final var screenStats = plugin.getBrowserIPCClient().snapshotScreenStats();
            final long uptime = Math.max(1L, ipcStats.uptimeSeconds());
            final long totalInbound = ipcStats.inboundTotal();
            final long perSecond = totalInbound / uptime;
            final Runtime runtime = Runtime.getRuntime();
            final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
            final long maxMb = runtime.maxMemory() / (1024L * 1024L);

            messages.sendHeader(sender, messages.tk("command.admin.perf.header", "MAPBROWSER PERF", "MAPBROWSER パフォーマンス"));
            messages.sendInfo(sender, messages.tkp("command.admin.perf.uptime", "uptime={uptime}s inbound={inbound} msg({rate}/s)", "uptime={uptime}s inbound={inbound} msg({rate}/s)", Map.of("uptime", uptime, "inbound", totalInbound, "rate", perSecond)));
            messages.sendInfo(sender, messages.tkp("command.admin.perf.frames", "frames(full={full}, delta={delta}) errors={errors}", "frames(full={full}, delta={delta}) errors={errors}", Map.of("full", ipcStats.inboundFrame(), "delta", ipcStats.inboundDelta(), "errors", ipcStats.inboundErrorEvent())));
            messages.sendInfo(sender, messages.tkp("command.admin.perf.tps", "tps={value}", "tps={value}", Map.of("value", readCurrentTpsText())));
            messages.sendInfo(sender, messages.tkp("command.admin.perf.memory", "memory={used}MB/{max}MB screens={screens}", "memory={used}MB/{max}MB screens={screens}", Map.of("used", usedMb, "max", maxMb, "screens", plugin.getScreenManager().getAllScreens().size())));
            messages.sendInfo(sender, messages.tkp("command.admin.perf.audio", "audio={value}", "audio={value}", Map.of("value", plugin.getAudioBridge().diagnostics())));

            if (args.length >= 3 && sender instanceof Player player) {
                final Optional<Screen> target = screenResolver.resolve(args[2], player);
                if (target.isEmpty()) {
                    messages.sendError(sender, messages.tkp(
                            "command.error.screen-not-found-perf",
                            "Screen not found for perf detail: {screen}",
                            "パフォーマンス詳細対象のスクリーンが見つかりません: {screen}",
                            Map.of("screen", args[2])
                    ));
                    messages.sendLine(sender);
                    return true;
                }
                final Screen screen = target.get();
                final var detail = screenStats.get(screen.getId());
                messages.sendInfo(sender, messages.tkp("command.admin.perf.screen", "screen={screen} id={id}", "screen={screen} id={id}", Map.of("screen", screen.getName(), "id", screen.getId())));
                messages.sendInfo(sender, messages.tkp("command.admin.perf.screen-state", "state={state} size={width}x{height} fps={fps}", "state={state} size={width}x{height} fps={fps}", Map.of("state", screen.getState(), "width", screen.getWidth(), "height", screen.getHeight(), "fps", screen.getFps())));
                if (detail == null) {
                    messages.sendInfo(sender, messages.tk("command.admin.perf.screen-ipc-empty", "ipc(per-screen)=no data yet", "ipc(per-screen)=まだデータなし"));
                } else {
                    final long since = detail.lastInboundAtEpochMillis() <= 0L
                            ? -1L
                            : Math.max(0L, (System.currentTimeMillis() - detail.lastInboundAtEpochMillis()) / 1000L);
                    messages.sendInfo(sender, messages.tkp("command.admin.perf.screen-ipc", "ipc(per-screen) full={full} delta={delta} err={errors} last={last}", "ipc(per-screen) full={full} delta={delta} err={errors} last={last}", Map.of("full", detail.frameCount(), "delta", detail.deltaCount(), "errors", detail.errorCount(), "last", since >= 0 ? since + "s" : "never")));
                }
                messages.sendLine(sender);
                return true;
            }

            final List<Screen> topScreens = plugin.getScreenManager().getAllScreens().stream()
                    .sorted((left, right) -> Long.compare(
                            totalFrames(screenStats.get(right.getId())),
                            totalFrames(screenStats.get(left.getId()))
                    ))
                    .limit(5)
                    .toList();
            for (final Screen screen : topScreens) {
                final var detail = screenStats.get(screen.getId());
                final long full = detail == null ? 0L : detail.frameCount();
                final long delta = detail == null ? 0L : detail.deltaCount();
                final long errors = detail == null ? 0L : detail.errorCount();
                messages.sendInfo(sender, messages.tkp("command.admin.perf.top-screen", "screen={screen} frames={frames} (f={full} d={delta}) err={errors}", "screen={screen} frames={frames} (f={full} d={delta}) err={errors}", Map.of("screen", screen.getName(), "frames", (full + delta), "full", full, "delta", delta, "errors", errors)));
            }
            messages.sendLine(sender);
            return true;
        }

        if ("perfbench".equalsIgnoreCase(args[1])) {
            if (!(sender instanceof Player player)) {
                messages.sendError(sender, messages.tk("command.error.player-only", "Player only command.", "このコマンドはプレイヤー専用です。"));
                return true;
            }
            final int durationSec;
            if (args.length >= 3) {
                try {
                    durationSec = Integer.parseInt(args[2]);
                } catch (final NumberFormatException ex) {
                    messages.sendError(sender, messages.tk("command.error.perfbench-duration-integer", "Duration must be integer seconds.", "計測時間は整数秒で指定してください。"));
                    return true;
                }
            } else {
                durationSec = 30;
            }
            if (durationSec < 5 || durationSec > 600) {
                messages.sendError(sender, messages.tk("command.error.perfbench-duration-range", "Duration must be 5..600 seconds.", "計測時間は 5..600 秒の範囲で指定してください。"));
                return true;
            }
            startPerfBench(player, durationSec);
            return true;
        }

        if ("stop".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                messages.sendError(sender, messages.tk("command.usage.admin-stop", "Usage: /mb admin stop <screenId>", "使用法: /mb admin stop <screenId>"));
                return true;
            }
            try {
                final UUID screenId = UUID.fromString(args[2]);
                plugin.getBrowserIPCClient().sendClose(screenId);
                messages.sendOk(sender, messages.tkp("command.ok.admin-stop-sent", "Sent CLOSE for {id}", "CLOSEを送信しました: {id}", Map.of("id", screenId)));
                return true;
            } catch (final IllegalArgumentException ex) {
                messages.sendError(sender, messages.tk("command.error.invalid-uuid", "Invalid UUID format.", "UUID形式が不正です。"));
                return true;
            }
        }

        messages.sendError(sender, messages.tk("command.error.unknown-admin", "Unknown admin command.", "不明なadminコマンドです。"));
        return true;
    }

    private void startPerfBench(final Player player, final int durationSec) {
        final UUID playerId = player.getUniqueId();
        final Integer existingTaskId = perfBenchTaskIds.remove(playerId);
        if (existingTaskId != null) {
            Bukkit.getScheduler().cancelTask(existingTaskId);
        }

        final var startStats = plugin.getBrowserIPCClient().snapshotStats();
        final long startedAt = System.currentTimeMillis();
        final double[] samples = new double[durationSec];
        final int[] sampleIndex = {0};

        messages.sendHeader(player, messages.tk("command.perfbench.header", "MAPBROWSER PERFBENCH", "MAPBROWSER PERFBENCH"));
        messages.sendInfo(player, messages.tkp("command.perfbench.duration", "Duration: {seconds}s", "計測時間: {seconds}秒", Map.of("seconds", durationSec)));
        messages.sendInfo(player, messages.tk("command.perfbench.collecting", "Collecting TPS samples...", "TPSサンプルを収集中..."));
        messages.sendLine(player);

        final int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                final Integer removed = perfBenchTaskIds.remove(playerId);
                if (removed != null) {
                    Bukkit.getScheduler().cancelTask(removed);
                }
                return;
            }

            final int index = sampleIndex[0];
            if (index >= durationSec) {
                final Integer removed = perfBenchTaskIds.remove(playerId);
                if (removed != null) {
                    Bukkit.getScheduler().cancelTask(removed);
                }

                final var endStats = plugin.getBrowserIPCClient().snapshotStats();
                final long elapsedSec = Math.max(1L, (System.currentTimeMillis() - startedAt) / 1000L);
                double min = 20.0;
                double max = 0.0;
                double sum = 0.0;
                for (final double sample : samples) {
                    min = Math.min(min, sample);
                    max = Math.max(max, sample);
                    sum += sample;
                }
                final double avg = sum / samples.length;

                final long frameDelta = Math.max(0L, endStats.inboundFrame() - startStats.inboundFrame());
                final long deltaDelta = Math.max(0L, endStats.inboundDelta() - startStats.inboundDelta());

                messages.sendHeader(player, messages.tk("command.perfbench.result-header", "MAPBROWSER PERFBENCH RESULT", "MAPBROWSER PERFBENCH 結果"));
                messages.sendInfo(player, messages.tkp(
                    "command.perfbench.tps-stats",
                    "tps avg={avg} min={min} max={max}",
                    "tps avg={avg} min={min} max={max}",
                    Map.of(
                        "avg", String.format(java.util.Locale.ROOT, "%.2f", avg),
                        "min", String.format(java.util.Locale.ROOT, "%.2f", min),
                        "max", String.format(java.util.Locale.ROOT, "%.2f", max)
                    )
                ));
                messages.sendInfo(player, messages.tkp(
                    "command.perfbench.elapsed",
                    "elapsed={elapsed}s fullFrames={full} deltaFrames={delta}",
                    "elapsed={elapsed}s fullFrames={full} deltaFrames={delta}",
                    Map.of("elapsed", elapsedSec, "full", frameDelta, "delta", deltaDelta)
                ));
                messages.sendInfo(player, messages.tkp(
                    "command.perfbench.frames-per-sec",
                    "frames/sec={fps}",
                    "frames/sec={fps}",
                    Map.of("fps", ((frameDelta + deltaDelta) / elapsedSec))
                ));
                messages.sendLine(player);
                return;
            }

            samples[index] = readCurrentTpsValue();
            sampleIndex[0] = index + 1;
        }, 20L, 20L);

        perfBenchTaskIds.put(playerId, taskId);
    }

    private static long totalFrames(final com.tukuyomil032.mapbrowser.ipc.BrowserIPCClient.ScreenIpcStatsSnapshot snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        return snapshot.frameCount() + snapshot.deltaCount();
    }

    private String readCurrentTpsText() {
        try {
            final double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) {
                return "N/A";
            }
            return String.format(java.util.Locale.ROOT, "%.2f", tps[0]);
        } catch (final Throwable ignored) {
            return "N/A";
        }
    }

    private double readCurrentTpsValue() {
        try {
            final double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) {
                return 20.0;
            }
            return Math.max(0.0, tps[0]);
        } catch (final Throwable ignored) {
            return 20.0;
        }
    }

    private String pluginState(final String pluginName) {
        final var found = Bukkit.getPluginManager().getPlugin(pluginName);
        if (found == null) {
            return "not installed";
        }
        return found.isEnabled()
                ? "enabled v" + found.getPluginMeta().getVersion()
                : "installed but disabled";
    }

    interface MessageBridge {
        void sendHeader(CommandSender sender, String message);

        void sendLine(CommandSender sender);

        void sendInfo(CommandSender sender, String message);

        void sendOk(CommandSender sender, String message);

        void sendError(CommandSender sender, String message);

        String tk(String key, String en, String ja);

        String tkp(String key, String en, String ja, Map<String, ?> placeholders);
    }

    interface ScreenResolver {
        Optional<Screen> resolve(String query, Player player);
    }
}
