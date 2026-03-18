# Operations (en-us)

## Routine checks

- verify plugin enabled on startup
- verify renderer process is alive
- run /mb admin status
- confirm screen count and ipc connected state

## Phase4 audio integration validation

1. Set `audio.companion-mod-enabled: true` and restart the server.
2. Join with a client that has the Companion Mod installed.
3. Optionally set `audio.capture-mode: media-recorder`, then open a media URL with `/mb open`.
4. Run `/mb admin status` and confirm audio diagnostics delivered counters increase.
5. Move near the screen in-game and confirm audio playback on the modded client.

Validation notes:
Clients without the Companion Mod cannot decode and play plugin-message audio payloads.
Set `audio.test-opus-base64` to emit deterministic Opus test payloads for troubleshooting.

## Release process

1. bump version in build.gradle.kts
2. push main branch
3. GitHub Actions creates tag v<version> if missing
4. workflow builds artifacts and publishes release assets

## Artifacts

- plugin: MapBrowser-<version>-all.jar
- renderer: browser-renderer-dist-<version>.tar.gz

## Backups

- yaml mode: backup plugin data folder including screens.yml
- sqlite mode: backup mapbrowser.db

## Safe rollback

- keep previous release artifacts
- revert plugin jar and renderer dist together
- restore previous data backup when schema changes are introduced
