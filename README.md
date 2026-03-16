<div align="center">
  <h1>Map Browser</h1>
    <p><strong>In-game web browser rendering for Minecraft map screens.</strong></p>
</div>

<p align="center">
  <img src="https://img.shields.io/badge/java-21-orange" alt="java" />
  <img src="https://img.shields.io/badge/paper-1.21-blue" alt="paper" />
  <img src="https://img.shields.io/badge/node-20%2B-green" alt="node" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="license" />
  <img src="https://img.shields.io/badge/release-automated-success" alt="release" />
</p>

MapBrowser renders live browser frames onto Minecraft map surfaces by running:

- a Java plugin side for game integration and packet flow
- a Node.js renderer side for Playwright capture and frame processing
- JSON-over-WebSocket IPC between them on localhost

By default, browser rendering is isolated in a child process and restarted automatically when needed.

## Features

- live web page rendering on map-based screens
- basic interaction flow: create, open URL, back/forward/reload, fps control
- delta frame optimization with full-frame fallback for heavy changes
- storage backends: yaml and sqlite
- velocity messaging bridge command path: PING and OPEN_URL
- release pipeline ready for auto-tag plus GitHub Releases

## Table of Contents

- [Project Layout](#project-layout)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Build](#build)
- [Configuration](#configuration)
- [Commands](#commands)
- [Documentation](#documentation)
- [Release Automation](#release-automation)
- [Safety Notes](#safety-notes)

## Project Layout

- Java plugin: src/main/java/com/tukuyomil032/mapbrowser
- resources: src/main/resources
- browser renderer: browser-renderer/src
- docs: docs

## Requirements

- Java 21
- Node.js 20+
- pnpm 10+
- Spigot 1.21.x server

## Quick Start

### 1) Install renderer dependencies

```bash
cd browser-renderer
pnpm install
pnpm exec playwright install chromium
cd ..
```

### 2) Build

```bash
./gradlew build
cd browser-renderer && pnpm run typecheck && pnpm run build
```

### 3) Produce plugin artifact

```bash
./gradlew shadowJar
```

Output:

- build/libs/MapBrowser-<version>-all.jar

### 4) Smoke test in server

1. Put MapBrowser-<version>-all.jar into plugins directory.
2. Start server.
3. Run /mb create 2 2 test.
4. Run /mb open https://example.com.

## Build

### Java plugin

```bash
./gradlew build
```

### Renderer

```bash
cd browser-renderer
pnpm run typecheck
pnpm run build
```

## Configuration

Main runtime config:

- src/main/resources/config.yml

Important keys:

- browser.ipc-port
- screen.default-fps and screen.max-fps
- security.allow-http and security.block-local-network
- storage (yaml or sqlite)

## Commands

- /mb create <w> <h> [name]
- /mb open <url>
- /mb back
- /mb forward
- /mb reload
- /mb fps <value>
- /mb list
- /mb info
- /mb destroy
- /mb give <pointer|back|forward|reload|url-bar|scroll-up|scroll-down>
- /mb exit
- /mb admin status
- /mb admin stop <screenId>

## Documentation

Detailed manuals are available in both locales:

- English index: docs/en-us/README.md
- Japanese index: docs/ja-jp/README.md

## Release Automation

GitHub Actions release workflow:

- reads version from build.gradle.kts
- creates a new tag v<version> if not existing
- builds plugin and renderer assets
- publishes assets to GitHub Releases

Workflow file:

- .github/workflows/release.yml

## Safety Notes

- URL validation is applied in command flow and velocity OPEN_URL flow.
- local/private network destinations are blocked by default.
- renderer crash recovery and IPC reconnect are enabled.
