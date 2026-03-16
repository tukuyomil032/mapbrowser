# インストール (ja-jp)

## 前提

- Java 21
- Node.js 20 以上
- pnpm 10 以上
- Paper/Leaf 1.21 系サーバー

## 1. プラグインをビルド

```bash
./gradlew shadowJar
```

生成物:

- build/libs/MapBrowser-<version>-all.jar

## 2. renderer を準備

```bash
cd browser-renderer
pnpm install
pnpm exec playwright install chromium
pnpm run build
cd ..
```

## 3. サーバーへ配置

1. build/libs の jar を plugins に配置
2. renderer ディレクトリを設定値に合わせて配置
3. サーバー起動

## 4. 初期確認

- /mb create 2 2 test
- /mb open https://example.com

映像更新が確認できれば導入完了です。
