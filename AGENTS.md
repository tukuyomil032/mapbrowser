# AGENTS.md — MapBrowser プロジェクト AI エージェント指示書

このファイルは Claude / Cursor / Copilot 等の AI コーディングエージェントがこのリポジトリで作業する際に必ず読むべき指示書です。

---

## プロジェクト概要

**MapBrowser** は Minecraft Java Edition のサーバープラグインで、ゲーム内マップ（額縁に貼ったマップアイテム）にリアルタイムの Web ブラウザ映像を描画するシステムです。

### 2 つのサブプロジェクト

| ディレクトリ | 言語 | 役割 |
|------------|------|------|
| `src/` | Java 21 | Minecraft サーバープラグイン（Leaf/Paper/Spigot） |
| `browser-renderer/` | TypeScript / Node.js 20 | Headless Chromium 制御・フレーム配信 |

**2 つは WebSocket（localhost:25600）で通信する。Java 側が親プロセスで Node.js 側を子プロセスとして起動する。**

---

## ディレクトリ構成（必ず把握すること）

```
MAPBROWSER/
├── src/main/java/com/tukuyomil032/mapbrowser/
│   ├── MapBrowserPlugin.java      ← プラグインのエントリーポイント
│   ├── screen/
│   │   ├── Screen.java            ← スクリーンのデータモデル
│   │   ├── ScreenManager.java     ← スクリーンの CRUD・ライフサイクル
│   │   └── FrameRenderer.java     ← MapPacket を組み立てプレイヤーへ送信
│   ├── ipc/
│   │   ├── BrowserIPCClient.java  ← WS クライアント + 子プロセス起動・管理
│   │   └── IPCMessage.java        ← IPC メッセージ型定義（Java 側）
│   ├── input/
│   │   └── InputHandler.java      ← PlayerInteractEvent → IPC 変換
│   ├── command/
│   │   └── MapBrowserCommand.java ← /mb コマンド一式
│   ├── permission/
│   │   └── PermissionManager.java ← LuckPerms / Vault / OP フォールバック
│   ├── storage/
│   │   └── DataStore.java         ← SQLite / YAML 永続化
│   └── util/
│       ├── MapColorUtil.java       ← Minecraft 144色パレット定義
│       └── RaycastUtil.java        ← クリック位置 → ブラウザ座標 変換
│
├── browser-renderer/
│   └── src/
│       ├── index.ts               ← エントリーポイント（IPC サーバー起動）
│       ├── types/ipc.ts           ← IPC メッセージ型（Java の IPCMessage と対応）
│       ├── ipc/IPCServer.ts       ← WebSocket サーバー・メッセージルーティング
│       ├── browser/
│       │   ├── BrowserPool.ts     ← screenId ↔ PageController のマッピング
│       │   └── PageController.ts  ← Playwright Page 操作・スクリーンキャスト
│       ├── renderer/
│       │   ├── FrameProcessor.ts  ← sharp リサイズ → Worker → デルタ → IPC 送信
│       │   └── quantize.worker.ts ← Floyd-Steinberg 量子化（Worker Thread）
│       └── youtube/
│           └── YtDlpBridge.ts     ← yt-dlp で YouTube ストリーム URL 取得
│
├── docs/REQUIREMENTS.md           ← 完全版要件定義書（必ず参照）
└── AGENTS.md                      ← このファイル
```

---

## IPC プロトコル（最重要）

Java ↔ Node.js の通信は **WebSocket**。

- Java → Node.js のコマンドは JSON テキスト
- Node.js → Java の制御イベント（READY/URL_CHANGED/PAGE_LOADED/ERROR）は JSON テキスト
- Node.js → Java のフレーム（FRAME/DELTA_FRAME）は **独自バイナリフレーム**

### Java → Node.js（コマンド）

```json
{ "type": "OPEN",       "screenId": "<uuid>", "width": 2, "height": 2, "fps": 10 }
{ "type": "NAVIGATE",   "screenId": "<uuid>", "url": "https://..." }
{ "type": "MOUSE_CLICK","screenId": "<uuid>", "x": 320, "y": 240, "button": "left" }
{ "type": "SCROLL",     "screenId": "<uuid>", "deltaY": 300 }
{ "type": "GO_BACK",    "screenId": "<uuid>" }
{ "type": "GO_FORWARD", "screenId": "<uuid>" }
{ "type": "RELOAD",     "screenId": "<uuid>" }
{ "type": "CLOSE",      "screenId": "<uuid>" }
{ "type": "SET_FPS",    "screenId": "<uuid>", "fps": 15 }
```

### Node.js → Java（イベント）

```json
{ "type": "READY" }
{ "type": "URL_CHANGED", "screenId": "<uuid>", "url": "https://..." }
{ "type": "PAGE_LOADED", "screenId": "<uuid>" }
{ "type": "ERROR",       "screenId": "<uuid>", "message": "..." }
```

FRAME/DELTA_FRAME は JSON ではなくバイナリで送信する。

### フレームデータ仕様（Node.js → Java）

