# Operations (en-us)

## Routine checks

- verify plugin enabled on startup
- verify renderer process is alive
- run /mb admin status
- confirm screen count and ipc connected state
- verify command UX uses canonical subcommands (`menu`, `delete`, `give-frame`) with aliases normalized
- verify unloaded screens reject browser controls until `/mb load` is executed

## Phase4 audio integration validation

1. Set `audio.companion-mod-enabled: true` and restart the server.
2. Join with a client that has the Companion Mod installed.
3. Optionally set `audio.capture-mode: media-recorder`, then open a media URL with `/mb open`.
4. Run `/mb admin status` and confirm audio diagnostics delivered counters increase.
5. Move near the screen in-game and confirm audio playback on the modded client.

Validation notes:
Clients without the Companion Mod cannot decode and play plugin-message audio payloads.
Set `audio.test-opus-base64` to emit deterministic Opus test payloads for troubleshooting.

## Phase5 release readiness checklist

1. Run `/mb admin status` and confirm IPC health summary and inbound counters are stable.
2. Verify `MapBrowserService.status()` values are populated for:
	- `ipcConnected`, `screenCount`, `onlinePlayers`
	- `ipcHealthSummary`, `inboundTotal`, `inboundFrame`, `inboundDelta`
	- `inboundErrorEvent`, `ipcUptimeSeconds`, `audioDiagnostics`
3. Send Velocity plugin messages for `OPEN_URL`, `RELOAD_SCREEN`, `SET_FPS`, `CLOSE_SCREEN`, `BACK_SCREEN`, `FORWARD_SCREEN` and confirm acceptance logs.
4. Decode Velocity `STATUS` with EOF-safe parsing (legacy fields first, then extended fields).
5. Re-run build and smoke test with at least one screen loaded and one unloaded screen to verify unload safeguards.

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
