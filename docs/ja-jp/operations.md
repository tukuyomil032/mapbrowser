# 運用ガイド (ja-jp)

## 日次確認

- サーバー起動時にプラグイン有効化ログを確認
- /mb admin status で IPC 接続状態を確認
- スクリーン数と描画更新を定期確認

## Phase4 音声連携の実機検証

1. `audio.companion-mod-enabled: true` を設定し、サーバーを再起動する
2. Companion Mod を導入したクライアントでサーバーへ接続する
3. 必要に応じて `audio.capture-mode: media-recorder` を設定し、音声を含む URL を `/mb open` で表示する
4. `/mb admin status` で audio diagnostics の delivered 値が増えていることを確認する
5. Companion Mod 導入クライアントでスクリーン近傍に移動し、音声再生を確認する

検証メモ:
Companion Mod を導入していないクライアントは、音声プラグインメッセージを再生できない。
`audio.test-opus-base64` を設定すると、テスト用の Opus データを送信できる。

## リリース手順

1. build.gradle.kts の version を更新
2. main ブランチへ push
3. GitHub Actions がタグ v<version> を新規作成
4. アーティファクトをビルドして Releases へ配置

## 配布物

- プラグイン jar
- browser-renderer dist アーカイブ

## バックアップ

- yaml モード: データフォルダ全体
- sqlite モード: mapbrowser.db

## ロールバック

- 旧バージョンの jar と renderer をセットで戻す
- 必要ならバックアップDBを復元
