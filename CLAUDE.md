# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CrabShell is a Kotlin Multiplatform dashboard application with a Ktor server backend and a Compose for Web (WebAssembly) frontend. The shared module contains data models used by both.

## Build Commands

```bash
# Run the server (includes building the WASM frontend automatically)
./gradlew :server:run

# Build the WASM frontend only
./gradlew :app:wasmJsBrowserDistribution

# Build the server (also triggers frontend build via copyWasmFrontend task)
./gradlew :server:build

# Build the shared library
./gradlew :shared:build
```

The server listens on `0.0.0.0:8080`. Building the server automatically copies the compiled WASM frontend into `server/build/resources/main/static/` so the server serves both API and UI.

## Development (Split Mode)

フロントエンドとサーバーを分離起動し、UI 変更の反映を高速化する開発モード。フルビルド（約5分）に対し、インクリメンタルビルド（数十秒）で変更を確認できる。

### セットアップ

```bash
# .env ファイルを作成し、環境変数を設定
cp .env.example .env
# .env を編集して GEMINI_API_KEY 等を設定
```

### 起動（dev.sh 推奨）

`dev.sh` でサーバー・フロントエンドをバックグラウンド管理できる。PID ファイル（`.dev/`）で状態を追跡し、start / stop / restart / log / status を提供する。

```bash
./dev.sh start              # サーバー + フロントエンド両方起動
./dev.sh stop               # 両方停止
./dev.sh server restart     # サーバーのみ再起動
./dev.sh frontend restart   # フロントエンドのみ再起動
./dev.sh status             # 両方の状態を表示
./dev.sh server log         # サーバーログを tail -f
```

### 起動（手動）

```bash
# Terminal 1: API サーバー（fat JAR をビルドして直接起動）
./gradlew :server:buildFatJar -PskipFrontend && java -jar server/build/libs/server-all.jar

# Terminal 2: webpack dev server（フロントエンド開発用）
./gradlew :app:wasmJsBrowserDevelopmentRun

# ブラウザ: http://localhost:3000
# Swagger UI: http://localhost:3000/swagger（SWAGGER_ENABLED=true 時のみ）
```

- `./gradlew :server:run` は Gradle のプロジェクトロックを保持し続けるため使用不可。fat JAR で起動すること。

### 環境変数

