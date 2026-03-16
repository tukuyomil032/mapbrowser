package com.tukuyomil032.mapbrowser.storage;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.tukuyomil032.mapbrowser.MapBrowserPlugin;
import com.tukuyomil032.mapbrowser.screen.Screen;
import com.tukuyomil032.mapbrowser.screen.ScreenState;

/**
 * Stores and loads screen metadata.
 */
public final class DataStore {
    private final MapBrowserPlugin plugin;
    private final File screensFile;
    private Connection sqliteConnection;

    /**
     * Creates the datastore.
     */
    public DataStore(final MapBrowserPlugin plugin) {
        this.plugin = plugin;
        this.screensFile = new File(plugin.getDataFolder(), "screens.yml");
    }

    /**
     * Initializes storage files.
     */
    public void init() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder.");
        }

        if (isSqliteEnabled()) {
            initSqlite();
            return;
        }

        if (!screensFile.exists()) {
            try {
                if (!screensFile.createNewFile()) {
                    plugin.getLogger().warning("Failed to create screens.yml");
                }
            } catch (final IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to initialize screens.yml: {0}", ex.getMessage());
            }
        }
    }

    /**
     * Loads all screens from disk.
     */
    public List<Screen> loadScreens() {
        if (isSqliteEnabled() && sqliteConnection != null) {
            return loadScreensFromSqlite();
        }

        final List<Screen> screens = new ArrayList<>();
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(screensFile);
        final ConfigurationSection section = yaml.getConfigurationSection("screens");
        if (section == null) {
            return screens;
        }

        for (final String key : section.getKeys(false)) {
            final ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }

            try {
                final UUID id = UUID.fromString(key);
                final String name = s.getString("name", "unnamed");
                final String world = s.getString("world", "world");
                final int x = s.getInt("x");
                final int y = s.getInt("y");
                final int z = s.getInt("z");
                final BlockFace face = BlockFace.valueOf(s.getString("face", BlockFace.NORTH.name()));
                final int width = s.getInt("width", 1);
                final int height = s.getInt("height", 1);
                final UUID owner = UUID.fromString(s.getString("owner_uuid", new UUID(0L, 0L).toString()));
                final long createdAt = s.getLong("created_at", System.currentTimeMillis());
                final List<Integer> mapIdList = s.getIntegerList("map_ids");
                final int[] mapIds = new int[mapIdList.size()];
                for (int i = 0; i < mapIdList.size(); i++) {
                    mapIds[i] = mapIdList.get(i);
                }
                final String currentUrl = s.getString("current_url", "about:blank");
                final int fps = s.getInt("fps", plugin.getConfig().getInt("screen.default-fps", 10));
                final ScreenState state = ScreenState.valueOf(s.getString("state", ScreenState.PAUSED.name()));

                screens.add(new Screen(
                        id,
                        name,
                        world,
                        x,
                        y,
                        z,
                        face,
                        width,
                        height,
                        owner,
                        createdAt,
                        mapIds,
                        currentUrl,
                        fps,
                        state
                ));
            } catch (final IllegalArgumentException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load screen {0}: {1}", new Object[]{key, ex.getMessage()});
            }
        }

        return screens;
    }

    /**
     * Saves all screens to disk.
     */
    public void saveScreens(final Collection<Screen> screens) {
        if (isSqliteEnabled() && sqliteConnection != null) {
            saveScreensToSqlite(screens);
            return;
        }

        final YamlConfiguration yaml = new YamlConfiguration();
        final ConfigurationSection section = yaml.createSection("screens");

        for (final Screen screen : screens) {
            final ConfigurationSection s = section.createSection(screen.getId().toString());
            s.set("name", screen.getName());
            s.set("world", screen.getWorldName());
            s.set("x", screen.getOriginX());
            s.set("y", screen.getOriginY());
            s.set("z", screen.getOriginZ());
            s.set("face", screen.getFace().name());
            s.set("width", screen.getWidth());
            s.set("height", screen.getHeight());
            s.set("owner_uuid", screen.getOwnerUuid().toString());
            s.set("created_at", screen.getCreatedAt());
            s.set("current_url", screen.getCurrentUrl());
            s.set("fps", screen.getFps());
            s.set("state", screen.getState().name());

            final List<Integer> mapIds = new ArrayList<>();
            for (final int mapId : screen.getMapIds()) {
                mapIds.add(mapId);
            }
            s.set("map_ids", mapIds);
        }

        try {
            yaml.save(screensFile);
        } catch (final IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save screens.yml: {0}", ex.getMessage());
        }
    }

    /**
     * Closes resources.
     */
    public void close() {
        if (sqliteConnection != null) {
            try {
                sqliteConnection.close();
            } catch (final SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to close SQLite connection: {0}", ex.getMessage());
            } finally {
                sqliteConnection = null;
            }
        }
    }

    private boolean isSqliteEnabled() {
        return "sqlite".equalsIgnoreCase(plugin.getConfig().getString("storage", "yaml"));
    }

    private void initSqlite() {
        final File dbFile = new File(plugin.getDataFolder(), "mapbrowser.db");
        final String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        try {
            sqliteConnection = DriverManager.getConnection(jdbcUrl);
            try (PreparedStatement statement = sqliteConnection.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS screens (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        face TEXT NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        current_url TEXT NOT NULL,
                        fps INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        map_ids TEXT NOT NULL
                    )
                    """
            )) {
                statement.executeUpdate();
            }
            plugin.getLogger().log(Level.INFO, "SQLite storage initialized: {0}", dbFile.getName());
        } catch (final SQLException ex) {
            sqliteConnection = null;
            plugin.getLogger().log(Level.WARNING, "Failed to initialize SQLite. Fallback to YAML: {0}", ex.getMessage());
        }
    }

    private List<Screen> loadScreensFromSqlite() {
        final List<Screen> screens = new ArrayList<>();
        if (sqliteConnection == null) {
            return screens;
        }

        try (PreparedStatement statement = sqliteConnection.prepareStatement("SELECT * FROM screens");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                final UUID id = UUID.fromString(rs.getString("id"));
                final String name = rs.getString("name");
                final String world = rs.getString("world");
                final int x = rs.getInt("x");
                final int y = rs.getInt("y");
                final int z = rs.getInt("z");
                final BlockFace face = BlockFace.valueOf(rs.getString("face"));
                final int width = rs.getInt("width");
                final int height = rs.getInt("height");
                final UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                final long createdAt = rs.getLong("created_at");
                final String currentUrl = rs.getString("current_url");
                final int fps = rs.getInt("fps");
                final ScreenState state = ScreenState.valueOf(rs.getString("state"));
                final int[] mapIds = deserializeMapIds(rs.getString("map_ids"));

                screens.add(new Screen(
                        id,
                        name,
                        world,
                        x,
                        y,
                        z,
                        face,
                        width,
                        height,
                        owner,
                        createdAt,
                        mapIds,
                        currentUrl,
                        fps,
                        state
                ));
            }
        } catch (final SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load screens from SQLite: {0}", ex.getMessage());
        }
        return screens;
    }

    private void saveScreensToSqlite(final Collection<Screen> screens) {
        if (sqliteConnection == null) {
            return;
        }

        try (PreparedStatement delete = sqliteConnection.prepareStatement("DELETE FROM screens")) {
            delete.executeUpdate();
        } catch (final SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to clear screens table: {0}", ex.getMessage());
            return;
        }

        try (PreparedStatement insert = sqliteConnection.prepareStatement(
                """
                INSERT INTO screens (
                    id, name, world, x, y, z, face,
                    width, height, owner_uuid, created_at,
                    current_url, fps, state, map_ids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
        )) {
            for (final Screen screen : screens) {
                insert.setString(1, screen.getId().toString());
                insert.setString(2, screen.getName());
                insert.setString(3, screen.getWorldName());
                insert.setInt(4, screen.getOriginX());
                insert.setInt(5, screen.getOriginY());
                insert.setInt(6, screen.getOriginZ());
                insert.setString(7, screen.getFace().name());
                insert.setInt(8, screen.getWidth());
                insert.setInt(9, screen.getHeight());
                insert.setString(10, screen.getOwnerUuid().toString());
                insert.setLong(11, screen.getCreatedAt());
                insert.setString(12, screen.getCurrentUrl());
                insert.setInt(13, screen.getFps());
                insert.setString(14, screen.getState().name());
                insert.setString(15, serializeMapIds(screen.getMapIds()));
                insert.addBatch();
            }
            insert.executeBatch();
        } catch (final SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save screens to SQLite: {0}", ex.getMessage());
        }
    }

    private String serializeMapIds(final int[] mapIds) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mapIds.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(mapIds[i]);
        }
        return builder.toString();
    }

    private int[] deserializeMapIds(final String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        final String[] parts = raw.split(",");
        final int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (final NumberFormatException ex) {
                result[i] = 0;
            }
        }
        return result;
    }
}
