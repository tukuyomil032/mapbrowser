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

## items

- pointer
- back
- forward
- reload
- url-bar
- scroll-up
- scroll-down

## storage

- yaml
- sqlite

## debug

- 追加ログの有効化