サーバーはプロジェクトルートの `.env` ファイルから環境変数を自動読み込みする（[dotenv-java](https://github.com/cdimascio/dotenv-java) 使用）。`.env` が存在しない場合は無視される。OS の環境変数が `.env` より優先される。

| 変数 | 説明 | 必須 |
|------|------|------|
| `FIREBASE_API_KEY` | Firebase クライアント API キー | はい |
| `FIREBASE_AUTH_DOMAIN` | Firebase Auth ドメイン（例: `your-project.firebaseapp.com`） | はい |
| `FIREBASE_PROJECT_ID` | Firebase プロジェクト ID | はい |
| `FIREBASE_STORAGE_BUCKET` | Firebase Storage バケット | はい |
| `FIREBASE_MESSAGING_SENDER_ID` | Firebase Cloud Messaging 送信者 ID | はい |
| `FIREBASE_APP_ID` | Firebase アプリ ID | はい |
| `WEBAUTHN_RP_ID` | WebAuthn の Relying Party ID（開発時は `localhost`） | はい |
| `WEBAUTHN_ORIGIN` | WebAuthn の許可オリジン（カンマ区切り） | はい |
| `GEMINI_API_KEY` | Google AI Studio の API キー（クエスト AI テキスト生成用） | いいえ（未設定時は AI 生成ボタン非表示） |
| `GEMINI_MODEL` | Gemini モデル名（デフォルト: `gemini-2.5-flash`） | いいえ |
| `PASSKEY_DB_PATH` | Passkey SQLite DB のパス（デフォルト: `data/passkey.db`） | いいえ |
| `APP_URL` | アプリケーションの公開 URL（給餌リマインダー Webhook のリンクに使用） | いいえ（未設定時はリンクなし） |
| `SWAGGER_ENABLED` | `true` で Swagger UI (`/swagger`) を有効化（本番では設定しない） | いいえ |
| `LOG_LEVEL` | サーバーのログレベル（デフォルト: `INFO`、開発時は `DEBUG` 推奨） | いいえ |

- webpack dev server (port 3000) が `/api/*` を Ktor サーバー (port 8080) にプロキシ
- `-PskipFrontend` を付けるとサーバービルド時に WASM フロントエンドのビルドをスキップ

### コード変更時の操作

| 変更箇所 | dev.sh | 手動 |
|----------|--------|------|
| feature/ や core/ の Kotlin (UI) | `./dev.sh frontend restart` | Terminal 2 を **Ctrl+D → 再実行** |
| server/ の Kotlin (API) | `./dev.sh server restart` | Terminal 1 を再ビルド＆再起動 |
| shared/ のモデル変更 | `./dev.sh restart` | 両方再起動 |

> **Note:** ネイティブ Linux / macOS 環境では `./gradlew :app:wasmJsBrowserDevelopmentRun -t` の `-t`（continuous build）でファイル変更を自動検知し、リビルド＆リロードが自動化される。WSL2 の `/mnt/`（Windows ファイルシステム）では inotify が機能しないため `-t` は使えない。

## Architecture

```
build-logic/         → Gradle included build (Convention Plugin)
                       crabshell.compose.wasmjs: KMP + Compose + wasmJs { browser() }
                       crabshell.feature: wasmjs を継承 + Compose UI + Koin + ViewModel
                       core/ と app は wasmjs、feature/ は feature を使用

shared/              → Kotlin Multiplatform library
                       Contains serializable data models (DashboardItem, User, Status)

server/              → Ktor server (Netty, JVM)
                       Depends on :shared
                       Routes: /api/{firebase-config,users,pets,feeding,garbage,money,report,quest,point,quest-webhook,cache,login-history,passkey}
                       Firebase Auth verification
                       Koin DI でリポジトリ注入（ServerModule）
                       Repository 層: interface + Firestore 実装 class
                       ルートハンドラは HTTP 処理 + ビジネスルール判定のみ

core/common/         → 環境判定（isDevEnvironment）、AppLogger、TabResumedEvent など横断的ユーティリティ (commonMain)
                       wasmJs: window.location.port による開発環境判定、PageVisibility (visibilitychange)
                       wasmJs: AppLogger → 開発環境のみ console.log/warn/error 出力（本番は no-op）
                       Compose 非依存の純粋 KMP モジュール（kotlinx-coroutines-core のみ依存）
core/auth/           → AuthRepository interface + AuthState/AuthStateHolder (commonMain)
                       Firebase/WebAuthn interop + AuthRepositoryImpl (wasmJsMain)
                       Depends on :shared, :core:common, compose.runtime
core/network/        → 認証トークン付き HTTP client + Repository interfaces/impls (commonMain)
                       PasskeyRepositoryImpl + NetworkModule (wasmJsMain)
                       Depends on :core:common, :core:auth, :shared, ktor-client
core/ui/             → テーマ定義 + WindowSizeClass + 汎用UIコンポーネント (commonMain)
                       DateUtils (@JsFun) + CalendarView (wasmJsMain)
                       Depends on :core:common, :shared (api), compose (runtime, foundation, material3, ui)

feature/auth/        → LoginViewModel + LoginScreen (commonMain)
                       AuthenticatedApp + PasskeySetupViewModel (wasmJsMain)
                       Depends on :core:auth, :core:common, :core:network, :core:ui
feature/dashboard/   → DashboardViewModel + DashboardScreen (wasmJsMain)
                       Depends on :core:auth, :core:network, :core:ui, :shared
feature/feeding/     → FeedingViewModel + FeedingScreen (wasmJsMain)
                       Depends on :core:network, :core:ui, :shared
feature/money/       → MoneyViewModel + MoneyScreen (wasmJsMain)
                       Depends on :core:auth, :core:network, :core:ui, :shared
feature/payment/     → PaymentViewModel + PaymentScreen (wasmJsMain)
                       Depends on :core:auth, :core:network, :core:ui, :shared
feature/quest/       → QuestCategory enum (commonMain)
                       QuestViewModel + QuestScreen (wasmJsMain)
                       Depends on :core:network, :core:ui, :shared + wasmJs: :core:auth
feature/report/      → ReportSummaryCard + MonthlyBarChart + CategoryBreakdown (commonMain)
                       ReportViewModel + ReportScreen (wasmJsMain)
                       Depends on :core:ui, :shared + wasmJs: :core:auth, :core:network
feature/pet-management/ → PetManagementViewModel + PetManagementScreen (commonMain)
                       Depends on :core:network, :core:ui, :shared
feature/settings/    → 全ファイル commonMain (wasmJs 固有 API 不使用)
                       Depends on :core:auth, :core:network, :core:ui, :shared

app/                 → Screen enum + Sidebar + DrawerContent + NavigationItems (commonMain)
                       Main.kt + Navigator + App.kt + AppModule (wasmJsMain)
                       Depends on :core:auth, :core:ui, :feature:auth, :feature:dashboard
```

MVVM パターンで関心事を分離: ViewModel がビジネスロジック・状態管理を担当し、Screen (Composable) は UI 描画のみ。

The `server/build.gradle.kts` has a `copyWasmFrontend` task that copies the frontend build output into the server's static resources during `processResources`, making the final server artifact self-contained.

## Tech Stack

- **Kotlin** 2.3.10, **Compose Multiplatform** 1.10.2, **Ktor** 3.4.1
- **DI**: Koin 4.2.0（クライアント + サーバー共通。Kotlin 2.3.0 wasmJs 互換の唯一のバージョン）
- **Serialization**: kotlinx-serialization-json 1.10.0
- **API Docs**: ktor-openapi-tools 5.6.0（OpenAPI spec + Swagger UI、開発モード時のみ）
- **Logging**: サーバー: SLF4J + Logback（`LOG_LEVEL` 環境変数で制御）、フロントエンド: `AppLogger`（開発環境のみ console 出力、本番 no-op）
- **Dependency versions**: managed in `gradle/libs.versions.toml` (bundles: `ktor-server`, `ktor-client`, `koin`, `feature`, `exposed`)
- **Kotlin code style**: official (set in `gradle.properties`)

## Key Source Locations

- Convention Plugins: `build-logic/src/main/kotlin/CrabshellComposeWasmJsPlugin.kt`, `CrabshellFeaturePlugin.kt`
- Shared models: `shared/src/commonMain/kotlin/model/DashboardItem.kt`, `User.kt`
- Server entry point: `server/src/main/kotlin/server/Application.kt`
- Server DI: `server/src/main/kotlin/server/di/ServerModule.kt`
- Server repositories: `server/src/main/kotlin/server/{money,quest,feeding,garbage,pet,loginhistory}/` (interface + Firestore 実装)
- Core common: `core/common/src/commonMain/kotlin/core/common/` (Environment.kt, AppLogger.kt, TabResumedEvent.kt)
- Core common (wasmJsMain): `core/common/src/wasmJsMain/kotlin/core/common/` (Environment.kt, AppLogger.wasmJs.kt, PageVisibility.kt)
- Core auth (commonMain): `core/auth/src/commonMain/kotlin/core/auth/` (AuthRepository interface, AuthState)
- Core auth (wasmJsMain): `core/auth/src/wasmJsMain/kotlin/core/auth/` (AuthRepositoryImpl, FirebaseInterop, WebAuthnInterop)
- Core network (commonMain): `core/network/src/commonMain/kotlin/core/network/` (AuthHttpClient, Repository interfaces/impls)
- Core network (wasmJsMain): `core/network/src/wasmJsMain/kotlin/core/network/` (PasskeyRepositoryImpl, NetworkModule)
- Core theme (commonMain): `core/ui/src/commonMain/kotlin/core/ui/theme/` (Color.kt, Theme.kt, Typography.kt)
- Core UI (wasmJsMain): `core/ui/src/wasmJsMain/kotlin/core/ui/` (DateUtils.kt, CalendarView.kt)
- Feature auth (commonMain): `feature/auth/src/commonMain/kotlin/feature/auth/` (LoginViewModel, LoginScreen)
- Feature auth (wasmJsMain): `feature/auth/src/wasmJsMain/kotlin/feature/auth/` (AuthenticatedApp, PasskeySetupViewModel)
- Feature pet-management (commonMain): `feature/pet-management/src/commonMain/kotlin/feature/petmanagement/` (PetManagementViewModel, PetManagementScreen)
- Feature settings (commonMain): `feature/settings/src/commonMain/kotlin/feature/settings/` (全ファイル)
- Feature dashboard: `feature/dashboard/src/wasmJsMain/kotlin/feature/dashboard/` (DashboardViewModel, DashboardScreen)
- Feature feeding: `feature/feeding/src/wasmJsMain/kotlin/feature/feeding/` (FeedingViewModel, FeedingScreen)
- Feature money: `feature/money/src/wasmJsMain/kotlin/feature/money/` (MoneyViewModel, MoneyScreen)
- Feature payment: `feature/payment/src/wasmJsMain/kotlin/feature/payment/` (PaymentViewModel, PaymentScreen)
- Feature quest (commonMain): `feature/quest/src/commonMain/kotlin/feature/quest/` (QuestCategory)
- Feature quest (wasmJsMain): `feature/quest/src/wasmJsMain/kotlin/feature/quest/` (QuestViewModel, QuestScreen)
- Feature report (commonMain): `feature/report/src/commonMain/kotlin/feature/report/components/` (UI コンポーネント)
- Feature report (wasmJsMain): `feature/report/src/wasmJsMain/kotlin/feature/report/` (ReportViewModel, ReportScreen)
- App shell (commonMain): `app/src/commonMain/kotlin/app/` (Screen.kt, components/)
- App shell (wasmJsMain): `app/src/wasmJsMain/kotlin/app/` (Main.kt, App.kt, Navigator.kt)

## CI/CD

GitHub Actions で CI/CD を構成。

- **CI** (`.github/workflows/ci.yml`): PR・main push 時に lint / test / build を実行。Renovate の patch auto-merge は `platformAutomerge: false` により CI pass 後に Renovate 自身がマージする。
- **CD** (`.github/workflows/cd.yml`): `v*` タグ push 時に Docker イメージをビルドし GHCR に push。`:latest` と `:v1.x.x` の2タグ。

### デプロイフロー

```
PR マージ → main 更新（CI 検証済み）
  ↓ 任意のタイミングでタグ push
git tag v1.x.x && git push origin v1.x.x
  ↓ CD が GHCR にイメージ push
  ↓ 本番サーバーが定期的に pull → 自動反映
```

## Docker

Dockerfile はマルチステージビルド（Gradle でビルド → JRE Alpine で実行）。ビルドステージで WASM フロントエンド + fat JAR を生成し、実行ステージは `eclipse-temurin:21-jre-alpine` 上で `app.jar` を起動する。ポート 8080 を公開。GHCR 経由で本番デプロイする（リバースプロキシ前提）。HEALTHCHECK 付き。詳細は README.md の「Docker」セクションを参照。

```bash
# 手動ビルド & push（通常は CD ワークフローが自動実行するため不要）
docker build --build-arg COMMIT_HASH=$(git rev-parse --short HEAD) \
  -t ghcr.io/ptknktq/crabshell:latest .
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

- **Popup 系コンポーネント（AlertDialog, DropdownMenu 等）は使用禁止。** Compose for WASM の描画システム上、Popup の DOM teardown とリスト recomposition が同時に走ると高確率で UI フリーズが発生するため。入力フォームはインライン（Card ベース）、選択 UI はインライン（FilterChip 等）で実装すること。

## Testing

```bash
# shared モデルテスト（JVM）
./gradlew :shared:jvmTest

# server ユニットテスト
./gradlew :server:test -PskipFrontend
```

- テスト対象: **純粋ロジック** + **Repository をモックしたビジネスロジック**
- Repository 層（interface + Koin DI）により、ルートハンドラのビジネスロジック（ステータス遷移、権限チェック、上限判定等）は Repository モックでテスト可能
- shared: `shared/src/commonTest/kotlin/model/` — `@Serializable` モデルのシリアライズ往復テスト
- server: `server/src/test/kotlin/server/` — `ChallengeStore`、money パース関数等のユニットテスト
- wasmJs ブラウザテスト (`allTests`) はヘッドレス Chrome が必要。CI 以外では `jvmTest` を使用する

## Linting

- **ktlint** (`org.jlleitschuh.gradle.ktlint`) を全サブプロジェクトに適用済み。
- **pre-commit hook** でコミット時に自動で `ktlintFormat` が実行される（ステージング済み Kotlin ファイルのみ対象）。手動実行は不要。
- hook 未設定の場合: `./gradlew addKtlintFormatGitPreCommitHook` を実行（リポジトリごとに初回のみ）。
- 手動チェック: `./gradlew ktlintCheck`
- 手動フォーマット: `./gradlew ktlintFormat`

## Dependency Management

- **Renovate Bot** (`renovate.json`) がライブラリの更新を自動監視し、PR を作成する。
- patch 更新は自動マージ、Kotlin & Compose / Ktor / Koin / Exposed はグループ化して1つの PR にまとめる。
- `gradle/libs.versions.toml` の `[bundles]` セクションで関連ライブラリをグループ化済み。新しいライブラリ追加時は既存 bundle に含められるか確認すること。
- **コードが直接 import しているモジュール・ライブラリは、推移的に利用可能であっても `build.gradle.kts` に明示的に宣言すること。** 推移的依存に頼ると、中間モジュールの変更（`api()` → `implementation()`）で下流が壊れるリスクがある。

## Security

- **CORS は不要。** サーバーがフロントエンドを同一オリジンで配信し、開発時も webpack がプロキシするため。過去に導入→削除した経緯あり（PR #48）。再導入しないこと。
- **セキュリティヘッダ（HSTS, X-Frame-Options, CSP 等）はリバースプロキシ側で設定する。** アプリケーション側では設定しない（重複するとヘッダ値が矛盾するため）。

## Notes

- Comments in build files are in Japanese.
