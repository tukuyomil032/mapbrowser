# トラブルシュート (ja-jp)

## プラグインは起動するが映像が出ない

- browser-renderer/dist が存在するか
- ipc-port が一致しているか
- node-path が実行可能か
- Playwright の chromium が導入済みか

## IPC が切断される

- /mb admin status で状態確認
- サーバーログで再接続ログを確認
- localhost ポート競合の有無を確認

## URL が開けない

- allow-http が false で http URL を使っていないか
- blacklist に一致していないか
- block-local-network で拒否されていないか
- whitelist 有効時に許可対象に入っているか

## renderer が再起動ループ

- Node バージョン確認
- sharp/playwright の依存確認
- browser.max-restart-attempts の設定確認

## リリース自動化が失敗

- build.gradle.kts の version が通常の文字列か
- workflow に contents:write 権限があるか
- 既存タグとの衝突がないか
