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
                return;
            }
            if ("RELOAD_SCREEN".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                handleReloadScreen(player, screenIdRaw);
                return;
            }
            if ("SET_FPS".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                final int fps = in.readInt();
                handleSetFps(player, screenIdRaw, fps);
                return;
            }
            if ("CLOSE_SCREEN".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                handleCloseScreen(player, screenIdRaw);
                return;
            }
            if ("BACK_SCREEN".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                handleBackScreen(player, screenIdRaw);
                return;
            }
            if ("FORWARD_SCREEN".equalsIgnoreCase(command)) {
                final String screenIdRaw = in.readUTF();
                handleForwardScreen(player, screenIdRaw);
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

    private void handleReloadScreen(final Player player, final String screenIdRaw) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity RELOAD_SCREEN rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        if (!plugin.getService().reload(screenId)) {
            plugin.getLogger().log(Level.WARNING, "Velocity RELOAD_SCREEN rejected: screen not found {0}", screenId);
            return;
        }

        plugin.getLogger().log(Level.INFO, "Velocity RELOAD_SCREEN accepted: {0}", screenId);
        sendStatus(player);
    }

    private void handleSetFps(final Player player, final String screenIdRaw, final int fps) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity SET_FPS rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        final int maxFps = plugin.getConfig().getInt("screen.max-fps", 30);
        if (fps < 1 || fps > maxFps) {
            plugin.getLogger().log(Level.WARNING, "Velocity SET_FPS rejected: invalid fps {0}", fps);
            return;
        }

        if (!plugin.getService().setFps(screenId, fps)) {
            plugin.getLogger().log(Level.WARNING, "Velocity SET_FPS rejected: screen not found {0}", screenId);
            return;
        }

        plugin.getLogger().log(Level.INFO, "Velocity SET_FPS accepted: {0} -> {1}", new Object[]{screenId, fps});
        sendStatus(player);
    }

    private void handleCloseScreen(final Player player, final String screenIdRaw) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity CLOSE_SCREEN rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        if (!plugin.getService().close(screenId)) {
            plugin.getLogger().log(Level.WARNING, "Velocity CLOSE_SCREEN rejected: screen not found {0}", screenId);
            return;
        }

        plugin.getLogger().log(Level.INFO, "Velocity CLOSE_SCREEN accepted: {0}", screenId);
        sendStatus(player);
    }

    private void handleBackScreen(final Player player, final String screenIdRaw) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity BACK_SCREEN rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        if (!plugin.getService().goBack(screenId)) {
            plugin.getLogger().log(Level.WARNING, "Velocity BACK_SCREEN rejected: screen not found {0}", screenId);
            return;
        }

        plugin.getLogger().log(Level.INFO, "Velocity BACK_SCREEN accepted: {0}", screenId);
        sendStatus(player);
    }

    private void handleForwardScreen(final Player player, final String screenIdRaw) {
        final UUID screenId;
        try {
            screenId = UUID.fromString(screenIdRaw);
        } catch (final IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Velocity FORWARD_SCREEN rejected: invalid screenId {0}", screenIdRaw);
            return;
        }

        if (!plugin.getService().goForward(screenId)) {
            plugin.getLogger().log(Level.WARNING, "Velocity FORWARD_SCREEN rejected: screen not found {0}", screenId);
            return;
        }

        plugin.getLogger().log(Level.INFO, "Velocity FORWARD_SCREEN accepted: {0}", screenId);
        sendStatus(player);
    }
}
