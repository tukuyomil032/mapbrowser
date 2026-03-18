# Configuration (en-us)

Main file: src/main/resources/config.yml

## browser

- node-path: custom Node executable path
- renderer-dir: renderer folder path
- ipc-port: websocket port between plugin and renderer
- auto-restart: restart renderer when process dies
- max-restart-attempts: max restart retries
- restart-delay: restart delay in seconds

## screen

- default-fps: default frame rate
- max-fps: fps upper limit
- max-width / max-height: screen bounds
- max-screens-per-world: optional world-level guard
- render-distance: packet delivery distance

## security

- allow-http: permit plain HTTP URLs
- block-local-network: block localhost/private ranges
- url-whitelist: allow-list patterns
- url-blacklist: deny-list patterns

## youtube

- use-ytdlp: toggle yt-dlp bridge
- ytdlp-path: path to yt-dlp executable

## audio

- companion-mod-enabled: enable plugin messaging audio bridge
- channel: plugin message channel for audio
- capture-mode: `none` or `media-recorder`
- media-recorder-timeslice-ms: MediaRecorder chunk interval in milliseconds
- sample-rate: sample rate for forwarded Opus frames
- frame-interval-ms: synthetic audio frame cadence in milliseconds
- test-opus-base64: when set, renderer emits periodic Opus test frames

## items

Material mapping for control items:

- pointer, back, forward, reload, url-bar, scroll-up, scroll-down

## storage

- yaml
- sqlite

## debug

- enable debug mode for additional logs
