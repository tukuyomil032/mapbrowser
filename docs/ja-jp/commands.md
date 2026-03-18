# コマンド一覧 (ja-jp)

ベースコマンド:

- /mapbrowser
- /mb

## 画面管理

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

## メニュー

- /mb menu
- /mb gui

## ブラウザ操作

- /mb open <url>
- /mb type <text>
- /mb back
- /mb forward
- /mb reload
- /mb fps <value>

## アイテム付与

- /mb give <pointer-left|pointer-right|pointer|back|forward|reload|url-bar|text-input|text-delete|text-enter|scroll|scroll-up|scroll-down>

## 設定

- /mb config simulate_particle <end_rod|flame>
- /mb config language <en|ja>

## 管理系

- /mb admin status
- /mb admin deps
- /mb admin reload
- /mb admin perf [screen-id|screen-name|latest]
- /mb admin perfbench <秒>
- /mb admin stop <screenId>

## 注意

- 多くのコマンドはプレイヤー実行前提です。
- 画面引数を省略した場合は、選択中スクリーンが対象です。
- タイル指定は `all` / `odd` / `even`、座標 `x-y`、矩形 `x1-y1:x2-y2`、連番範囲 `n..m` に対応します。
- 例: `all`, `odd`, `1-2`, `1-1:3-2`, `1`, `1..3`, `1,4,6..8`。
- URL はセキュリティ設定で検証されます。
