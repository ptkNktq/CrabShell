# CrabShell

Kotlin Multiplatform のダッシュボードアプリケーション。Ktor サーバー + Compose for Web (WASM) フロントエンド。

## 技術スタック

| カテゴリ | 技術 | バージョン |
|---|---|---|
| 言語 | Kotlin | 2.3.0 |
| UI | Compose Multiplatform | 1.10.0 |
| サーバー | Ktor (Netty) | 3.4.0 |
| シリアライゼーション | kotlinx-serialization-json | 1.8.1 |
| JDK | Eclipse Temurin | 21 |

## 構成

```
shared/         → 共有データモデル（JVM + WASM/JS）
server/         → Ktor サーバー（API + 静的ファイル配信）
web-frontend/   → Compose for Web（WASM）フロントエンド
```

## セットアップ

```bash
# サーバー起動（フロントエンドも自動ビルド）
./gradlew :server:run

# フロントエンドのみビルド
./gradlew :web-frontend:wasmJsBrowserDistribution
```

サーバーは `http://localhost:8080` で起動。

### Docker

```bash
docker compose up -d --build
```

## ブランチ戦略（GitHub Flow）

`main` ブランチを常にデプロイ可能な状態に保つシンプルなフローを採用。

1. `main` から機能ブランチを作成
2. ブランチ上でコミットを重ねる
3. Pull Request を作成してレビューを依頼
4. レビュー承認後、`main` にマージ
5. マージ後にブランチを削除

### ブランチ命名規則

`feat/`, `fix/`, `chore/`, `refactor/` + 簡潔な説明（例: `feat/websocket-realtime`）

### ルール

- `main` に直接 push しない — 常に PR 経由
- PR はマージ前にレビューを受ける
- マージ後にブランチを削除して整理する