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
gradle :server:run

# フロントエンドのみビルド
gradle :web-frontend:wasmJsBrowserDistribution
```

サーバーは `http://localhost:8080` で起動。

### Docker

```bash
docker compose up -d --build
```

## ブランチ戦略

- `main` に直接 push しない — 常に PR 経由
- ブランチは `main` から切る
- PR は Squash merge でマージ
- マージ後にブランチ削除

### ブランチ命名規則

`feat/`, `fix/`, `chore/`, `refactor/` + 簡潔な説明（例: `feat/websocket-realtime`）

## 並行開発（git worktree）

複数の機能を並行開発する場合は `git worktree` を使い、ブランチごとに別ディレクトリで作業する。

```bash
# worktree 作成（メインリポジトリの親ディレクトリに配置）
git worktree add ../CrabShell-<task> -b feat/<task>

# 例: テーマ更新と認証機能を並行開発
git worktree add ../CrabShell-theme -b feat/theme-update
git worktree add ../CrabShell-auth -b feat/firebase-auth

# 各 worktree で独立してビルド・起動
cd ../CrabShell-theme && docker compose up -d --build

# 作業完了後に削除
git worktree remove ../CrabShell-theme
```

これにより、各作業ディレクトリでブランチが固定され、Claude Code 等による並行作業時のブランチ切り替え事故を防げる。