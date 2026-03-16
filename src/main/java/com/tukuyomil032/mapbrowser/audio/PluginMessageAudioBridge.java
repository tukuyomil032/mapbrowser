package com.tukuyomil032.mapbrowser.audio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
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

        final byte[] payload = encode(screenId, opusFrame, sampleRate);
        if (payload.length == 0) {
            return;
        }

        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("mapbrowser.view")) {
                continue;
            }
            player.sendPluginMessage(plugin, CHANNEL, payload);
        }
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
