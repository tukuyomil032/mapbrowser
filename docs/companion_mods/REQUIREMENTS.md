# MapBrowserCompanion (MBC) 要件定義書

**バージョン**: 1.0.0-draft  
**更新日**: 2026-03-18  
**ステータス**: Draft for Implementation

---

## 1. このModが必要な理由

MapBrowserプラグインはサーバー側でブラウザ映像/音声を処理するが、Minecraftバニラクライアントには次の制約がある。

- Plugin Messaging経由の独自音声ペイロードを再生する機構がない
- 3D音響（スクリーン位置に応じた距離減衰）を独自実装できない
- 将来的な高品質表示拡張（HUD/同期補助）の受け口がない

このため、**MapBrowserCompanion（以下 MBC）** は「MapBrowserサーバーが送る音声データをクライアントで正しく復元・再生する」ための必須コンポーネントである。

### 1.1 製品方針（固定）

- Mod名: **MapBrowserCompanion**（略称: **MBC**）
- Modローダー: **Fabric のみ**
- 実装言語: **Kotlin**
- リポジトリ: MapBrowser本体とは分離した**別リポジトリ**

---

## 2. 目的とスコープ

### 2.1 目的

1. `mapbrowser:audio` チャンネルの音声ペイロードを受信する
2. 受信したOpusデータをデコードしPCMへ復元する
3. スクリーン座標に基づく3D空間音声として再生する
4. サーバー実装（MapBrowser）と互換性を維持する
5. Fabric + Kotlin の実装規約を固定し保守性を高める

### 2.2 スコープ内

- FabricクライアントMod（Minecraft 1.21系想定、Fabric専用）
- Plugin Messaging受信
- Opusデコード
- OpenALベースの空間音声再生
- 基本デバッグ表示（受信数・ドロップ数・遅延）

### 2.3 スコープ外（初版）

- サーバー側エンコード方式の全面変更
- UIテーマや映像レンダリングの置換
- Voice Chat MODとの高度統合（将来拡張）

---

## 3. 連携対象（プラグイン側）

Companion Modが依存するサーバー側実装:

- 音声送信: `src/main/java/com/tukuyomil032/mapbrowser/audio/PluginMessageAudioBridge.java`
- AUDIO_FRAME受信: `src/main/java/com/tukuyomil032/mapbrowser/ipc/BrowserIPCClient.java`
- 音声設定: `src/main/resources/config.yml`
- 運用診断（STATUS）: `src/main/java/com/tukuyomil032/mapbrowser/velocity/VelocityMessagingBridge.java`

### 3.1 サーバー側の実ペイロード構造

`PluginMessageAudioBridge#encode(...)` の構造（Big Endian）:

1. `long` screenMostSigBits
2. `long` screenLeastSigBits
3. `int` sampleRate
4. `int` opusFrameLength
5. `byte[]` opusFrame

Companion Modはこの順序で厳密にデコードすること。

---

## 4. 通信プロトコル要件

### 4.1 チャンネル

- チャンネル名: `mapbrowser:audio`
- 受信方向: Server -> Client

### 4.2 互換要件

- `sampleRate <= 0` または `opusFrameLength <= 0` は破棄
- `opusFrameLength` が残りバイトを超える場合は破棄
- 破棄時はデバッグカウンタを増加

### 4.3 エラー耐性

- 不正ペイロード1件で再生ループを停止しない
- 例外は握り潰さずログに記録
- 一定時間無音であっても再生スレッドを健全維持

---

## 5. 技術要件

### 5.1 推奨スタック

- Minecraft/Fabric: 1.21.x
- Java: 21
- ローダー: Fabric Loader
- ビルド: Gradle + Loom
- 言語: Kotlin
- Opus: JNI/Javaバインディングいずれか
- 音声出力: OpenAL（Minecraftクライアント標準音声基盤との整合）

### 5.2 言語

- Kotlin 固定
- Java補助コードを使用する場合でも、公開API層はKotlinを基準に設計すること

### 5.3 パフォーマンス

- 受信->再生まで平均遅延: 250ms以下を目標
- デコード失敗率: 1%未満
- クライアントFPSへの影響: 5%未満（目標）

---

## 6. クライアント内部アーキテクチャ

## 6.1 コンポーネント

1. `AudioPayloadListener`
- Plugin Messaging受信
- バイト列バリデーション

2. `OpusDecoderService`
- Opus -> PCM変換
- サンプルレート変換（必要時）

3. `SpatialAudioMixer`
- screenIdごとの音源管理
- プレイヤー位置との差分でゲイン計算

