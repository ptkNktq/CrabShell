# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CrabShell is a Kotlin Multiplatform dashboard application with a Ktor server backend and a Compose for Web (WebAssembly) frontend. The shared module contains data models used by both.

## Build Commands

```bash
# Run the server (includes building the WASM frontend automatically)
./gradlew :server:run

# Build the WASM frontend only
./gradlew :web-frontend:wasmJsBrowserDistribution

# Build the server (also triggers frontend build via copyWasmFrontend task)
./gradlew :server:build

# Build the shared library
./gradlew :shared:build
```

The server listens on `0.0.0.0:8080`. Building the server automatically copies the compiled WASM frontend into `server/build/resources/main/static/` so the server serves both API and UI.

## Development (Split Mode)

フロントエンドとサーバーを分離起動し、UI 変更の反映を高速化する開発モード。フルビルド（約5分）に対し、インクリメンタルビルド（数十秒）で変更を確認できる。

```bash
# Terminal 1: API サーバー（fat JAR をビルドして直接起動）
./gradlew :server:buildFatJar -PskipFrontend && \
  WEBAUTHN_RP_ID=localhost WEBAUTHN_ORIGIN=http://localhost:8080,http://localhost:3000 \
  java -jar server/build/libs/server-all.jar

# Terminal 2: webpack dev server（フロントエンド開発用）
./gradlew :web-frontend:wasmJsBrowserDevelopmentRun

# ブラウザ: http://localhost:3000
```

- `./gradlew :server:run` は Gradle のプロジェクトロックを保持し続けるため使用不可。fat JAR で起動すること。
- webpack dev server (port 3000) が `/api/*` を Ktor サーバー (port 8080) にプロキシ
- `-PskipFrontend` を付けるとサーバービルド時に WASM フロントエンドのビルドをスキップ

### コード変更時の操作

| 変更箇所 | 操作 |
|----------|------|
| feature/ や core/ の Kotlin (UI) | Terminal 2 を **Ctrl+D → 再実行**（インクリメンタルビルド、数十秒） |
| server/ の Kotlin (API) | Terminal 1 を再ビルド＆再起動 |
| shared/ のモデル変更 | 両方再起動 |

> **Note:** ネイティブ Linux / macOS 環境では `./gradlew :web-frontend:wasmJsBrowserDevelopmentRun -t` の `-t`（continuous build）でファイル変更を自動検知し、リビルド＆リロードが自動化される。WSL2 の `/mnt/`（Windows ファイルシステム）では inotify が機能しないため `-t` は使えない。

## Architecture

```
shared/              → Kotlin Multiplatform library (JVM + WASM/JS targets)
                       Contains serializable data models (DashboardItem, User, Status)

server/              → Ktor server (Netty, JVM)
                       Depends on :shared
                       Routes: GET /api/items (JSON), GET / (serves static frontend)
                       Firebase Auth verification, CORS enabled

core/auth/           → Firebase interop, AuthRepository, AuthState/AuthStateHolder
                       Depends on :shared, compose.runtime
core/network/        → 認証トークン付き HTTP client (authenticatedClient)
                       Depends on :core:auth, ktor-client
core/ui/             → テーマ定義 (AppColorScheme)
                       Depends on compose (runtime, foundation, material3, ui)

feature/auth/        → LoginViewModel + LoginScreen + AuthenticatedApp
                       Depends on :core:auth, :core:ui
feature/dashboard/   → DashboardViewModel + DashboardScreen
                       Depends on :core:network, :core:ui, :shared

web-frontend/        → App シェル: Main.kt, App.kt, Sidebar.kt
                       Depends on :core:auth, :core:ui, :feature:auth, :feature:dashboard
```

MVVM パターンで関心事を分離: ViewModel がビジネスロジック・状態管理を担当し、Screen (Composable) は UI 描画のみ。

The `server/build.gradle.kts` has a `copyWasmFrontend` task that copies the frontend build output into the server's static resources during `processResources`, making the final server artifact self-contained.

## Tech Stack

