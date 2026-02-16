# CrabShell

Kotlin Multiplatform のダッシュボードアプリケーション。Ktor サーバー + Compose for Web (WASM) フロントエンド。

## 技術スタック

| カテゴリ | 技術 | バージョン |
|---|---|---|
| 言語 | Kotlin | 2.3.0 |
| UI | Compose Multiplatform | 1.10.0 |
| サーバー | Ktor (Netty) | 3.4.0 |
| DI | Koin | 4.2.0-RC1 |
| 認証 | Firebase Admin / Firebase JS SDK | 9.4.3 |
| ViewModel | Lifecycle ViewModel Compose | 2.9.6 |
| シリアライゼーション | kotlinx-serialization-json | 1.8.1 |
| JDK | Eclipse Temurin | 21 |

## アーキテクチャ

MVVM パターンで関心事を分離。ViewModel がビジネスロジック・状態管理を担当し、Screen (Composable) は UI 描画のみ。

モジュールは4層に分かれる:

| 層 | モジュール | 説明 |
|---|---|---|
| **shared** | `shared` | 共有データモデル（JVM + wasmJs） |
| **core** | `core:auth`, `core:network`, `core:ui` | 認証・通信・UI 基盤 |
| **feature** | `feature:auth`, `feature:dashboard`, `feature:feeding`, `feature:money`, `feature:payment`, `feature:settings` | 各画面の ViewModel + Screen |
| **app** | `web-frontend`, `server` | アプリシェル / API サーバー |

### 依存関係図

```mermaid
graph TD
  subgraph shared
    S[shared]
  end

  subgraph core
    CA[core:auth]
    CN[core:network]
    CU[core:ui]
  end

  subgraph feature
    FA[feature:auth]
    FD[feature:dashboard]
    FF[feature:feeding]
    FM[feature:money]
    FP[feature:payment]
    FS[feature:settings]
  end

  subgraph app
    WF[web-frontend]
    SV[server]
  end

  CA --> S
  CN --> CA
  CN --> S
  CU --> S

  FA --> CA
  FA --> CU
  FD --> CN
  FD --> CU
  FD --> S
  FF --> CN
  FF --> CU
  FF --> S
  FM --> CN
  FM --> CU
  FM --> S
  FP --> CA
  FP --> CN
  FP --> CU
  FP --> S
  FS --> CA
  FS --> CN
  FS --> CU
  FS --> S

  WF --> CA
  WF --> CN
  WF --> CU
  WF --> FA
  WF --> FD
  WF --> FF
  WF --> FM
  WF --> FP
  WF --> FS

  SV --> S
```

## 構成

```
shared/              → 共有データモデル（JVM + wasmJs）
server/              → Ktor サーバー（API + 静的ファイル配信）
core/auth/           → Firebase 認証・AuthState 管理
core/network/        → 認証付き HTTP クライアント + Repository
core/ui/             → テーマ・共通 UI コンポーネント
feature/auth/        → ログイン画面
feature/dashboard/   → ダッシュボード画面
feature/feeding/     → ごはん記録画面
feature/money/       → 支出管理画面（管理者向け）
feature/payment/     → 支払い画面（ユーザー向け）
feature/settings/    → 設定画面
web-frontend/        → アプリシェル（ルーティング・レイアウト）
```

## セットアップ

```bash
# サーバー起動（フロントエンドも自動ビルド）
./gradlew :server:run

# フロントエンドのみビルド
./gradlew :web-frontend:wasmJsBrowserDistribution
```

サーバーは `http://localhost:8080` で起動。

## Docker

### 本番用

```bash
docker compose up -d --build    # ビルド＆バックグラウンド起動
docker compose down              # 停止
docker compose logs -f           # ログ確認
```

### 開発用

```bash
docker compose -f docker-compose.dev.yml up -d --build   # 初回起動
docker compose -f docker-compose.dev.yml up -d            # 2回目以降
docker compose -f docker-compose.dev.yml down              # 停止
docker compose -f docker-compose.dev.yml logs -f           # ログ確認
```

## Lint

ktlint を全サブプロジェクトに適用済み。

```bash
# 自動フォーマット
./gradlew ktlintFormat

# チェックのみ
./gradlew ktlintCheck
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
