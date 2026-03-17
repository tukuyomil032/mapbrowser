# Commands (en-us)

Base command:

- /mapbrowser
- /mb

## Screen lifecycle

- /mb create <w> <h> [name]
- /mb destroy
- /mb list
- /mb info

## Browser control

- /mb open <url>
- /mb type <text>
- /mb back
- /mb forward
- /mb reload
- /mb fps <value>
- /mb exit

## Items

- /mb give <pointer-left|pointer-right|back|forward|reload|url-bar|text-input|scroll>

## Config

- /mb config simulate_particle <end_rod|flame>
- /mb config language <en|ja>

## Admin

- /mb admin status
- /mb admin stop <screenId>

## Notes

- Most commands are player-only.
- URL input is validated by security rules.
- FPS must be in configured allowed range.
