package com.tukuyomil032.mapbrowser.audio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;

/**
 * Companion-mod audio bridge over Bukkit plugin messaging.
 */
public final class PluginMessageAudioBridge implements AudioBridge {
    public static final String CHANNEL = "mapbrowser:audio";

    private final MapBrowserPlugin plugin;
    private final AtomicLong publishedFrames = new AtomicLong(0L);
    private final AtomicLong dispatchedPackets = new AtomicLong(0L);
    private final AtomicLong droppedFrames = new AtomicLong(0L);

    /**
     * Creates bridge.
     */
    public PluginMessageAudioBridge(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers outgoing plugin channel.
     */
    public void init() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /**
     * Unregisters outgoing plugin channel.
     */
    public void shutdown() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    /**
     * Publishes encoded audio frame to online players for companion mod handling.
     */
    @Override
    public void publishFrame(final UUID screenId, final byte[] opusFrame, final int sampleRate) {
        if (opusFrame == null || opusFrame.length == 0) {
            return;
        }

        publishedFrames.incrementAndGet();

        final byte[] payload = encode(screenId, opusFrame, sampleRate);
        if (payload.length == 0) {
            droppedFrames.incrementAndGet();
            return;
        }

        final var screen = plugin.getScreenManager().getScreen(screenId).orElse(null);
        if (screen == null) {
            droppedFrames.incrementAndGet();
            return;
        }

        final int maxDistance = plugin.getConfig().getInt("audio.max-distance", 32);
        final double maxDistanceSquared = (double) maxDistance * (double) maxDistance;
        int delivered = 0;

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("mapbrowser.view")) {
                continue;
            }
            if (!player.getWorld().getName().equals(screen.getWorldName())) {
                continue;
            }

            final double dx = player.getLocation().getX() - screen.getOriginX();
            final double dy = player.getLocation().getY() - screen.getOriginY();
            final double dz = player.getLocation().getZ() - screen.getOriginZ();
            final double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
            if (maxDistance > 0 && distanceSquared > maxDistanceSquared) {
                continue;
            }

            player.sendPluginMessage(plugin, CHANNEL, payload);
            delivered++;
        }

        if (delivered == 0) {
            droppedFrames.incrementAndGet();
            return;
        }
        dispatchedPackets.addAndGet(delivered);
    }

    /**
     * Returns diagnostics summary.
     */
    @Override
    public String diagnostics() {
        return "audio=pluginmsg published=" + publishedFrames.get()
                + " delivered=" + dispatchedPackets.get()
                + " dropped=" + droppedFrames.get();
    }

    private byte[] encode(final UUID screenId, final byte[] opusFrame, final int sampleRate) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeLong(screenId.getMostSignificantBits());
            dos.writeLong(screenId.getLeastSignificantBits());
            dos.writeInt(sampleRate);
            dos.writeInt(opusFrame.length);
            dos.write(opusFrame);
            dos.flush();
            return bos.toByteArray();
        } catch (final IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode audio frame payload: {0}", ex.getMessage());
            return new byte[0];
        }
    }
}
