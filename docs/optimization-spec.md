# 🚀 MapBrowser 動画最適化 最終仕様書（v2 完成版）

---

# 🎯 目的

* Map描画のまま動画を“視認可能レベル”にする
* 差分処理を「静止画」→「動画向け」に進化させる
* CPU / 通信 / TPS の最適化

---

# 🗂 実装フェーズ / ToDo（2026-03-25 更新）

## Phase 1: Node.js 動画最適化

- [x] タイル差分判定の閾値をシーン別（静止/動画）に分離
- [x] `changedPixels / totalPixels` の割合ベース SKIP 判定を導入
- [x] 動画URL判定と連動した動画モード切替を `PageController` から接続
- [x] 隣接タイル結合で DELTA 送信件数を削減

## Phase 2: Java 側色変換最適化

- [x] `MapColorUtil` に 24bit LUT (`byte[1<<24]`) を導入
- [x] `toMapColor(int rgb)` を追加し O(1) 変換を提供

## Phase 3: 長大クラス分割（可読性 / 保守性）

- [x] `InputHandler` を 1000行未満へ分割
- [x] `MapBrowserCommand` を 1000行未満へ分割
- [x] コマンド・入力の補助クラスを追加し責務分離

## Phase 4: ドキュメント同期

- [x] `AGENTS.md` の構成図を実装に追従
- [x] `docs/REQUIREMENTS.md` に分割構造と最新最適化を反映
- [x] `docs/ja-jp/architecture.md` / `docs/en-us/architecture.md` に詳細追記

## この仕様にない追加改善（今回実装）

- [x] DELTA対象タイルの横方向マージ（通信量・IPC回数の削減）
- [x] コマンドUI描画/ローカライズ層の分離（将来拡張しやすい構造へ）

---

# 🧠 現状評価

| 項目    | 状態                 |
| ----- | ------------------ |
| 基本設計  | ◎                  |
| 差分処理  | ○                  |
| 動画最適化 | △                  |
| 完成度   | **75% → 100%を目指す** |

---

# 🚨 本質的な課題

## ❗ 問題の正体

👉 現在は「画像差分アルゴリズム」

## 🎯 目標

👉 「動画向けストリーミング差分」に変える

---

# 🥇 最重要改善（Sランク）

---

## 1. タイル差分アルゴリズム（完全版）

### 🔥 コンセプト

* 1ピクセル差分は無視
* タイル単位で“変化量”を評価

---

### ✅ 実装

```ts
// FrameProcessor.ts

const TILE_SIZE = 16;
const DIFF_THRESHOLD = 8;
const TILE_CHANGE_THRESHOLD = 10;

function luma(rgb: number): number {
  const r = (rgb >> 16) & 0xff;
  const g = (rgb >> 8) & 0xff;
  const b = rgb & 0xff;
  return 0.299*r + 0.587*g + 0.114*b;
}

export function getChangedTiles(curr: Uint32Array, prev: Uint32Array, w: number, h: number) {
  const tiles = [];

  for (let ty = 0; ty < h; ty += TILE_SIZE) {
    for (let tx = 0; tx < w; tx += TILE_SIZE) {

      let strongChanges = 0;

      for (let y = 0; y < TILE_SIZE; y++) {
        for (let x = 0; x < TILE_SIZE; x++) {

          const px = tx + x;
          const py = ty + y;
          if (px >= w || py >= h) continue;

          const i = py * w + px;

          const diff = Math.abs(luma(curr[i]) - luma(prev[i]));

          if (diff > DIFF_THRESHOLD) strongChanges++;
        }
      }

      if (strongChanges > TILE_CHANGE_THRESHOLD) {
        tiles.push({ x: tx, y: ty, w: TILE_SIZE, h: TILE_SIZE });
      }
    }
  }

  return tiles;
}
```

---

## 2. 動画モード（アルゴリズム分離）

```ts
function getVideoConfig(isVideo: boolean) {
  if (!isVideo) {
    return {
      diffThreshold: 5,
      tileThreshold: 5,
      skipRatio: 0.005,
      fps: 3
    };
  }

  return {
    diffThreshold: 8,
    tileThreshold: 10,
    skipRatio: 0.02,
    fps: 8
  };
}
```

---

## 3. フレームスキップ（割合ベース）

```ts
const changeRatio = changedPixels / totalPixels;

if (changeRatio < config.skipRatio) {
  return { type: "SKIP" };
}
```

---

## 4. MapColor LUT（O(1)化）

```java
// MapColorUtil.java

private static final byte[] LUT = new byte[1 << 24];

static {
    for (int i = 0; i < LUT.length; i++) {
        LUT[i] = slowMatch(i);
    }
}

public static byte matchColor(int rgb) {
    return LUT[rgb & 0xFFFFFF];
}
```

---

# 🥈 中核改善（Aランク）

---

## 5. ぼかし処理（ノイズ削減）

```ts
function blur(frame: Uint32Array, w: number, h: number) {
  const out = new Uint32Array(frame.length);

  for (let y = 1; y < h - 1; y++) {
    for (let x = 1; x < w - 1; x++) {

      let sum = 0;

      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          sum += frame[(y + dy) * w + (x + dx)];
        }
      }

      out[y * w + x] = sum / 9;
    }
  }

  return out;
}
```

👉 差分前に適用する

---

## 6. バッチ送信

```java
// FrameRenderer.java

private final List<Update> queue = new ArrayList<>();
private long lastFlush = 0;

public void tick() {
    long now = System.currentTimeMillis();

    if (now - lastFlush > 50) {
        for (Update u : queue) send(u);
        queue.clear();
        lastFlush = now;
    }
}
```

---

## 7. 中央優先レンダリング

```ts
function sortTiles(tiles, w, h) {
  const cx = w / 2;
  const cy = h / 2;

  return tiles.sort((a, b) => {
    const da = Math.hypot(a.x - cx, a.y - cy);
    const db = Math.hypot(b.x - cx, b.y - cy);
    return da - db;
  });
}
```

---

# 🥉 仕上げ（Bランク）

---

## 8. 動的FPS制御

```ts
let fps = config.fps;

if (processTime > 80) fps = 4;
else if (processTime > 50) fps = 6;
```

---

## 9. 静止検出

```ts
if (changedPixels === 0) {
  noChangeFrames++;
} else {
  noChangeFrames = 0;
}

if (noChangeFrames > 20) {
  return { type: "PAUSE" };
}
```

---

# 🧠 最終パイプライン

```text
Frame取得
 ↓
ぼかし
 ↓
動画モード設定
 ↓
差分（グレースケール + 閾値）
 ↓
タイル分割
 ↓
スキップ判定
 ↓
優先度ソート
 ↓
バッチ送信
 ↓
Map描画
```

---

# 💥 完成後の状態

## Before

* カクカク
* FULL_FRAME多発
* CPU負荷高

## After

* 低FPSでも動画として成立
* 差分主体
* 軽量動作

---

# 🧠 設計思想（重要）

* 正確な描画より「視認性」
* 全部描かない
* “変わったところだけ”描く

---

# 🏁 完了条件

* FULL_FRAMEがほぼ発生しない
* CPU使用率が安定
* YouTubeが「動画として見える

---
