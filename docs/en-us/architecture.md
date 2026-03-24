# Architecture (en-us)

## High-Level Topology

```mermaid
flowchart LR
  subgraph MC["Minecraft Server Process"]
    Java["Java Plugin\nMapBrowserPlugin"]
    IPC["BrowserIPCClient\nWebSocket Client"]
    Java --> IPC
  end

  subgraph NodeProc["Child Process: browser-renderer"]
    WSS["IPCServer\nWebSocket Server"]
    Pool["BrowserPool"]
    Page["PageController\nPlaywright"]
    Proc["FrameProcessor + Worker"]
    WSS --> Pool --> Page --> Proc
    Proc --> WSS
  end

  IPC <-->|"JSON over WS :25600"| WSS
  Java -->|"Map packets"| Client["Minecraft Clients"]
```

## Component Matrix

| Side | Component | Responsibility |
|---|---|---|
| Java | ScreenManager | Screen lifecycle and selection state |
| Java | FrameRenderer | Apply FRAME/DELTA_FRAME to map buffers |
| Java | BrowserIPCClient | Process launch, WS connection, IPC routing |
| Java | InputHandler | Player interaction to browser control conversion |
| Java | DataStore | YAML/SQLite persistence |
| Node | IPCServer | Java message routing and event emission |
| Node | BrowserPool | screenId to page controller mapping |
| Node | PageController | Playwright page actions and screencast capture |
| Node | FrameProcessor | Resize, quantize, delta/full-frame decision |
| Node | quantize.worker | Palette quantization in worker thread |

## Java Responsibility Split (as implemented)

### command package

| Class | Primary responsibility |
|---|---|
| MapBrowserCommand | Subcommand dispatch and shared wiring |
| MapAdminSupport | admin/perf/perfbench/stop command logic |
| MapMenuSupport | /mb menu rendering and click handling |
| MapToolItemSupport | /mb give tool item creation/distribution |
| MapScreenInfoSupport | /mb list and /mb info rendering |
| MapTileRangeParser | give-frame tile-range syntax parsing |
| MapItemDistributor | Screen tile map creation and delivery |
| MapCommandMessageRenderer | Styled command UI output rendering |
| MapCommandLocalization | Language resolution and key-based localization |
| MapScreenQueryResolver | screen-id/name/latest query resolution |

### input package

| Class | Primary responsibility |
|---|---|
| InputHandler | Event intake and action routing |
| FrameClickResolver | Item-frame hit normalization to browser coordinates |
| InputMessageHelper | Input-side localized message sending |
| InputScreenMapItemFactory | Screen-bound map item generation for auto-assemble |

## Communication Workflow

```mermaid
sequenceDiagram
  participant P as Player
  participant J as Java Plugin
  participant N as Node Renderer
  participant B as Browser Page

  P->>J: /mb create 2 2 demo
  J->>N: OPEN(screenId,width,height,fps)
  P->>J: /mb open https://example.com
  J->>N: NAVIGATE(screenId,url)
  N->>B: page.goto(url)
  B-->>N: screencast frame
  N->>N: resize + quantize + delta decision
  N-->>J: FRAME or DELTA_FRAME
  J-->>P: map packet updates
```

## IPC Message Families

| Direction | Message types | Purpose |
|---|---|---|
| Java -> Node | OPEN, NAVIGATE, MOUSE_CLICK, SCROLL, GO_BACK, GO_FORWARD, RELOAD, CLOSE, SET_FPS | Control browser and screen lifecycle |
| Node -> Java | READY, FRAME, DELTA_FRAME, URL_CHANGED, PAGE_LOADED, AUDIO_FRAME, ERROR | Render/output events and status |

## Frame pipeline

1. Playwright captures frame
2. sharp resizes to map resolution
3. worker quantizes to map palette indexes
4. delta rectangle is computed
5. if delta is too large, full frame fallback is used
6. FRAME or DELTA_FRAME is sent to Java side

### Implemented optimization highlights

- Scene-aware policy: separate diff threshold, tile threshold, and skip ratio for still vs video
- Ratio-based skip: suppress updates when changedPixels / totalPixels is below threshold
- Center-priority ranking: prioritize tiles near viewport center
- Tile merge: merge adjacent tiles to reduce DELTA update count
- Adaptive FPS control: tune fps by processing cost and load signal
- Java LUT path: MapColorUtil now provides O(1) 24-bit lookup conversion

```mermaid
flowchart TD
  A[Capture PNG] --> B[sharp resize]
  B --> C[worker quantize]
  C --> D{Delta efficient?}
  D -- yes --> E[DELTA_FRAME]
  D -- no --> F[FRAME fallback]
  E --> G[Java FrameRenderer]
  F --> G
```

## Data persistence

| Backend | Use case | Notes |
|---|---|---|
| yaml | Simple local setup | Easy to inspect manually |
| sqlite | Production-like operation | Better scalability and consistency |

## Bridges

| Bridge | Channel | Current commands |
|---|---|---|
| Audio | mapbrowser:audio | encoded frame forwarding |
| Velocity | mapbrowser:velocity | PING/STATUS, OPEN_URL, RELOAD_SCREEN, SET_FPS, CLOSE_SCREEN, BACK_SCREEN, FORWARD_SCREEN |

Velocity `STATUS` currently returns:

- screenCount
- ipcConnected
- onlinePlayers
- ipcHealthSummary
- inboundTotal
- inboundFrame
- inboundDelta
- audioDiagnostics
