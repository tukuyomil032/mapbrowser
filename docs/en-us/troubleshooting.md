# Troubleshooting (en-us)

## Plugin enables but no frames appear

Checklist:

- renderer build exists (browser-renderer/dist)
- browser.ipc-port matches on both sides
- node executable is reachable by plugin process
- Playwright Chromium has been installed

## IPC disconnected

- run /mb admin status
- inspect server logs for websocket connect/reconnect messages
- verify local firewall is not blocking localhost port

## URL open rejected

Likely security policy:

- allow-http is false and URL is http
- host matches blacklist
- host is private/local and block-local-network is true
- whitelist is enabled and host not included

## Renderer crashes repeatedly

- verify Node.js version
- verify native dependencies for sharp/playwright
- test renderer manually with pnpm dev
- inspect restart limit in config (max-restart-attempts)

## Storage migration concerns

When changing storage backend:

1. backup old data
2. switch storage value
3. restart server
4. verify expected screens loaded

## Release pipeline fails

- ensure version format in build.gradle.kts is plain quoted string
- ensure workflow has contents write permission
- ensure tag v<version> does not already conflict
