# 🚀 MapBrowser Ultimate Optimization Spec（完全版）

---

# 🎯 目標

* Map描画は維持（バニラ準拠）
* YouTubeを「視認可能な動画」にする
* CPU / メモリ / 通信 全最適化

---

# 🗂 実装フェーズとToDo（運用管理）

このセクションを運用上のタスクボードとして扱い、実装時に状態を更新する。

## Phase A: Renderer Core（優先・進行中）

- [x] A1. Browser最適化（Chromiumフラグ / キャプチャ条件の見直し）
- [x] A2. 近似 + グレースケール差分の導入
- [x] A3. タイル分割（16x16）による差分抽出
- [x] A4. 変更量しきい値でSKIP判定を強化
- [x] A5. 優先度レンダリング（中央優先）

## Phase B: Throughput Control

- [x] B1. 動的FPS制御の閾値運用を明確化
- [x] B2. 動画モード（YouTube判定）でFPSポリシー切替
- [x] B3. 静止検出（連続無変化）と低負荷化
- [x] B4. バッチ送信（複数差分の一括送出）

## Phase C: Java Side Render Efficiency

- [x] C1. 視線制御（Line of Sight相当）
- [x] C2. 距離ベース品質制御（遠距離時の送信FPS抑制）
- [x] C3. ダブルバッファ運用見直し（既存キャッシュとの統合）

## Phase D: Optional / Validation

- [x] D1. ぼかし（ノイズ低減）
- [x] D2. 圧縮（任意機能として評価）
- [ ] D3. 計測（CPU / メモリ / 通信）と閾値再調整

## 実装ログ

- 2026-03-23: タスクボード初期化（本ドキュメントにフェーズ運用を追加）。
- 2026-03-23: Phase A/B を実装。タイル差分・中心優先・動画モードFPS・静止検出・DELTAバッチ送信を反映。
- 2026-03-23: Phase C の一部を実装。視線制御（設定で有効化）と距離ベース描画間引きを追加。
- 2026-03-23: Phase C3 を実装。FrameRendererをfront/backバッファへ統合し、FULL/DELTA適用時にswapする構成へ変更。
- 2026-03-23: Phase D1 を実装。差分判定前に3x3ボックスブラーを追加し、ノイズ起因の更新を低減。
- 2026-03-23: Phase D2 を実装。WebSocket per-message deflateを有効化し、バイナリ送信で任意圧縮を利用可能にした。

---

# 🧠 全体アーキテクチャ

```text
Browser (低解像度固定)
   ↓
Frame取得
   ↓
前処理（ぼかし・縮小）
   ↓
差分検出（近似 + グレースケール）
   ↓
タイル分割
   ↓
変更量チェック（スキップ判定）
   ↓
優先度付け（中央優先）
   ↓
圧縮（任意）
   ↓
バッチ送信
   ↓
Map描画
```

---

# 🥇 1. ブラウザ最適化（必須）

```ts
browser.setSize(128, 128);
```

### Chromiumフラグ

```ts
[
 "--disable-gpu-vsync",
 "--disable-frame-rate-limit",
 "--enable-gpu"
]
```

---

# 🥇 2. 近似 + グレースケール差分

```ts
function luma(rgb: number): number {
  const r = (rgb >> 16) & 0xff;
  const g = (rgb >> 8) & 0xff;
  const b = rgb & 0xff;
  return 0.299*r + 0.587*g + 0.114*b;
}

const THRESHOLD = 5;

if (Math.abs(luma(curr) - luma(prev)) < THRESHOLD) continue;
```

---

# 🥇 3. タイル分割（完全版）

```ts
const TILE = 16;

function getTiles(curr, prev, w, h) {
  const tiles = [];

  for (let ty = 0; ty < h; ty += TILE) {
    for (let tx = 0; tx < w; tx += TILE) {

      let changed = 0;

      for (let y = 0; y < TILE; y++) {
        for (let x = 0; x < TILE; x++) {
          const i = (ty+y)*w + (tx+x);

          if (Math.abs(curr[i] - prev[i]) > 3) {
            changed++;
          }
        }
      }

      if (changed > 10) {
        tiles.push({x:tx,y:ty});
      }
    }
  }

  return tiles;
}
```

---

# 🥇 4. フレームスキップ

```ts
if (changedPixels < 30) return SKIP;
```

---

# 🥇 5. 動的FPS制御

```ts
let fps = 10;

if (processTime > 50) fps = 6;
if (processTime > 80) fps = 4;
```

---

# 🥇 6. 動画モード

```ts
const isVideo =
  url.includes("youtube") ||
  url.includes("youtu.be");

const FPS = isVideo ? 10 : 3;
```

---

# 🥇 7. MapColor LUT（O(1)化）

## 初期化

```java
private static final byte[] LUT = new byte[1 << 24];

static {
  for (int i = 0; i < LUT.length; i++) {
    LUT[i] = slowMatch(i);
  }
}
```

## 使用

```java
public static byte matchColor(int rgb) {
  return LUT[rgb & 0xFFFFFF];
}
```

---

# 🥇 8. バッチ送信

```java
List<Update> queue = new ArrayList<>();

void flush() {
  for (Update u : queue) send(u);
  queue.clear();
}
```

---

# 🥇 9. 優先度レンダリング（中央優先）

```ts
function priority(x,y,w,h){
  const cx = w/2;
  const cy = h/2;
  return Math.hypot(x-cx,y-cy);
}

tiles.sort((a,b)=>priority(a.x,a.y)-priority(b.x,b.y));
```

---

# 🥈 10. ぼかし（ノイズ削減）

```ts
function blur(frame,w,h){
  const out = new Uint32Array(frame.length);

  for(let y=1;y<h-1;y++){
    for(let x=1;x<w-1;x++){
      let sum=0;

      for(let dy=-1;dy<=1;dy++){
        for(let dx=-1;dx<=1;dx++){
          sum+=frame[(y+dy)*w+(x+dx)];
        }
      }

      out[y*w+x]=sum/9;
    }
  }

  return out;
}
```

---

# 🥈 11. 静止検出

```ts
if (noChangeFrames > 20) {
  pauseRendering();
}
```

---

# 🥈 12. 視線制御（Java）

```java
if (!player.hasLineOfSight(screen)) return;
```

---

# 🥈 13. 距離ベース品質

```java
double dist = player.getLocation().distance(screen);

if (dist > 20) fps = 4;
```

---

# 🥈 14. 圧縮（任意）

```ts
import zlib from "zlib";

const compressed = zlib.deflateSync(buffer);
```

---

# 🥈 15. ダブルバッファ

```java
byte[] front;
byte[] back;

swap(front, back);
```

---

# 🧠 最終パイプライン

```text
Frame取得
 ↓
ぼかし
 ↓
グレースケール差分
 ↓
タイル分割
 ↓
変更少なければスキップ
 ↓
優先度付け
 ↓
バッチ送信
 ↓
Map描画
```

---

# 💥 到達性能

## Before

* カクカク
* CPU爆死
* FULL_FRAME連発

## After

* 10fpsで動画っぽく再生
* CPU大幅削減
* 通信量激減

---

# 🏁 結論

👉 Map描画のまま“動画として成立する”限界構成

---

# 😏 一言

👉 「全部描くな。必要なとこだけ描け」

---
