# AGENTS.md — MapBrowser Companion Mod プロジェクト AI エージェント指示書

このファイルは Claude / Cursor / Copilot 等の AI コーディングエージェントが Companion Mod 実装で作業する際に必ず読むべき指示書です。

---

## プロジェクト概要

**MapBrowser Companion Mod** は Minecraft Java Edition クライアント側で動作し、MapBrowserサーバープラグインから送られる `mapbrowser:audio` ペイロードを受信して、Opusデコードおよび空間音声再生を行う Fabric Mod です。

### 連携対象

| 対象 | 役割 |
|------|------|
| MapBrowser Plugin | 音声ペイロード送信（Plugin Messaging） |
| Companion Mod | 受信・デコード・再生 |

関連要件は `docs/companion_mods/REQUIREMENTS.md` を正とすること。

---

## 最重要連携仕様

- チャンネル: `mapbrowser:audio`
- ペイロード順序（Big Endian）:
  1. `long` screenMostSigBits
  2. `long` screenLeastSigBits
  3. `int` sampleRate
  4. `int` opusFrameLength
  5. `byte[]` opusFrame

この順序・型を変更する場合、MapBrowserプラグイン側と同時更新すること。

---

## コーディング規約（Companion Mod）

### Java/Kotlin 側

- Java 21 互換を維持すること
- public API には必ずJavadocまたはKDocを付与すること
- 例外は握り潰さず、必ずログ出力すること
- 長時間動作を前提に、再生スレッドとキュー管理を明確に分離すること

### 共通

- 非同期処理とメインスレッド処理の境界を明確にすること
- デコード失敗時は音声のみ破棄し、クライアント全体に影響させないこと
- 既存のサーバープラグイン仕様に合わせること（独自拡張は後方互換を維持）

---

## 作業する前に確認すること

1. `docs/companion_mods/REQUIREMENTS.md` の該当セクションを読む
2. プロトコル差分が出る変更かを確認する
3. サーバー側 `PluginMessageAudioBridge` の現行実装と一致するか確認する

---

## 判断が必要な場合

以下の場合は実装を止めて確認を求めること:

1. 音声ペイロード構造を変更する場合
2. 新しい外部ライブラリを追加する場合
3. 既存のMapBrowserサーバー設定との互換性に影響する場合
4. 再生方式（OpenAL/ミキサ）を大きく変更する場合

---

## MapBrowser本体AGENTSからの継承（非技術・運用ルール）

以下は本体 `AGENTS.md` の運用規則を継承する。

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
- `build/`, `run/`はコミットしない

---

## フェーズ状態（Companion Mod）

> **現在: Phase A 準備中（要件定義・仕様固定）**

- [ ] Phase A: 受信基盤（Plugin Messaging + payload decode）
- [ ] Phase B: 再生基盤（Opus decode + PCM pipeline）
- [ ] Phase C: 空間化/運用（3D減衰 + diagnostics）
- [ ] Phase D: 品質保証（実機検証 + 配布）

---

## 参考リポジトリ

| リポジトリ | 参照目的 |
|-----------|---------|
| https://github.com/tukuyomil032/mapbrowser.git | このModと連携する大本のプラグインのリポジトリ |
| https://github.com/LOOHP/ImageFrameClient | Fabric Modとサーバープラグイン連携の実装参考 |
| https://github.com/FabricMC/fabric | Fabric API実装全般 |
| https://docs.papermc.io | サーバー側仕様の確認 |