4. `AudioDiagnostics`
- 受信数、破棄数、再生中screen数

### 6.2 データフロー

1. Serverから `mapbrowser:audio` 受信
2. payloadを `UUID + sampleRate + opusBytes` に復元
3. screenIdに紐づく音源へキュー投入
4. デコード後PCMをOpenALへ供給
5. プレイヤー座標から距離減衰を適用して再生

---

## 7. サーバー連携仕様（重要）

### 7.1 サーバーで既に実装済みの前提

- Node renderer -> Java plugin: `AUDIO_FRAME` 経路が存在
- Java plugin -> Client: `mapbrowser:audio` 送信が存在
- サーバーは `audio.max-distance` で対象プレイヤーを絞り込む

### 7.2 Companion Mod側で実装すべきこと

- `screenId` ごとの再生チャネル管理
- 短いバッファリング（ジッタ吸収）
- 同一screenの古いキュー破棄ポリシー
- World切替時の音源クリーンアップ

---

## 8. 参照コード（プラグイン側）

### 8.1 サーバー送信コード（実装済み）

```java
// PluginMessageAudioBridge#encode

dos.writeLong(screenId.getMostSignificantBits());
dos.writeLong(screenId.getLeastSignificantBits());
dos.writeInt(sampleRate);
dos.writeInt(opusFrame.length);
dos.write(opusFrame);
```

### 8.2 サーバー受信コード（AUDIO_FRAME）

```java
// BrowserIPCClient#handleAudioFrame

final UUID screenId = UUID.fromString(obj.get("screenId").getAsString());
final int sampleRate = obj.has("sampleRate") ? obj.get("sampleRate").getAsInt() : 48000;
final byte[] opus = Base64.getDecoder().decode(obj.get("data").getAsString().getBytes(StandardCharsets.UTF_8));
if (sampleRate <= 0 || opus.length == 0) {
    return;
}
plugin.getAudioBridge().publishFrame(screenId, opus, sampleRate);
```

### 8.3 Companion Mod側デコード例（実装サンプル）

```java
try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
    long msb = in.readLong();
    long lsb = in.readLong();
    UUID screenId = new UUID(msb, lsb);

    int sampleRate = in.readInt();
    int opusLength = in.readInt();
    if (sampleRate <= 0 || opusLength <= 0 || opusLength > in.available()) {
        return; // invalid
    }

    byte[] opus = new byte[opusLength];
    in.readFully(opus);
    decoder.enqueue(screenId, sampleRate, opus);
}
```

---

## 9. 設定要件（Companion Mod）

推奨クライアント設定（初版）:

- `enabled`: true/false
- `maxConcurrentScreens`: 同時再生screen上限（例: 4）
- `bufferMillis`: ジッタ吸収バッファ（例: 80-160ms）
- `masterGain`: 全体音量
- `debugOverlay`: 診断表示

---

## 10. テスト要件

### 10.1 機能テスト

1. 音声付きページで受信数が増える
2. screenから離れると聞こえなくなる
3. 複数screenで混線しない
4. world移動時に音源リークしない

### 10.2 異常系

1. 不正payloadを無害化できる
2. 大量連続受信時でもクライアントが固まらない
3. サーバー再起動後に再接続で復旧できる

### 10.3 互換性

- Companion Mod未導入クライアントで致命的不具合が発生しない
- 導入クライアントのみ音声機能が有効化される

---

## 11. リリース受け入れ基準

MapBrowserCompanion 初版の受け入れ条件:

- `mapbrowser:audio` を安定受信できる
- Opusデコードと3D再生が実機で確認できる
- 30分連続再生で重大リーク/クラッシュがない
- 既存MapBrowserサーバー機能に回帰を生まない
- Fabric以外のローダー依存が混入していない

---

## 12. 実装フェーズ（Companion Mod側）

### Phase A: 受信基盤
- Fabric Mod雛形
- Plugin Messaging受信
- payloadデコード

### Phase B: 再生基盤
- Opusデコード
- PCMキュー
- OpenAL再生

### Phase C: 空間化/運用
- screenIdベースの音源管理
- 距離減衰
- diagnostics overlay

### Phase D: 品質保証
- 長時間試験
- 遅延/ドロップ測定
- 配布パッケージ化

---

## 13. 補足

- 本書は「MapBrowser本体のPhase5公開準備」と同時に運用するCompanion Mod要件である。
- サーバー側の最終リリース判定では、本書のテスト要件を最低限満たすことが推奨される。
- 実装リポジトリはMapBrowser本体と分離し、CI/CD・バージョニングを独立運用する。
