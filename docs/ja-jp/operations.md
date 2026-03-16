# 運用ガイド (ja-jp)

## 日次確認

- サーバー起動時にプラグイン有効化ログを確認
- /mb admin status で IPC 接続状態を確認
- スクリーン数と描画更新を定期確認

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
