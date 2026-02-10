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

## Architecture

```
shared/          → Kotlin Multiplatform library (JVM + WASM/JS targets)
                   Contains serializable data models (DashboardItem, Status enum)

server/          → Ktor server (Netty, JVM)
                   Depends on :shared
                   Routes: GET /api/items (JSON), GET / (serves static frontend)
                   CORS enabled for frontend communication

web-frontend/    → Compose Multiplatform (WASM/JS target)
                   Depends on :shared
                   Material Design 3, dark theme
                   Ktor HTTP client fetches from /api/items
```

The `server/build.gradle.kts` has a `copyWasmFrontend` task that copies the frontend build output into the server's static resources during `processResources`, making the final server artifact self-contained.

## Tech Stack

- **Kotlin** 2.3.0, **Compose Multiplatform** 1.10.0, **Ktor** 3.4.0
- **Serialization**: kotlinx-serialization-json 1.8.1
- **Dependency versions**: managed in `gradle/libs.versions.toml`
- **Kotlin code style**: official (set in `gradle.properties`)

## Key Source Locations

- Shared model: `shared/src/commonMain/kotlin/shared/model/DashboardItem.kt`
- Server entry point: `server/src/main/kotlin/server/Application.kt`
- Frontend composables: `web-frontend/src/wasmJsMain/kotlin/frontend/App.kt`
- Frontend WASM entry: `web-frontend/src/wasmJsMain/kotlin/frontend/Main.kt`

## Docker

### 本番用

```bash
docker compose up -d --build    # ビルド＆バックグラウンド起動
docker compose down              # 停止
docker compose logs -f           # ログ確認
```

Dockerfile はマルチステージビルド（Gradle でビルド → JRE で実行）。ビルドステージで WASM フロントエンド + fat JAR を生成し、実行ステージは `eclipse-temurin:21-jre` 上で `app.jar` を起動する。ポート 8080 を公開。

### 開発用

```bash
docker compose -f docker-compose.dev.yml up -d --build   # 初回起動
docker compose -f docker-compose.dev.yml up -d            # 2回目以降
docker compose -f docker-compose.dev.yml down              # 停止
docker compose -f docker-compose.dev.yml logs -f           # ログ確認
```

`Dockerfile.dev` + `docker-compose.dev.yml` を使用。fat JAR を生成せず `gradle :server:run` で起動する。ソースコードはボリュームマウントされるため、コンテナ再起動だけで変更が反映される。Gradle キャッシュは named volume で永続化。

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

## Notes

- No tests, linting, or formatting tools are currently configured.
- Comments in build files are in Japanese.