- **Kotlin** 2.3.0, **Compose Multiplatform** 1.10.0, **Ktor** 3.4.0
- **DI**: Koin 4.2.0-RC1（Kotlin 2.3.0 wasmJs 互換の唯一のバージョン）
- **Serialization**: kotlinx-serialization-json 1.8.1
- **Dependency versions**: managed in `gradle/libs.versions.toml`
- **Kotlin code style**: official (set in `gradle.properties`)

## Key Source Locations

- Shared models: `shared/src/commonMain/kotlin/model/DashboardItem.kt`, `User.kt`
- Server entry point: `server/src/main/kotlin/server/Application.kt`
- Core auth: `core/auth/src/wasmJsMain/kotlin/core/auth/` (AuthRepository, AuthState, FirebaseInterop)
- Core network: `core/network/src/wasmJsMain/kotlin/core/network/AuthHttpClient.kt`
- Core theme: `core/ui/src/wasmJsMain/kotlin/core/ui/theme/Color.kt`
- Feature auth: `feature/auth/src/wasmJsMain/kotlin/feature/auth/` (LoginViewModel, LoginScreen, AuthenticatedApp)
- Feature dashboard: `feature/dashboard/src/wasmJsMain/kotlin/feature/dashboard/` (DashboardViewModel, DashboardScreen)
- App shell: `web-frontend/src/wasmJsMain/kotlin/app/` (Main.kt, App.kt, components/Sidebar.kt)

## Docker

### ローカルビルド

```bash
docker compose up -d --build    # ビルド＆バックグラウンド起動
docker compose down              # 停止
docker compose logs -f           # ログ確認
```

Dockerfile はマルチステージビルド（Gradle でビルド → JRE で実行）。ビルドステージで WASM フロントエンド + fat JAR を生成し、実行ステージは `eclipse-temurin:21-jre` 上で `app.jar` を起動する。ポート 8080 を公開。

### GHCR からデプロイ（本番環境）

リバースプロキシ環境向け。詳細は README.md の「GHCR からデプロイ」セクションを参照。

```bash
# 開発マシン: ビルド & push
docker build -t ghcr.io/ptknktq/crabshell:latest .
docker push ghcr.io/ptknktq/crabshell:latest

# 本番サーバー: 起動 / 更新
docker compose up -d
docker compose pull && docker compose up -d
```


## Branch Strategy (GitHub Flow)

`main` ブランチを常にデプロイ可能な状態に保つ。

1. `main` から機能ブランチを作成（`git checkout -b feat/xxx`）
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

## Commit Policy

- **こまめにコミットすること。** ユーザーの指示を待たず、意味のある単位で自発的にコミットする。
- 1コミット = 1つの論理的変更。複数ファイルにまたがっても「1つの目的」ならまとめてよいが、目的が異なる変更は分ける。
- 例: 「依存追加」「UI実装」「バグ修正」は別々のコミットにする。「UIコンポーネント追加 + それを使う画面の更新」は1コミットでよい。
- コミットメッセージは英語、1行目は簡潔に（50文字以内目安）。

## UI Rules

- **モーダル（AlertDialog 等）は使用禁止。** Compose for WASM の描画システム上、ダイアログの DOM teardown とリスト recomposition が同時に走ると高確率で UI フリーズが発生するため。入力フォームはインライン（Card ベース）で実装すること。

## Testing

```bash
# shared モデルテスト（JVM）
./gradlew :shared:jvmTest

# server ユニットテスト
./gradlew :server:test -PskipFrontend
```

- テスト対象は **純粋ロジック** に絞る（Firebase/Firestore 依存のコードは対象外）
- shared: `shared/src/commonTest/kotlin/model/` — `@Serializable` モデルのシリアライズ往復テスト
- server: `server/src/test/kotlin/server/` — `ChallengeStore`、money パース関数等のユニットテスト
- wasmJs ブラウザテスト (`allTests`) はヘッドレス Chrome が必要。CI 以外では `jvmTest` を使用する

## Linting

- **ktlint** (`org.jlleitschuh.gradle.ktlint`) を全サブプロジェクトに適用済み。
- コミット前に必ず `./gradlew ktlintFormat` を実行してからステージング・コミットすること。
- チェックのみ: `./gradlew ktlintCheck`

## Notes

- Comments in build files are in Japanese.
