# Commands (en-us)

Base command:

- /mapbrowser
- /mb

## Screen lifecycle

- /mb create <w> <h> [name] [--autofill]
- /mb select <screen-id|screen-name|latest>
- /mb list
- /mb info
- /mb load [screen-id|screen-name|latest]
- /mb unload [screen-id|screen-name|latest]
- /mb delete|remove|destroy [screen-id|screen-name|latest]
- /mb give-frame|gif <screen-id|screen-name|latest> <tile-range>
- /mb resize <screen-id|screen-name|latest> <w> <h>
- /mb exit

## Menu

- /mb menu
- /mb gui

## Browser control

- /mb open <url>
- /mb type <text>
- /mb back
- /mb forward
- /mb reload
- /mb fps <value>

## Items

- /mb give <pointer-left|pointer-right|pointer|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll|scroll-up|scroll-down>

## Config

- /mb config simulate_particle <end_rod|flame>
- /mb config language <en|ja>

## Admin

- /mb admin status
- /mb admin deps
- /mb admin reload
- /mb admin perf [screen-id|screen-name|latest]
- /mb admin perfbench <seconds>
- /mb admin stop <screenId>

## Notes

- Most commands are player-only.
- Most commands target selected screen when screen argument is omitted.
- Tile range supports `all`, `odd`, `even` and 1-based index expressions.
- Examples: `all`, `odd`, `1`, `1-3`, `1,4,6-8`.
- URL input is validated by security rules.
- FPS must be in configured allowed range.