```
Binary Header:
  magic(4) = MBFR
  version(1)
  type(1) = 1:FRAME, 2:DELTA_FRAME
  reserved(2)
  screenId(16, UUID bytes)
  meta(16, int32 x4)

Payload:
  Uint8Array (Minecraft MapColor インデックス)
  走査順 = 左→右、上→下（ラスタースキャン）
```

**このプロトコルを変更する場合は必ず `types/ipc.ts` と `IPCMessage.java` を同時に更新すること。**

---

## コーディング規約

### Java 側

- **Paper API** のみ使用。NMS（net.minecraft.server）への直接アクセスは禁止。NMS が必要な場合は PacketEvents を使う
- パッケージ名: `com.tukuyomil032.mapbrowser`
- クラス名: PascalCase、メソッド名: camelCase
- すべての public メソッドに Javadoc コメントを書く
- `@Override` アノテーションは必ず付ける
- プラグインの logger は `MapBrowserPlugin.getInstance().getLogger()` を使う
- Bukkit の非同期スレッドから Bukkit API を呼ぶな。必ず `Bukkit.getScheduler().runTask()` でメインスレッドに戻すこと
- `PacketEvents` を使ったパケット送信は非同期で行ってよい

### TypeScript 側（browser-renderer）

- **strict モード**を維持する。`any` は使用禁止（やむを得ない場合は `unknown` + 型ガード）
- `async/await` を使う。`Promise.then()` チェーンは書かない
- エラーハンドリングは必ず `try/catch` で行い、`console.error` ではなく logger を通す
- `sharp` の操作は必ず `await` を付ける（非同期）
- Worker Thread（piscina）への渡すデータは `Transferable`（`ArrayBuffer` 等）にして zero-copy にする
- ファイル冒頭に「このファイルの責務」を 1 行コメントで書く

### 共通

- コミットメッセージは英語で `feat:`, `fix:`, `refactor:`, `docs:` のプレフィックスを使う
- 実装や編集、1プロンプトの作業が終わったあと、同Phase内での未実装ポイント、あるいは今のPhaseでのすべての実装が終わった場合は次のPhaseで実装すべき点があるか確認し、ある場合はどこから実装を進めていくかユーザーに質問モードで選択肢で質問をすること。これは全てのPhaseの全ての実装を終えるまで、つまりリリースできる段階になるまで実装後に毎回質問をすること。また、その質問の後、その実装ごとの変更内容にふさわしいコミットメッセージを含めたコマンドを生成し、実際にそのコマンドを実行するかしないかユーザーに質問モードで質問をすること。同Phase内での変更や実装ごとにはコミットコマンドの生成は質問はしないこと。ただし、同フェーズ内での変更や実装の場合でも、1つ1つの実装の規模が大きい場合は例外とする。更に、コミットコマンドを実行しないを選んだ場合にも、まだ実装すべき同フェーズ内の作業や、次のフェーズが残っている場合は、実装を続け、実装を完了するまで半永久的に質問→実装→質問を繰り返すこと。ここで言う質問とは、あなたが出力する文章で聞くのではなく、Planモードで要件定義をするときに使用する選択式の質問のこと。コミットコマンドのフォーマットは、`git commit -m "message" -m "message" -m "message"`のように、実装した内容を大まかにまとめて、-mオプションでそれぞれ改行すること。
  - git操作には、VSCode内のターミナルからコマンドで行うこと。MCPなどを通さず、直接コマンドを叩くこと。
    - ファイルの追加：git add .
    - コミット：↑1つ上の箇条書きで述べている通り、その実装ごとの変更内容にふさわしいコミットメッセージを含めた形式にすること。`git commit -m "message" -m "message" -m "message"`
    - プッシュ：git push origin main
      - 何らかの事象によりgit reset --soft HEAD^などのリセットコマンドを打った場合には私がそう伝えるので、その場合はforce pushをしないとコンフリクトが出て拒否されるので、git push -f -u origin mainを実行してください
- 当該プロンプトで実装や編集を終えたあと、ビルドをする前にバックグラウンドから全ファイル(.java / .ts / .json / .ymlなど)を読み取り、エラーや警告がある場合、エラーの内容と原因を調査し、それも修正すること。1度の修正で直らない場合もあるため、修正後にもう一度バックグラウンドからエラーを取得し、エラー及び警告が0件になるまで処理を続行すること。
- 複数の更新・追加作業内容がある場合、段階ごとに1,2,3と番号をつけて振り分けること。また、この番号付きの作業内容は1度のプロンプトで全て実装すること。段階ごとに実装を進め、段階ごとに実装内容をユーザに逐一報告しながら進めること。
  - 例えば、1.バイナリIPC化（Base64撤廃, 2.適応FPS制御, 3.Java側の矩形差分描画拡張の3つのタスクがある場合、1〜3のタスクをこなしてくださいと命令されたら、その1回のプロンプトで全て実装を終えてください。
- `node_modules/`, `dist/`, `build/`, `run/`はコミットしない

---

## 作業する前に確認すること

### 新しいファイルを作る前に

