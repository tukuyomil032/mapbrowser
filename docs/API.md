# MapBrowser Public API (Draft)

This document describes the current plugin-facing API boundary for integrations.

## API Flow

```mermaid
sequenceDiagram
	participant Caller as External Plugin
	participant Service as MapBrowserService
	participant IPC as BrowserIPCClient
	participant Renderer as Node Renderer

	Caller->>Service: openUrl(screenId, url)
	Service->>Service: update screen in-memory URL
	Service->>IPC: sendNavigate(screenId, url)
	IPC->>Renderer: NAVIGATE JSON over WebSocket
```

## Entry Point

Use the plugin instance and service accessor:

```java
MapBrowserPlugin plugin = MapBrowserPlugin.getInstance();
MapBrowserService service = plugin.getService();
```

## Service Interface

Current methods are defined in [src/main/java/com/tukuyomil032/mapbrowser/service/MapBrowserService.java](../src/main/java/com/tukuyomil032/mapbrowser/service/MapBrowserService.java):

- `Collection<Screen> getAllScreens()`
- `Optional<Screen> getScreen(UUID screenId)`
- `void openUrl(UUID screenId, String url)`

| Method | Input | Output | Behavior |
|---|---|---|---|
| getAllScreens | - | Collection<Screen> | Returns current runtime screen list |
| getScreen | UUID | Optional<Screen> | Returns screen if found |
| openUrl | UUID, String | void | Updates URL and emits NAVIGATE IPC |

## Notes

- `openUrl(...)` sends a navigate request to browser-renderer and updates in-memory URL state.
- URL validation should be handled by caller or routed through command/security flow when needed.
- API is intentionally minimal and may evolve before stable release.

## Integration Guidance

- Avoid direct manager access from external plugins; prefer `MapBrowserService`.
- Treat `Screen` as a runtime model; persistence format may change.
- For cross-server/proxy scenarios, use the velocity messaging bridge channel `mapbrowser:velocity`.

## Velocity Bridge Commands (Current)

- `PING`:
	- Request status snapshot from backend server.
	- Response command is `STATUS` with fields: `screenCount`, `ipcConnected`, `onlinePlayers`.
- `OPEN_URL`:
	- Payload: `screenId` (UUID), `url`.
	- Backend validates URL with the same security rules as player command flow.

| Command | Direction | Payload | Result |
|---|---|---|---|
| PING | Proxy -> Backend | - | STATUS response |
| STATUS | Backend -> Proxy | screenCount, ipcConnected, onlinePlayers | Current backend snapshot |
| OPEN_URL | Proxy -> Backend | screenId, url | URL validated then NAVIGATE applied |
