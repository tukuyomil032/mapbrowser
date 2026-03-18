package com.tukuyomil032.mapbrowser;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.tukuyomil032.mapbrowser.audio.AudioBridge;
import com.tukuyomil032.mapbrowser.audio.NoopAudioBridge;
import com.tukuyomil032.mapbrowser.audio.PluginMessageAudioBridge;
import com.tukuyomil032.mapbrowser.command.MapBrowserCommand;
import com.tukuyomil032.mapbrowser.input.InputHandler;
import com.tukuyomil032.mapbrowser.ipc.BrowserIPCClient;
import com.tukuyomil032.mapbrowser.permission.PermissionManager;
import com.tukuyomil032.mapbrowser.screen.FrameRenderer;
import com.tukuyomil032.mapbrowser.screen.ScreenManager;
import com.tukuyomil032.mapbrowser.service.DefaultMapBrowserService;
import com.tukuyomil032.mapbrowser.service.MapBrowserService;
import com.tukuyomil032.mapbrowser.storage.DataStore;
import com.tukuyomil032.mapbrowser.velocity.VelocityMessagingBridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MapBrowserPlugin extends JavaPlugin {
    private static MapBrowserPlugin instance;

    private DataStore dataStore;
    private PermissionManager permissionManager;
    private FrameRenderer frameRenderer;
    private ScreenManager screenManager;
    private BrowserIPCClient browserIPCClient;
    private AudioBridge audioBridge;
    private MapBrowserService service;
    private VelocityMessagingBridge velocityBridge;
    private int hudTaskId = -1;

    /**
     * Returns plugin singleton.
     */
    public static MapBrowserPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.dataStore = new DataStore(this);
        this.permissionManager = new PermissionManager(this);
        this.frameRenderer = new FrameRenderer(this);
        this.screenManager = new ScreenManager(this);
        this.browserIPCClient = new BrowserIPCClient(this);
        this.audioBridge = createAudioBridge();
        this.service = new DefaultMapBrowserService(this);
        this.velocityBridge = new VelocityMessagingBridge(this);

        dataStore.init();
        screenManager.init();
        browserIPCClient.start();
        velocityBridge.init();

        final MapBrowserCommand command = new MapBrowserCommand(this);
        if (getCommand("mapbrowser") != null) {
            getCommand("mapbrowser").setExecutor(command);
            getCommand("mapbrowser").setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(command, this);

        getServer().getPluginManager().registerEvents(new InputHandler(this), this);
        startHudTask();
        getLogger().info("MapBrowser enabled!");
    }

    @Override
    public void onDisable() {
        if (screenManager != null) {
            screenManager.shutdown();
        }
        if (browserIPCClient != null) {
            browserIPCClient.stop();
        }
        if (frameRenderer != null) {
            frameRenderer.shutdown();
        }
        if (dataStore != null) {
            dataStore.close();
        }
        if (audioBridge instanceof PluginMessageAudioBridge pluginMessageAudioBridge) {
            pluginMessageAudioBridge.shutdown();
        }
        if (velocityBridge != null) {
            velocityBridge.shutdown();
        }
        if (hudTaskId != -1) {
            Bukkit.getScheduler().cancelTask(hudTaskId);
            hudTaskId = -1;
        }
        getLogger().info("MapBrowser disabled!");
    }

    private void startHudTask() {
        hudTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("mapbrowser.use")) {
                    continue;
                }
                getScreenManager().getSelected(player.getUniqueId()).ifPresent(screen -> {
                    final String url = screen.getCurrentUrl() == null ? "about:blank" : screen.getCurrentUrl();
                    player.sendActionBar(Component.text("URL: " + url, NamedTextColor.GRAY));
                });
            }
        }, 20L, 20L);
    }

    private AudioBridge createAudioBridge() {
        if (getConfig().getBoolean("audio.companion-mod-enabled", false)) {
            final PluginMessageAudioBridge bridge = new PluginMessageAudioBridge(this);
            bridge.init();
            return bridge;
        }
        return new NoopAudioBridge();
    }

    /**
     * Returns storage manager.
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    /**
     * Returns permission manager.
     */
    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    /**
     * Returns frame renderer.
     */
    public FrameRenderer getFrameRenderer() {
        return frameRenderer;
    }

    /**
     * Returns screen manager.
     */
    public ScreenManager getScreenManager() {
        return screenManager;
    }

    /**
     * Returns IPC client.
     */
    public BrowserIPCClient getBrowserIPCClient() {
        return browserIPCClient;
    }

    /**
     * Returns audio bridge.
     */
    public AudioBridge getAudioBridge() {
        return audioBridge;
    }

    /**
     * Returns public plugin service.
     */
    public MapBrowserService getService() {
        return service;
    }
}