1. `docs/REQUIREMENTS.md` の該当セクションを読む
2. そのファイルの責務が既存のクラスと重複していないか確認する
3. IPC に関わる変更なら両サイド（Java + TypeScript）を同時に修正する

### Java 側を修正する前に

- `paper-plugin.yml` の依存関係（PacketEvents, AnvilGUI）が正しく記述されているか確認
- Minecraft イベントを扱う場合は必ずメインスレッドで実行されているか確認
- `ClientboundMapItemDataPacket` の構造は Minecraft バージョンごとに異なる。1.21 系に対応したコードを書くこと

### Node.js 側を修正する前に

- `pnpm install` または `bun install` 済みか確認（`node_modules` が存在するか）
- Playwright が `playwright install chromium` 済みか確認
- Worker Thread に渡すデータは `SharedArrayBuffer` か `ArrayBuffer` のコピーを使う（オブジェクト参照は渡せない）

---

## よくある間違いと禁止事項

### Java 側

```java
// NG: 非同期スレッドから Bukkit API を直接呼ぶ
CompletableFuture.runAsync(() -> {
    player.sendMessage("hello"); // クラッシュの原因
});

// OK: メインスレッドに戻す
CompletableFuture.runAsync(() -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        player.sendMessage("hello");
    });
});

// NG: NMS に直接アクセス
net.minecraft.server.level.ServerPlayer nmsPlayer = ...;

// OK: PacketEvents または Paper API を使う
PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
```

### TypeScript 側

```typescript
// NG: any の使用
const data: any = msg.data;

// OK: 型ガード
if (typeof msg.data === 'string') { ... }

// NG: エラーを握り潰す
try { ... } catch (e) {}

// OK: 必ずログに出す
try { ... } catch (e) {
  logger.error('Failed to process frame:', e);
}

// NG: メインスレッドで重い同期処理
const result = heavyQuantize(buffer); // メインスレッドをブロック

// OK: Worker Thread（piscina）に投げる
const result = await this.pool.run({ buffer, width, height });
```

---

## ビルド・実行方法

### Java Plugin のビルド

```bash
# リポジトリルートで
./gradlew shadowJar
# → build/libs/MapBrowser-x.x.x-all.jar が生成される
```

### browser-renderer の開発起動

```bash
cd browser-renderer

# 依存インストール（初回のみ）
pnpm install
pnpm exec playwright install chromium

# 開発モード（ファイル変更で自動再起動）
pnpm dev
# = tsx watch src/index.ts

# 本番ビルド
pnpm build
# = tsc → dist/ に出力

# 型チェックのみ
pnpm typecheck
```

### ローカルでのテスト手順

```
1. ./gradlew shadowJar でプラグインをビルド
2. build/libs/MapBrowser-*.jar を minecraft-server/plugins/ にコピー
3. PacketEvents.jar と AnvilGUI.jar も plugins/ にあることを確認
4. サーバー起動（初回は Chromium の自動ダウンロードで数分かかる）
5. ゲームに参加して /mb create 2 2 test で動作確認
```

---

## フェーズ状態（現在の開発フェーズ）

> **現在: Phase 3 実装中（最適化・安定化）**

- [x] Phase 1: 映像表示 MVP（IPC 基盤 + MapPacket 送信 + 10 FPS 表示）
- [x] Phase 2: インタラクション（クリック・URL 入力・操作アイテム）
- [ ] Phase 3: 最適化・安定化（デルタ圧縮・YouTube・権限・永続化）
- [ ] Phase 4: 音声・コンパニオン Mod
- [ ] Phase 5: Velocity 対応・API 公開

**現在は Phase 3 最適化タスクを優先して実装すること。**

---

## 質問・判断が必要な場合

以下の場合は実装を止めて確認を求めること:

1. IPC プロトコル（JSON項目またはバイナリヘッダ仕様）を追加・変更する場合
2. 新しい外部ライブラリを追加する場合
3. `config.yml` の構造を変更する場合
4. Minecraft の NMS に直接アクセスが必要に見える場合
5. フレームデータのフォーマットを変更する場合

---

## ファイルごとのルール
1. Java言語としての構文やインデントルールを厳密に守り、可読性の高いコードを書くこと
2. 1ファイルあたりの行数が700または800行を超える場合、ロジックごとになるべくファイルを分割して可読性の低下を防ぐこと
3. ファイルを新規作成、または既存ファイルのファイル名を変更する場合、ファイル名はなるべく短く、わかり易い名前にすること

---

## 参考リポジトリ

| リポジトリ | 参照目的 |
|-----------|---------|
| https://github.com/LOOHP/ImageFrame | MapPacket 送信・Floyd-Steinberg 実装・NMS abstraction 構造 |
| https://github.com/LOOHP/ImageFrameClient | Fabric Mod ↔ Plugin カスタムパケット設計（Phase 4） |
| https://github.com/CinemaMod/webdisplays | スクリーンエンティティ設計・レイキャスト座標変換 |
| https://github.com/retrooper/packetevents | PacketEvents API リファレンス |
| https://docs.papermc.io | Paper API リファレンス |
| https://playwright.dev/docs/api | Playwright API リファレンス |
