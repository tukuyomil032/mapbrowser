# Roadmap Ideas (en-us)

This document collects practical improvement and feature ideas aligned with current implementation.

## Command and UX

1. Add command profile presets (`/mb preset low-latency`, `/mb preset kiosk`).
2. Add dry-run validation mode for URL rules (`/mb open --check <url>`).
3. Add command suggestion for unloaded screens (clickable `/mb load <screen>` hint).
4. Add player-scoped command history (`/mb history`).
5. Add guided wizard (`/mb setup`) for first-time operators.

## Rendering and Performance

1. Dynamic tile size adaptation based on motion complexity.
2. Region-of-interest rendering tied to pointer focus area.
3. Automatic quality downgrade when server TPS drops.
4. Per-world render policy presets.
5. Incremental map palette cache warm-up at startup.

## Reliability and Operations

1. Health endpoint for renderer status (for monitoring tools).
2. Crash loop circuit-breaker with exponential backoff.
3. Automatic stale-screen cleanup job.
4. Runtime config diff audit log.
5. One-command diagnostics export (`/mb admin dump`).

## Localization and Messaging

1. Complete key-based migration for all command outputs.
2. Message key lint check in CI.
3. Locale fallback chain (`ja-JP` -> `ja` -> `en`).
4. Structured placeholders in keys (`{screen}`, `{fps}`).
5. Message preview command for translators.

## Security

1. DNS rebind guard with resolved-IP lock.
2. Optional URL scheme denylist by config.
3. Per-screen host allow-list overrides.
4. Safer redirect policy (max redirect depth + host pinning).
5. Admin audit trail for URL navigation changes.

## Companion Mod and Audio

1. Volume attenuation curves per distance band.
2. Multi-screen audio mixing policy.
3. Audio diagnostics HUD for modded clients.
4. Optional low-bitrate audio mode.
5. Screen-to-audio channel mapping profile.

## API and Ecosystem

1. Public event bus for screen lifecycle hooks.
2. Stable external API versioning and capability flags.
3. REST bridge for out-of-game administration.
4. Web dashboard for screen inventory and status.
5. Import/export format for screen layouts.

## Data and Persistence

1. Screen snapshots and rollback points.
2. Soft-delete with retention period.
3. Migration framework for storage schema updates.
4. Optional PostgreSQL backend.
5. Integrity checker for map tile references.

## Testing and QA

1. Golden-frame regression tests for renderer output.
2. Command behavior integration tests (alias normalization included).
3. Localization snapshot tests (EN/JA parity).
4. Replay-based performance benchmark scenes.
5. Automated chaos tests for renderer restarts.
