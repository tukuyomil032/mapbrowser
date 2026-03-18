# MapBrowser — 完全版要件定義書

**バージョン**: 1.0.0  
**作成日**: 2026-03-16  
**ステータス**: Confirmed Draft  
**対象読者**: 開発者（本人）、AIコーディングエージェント

---

## 目次

1. [プロジェクト概要](#1-プロジェクト概要)
2. [用語定義](#2-用語定義)
3. [リポジトリ構成](#3-リポジトリ構成)
4. [システムアーキテクチャ](#4-システムアーキテクチャ)
5. [コンポーネント詳細 — Java Plugin](#5-コンポーネント詳細--java-plugin)
6. [コンポーネント詳細 — browser-renderer (Node.js)](#6-コンポーネント詳細--browser-renderer-nodejs)
7. [IPC プロトコル仕様](#7-ipc-プロトコル仕様)
8. [機能要件](#8-機能要件)
9. [非機能要件](#9-非機能要件)
10. [画面・表示システム](#10-画面表示システム)
11. [ブラウザ操作システム](#11-ブラウザ操作システム)
12. [音声システム](#12-音声システム)
13. [権限・パーミッションシステム](#13-権限パーミッションシステム)
14. [コマンド仕様](#14-コマンド仕様)
15. [設定ファイル仕様](#15-設定ファイル仕様)
16. [対応環境](#16-対応環境)
17. [技術スタック詳細](#17-技術スタック詳細)
18. [制約・既知の限界](#18-制約既知の限界)
19. [開発フェーズ](#19-開発フェーズ)
20. [参考プロジェクト分析](#20-参考プロジェクト分析)

---

## 1. プロジェクト概要

### 1.1 プロジェクト名

**MapBrowser**

### 1.2 目的

Minecraft Java Edition のサーバープラグインとして、ゲーム内のマップアイテムと額縁を組み合わせた「スクリーン」を構築し、その上にリアルタイムの Web ブラウザを描画するシステムを提供する。プレイヤーはゲームを離れることなく YouTube 等の動画再生や Web ブラウジングをゲーム内で体験できる。

### 1.3 プロジェクト構成

本プロジェクトは **2 つのサブプロジェクト** から成る単一リポジトリ（モノレポ）構成。

| サブプロジェクト | 言語 | 役割 |
|----------------|------|------|
| `src/` (Java Plugin) | Java 21 | Minecraft サーバー側のすべてのロジック |
| `browser-renderer/` (Node.js) | TypeScript 5 / Node.js 20 | Headless Chromium の制御とフレーム配信 |

Companion Mod は本リポジトリには同居させず、**別リポジトリ**で開発する。
- 公式名称: **MapBrowserCompanion**（略称: **MBC**）
- 対応ローダー: **Fabric のみ**
- 実装言語: **Kotlin**

### 1.4 前提条件

- 開発者は Node.js / TypeScript の Web 開発経験を持つ
- パッケージマネージャは **pnpm** または **bun** を使用（pnpm 推奨）
- サーバーは Leaf / Paper / Spigot で動作する Java サーバー
- ローカル環境には Node.js 20 LTS がインストール済み
- VS Code を開発 IDE として使用

---

## 2. 用語定義

| 用語 | 定義 |
|------|------|
| **スクリーン** | ゲーム内に配置された、複数の Item Frame（額縁）で構成される表示領域 |
| **ブラウザインスタンス** | 1 つのスクリーンに対応する Headless Chromium のタブ |
| **MapPacket** | `ClientboundMapItemDataPacket` — マップの描画データをクライアントに送る Minecraft の低レベルパケット |
| **色量子化** | フルカラー画像を Minecraft マップの 144 色パレットに変換する処理 |
| **Floyd-Steinberg** | 使用する色量子化アルゴリズム（誤差拡散法） |
| **フレーム** | ブラウザからキャプチャされた 1 枚の静止画（PNG バッファ） |
| **デルタ圧縮** | 前フレームとの差分のみを MapPacket で送信することで転送量を削減する最適化 |
| **IPC** | Java Plugin と browser-renderer 間の WebSocket プロセス間通信 |
| **MapBrowserCompanion (MBC)** | クライアント側に任意でインストールする Fabric専用 Kotlin Mod（音声・高解像度対応、Phase 4） |
| **yt-dlp** | YouTube 等の動画サイトからストリーム URL を取得する外部バイナリ |
| **Worker Thread** | 色量子化処理を Node.js のメインスレッドから切り離して並列実行するための機構 |

---

## 3. リポジトリ構成

```
MAPBROWSER/                          ← リポジトリルート
├── .gradle/
├── .vscode/
│   ├── settings.json                ← 既存
│   └── extensions.json              ← 推奨拡張機能リスト（追加）
│
├── src/                             ── Java Plugin ──────────────────
│   ├── build/
│   └── main/
│       ├── java/com/tukuyomil032/mapbrowser/
│       │   ├── MapBrowserPlugin.java        ← メインクラス（既存）
│       │   ├── screen/
│       │   │   ├── Screen.java              ← スクリーンエンティティ
│       │   │   ├── ScreenManager.java       ← スクリーンライフサイクル管理
│       │   │   └── FrameRenderer.java       ← MapPacket 送信
│       │   ├── ipc/
│       │   │   ├── BrowserIPCClient.java    ← WebSocket IPC クライアント
│       │   │   └── IPCMessage.java          ← メッセージ型定義
│       │   ├── input/
│       │   │   └── InputHandler.java        ← プレイヤーインタラクション
│       │   ├── command/
│       │   │   └── MapBrowserCommand.java   ← /mb コマンド
│       │   ├── permission/
│       │   │   └── PermissionManager.java   ← LuckPerms / Vault 連携
│       │   ├── storage/
│       │   │   └── DataStore.java           ← SQLite / YAML 永続化
│       │   └── util/
│       │       ├── MapColorUtil.java         ← 144色パレット定義・変換
│       │       └── RaycastUtil.java          ← クリック座標計算
│       └── resources/
│           ├── paper-plugin.yml             ← 既存
│           └── config.yml                   ← デフォルト設定
│
├── browser-renderer/                ── Node.js / TypeScript ─────────
│   ├── src/
│   │   ├── index.ts                 ← エントリーポイント
│   │   ├── types/
│   │   │   └── ipc.ts               ← IPC メッセージ型定義（Java側と対応）
│   │   ├── ipc/
│   │   │   └── IPCServer.ts         ← WebSocket IPC サーバー
│   │   ├── browser/
│   │   │   ├── BrowserPool.ts       ← Chromium インスタンスプール
│   │   │   └── PageController.ts    ← URL操作・スクリーンキャスト
│   │   ├── renderer/
│   │   │   ├── FrameProcessor.ts    ← 色量子化・デルタ圧縮
│   │   │   └── quantize.worker.ts   ← Worker Thread（並列色量子化）
│   │   └── youtube/
│   │       └── YtDlpBridge.ts       ← yt-dlp 連携
│   ├── package.json
│   ├── pnpm-lock.yaml               ← (bun の場合は bun.lockb)
│   ├── tsconfig.json
│   └── node_modules/                ← .gitignore 対象
│
├── docs/
│   ├── REQUIREMENTS.md              ← 本ファイル
│   └── mapbrowser-instructions.md  ← 既存
│
├── build/                           ← .gitignore 対象
├── gradle/
├── .gitattributes
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── settings.gradle.kts
└── AGENTS.md                        ← AI エージェント向け指示書
```

---

## 4. システムアーキテクチャ

### 4.1 全体構成

```
┌─────────────────────────────────────────────────────────┐
│                   Minecraft Server (Leaf)                │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │              MapBrowser Java Plugin              │   │
│  │  ScreenManager / FrameRenderer / InputHandler   │   │
│  │  CommandHandler / PermissionManager / DataStore  │   │
│  └──────────────────┬──────────────────────────────┘   │
│                     │ WebSocket  localhost:25600        │
│  ┌──────────────────▼──────────────────────────────┐   │
│  │         browser-renderer (Node.js プロセス)      │   │
│  │  IPCServer / BrowserPool / PageController       │   │
│  │  FrameProcessor / YtDlpBridge                   │   │
│  │                  │                               │   │
│  │         Playwright (Headless Chromium)            │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
              │ Minecraft Protocol (MapPacket)
              ▼
    ┌─────────────────────┐
    │  Minecraft Client   │
    │  (バニラ / Companion)│
    └─────────────────────┘
```

### 4.2 映像データフロー

```
Playwright page.screencast()
  └→ PNG バッファ (Node.js)
       └→ sharp でリサイズ（スクリーン解像度に合わせる）
            └→ Worker Thread: Floyd-Steinberg 色量子化
                 └→ Minecraft 144色パレットインデックス配列
                      └→ デルタ圧縮（前フレームとの差分抽出）
                           └→ WebSocket → Java Plugin
                                └→ ClientboundMapItemDataPacket
                                     └→ プレイヤーへ送信
```

### 4.3 操作データフロー

```
プレイヤーが額縁を右クリック
  └→ Java: PlayerInteractEvent
       └→ RaycastUtil でスクリーン座標を計算
            └→ WebSocket → browser-renderer
                 └→ Playwright page.mouse.click(x, y)
                      └→ 次フレームがスクリーンに反映
```

### 4.4 プロセス管理

Java Plugin が browser-renderer を **子プロセスとして起動**する。

```java
// BrowserIPCClient.java のイメージ
ProcessBuilder pb = new ProcessBuilder("node", "dist/index.js");
pb.directory(rendererDir);
Process rendererProcess = pb.start();
```

- Java Plugin のシャットダウン時に子プロセスも終了する
- クラッシュ検知して自動再起動（最大 3 回）
- IPC 接続が切断された場合は再接続を試みる

---

## 5. コンポーネント詳細 — Java Plugin

### 5.1 MapBrowserPlugin.java（メインクラス）

**責務**:
- プラグインの初期化・終了処理
- BrowserIPCClient の起動・停止
- 各マネージャの DI（依存注入）

**ライフサイクル**:
```
onEnable()
  → DataStore.init()
  → ScreenManager.init()
  → BrowserIPCClient.start()  ← Node.js 子プロセス起動
  → コマンド・イベントリスナー登録

onDisable()
  → ScreenManager.shutdown()
  → BrowserIPCClient.stop()   ← 子プロセス終了
  → DataStore.close()
```

### 5.2 ScreenManager.java

**責務**: スクリーンエンティティのライフサイクル管理

```java
// 主要メソッド
Screen createScreen(Player player, BlockFace face, int width, int height, String name)
void destroyScreen(UUID screenId)
Screen getScreenAt(Location loc)
List<Screen> getScreensInRange(Location center, double range)
void saveAll()  // シャットダウン時に全スクリーンを DataStore に保存
```

### 5.3 Screen.java

**責務**: スクリーン 1 つのデータモデル

```java
public class Screen {
    UUID id;
    String name;
    World world;
    BlockPos origin;        // 左上の Item Frame の座標
    BlockFace face;         // 設置面
    int width, height;      // マップ単位（1 = 128px）
    UUID ownerUUID;
    String currentUrl;
    int[] mapIds;           // 使用中のマップ ID（width * height 個）
    ScreenState state;      // LOADING / PLAYING / PAUSED / ERROR
    long createdAt;
}
```

### 5.4 FrameRenderer.java

**責務**: フレームデータを MapPacket に変換してプレイヤーへ送信

```java
// IPC から受信した色インデックス配列を各マップの MapPacket に分割して送信
void renderFrame(Screen screen, byte[] colorData)

// 近くのプレイヤーにのみ送信（パフォーマンス最適化）
List<Player> getViewers(Screen screen)
```

**デルタ送信**:
- 前フレームのデータをキャッシュ
- 変化のあったピクセル領域の BoundingRect のみ `dirtyArea` として送信
- 変化なし → パケット送信しない

### 5.5 BrowserIPCClient.java

**責務**: browser-renderer との WebSocket 通信

```java
// 送信（Java → Node.js）
void sendNavigate(UUID screenId, String url)
void sendMouseClick(UUID screenId, int x, int y, String button)
void sendScroll(UUID screenId, int deltaY)
void sendGoBack(UUID screenId)
void sendGoForward(UUID screenId)
void sendReload(UUID screenId)
void sendClose(UUID screenId)

// 受信コールバック（Node.js → Java）
void onFrameReceived(UUID screenId, byte[] colorData)
void onUrlChanged(UUID screenId, String url)
void onPageLoaded(UUID screenId)
void onError(UUID screenId, String message)
```

### 5.6 InputHandler.java

**責務**: プレイヤーのインタラクションを検出し BrowserIPCClient に転送

**対応イベント**:
- `PlayerInteractEvent` — 額縁への右クリック
- `PlayerItemHeldEvent` — アイテム切り替え（操作モード維持）
- `PlayerQuitEvent` — 操作モード解除

**座標変換（RaycastUtil）**:
```java
// 額縁の右クリック hitVector → ブラウザ座標 (px)
Vector2i toBrowserCoords(ItemFrame frame, Vector hitLocation, Screen screen)
// hitLocation は 0.0〜1.0 の正規化済み座標
// → screen.width * 128 × screen.height * 128 の座標系に変換
```

### 5.7 MapColorUtil.java

**責務**: Minecraft の 144 色パレット定義と色変換

```java
// Minecraft MapColor の RGB 値テーブル（144色分）
static final int[] MAP_COLORS_RGB = { ... };

// RGB バイト値を最近傍 MapColor インデックスに変換（サーバー検証用）
static byte toMapColor(int r, int g, int b)
```

> **注**: 実際の Floyd-Steinberg 量子化は Node.js 側の Worker Thread で行う。Java 側はパレット定義のみ持ち、受信したインデックス配列をそのまま MapPacket に載せる。

---

## 6. コンポーネント詳細 — browser-renderer (Node.js)

### 6.1 技術スタック

| 区分 | パッケージ | バージョン | 役割 |
|------|-----------|-----------|------|
| **ランタイム** | Node.js | 20 LTS | 実行環境 |
| **言語** | TypeScript | 5.x | 開発言語 |
| **ブラウザ制御** | playwright | ^1.44.0 | Headless Chromium 操作・スクリーンキャスト |
| **IPC 通信** | ws | ^8.17.0 | WebSocket サーバー |
| **画像処理** | sharp | ^0.33.4 | PNG リサイズ・前処理（libvips ベース） |
| **並列処理** | piscina | ^4.4.0 | Worker Thread プール管理 |
| **TS 実行 (dev)** | tsx | ^4.15.0 | ビルドなしで `.ts` を実行 |
| **型定義** | typescript, @types/node, @types/ws | latest | 型安全な開発 |
| **パッケージ管理** | pnpm（推奨）または bun | latest | 依存管理 |

### 6.2 package.json スクリプト

```json
{
  "scripts": {
    "dev":   "tsx watch src/index.ts",
    "build": "tsc",
    "start": "node dist/index.js",
    "typecheck": "tsc --noEmit"
  }
}
```

### 6.3 tsconfig.json

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "Node16",
    "moduleResolution": "Node16",
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  }
}
```

### 6.4 IPCServer.ts

**責務**: Java Plugin からの WebSocket 接続を受け付け、メッセージをルーティング

```typescript
// 受信メッセージ処理
switch (msg.type) {
  case 'NAVIGATE':    browserPool.navigate(msg.screenId, msg.url); break;
  case 'MOUSE_CLICK': browserPool.click(msg.screenId, msg.x, msg.y); break;
  case 'SCROLL':      browserPool.scroll(msg.screenId, msg.deltaY); break;
  case 'GO_BACK':     browserPool.goBack(msg.screenId); break;
  case 'GO_FORWARD':  browserPool.goForward(msg.screenId); break;
  case 'RELOAD':      browserPool.reload(msg.screenId); break;
  case 'CLOSE':       browserPool.close(msg.screenId); break;
}

// 送信メッセージ
sendFrame(screenId: string, colorData: Uint8Array): void
sendUrlChanged(screenId: string, url: string): void
sendPageLoaded(screenId: string): void
sendError(screenId: string, message: string): void
```

### 6.5 BrowserPool.ts

**責務**: スクリーン ID と Chromium Page のマッピング管理

```typescript
class BrowserPool {
  private pages: Map<string, PageController> = new Map();

  async open(screenId: string, width: number, height: number): Promise<void>
  async close(screenId: string): Promise<void>
  async navigate(screenId: string, url: string): Promise<void>
  async click(screenId: string, x: number, y: number): Promise<void>
  async scroll(screenId: string, deltaY: number): Promise<void>
  async goBack(screenId: string): Promise<void>
  async goForward(screenId: string): Promise<void>
  async reload(screenId: string): Promise<void>
}
```

### 6.6 PageController.ts

**責務**: 個々の Chromium Page の操作とフレームキャプチャ

```typescript
class PageController {
  private page: Page;
  private screencast: any;  // CDP screencast session
  private frameProcessor: FrameProcessor;

  async startCapture(fps: number): Promise<void>
  // page.on('screencastframe') でフレームを受信 → FrameProcessor へ
}
```

**スクリーンキャスト方式**:
- Playwright の CDP (Chrome DevTools Protocol) `Page.startScreencast` を使用
- `format: 'png'`, `everyNthFrame: Math.floor(60 / fps)` で FPS を制御
- `maxWidth`, `maxHeight` でスクリーン解像度を指定

### 6.7 FrameProcessor.ts

**責務**: PNG バッファを受け取り Minecraft パレットに変換して IPC で送信

```typescript
class FrameProcessor {
  private pool: Piscina;
  private prevColorData: Uint8Array | null = null;

  async process(pngBuffer: Buffer, screenW: number, screenH: number): Promise<void> {
    // 1. sharp でリサイズ (screenW*128 x screenH*128) → RGB バッファ取得
    // 2. Worker Thread で Floyd-Steinberg 量子化 → パレットインデックス配列
    // 3. デルタ計算（前フレームとの差分）
    // 4. IPC で Java Plugin へ送信
  }
}
```

### 6.8 quantize.worker.ts（Worker Thread）

**責務**: Floyd-Steinberg dithering による 144 色量子化（CPU ヘビーな処理）

```typescript
// piscina の Worker として動作
// 入力: RGB バッファ (Uint8Array), width, height
// 出力: Minecraft MapColor インデックス配列 (Uint8Array)
export default function quantize(
  rgbBuffer: Uint8Array,
  width: number,
  height: number
): Uint8Array
```

**アルゴリズム**:
1. 各ピクセルの RGB → Minecraft 144 色パレットの最近傍色を探索
2. 量子化誤差を右・左下・下・右下のピクセルに拡散（Floyd-Steinberg 係数）
3. パレットインデックス（0〜143）の配列を返す

### 6.9 YtDlpBridge.ts

**責務**: YouTube URL を yt-dlp で解析してストリーム URL を取得

```typescript
async function resolveYouTubeUrl(youtubeUrl: string): Promise<string> {
  // yt-dlp --get-url -f "best[height<=720]" <url>
  // → HLS/DASH ストリーム URL を返す
  // → PageController がこの URL を page.goto() で開く
}
```

**YouTube 以外の動画サイト**:
- yt-dlp は YouTube 以外（ニコニコ、Twitch 等）も対応
- URL が YouTube ドメインの場合のみ自動的に YtDlpBridge を通す
- その他のサイトは通常のブラウザアクセスにフォールバック

---

## 7. IPC プロトコル仕様

Java Plugin と browser-renderer は **localhost WebSocket（ポート 25600）** で通信する。

### 7.1 メッセージフォーマット

すべてのメッセージは JSON 文字列。

```typescript
interface IPCMessage {
  type: string;
  screenId: string;
  [key: string]: any;
}
```

### 7.2 Java → Node.js（コマンド）

| type | 追加フィールド | 説明 |
|------|--------------|------|
| `OPEN` | `width`, `height`, `fps` | スクリーン用ブラウザを初期化 |
| `NAVIGATE` | `url` | URL を開く |
| `MOUSE_CLICK` | `x`, `y`, `button` (`left`/`right`) | クリック |
| `SCROLL` | `deltaY` | スクロール（正=下、負=上）|
| `GO_BACK` | — | ブラウザバック |
| `GO_FORWARD` | — | 進む |
| `RELOAD` | — | リロード |
| `CLOSE` | — | ブラウザインスタンス破棄 |
| `SET_FPS` | `fps` | FPS 変更 |

### 7.3 Node.js → Java（イベント）

| type | 追加フィールド | 説明 |
|------|--------------|------|
| `FRAME` | `width`, `height`（メタ）、`payload`（binary） | キャプチャフレーム（色インデックス配列） |
| `DELTA_FRAME` | `x`, `y`, `w`, `h`（メタ）、`payload`（binary） | 差分フレーム（変化領域のみ） |
| `URL_CHANGED` | `url` | ページ遷移後の URL |
| `PAGE_LOADED` | — | ページロード完了 |
| `ERROR` | `message` | エラー通知 |
| `READY` | — | browser-renderer 起動完了 |

### 7.4 フレームデータ仕様

```
FRAME/DELTA_FRAME の payload:
  バイナリの Uint8Array
  サイズ = screenWidth * 128 * screenHeight * 128 バイト
  各バイト = Minecraft MapColor インデックス (0〜143)
  
  マップ (0,0) の左上ピクセル = インデックス 0
  走査順: 左→右、上→下
```

---

## 8. 機能要件

### 8.1 スクリーン作成・管理

| ID | 要件 | 優先度 |
|----|------|--------|
| F-SCR-01 | スクリーンをゲーム内の任意ブロック面に配置できる | Must |
| F-SCR-02 | スクリーンサイズを N×M（マップ単位）で指定できる（最大 8×8） | Must |
| F-SCR-03 | スクリーンはワールドに永続化される（サーバー再起動後も維持） | Must |
| F-SCR-04 | スクリーンに名前（ラベル）を付けられる | Should |
| F-SCR-05 | スクリーンを削除できる | Must |
| F-SCR-06 | スクリーン一覧を確認できる | Must |
| F-SCR-07 | スクリーンのオーナーシップ管理 | Should |
| F-SCR-08 | 同一ワールドに最大 N 個のスクリーンを配置できる（設定可能） | Must |
| F-SCR-09 | 一定距離外のプレイヤーにはパケットを送信しない | Must |

### 8.2 ブラウザ表示

| ID | 要件 | 優先度 |
|----|------|--------|
| F-BRW-01 | 指定 URL をブラウザで開いてスクリーンに表示できる | Must |
| F-BRW-02 | YouTube の動画を再生できる（yt-dlp 経由） | Must |
| F-BRW-03 | 任意の HTTP / HTTPS ページを表示できる | Must |
| F-BRW-04 | フレームレートは設定可能（デフォルト 10 FPS、最大 20 FPS） | Must |
| F-BRW-05 | 解像度はスクリーンサイズに応じて自動決定 | Must |
| F-BRW-06 | ページロード完了後に描画を開始する | Must |
| F-BRW-07 | ページロード中はローディング画面を表示する | Should |
| F-BRW-08 | ブラウザクラッシュ時に自動再起動する | Must |
| F-BRW-09 | JavaScript / HTML5 動画の再生をサポートする | Must |

### 8.3 ブラウザ操作

| ID | 要件 | 優先度 |
|----|------|--------|
| F-INT-01 | 操作アイテムを持って額縁を右クリックするとクリックイベントを送信する | Must |
| F-INT-02 | クリック位置をスクリーン座標に変換してブラウザに送信する | Must |
| F-INT-03 | URL 入力は AnvilGUI 経由で行う | Must |
| F-INT-04 | 戻る・進む・リロード操作をサポートする | Must |
| F-INT-05 | スクロール操作をサポートする | Should |
| F-INT-06 | 操作モード中に HUD で現在 URL を表示する | Should |

### 8.4 セキュリティ

| ID | 要件 | 優先度 |
|----|------|--------|
| F-SEC-01 | URL ホワイトリスト / ブラックリストをサポートする | Must |
| F-SEC-02 | ローカルネットワーク（192.168.x.x / 10.x.x.x 等）へのアクセスを禁止する | Must |
| F-SEC-03 | スクリーン作成・操作の権限チェックを行う | Must |
| F-SEC-04 | HTTP（非 HTTPS）のアクセスを設定で禁止できる | Should |

### 8.5 音声（Phase 4）

| ID | 要件 | 優先度 |
|----|------|--------|
| F-AUD-01 | MapBrowserCompanion (MBC) 導入時、スクリーン周辺の音声を再生できる | Should |
| F-AUD-02 | 音量はスクリーンからの距離に応じてフェードアウトする | Should |
| F-AUD-03 | バニラクライアントでは音声なし（制限として明示） | Must |
| F-AUD-04 | MBC は Fabric ローダーのみをサポートする | Must |
| F-AUD-05 | MBC は Kotlin 実装を標準とする | Must |

---

## 9. 非機能要件

### 9.1 パフォーマンス

| ID | 要件 | 目標値 |
|----|------|--------|
| NF-PERF-01 | 2×2 スクリーンでのフレーム送信レイテンシ | < 150ms |
| NF-PERF-02 | 1 サーバーあたりの同時アクティブスクリーン数 | 最大 8（設定可能） |
| NF-PERF-03 | 1 スクリーンあたりの browser-renderer メモリ使用量 | < 400MB |
| NF-PERF-04 | MapPacket による TPS への影響 | TPS 19.0 以上を維持 |
| NF-PERF-05 | 色量子化は Worker Thread で並列化する | 必須 |
| NF-PERF-06 | デルタ圧縮により変化なしフレームの転送量を削減 | 変化なし時 < 1KB |

### 9.2 安定性

| ID | 要件 |
|----|------|
| NF-AVAIL-01 | browser-renderer がクラッシュした場合 30 秒以内に自動再起動する |
| NF-AVAIL-02 | IPC 接続が切断された場合、再接続を自動的に試みる |
| NF-AVAIL-03 | サーバーシャットダウン時に browser-renderer を正常に終了する |
| NF-AVAIL-04 | プラグインリロード時に browser-renderer が正常に再起動する |

### 9.3 保守性

| ID | 要件 |
|----|------|
| NF-MAINT-01 | Minecraft バージョンごとの NMS 差分は abstraction レイヤーで吸収する |
| NF-MAINT-02 | browser-renderer はプラグインとは独立してアップデートできる |
| NF-MAINT-03 | ログは INFO / DEBUG / TRACE の 3 レベルで出力する |

### 9.4 互換性

| ID | 要件 |
|----|------|
| NF-COMPAT-01 | Paper / Spigot / Leaf 1.21〜1.21.11 で動作する |
| NF-COMPAT-02 | Velocity 3.x プロキシ下で動作する |
| NF-COMPAT-03 | バニラクライアントで映像を視聴できる |
| NF-COMPAT-04 | Java 21、Node.js 20 LTS で動作する |

---

## 10. 画面・表示システム

### 10.1 スクリーン解像度

```
スクリーン全体解像度 = width * 128 px × height * 128 px

例:
  1×1 マップ =  128 ×  128 px
  2×2 マップ =  256 ×  256 px
  4×4 マップ =  512 ×  512 px （推奨・実用的な上限）
  8×8 マップ = 1024 × 1024 px （高負荷・非推奨）
```

### 10.2 マップ ID の割り当て

```
スクリーン width=2, height=2 の場合:
  mapIds[0] = 左上   mapIds[1] = 右上
  mapIds[2] = 左下   mapIds[3] = 右下

  インデックス = y * width + x
```

### 10.3 色量子化アルゴリズム

Floyd-Steinberg dithering を使用する。

```
誤差拡散係数:
         * 7/16
  3/16  5/16  1/16

処理フロー:
  各ピクセル (x, y) について:
    1. 現在の RGB 値に最近傍の Minecraft MapColor を探索
    2. 量子化誤差 = 元の RGB - 選択した MapColor の RGB
    3. 誤差を隣接ピクセルに拡散:
       (x+1, y)   += 誤差 * 7/16
       (x-1, y+1) += 誤差 * 3/16
       (x,   y+1) += 誤差 * 5/16
       (x+1, y+1) += 誤差 * 1/16
```

### 10.4 デルタ圧縮

```
前フレームのカラーデータをキャッシュ（スクリーンごと）
├─ 変化なし → パケット送信しない（フレームをスキップ）
└─ 変化あり → 変化領域の最小包含矩形 (x, y, w, h) を計算
               → DELTA_FRAME メッセージで送信
               → Java 側で対応するマップの dirtyArea のみ更新
```

---

## 11. ブラウザ操作システム

### 11.1 操作アイテム（config.yml で変更可能）

| アイテム | デフォルト素材 | 操作 |
|---------|--------------|------|
| 左クリックポインター | `FEATHER` | スクリーン右クリック位置へ左クリック送信 |
| 右クリックポインター | `FLINT` | スクリーン右クリック位置へ右クリック送信 |
| 戻る | `BOW` | ブラウザバック |
| 進む | `ARROW` | ブラウザ進む |
| リロード | `COMPASS` | ページリロード |
| URL バー | `WRITABLE_BOOK` | AnvilGUI で URL 入力 |
| テキスト入力 | `WRITABLE_BOOK` | AnvilGUI でフォーム文字入力 |
| スクロール | `MAGMA_CREAM` | 右クリック:下 / Shift+右クリック:上 |

### 11.2 操作モード

| モード | 遷移条件 | 動作 |
|--------|---------|------|
| 閲覧モード（デフォルト） | — | 映像を見るだけ |
| 操作モード | ポインターを持って額縁右クリック | スクリーン操作が有効 |
| 操作モード終了 | スニーク または `/mb exit` | 閲覧モードに戻る |

### 11.3 クリック座標変換（RaycastUtil）

```
PlayerInteractEvent.getInteractionPoint() → ヒット座標（ワールド座標）
  └→ Item Frame のローカル座標に変換 (0.0〜1.0)
       └→ スクリーン全体のピクセル座標に変換
            └→ WebSocket で browser-renderer へ送信
```

---

## 12. 音声システム

音声は **Phase 4** での実装。バニラクライアントでは非対応。

### 12.1 アーキテクチャ（Phase 4）

```
Chromium → CDP WebAudio domain → PCM バッファ (Node.js)
  └→ Opus エンコード (64kbps, 48kHz, mono)
       └→ WebSocket → Java Plugin
              └→ カスタムパケット → MapBrowserCompanion (Fabric/Kotlin)
                 └→ Opus デコード → OpenAL 3D 空間音声再生
```

### 12.2 仕様

| 項目 | 値 |
|------|----|
| サンプルレート | 48,000 Hz |
| チャンネル | ステレオ → モノラルにダウンミックス |
| エンコード | Opus |
| ビットレート | 64 kbps |
| 最大再生距離 | 32 ブロック（設定可能） |

---

## 13. 権限・パーミッションシステム

### 13.1 パーミッションノード

| ノード | 説明 | デフォルト |
|--------|------|-----------|
| `mapbrowser.create` | スクリーン作成 | `op` |
| `mapbrowser.destroy` | 自分のスクリーン削除 | `op` |
| `mapbrowser.destroy.others` | 他人のスクリーン削除 | `op` |
| `mapbrowser.use` | スクリーン操作・URL 変更 | `op` |
| `mapbrowser.use.others` | 他人のスクリーンを操作 | `op` |
| `mapbrowser.view` | 映像視聴 | `true` |
| `mapbrowser.admin` | 全管理操作 | `op` |
| `mapbrowser.bypass.whitelist` | URL ホワイトリストを無視 | `op` |
| `mapbrowser.reload` | プラグインリロード | `op` |

### 13.2 フォールバック

1. LuckPerms が導入されている場合 → LuckPerms API 使用
2. Vault が導入されている場合 → Vault 使用
3. どちらもない場合 → OP ベースの権限管理

---

## 14. コマンド仕様

メインコマンド: `/mapbrowser` エイリアス: `/mb`

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/mb create <w> <h> [name]` | 見ているブロック面にスクリーンを作成 | `mapbrowser.create` |
| `/mb destroy [name\|id]` | スクリーンを削除 | `mapbrowser.destroy` |
| `/mb open <url>` | URL を開く | `mapbrowser.use` |
| `/mb back` | ブラウザバック | `mapbrowser.use` |
| `/mb forward` | ブラウザ進む | `mapbrowser.use` |
| `/mb reload` | ページリロード | `mapbrowser.use` |
| `/mb list [world]` | スクリーン一覧 | `mapbrowser.use` |
| `/mb info [name\|id]` | スクリーン詳細 | `mapbrowser.use` |
| `/mb fps <value>` | FPS 変更 | `mapbrowser.use` |
| `/mb give <item>` | 操作アイテムを付与 | `mapbrowser.use` |
| `/mb exit` | 操作モード終了 | — |
| `/mb admin reload` | プラグインリロード | `mapbrowser.reload` |
| `/mb admin status` | browser-renderer の状態確認 | `mapbrowser.admin` |
| `/mb admin stop <id>` | スクリーンのブラウザを停止 | `mapbrowser.admin` |

---

## 15. 設定ファイル仕様

### 15.1 config.yml（Java Plugin 側）

```yaml
browser:
  node-path: ""           # Node.js の実行パス（空 = PATH から自動検出）
  ipc-port: 25600         # IPC WebSocket ポート
  auto-restart: true      # クラッシュ時の自動再起動
  max-restart-attempts: 3
  restart-delay: 30       # 再起動待機秒数

screen:
  default-fps: 10         # デフォルト FPS
  max-fps: 30             # 最大 FPS
  max-width: 8
  max-height: 8
  max-screens-per-world: 8
  render-distance: 64     # パケット送信距離（ブロック）

security:
  allow-http: false
  block-local-network: true
  url-whitelist: []       # 空 = 無効
  url-blacklist:
    - "*.onion"

youtube:
  use-ytdlp: true
  ytdlp-path: "yt-dlp"

items:
  pointer: FEATHER
  pointer-left: FEATHER
  pointer-right: FLINT
  back: BOW
  forward: ARROW
  reload: COMPASS
  url-bar: WRITABLE_BOOK
  text-input: WRITABLE_BOOK
  scroll: MAGMA_CREAM
  scroll-up: SLIME_BALL
  scroll-down: MAGMA_CREAM

storage: sqlite           # yaml または sqlite
debug: false
```

### 15.2 スクリーンデータ（SQLite）

| カラム | 型 | 説明 |
|-------|----|----|
| `id` | TEXT (UUID) | スクリーン固有 ID |
| `name` | TEXT | ラベル |
| `world` | TEXT | ワールド名 |
| `x`, `y`, `z` | INTEGER | 基準ブロック座標 |
| `face` | TEXT | 設置面 |
| `width`, `height` | INTEGER | マップ単位のサイズ |
| `owner_uuid` | TEXT | 作成者 UUID |
| `created_at` | INTEGER | 作成時刻（epoch ms） |
| `current_url` | TEXT | 現在の URL |
| `map_ids` | TEXT (JSON) | マップ ID リスト |

---

## 16. 対応環境

### 16.1 サーバー

| ソフトウェア | バージョン | 備考 |
|------------|-----------|------|
| Leaf | 1.21〜1.21.11 | メインターゲット |
| Paper | 1.21〜1.21.11 | フル対応 |
| Spigot | 1.21〜1.21.11 | Paper 互換 API 範囲内 |
| Velocity | 3.x | プロキシ連携 |
| Folia | — | 非対応 |

### 16.2 開発環境

| ツール | バージョン |
|--------|-----------|
| JDK | 21 |
| Node.js | 20 LTS |
| pnpm | 9.x（または bun latest）|
| TypeScript | 5.x |
| VS Code | 最新安定版 |
| Gradle | 8.x（Wrapper 使用）|

---

## 17. 技術スタック詳細

### 17.1 Java Plugin

| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| Paper API | 1.21+ | Minecraft プラグイン API |
| PacketEvents | 2.x | 低レベルパケット操作 |
| Adventure API | 4.x（Paper 同梱） | テキスト / HUD 表示 |
| Java-WebSocket | 1.5.x | IPC WebSocket クライアント |
| SQLite JDBC | 3.x | データ永続化 |
| AnvilGUI | latest | Anvil UI（URL 入力） |
| LuckPerms API | 5.x | 権限管理（optional） |

### 17.2 Node.js (browser-renderer)

| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| playwright | ^1.44.0 | Headless Chromium 制御 |
| ws | ^8.17.0 | WebSocket IPC サーバー |
| sharp | ^0.33.4 | 高速画像処理（libvips） |
| piscina | ^4.4.0 | Worker Thread プール |
| typescript | ^5.4.0 | 開発言語 |
| tsx | ^4.15.0 | TS 直接実行（dev） |
| @types/node | ^20.0.0 | Node.js 型定義 |
| @types/ws | ^8.5.0 | ws 型定義 |

---

## 18. 制約・既知の限界

| 制約 | 詳細 |
|------|------|
| **色数** | Minecraft マップは 144 色パレットに制限。フルカラーより品質が落ちる |
| **FPS 上限** | 実装上の設定上限は 30 FPS。実効値はマップ描画負荷に依存 |
| **解像度上限** | 4×4 スクリーンで 512×512px が実用的な上限 |
| **YouTube DRM** | Headless Chrome での DRM は yt-dlp で回避可能（Netflix 等の Widevine DRM は不可）|
| **音声** | バニラクライアントでは音声非対応（コンパニオン Mod が必要）|
| **メモリ** | Chromium 1 インスタンスあたり 200〜400MB |
| **Pterodactyl** | Node.js のインストールにホスト会社への依頼またはカスタム Egg が必要 |

---

## 19. 開発フェーズ

**現在ステータス**: Phase 5（公開準備・Companion Mod連携要件の固定）を実装中。

### Phase 1 — 映像表示 MVP（最優先）

**目標**: バニラクライアントで静止画ページが 10 FPS で表示できる

| # | タスク | 担当 |
|---|--------|------|
| 1 | browser-renderer プロジェクトセットアップ（package.json / tsconfig） | Node.js |
| 2 | IPCServer.ts — WebSocket サーバー基本実装 | Node.js |
| 3 | BrowserPool.ts + PageController.ts — Playwright によるキャプチャ | Node.js |
| 4 | FrameProcessor.ts + quantize.worker.ts — Floyd-Steinberg 量子化 | Node.js |
| 5 | MapColorUtil.java — 144 色パレット定義 | Java |
| 6 | BrowserIPCClient.java — WebSocket クライアント + 子プロセス起動 | Java |
| 7 | Screen.java + ScreenManager.java — スクリーンモデル | Java |
| 8 | FrameRenderer.java — MapPacket 送信 | Java |
| 9 | `/mb create` + `/mb open` コマンド実装 | Java |
| 10 | 疎通テスト（ローカルサーバーでの動作確認） | — |

### Phase 2 — インタラクション

**目標**: ゲーム内からブラウザを操作できる

| # | タスク |
|---|--------|
| 1 | RaycastUtil.java — クリック座標変換 |
| 2 | InputHandler.java — PlayerInteractEvent 処理 |
| 3 | 操作アイテムシステム（ポインター / 戻る / 進む / リロード） |
| 4 | AnvilGUI URL 入力 |
| 5 | HUD 表示（Adventure API で現在 URL を ActionBar に表示） |
| 6 | デルタ圧縮実装（FrameProcessor の最適化） |
| 7 | YouTube テスト（/mb open https://youtube.com） |

### Phase 3 — 最適化・安定化

**目標**: 本番利用に耐える品質

| # | タスク |
|---|--------|
| 1 | 距離ベースパケット制御（render-distance 設定の実装） |
| 2 | YtDlpBridge.ts — YouTube ストリーム最適化 |
| 3 | プロセス管理強化（クラッシュ検知・自動再起動・ヘルスチェック） |
| 4 | DataStore.java — SQLite 永続化 |
| 5 | PermissionManager.java — LuckPerms 連携 |
| 6 | URL セキュリティ（ブラックリスト・ローカルネット保護） |
| 7 | パフォーマンステスト（TPS 影響測定） |

### Phase 4 — 音声・コンパニオン Mod

**目標**: コンパニオン Mod による音声再生と高品質表示

| # | タスク |
|---|--------|
| 1 | Java 側 AudioBridge / Plugin Messaging 送信導線 |
| 2 | Node 側音声パイプライン（Noop / Synthetic / MediaRecorder） |
| 3 | AUDIO_FRAME の IPC 受信・中継実装 |
| 4 | MapBrowserCompanion（Fabric/Kotlin、別リポジトリ）側デコード再生仕様の定義 |
| 5 | 実機検証（距離減衰・品質・同期） |

### Phase 5 — Velocity 対応・拡張

| # | タスク |
|---|--------|
| 1 | Velocity Plugin Messaging Channel 実装（OPEN/RELOAD/FPS/CLOSE/BACK/FORWARD） |
| 2 | STATUS 診断拡張（IPC health / inbound / audio diagnostics） |
| 3 | パブリック API の公開（MapBrowserService 境界） |
| 4 | Companion Mod 連携ドキュメント整備 |
| 5 | リリース前チェックリストの確定 |

---

## 20. 参考プロジェクト分析

### 20.1 ImageFrame (LOOHP)

- `MapUtils.java` の **Floyd-Steinberg 実装**を参考にする
- NMS バージョン分岐の abstraction 構造（`V1_21`, `V1_21_1` ... フォルダ）を同様に採用
- `ClientboundMapItemDataPacket` の送信タイミングとフォーマット

### 20.2 WebDisplays / MCEF (CinemaMod / montoyo)

- スクリーンエンティティの概念設計（Item Frame の組み合わせ方）
- レイキャストによるクリック座標変換ロジック
- **根本的な違い**: MCEF はクライアントで JCEF を直接実行。本プロジェクトはサーバーサイドで Headless Chromium + WebSocket 経由というまったく異なる構成

### 20.3 ImageFrameClient (LOOHP)

- Java Plugin ↔ Fabric Mod 間のカスタムパケット設計（Phase 4 の参考）
- Plugin Messaging Channel の実装パターン
