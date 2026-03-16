package com.tukuyomil032.mapbrowser.screen;

import org.bukkit.block.BlockFace;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Data model of a screen composed of multiple in-game maps.
 */
public final class Screen {
    private final UUID id;
    private final String worldName;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final BlockFace face;
    private final int width;
    private final int height;
    private final UUID ownerUuid;
    private final long createdAt;
    private final int[] mapIds;

    private String name;
    private String currentUrl;
    private int fps;
    private ScreenState state;

    /**
     * Creates a new screen object.
     */
    public Screen(
            final UUID id,
            final String name,
            final String worldName,
            final int originX,
            final int originY,
            final int originZ,
            final BlockFace face,
            final int width,
            final int height,
            final UUID ownerUuid,
            final long createdAt,
            final int[] mapIds,
            final String currentUrl,
            final int fps,
            final ScreenState state
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.face = Objects.requireNonNull(face, "face");
        this.width = width;
        this.height = height;
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        this.createdAt = createdAt;
        this.mapIds = Arrays.copyOf(mapIds, mapIds.length);
        this.currentUrl = Objects.requireNonNullElse(currentUrl, "about:blank");
        this.fps = fps;
        this.state = Objects.requireNonNull(state, "state");
    }

    /**
     * Returns screen id.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns display name.
     */
    public String getName() {
        return name;
    }

    /**
     * Updates display name.
     */
    public void setName(final String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * Returns world name.
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Returns origin x.
     */
    public int getOriginX() {
        return originX;
    }

    /**
     * Returns origin y.
     */
    public int getOriginY() {
        return originY;
    }

    /**
     * Returns origin z.
     */
    public int getOriginZ() {
        return originZ;
    }

    /**
     * Returns attached face.
     */
    public BlockFace getFace() {
        return face;
    }

    /**
     * Returns width in map units.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns height in map units.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns owner uuid.
     */
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * Returns created timestamp.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns map ids.
     */
    public int[] getMapIds() {
        return Arrays.copyOf(mapIds, mapIds.length);
    }

    /**
     * Returns currently loaded URL.
     */
    public String getCurrentUrl() {
        return currentUrl;
    }

    /**
     * Sets current URL.
     */
    public void setCurrentUrl(final String currentUrl) {
        this.currentUrl = Objects.requireNonNullElse(currentUrl, "about:blank");
    }

    /**
     * Returns current fps.
     */
    public int getFps() {
        return fps;
    }

    /**
     * Sets fps.
     */
    public void setFps(final int fps) {
        this.fps = fps;
    }

    /**
     * Returns current state.
     */
    public ScreenState getState() {
        return state;
    }

    /**
     * Sets current state.
     */
    public void setState(final ScreenState state) {
        this.state = Objects.requireNonNull(state, "state");
    }
}
