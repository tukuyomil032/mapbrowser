# Operations (en-us)

## Routine checks

- verify plugin enabled on startup
- verify renderer process is alive
- run /mb admin status
- confirm screen count and ipc connected state

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
