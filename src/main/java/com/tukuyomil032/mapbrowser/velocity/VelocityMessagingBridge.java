package com.tukuyomil032.mapbrowser.velocity;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.util.UrlSecurityValidator;

/**
 * Handles plugin messaging bridge for Velocity proxy integration.
 */
public final class VelocityMessagingBridge implements PluginMessageListener {
    public static final String CHANNEL = "mapbrowser:velocity";

    private final MapBrowserPlugin plugin;

    /**
     * Creates bridge.
     */
    public VelocityMessagingBridge(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers plugin channels.
     */
    public void init() {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /**
     * Unregisters plugin channels.
     */
    public void shutdown() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }

    /**
     * Handles incoming messages from proxy side.
     */
    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!CHANNEL.equals(channel) || message == null || message.length == 0) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(message))) {
            final String command = in.readUTF();
            if ("PING".equalsIgnoreCase(command)) {
                sendStatus(player);
                return;
            }
            if ("OPEN_URL".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                final String url = in.readUTF();
                handleOpenUrl(player, screenIdRaw, url);
            }
        } catch (final IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to decode velocity message: {0}", ex.getMessage());
        }
    }

    /**
     * Sends screen/status snapshot to proxy via a player connection.
     */
    public void sendStatus(final Player player) {
        if (player == null) {
            return;
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            out.writeUTF("STATUS");
            out.writeInt(plugin.getScreenManager().getAllScreens().size());
            out.writeBoolean(plugin.getBrowserIPCClient().isConnected());
            out.writeInt(player.getServer().getOnlinePlayers().size());
            out.flush();
            player.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (final IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to encode velocity status payload: {0}", ex.getMessage());
        }
    }

    private void handleOpenUrl(final Player player, final String screenIdRaw, final String url) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity OPEN_URL rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        final UrlSecurityValidator.ValidationResult validated = UrlSecurityValidator.validate(url, plugin.getConfig());
        if (!validated.allowed()) {
            plugin.getLogger().log(Level.WARNING, "Velocity OPEN_URL rejected: {0}", validated.valueOrReason());
            return;
        }

        plugin.getService().openUrl(screenId, validated.valueOrReason());
        plugin.getLogger().log(Level.INFO, "Velocity OPEN_URL accepted: {0} -> {1}", new Object[]{screenId, validated.valueOrReason()});
        sendStatus(player);
    }
}
