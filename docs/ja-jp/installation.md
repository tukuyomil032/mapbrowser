# インストールと動作確認 (ja-jp)

このプラグインは **Java プラグイン本体 + Node.js renderer** の 2 つで動きます。  
そのため、`MapBrowser-*.jar` を `plugins/` に置くだけでは動きません。

## 0. まず理解しておくこと

- Java 側: Minecraft サーバープラグイン
- Node 側: `browser-renderer` (Playwright/Chromium で画面を生成)
- 通信: `127.0.0.1:25600` の WebSocket

`plugins/MapBrowser/` フォルダが作られない場合は、ほぼ確実にプラグインが `onEnable` 前に読み込み失敗しています。

---

## A. 開発者向け: run-task で即テストする手順 (推奨)

このリポジトリには `xyz.jpenilla.run-paper` を導入済みです。  
`./gradlew runServer` で Paper を起動でき、毎回手動で jar をコピーする必要がありません。

### 1) 必要環境

- Java 21
- Node.js 20+
- `bun` または `pnpm`
- ネットワーク接続 (初回は Paper/依存のダウンロードが発生)

### 2) renderer 依存をインストール

`browser-renderer` で依存を入れます。

```bash
cd browser-renderer
bun install
# bun を使わない場合: pnpm install
```

### 3) Chromium をインストール

```bash
cd browser-renderer
bunx playwright install chromium
# bunx を使わない場合: pnpm exec playwright install chromium
```

### 4) renderer をビルド

```bash
cd browser-renderer
bun run build
# bun を使わない場合: pnpm run build
```

### 5) run-task で Paper を起動

```bash
./gradlew runServer
```

起動先ディレクトリは `run/` です。

### 6) 初回起動後に config を調整

`run/plugins/MapBrowser/config.yml` を開いて、`browser.renderer-dir` を実在パスに合わせます。

例:

```yaml
browser:
	node-path: "node"
	renderer-dir: "/absolute/path/to/mapbrowser/browser-renderer"
```

重要:

- `renderer-dir` は **dist と node_modules があるディレクトリ** を指定
- 相対パスだとサーバー起動ディレクトリ基準になるため、初回は絶対パス推奨

### 7) サーバーを再起動

`Ctrl + C` で停止して、再度:

```bash
./gradlew runServer
```

### 8) ゲーム内で動作確認

1. `/mb create 2 2 test`
2. `/mb open https://example.com`
3. ページがマップに描画されることを確認

必要なら:

- `/mb fps 10`
- `/mb admin status`

---

## B. サーバー運用者向け: 配布物から導入する手順

### 1) リリース資材を取得

- `MapBrowser-<version>-all.jar`
- `browser-renderer-dist-<version>.tar.gz` (または同等の renderer 配布ディレクトリ)

### 2) プラグイン本体を配置

- `MapBrowser-<version>-all.jar` を `plugins/` に置く

### 3) renderer を配置

任意の場所に展開し、最終的に次が揃っていることを確認:

- `dist/`
- `package.json`
- `node_modules/` (後述インストールで作成)

### 4) renderer 依存をインストール

```bash
cd <renderer-dir>
bun install --production
# bun を使わない場合: pnpm install --prod
```

### 5) Chromium をインストール

```bash
cd <renderer-dir>
bunx playwright install chromium
# bunx を使わない場合: pnpm exec playwright install chromium
```

### 6) MapBrowser の設定を合わせる

`plugins/MapBrowser/config.yml`:

```yaml
browser:
	node-path: "node"
	renderer-dir: "/absolute/path/to/renderer-dir"
	ipc-port: 25600
```

### 7) サーバー起動

- ログに `MapBrowser enabled!`
- その後 `browser-renderer is READY`

が出れば基本準備完了です。

---

## C. よくある失敗ポイント

### 1) プラグインが読み込まれず、plugins/MapBrowser が作られない

- `paper-plugin.yml` の不正
- Java バージョン違い
- 依存クラスのロード失敗

今回のリポジトリでは `paper-plugin.yml` のメタ定義を修正済みです。

### 2) jar は読み込まれたが画面が出ない

- `browser.renderer-dir` が誤っている
- `browser-renderer/dist` がない
- `node_modules` がない
- `playwright install chromium` 未実行

### 3) Node が見つからない

- `node-path` を絶対パスで指定 (例: `/opt/homebrew/bin/node`)

---

## D. 開発ループ最適化の基本コマンド

### Java 側だけ変更したとき

```bash
./gradlew runServer
```

run-task が最新 jar を使って起動します。

### renderer 側を変更したとき

```bash
cd browser-renderer
bun run build
# bun を使わない場合: pnpm run build
cd ..
./gradlew runServer
```

必要に応じて renderer の再ビルドだけ挟めば、手動 jar コピー運用よりかなり短いサイクルになります。
