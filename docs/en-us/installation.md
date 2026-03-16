# Installation (en-us)

## Prerequisites

- Java 21
- Node.js 20+
- pnpm 10+
- Paper/Leaf 1.21 server

## Step 1: Build plugin artifact

```bash
./gradlew shadowJar
```

Expected file:

- build/libs/MapBrowser-<version>-all.jar

## Step 2: Prepare renderer runtime

```bash
cd browser-renderer
pnpm install
pnpm exec playwright install chromium
pnpm run build
cd ..
```

## Step 3: Install to server

1. Copy build/libs/MapBrowser-<version>-all.jar to server plugins directory.
2. Ensure browser-renderer folder is available under server world-container path expected by config.
3. Start server once to generate config.yml.

## Step 4: Initial validation

Run in game:

- /mb create 2 2 test
- /mb open https://example.com

If you see page updates, installation is complete.
