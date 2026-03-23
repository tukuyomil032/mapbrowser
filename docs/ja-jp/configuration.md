# 設定リファレンス (ja-jp)

対象ファイル: src/main/resources/config.yml

## browser

- node-path: Node 実行パス
- renderer-dir: renderer 配置先
- ipc-port: Java と Node の通信用ポート
- auto-restart: renderer 自動再起動
- max-restart-attempts: 再起動上限
- restart-delay: 再起動待機秒

## screen

- default-fps: 既定FPS
- max-fps: 最大FPS
- max-width / max-height: 画面サイズ上限
- max-screens-per-world: ワールド毎上限
- render-distance: 送信距離
- tps-degrade-enabled: TPS低下時の自動品質ダウングレード有効化
- tps-threshold-medium / tps-threshold-low: 段階的な間引き開始TPS閾値
- tps-interval-multiplier-medium / tps-interval-multiplier-low: 各閾値で描画間隔に掛ける倍率

## security

- allow-http: HTTP の許可
- block-local-network: ローカル/プライベート帯へのアクセス遮断
- url-whitelist: 許可リスト
- url-blacklist: 拒否リスト

## youtube

- use-ytdlp
- ytdlp-path

## audio

- companion-mod-enabled
- channel
- capture-mode: `none` または `media-recorder`
- media-recorder-timeslice-ms: MediaRecorder のチャンク間隔(ms)
- sample-rate: Opus フレームのサンプルレート
- frame-interval-ms: テスト音声フレーム送信間隔(ms)
- test-opus-base64: 設定時にNode側でテスト用Opusフレームを定期送信
- max-distance: 音声転送の距離制限

## items

- pointer
- pointer-left
- pointer-right
- back
- forward
- reload
- url-bar
- text-input
- text-delete
- text-enter
- scroll
- scroll-up
- scroll-down

## ui

- simulate-particle: 操作フィードバック用パーティクル (`end_rod` または `flame`)
- language: ロケール文字列（推奨: `en`, `ja`, `ja-JP`）

## ローカライズ辞書

メッセージ辞書は `src/main/resources` 配下の YAML から読み込みます。

- `messages_en.yml` / `messages_ja.yml`（基本メッセージ）
- `messages_input_en.yml` / `messages_input_ja.yml`（入力/Anvil系）
- `messages_admin_en.yml` / `messages_admin_ja.yml`（admin/perf/status系）
- `messages_keys_en.yml` / `messages_keys_ja.yml`（キー方式UI文言）

実行時の言語切替は `ui.language` で制御します。
ロケールのフォールバック順は `ja-JP -> ja -> en` です。

### キー命名規約

- `command.<group>.<name>`: `/mb` コマンド出力文言
- `input.<group>.<name>`: 入力/Anvil操作フロー文言
- `common.<name>`: 複数メッセージで再利用する短い共通トークン
- プレースホルダーは `{screen}`, `{fps}`, `{url}`, `{id}` の形式で統一
- `messages_keys_en.yml` と `messages_keys_ja.yml` は同一キー集合を維持

## storage

- yaml
- sqlite

## debug

- 追加ログの有効化
