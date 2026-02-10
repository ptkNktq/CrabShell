# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CrabShell is a Kotlin Multiplatform dashboard application with a Ktor server backend and a Compose for Web (WebAssembly) frontend. The shared module contains data models used by both.

## Build Commands

No `gradlew` wrapper is checked in — use the system `gradle` command.

```bash
# Run the server (includes building the WASM frontend automatically)
gradle :server:run

# Build the WASM frontend only
gradle :web-frontend:wasmJsBrowserDistribution

# Build the server (also triggers frontend build via copyWasmFrontend task)
gradle :server:build

# Build the shared library
gradle :shared:build
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

```bash
# ビルド＆バックグラウンド起動
docker compose up -d --build

# 停止
docker compose down

# ログ確認
docker compose logs -f
```

Dockerfile はマルチステージビルド（Gradle でビルド → JRE で実行）。ビルドステージで WASM フロントエンド + fat JAR を生成し、実行ステージは `eclipse-temurin:21-jre` 上で `app.jar` を起動する。ポート 8080 を公開。

## Parallel Development with git worktree

複数の Claude Code セッションで並行作業する場合は `git worktree` を使う。**メインの作業ディレクトリでブランチを切り替えてはならない。**

```bash
# worktree を作成して別ブランチで作業
git worktree add ../CrabShell-<task> <branch-name>

# 例
git worktree add ../CrabShell-theme feature/theme-update
git worktree add ../CrabShell-sidebar feature/sidebar-navigation

# 不要になったら削除
git worktree remove ../CrabShell-<task>
```

### ルール

- **このディレクトリのブランチを勝手に切り替えないこと。** 他のセッションが同じリポジトリで作業中の可能性がある。
- 新しい機能ブランチで作業する場合は、必ず `git worktree add` で別ディレクトリを作成してからそこで作業する。
- worktree ディレクトリの命名規則: `CrabShell-<短い説明>`（例: `CrabShell-auth`, `CrabShell-theme`）
- 各 worktree は独立したディレクトリなので、`docker compose` や `gradle` コマンドはその worktree 内で実行すること。

## Notes

- No tests, linting, or formatting tools are currently configured.
- Comments in build files are in Japanese.
