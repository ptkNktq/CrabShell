# CrabShell プロジェクト監査レポート

**作成日**: 2026-02-19
**最終更新**: 2026-02-19

---

## 対応状況サマリー

| 状態 | 件数 |
|------|------|
| :white_check_mark: 対応済み | 9 |
| :hourglass_flowing_sand: 未対応（対応推奨） | 7 |
| :pause_button: 保留（外部要因待ち） | 2 |

### 対応済み PR 一覧

| PR | ブランチ | 対応項目 |
|----|---------|---------|
| [#45](https://github.com/ptkNktq/CrabShell/pull/45) | `fix/gitignore-data` | 3.2 `data/` を `.gitignore` に追加 |
| [#46](https://github.com/ptkNktq/CrabShell/pull/46) | `chore/gradle-parallel` | 3.3 Gradle parallel + caching 有効化 |
| [#47](https://github.com/ptkNktq/CrabShell/pull/47) | `chore/renovate-improvements` | 3.8 Renovate 設定改善 |
| [#48](https://github.com/ptkNktq/CrabShell/pull/48) | `fix/cors-env-var` | 3.5 CORS 環境変数制御 |
| [#49](https://github.com/ptkNktq/CrabShell/pull/49) | `chore/ci-pipeline` | 3.1 CI パイプライン追加 |
| [#50](https://github.com/ptkNktq/CrabShell/pull/50) | `fix/frontend-route-guard` | 2.2 フロントエンドルートガード |
| [#51](https://github.com/ptkNktq/CrabShell/pull/51) | `chore/docker-optimization` | 3.6 Docker 最適化 + 3.7 BuildConfig キャッシュ修正 |
| [#52](https://github.com/ptkNktq/CrabShell/pull/52) | `refactor/dedup-jst-functions` | 2.5 `toJstHHMM` 重複解消 |
| [#53](https://github.com/ptkNktq/CrabShell/pull/53) | `refactor/firestore-async` | 2.1 Firestore ブロッキング→非同期化 |

---

## 1. 依存関係の更新状況

> :information_source: パッチ・マイナー更新は Renovate Bot が自動で PR を作成する設定済み（PR #47 で改善）。手動対応が必要なのは破壊的変更を含むメジャー更新のみ。

| ライブラリ | 現在 | 最新安定版 | 優先度 | 状態 | 備考 |
|---|---|---|---|---|---|
| **Kotlin** | 2.3.0 | 2.3.10 | Medium | :robot: Renovate 対応 | パッチ。バグ修正のみ |
| **Compose Multiplatform** | 1.10.0 | 1.10.1 | Medium | :robot: Renovate 対応 | パッチ。Kotlin 2.3.10 と互換 |
| **Ktor** | 3.4.0 | 3.4.0 | - | :white_check_mark: 最新 | |
| **Koin** | 4.2.0-RC1 | 4.2.0-RC1 | High (リスク) | :pause_button: 安定版待ち | `resolutionStrategy` ワークアラウンド中 |
| **kotlinx-serialization** | 1.8.1 | **1.10.0** | High | :robot: Renovate 対応 | 大幅に遅れている |
| **Exposed** | 0.58.0 | **1.0.0** | High | :hourglass_flowing_sand: 手動対応要 | メジャー更新。パッケージ名変更あり（後述） |
| **Gradle** | 8.12 | **9.3.1** | Medium-High | :hourglass_flowing_sand: 手動対応要 | Kotlin DSL 変更、段階的アップグレード推奨（後述） |
| **Logback** | 1.5.12 | 1.5.32 | Low | :robot: Renovate 対応 | パッチ20個分 |
| **Firebase Admin** | 9.4.3 | 9.7.1 | Low | :robot: Renovate 対応 | マイナー |
| **ktlint-gradle** | 12.1.2 | 14.0.1 | Low | :robot: Renovate 対応 | メジャー2つ分 |
| **WebAuthn4J** | 0.28.5 | 0.31.0 | Low | :robot: Renovate 対応 | マイナー |
| **SQLite JDBC** | 3.47.1.0 | 3.51.2.0 | Low | :robot: Renovate 対応 | マイナー |

### 推奨アップグレード順序（手動対応が必要なもの）

1. **Exposed** 0.58.0 → 1.0.0（[公式マイグレーションガイド](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)。パッケージ名 `org.jetbrains.exposed.sql.*` → `org.jetbrains.exposed.v1.core.*` の全面変更。専用PR推奨）
2. **Gradle** 8.12 → 9.x（Kotlin DSL 変更あり。段階的アップグレード推奨）
3. **Koin** 4.2.0-RC1 → 4.2.0 stable（リリース待ち。リリース後即座に更新し `resolutionStrategy` ワークアラウンド削除）

---

## 2. アーキテクチャの課題

### 2.1 ~~Critical: サーバーサイドの Firestore ブロッキング呼び出し~~ :white_check_mark: 対応済み

> **PR [#53](https://github.com/ptkNktq/CrabShell/pull/53)** (`refactor/firestore-async`)
>
> カスタム `ApiFuture<T>.await()` 拡張関数（`server/util/ApiFutureAwait.kt`）を作成し、全5ルートファイルの `.get().get()` / `.set().get()` を `.get().await()` / `.set().await()` に変換。
>
> **技術メモ**: `kotlinx-coroutines-guava` は Firebase Admin SDK の `ApiFuture` と互換性がないため（`ListenableFuture` を直接 extend していない）、`suspendCancellableCoroutine` + `ApiFutureCallback` によるカスタム実装を採用。

### 2.2 ~~Critical: フロントエンドのルートガード欠如~~ :white_check_mark: 対応済み

> **PR [#50](https://github.com/ptkNktq/CrabShell/pull/50)** (`fix/frontend-route-guard`)
>
> `Screen` enum に `adminOnly` フラグを追加し、`App.kt` の `ScreenContent` で非管理者が admin 画面にアクセスした場合に Dashboard へリダイレクト。

### 2.3 High: AuthStateHolder がグローバル可変シングルトン — :hourglass_flowing_sand: 未対応

**影響**: テスタビリティ

`core/auth/AuthState.kt` の `AuthStateHolder` は `object` + `mutableStateOf` のグローバルシングルトン。複数箇所（`App.kt`, `AuthenticatedApp.kt`, `AuthHttpClient.kt`, `LoginViewModel.kt`）から直接参照されており、テスタビリティが低い。

**推奨**: Koin の `single` として登録し、DI 経由でインジェクトする。

**対応時の注意点**:
- 影響ファイルが多い（最低4ファイル）ため、十分な手動テストが必要
- `AuthHttpClient` は Koin モジュール外で使われている箇所があるか確認
- Compose の `remember` / `derivedStateOf` との相互作用に注意

### 2.4 High: エラーハンドリングの不統一 — :hourglass_flowing_sand: 未対応

**影響**: 保守性 / デバッグ容易性

| レイヤー | パターン |
|---|---|
| `AuthRepositoryImpl` | `Result<Unit>` を返す |
| `FeedingRepository` 等 | 例外をスロー |
| `PasskeyRepositoryImpl` | `runCatching` で `Result<T>` |
| 全 ViewModel | `catch (e: Exception)` で汎用キャッチ |

**推奨**: ドメインエラーの sealed class を導入し、全リポジトリで統一した戻り値型を使用する。

**対応時の注意点**:
- まず sealed class の設計を決定（`AppError` or モジュール別）
- 全 Repository + ViewModel の書き換えが必要
- 段階的に移行可能（新規コードから適用 → 既存コードを順次移行）

### 2.5 ~~Medium: コード重複~~ — 部分対応

| 箇所 | 状態 | 詳細 |
|---|---|---|
| `toJstHHMM` | :white_check_mark: **PR [#52](https://github.com/ptkNktq/CrabShell/pull/52)** | `core/ui/util/DateUtils.kt` に抽出済み |
| `Sidebar` / `DrawerContent` | :hourglass_flowing_sand: 未対応 | ナビゲーションアイテムの描画ロジックがほぼ同一 |
| Firestore 初期化 | :hourglass_flowing_sand: 未対応 | `private val firestore by lazy { ... }` が全ルートファイルに散在。サーバーリポジトリ層（2.6）導入時に解消推奨 |
| パラメータバリデーション | :hourglass_flowing_sand: 未対応 | `call.parameters["x"] ?: return@get ...` がルートごとに繰り返し。Ktor プラグインまたはヘルパー関数で解消可能 |

### 2.6 Medium: サーバーにリポジトリ/サービス層がない — :hourglass_flowing_sand: 未対応

ルートハンドラが直接 Firestore にアクセスしており、`@Suppress("UNCHECKED_CAST")` による型安全でないデータマッピングも見られる。テスタビリティと保守性の観点から、サーバーサイドにもリポジトリ層を導入すべき。

**対応時の注意点**:
- 2.5 の Firestore 初期化重複と合わせて解消できる
- インターフェース定義 → 実装 → ルートハンドラのリファクタリング の順序で
- テスト（モック可能な構造）の追加も同時に検討

### 2.7 Low: UI の細かい問題 — :hourglass_flowing_sand: 未対応

- **エラー状態にリトライ機能がない**: 全画面で `error` 表示のみ。リトライボタンなし。
- **`FeedingScreen.kt:66`**: `remember { todayDateJs().toString() }` で「今日」を1度だけ取得。日付変更時に更新されない。
- **`DashboardScreen.kt`** の公開 Composable (`DateTimeCard`, `DailyFeedingCard`, `HeaderSection`) が `private`/`internal` でない。
- **`LoginScreen`** の `Loading` 状態が `MaterialTheme(colorScheme = AppColorScheme)` を直接使用（`AppTheme` ではない）。

---

## 3. ビルドシステム / インフラの課題

### 3.1 ~~Critical: CI/CD パイプラインが存在しない~~ :white_check_mark: 対応済み

> **PR [#49](https://github.com/ptkNktq/CrabShell/pull/49)** (`chore/ci-pipeline`)
>
> `.github/workflows/ci.yml` を追加。lint (`ktlintCheck`)、test (`:shared:jvmTest` + `:server:test`)、build (`:server:buildFatJar`) の3ジョブ構成。

### 3.2 ~~Critical: `data/` が `.gitignore` にない~~ :white_check_mark: 対応済み

> **PR [#45](https://github.com/ptkNktq/CrabShell/pull/45)** (`fix/gitignore-data`)
>
> `.gitignore` に `data/` を追加。

### 3.3 ~~High: Gradle ビルドパフォーマンス設定の欠如~~ :white_check_mark: 対応済み

> **PR [#46](https://github.com/ptkNktq/CrabShell/pull/46)** (`chore/gradle-parallel`)
>
> `gradle.properties` に `org.gradle.parallel=true` と `org.gradle.caching=true` を追加。

### 3.4 High: Convention Plugin 未導入 — :hourglass_flowing_sand: 未対応

7つの feature モジュールの `build.gradle.kts` がほぼ同一。`build-logic/` に convention plugin を作成すれば、各モジュールは数行で済む:

```kotlin
plugins {
    id("crabshell.feature")
}
dependencies {
    implementation(project(":core:auth"))  // モジュール固有の追加のみ
}
```

**対応時の注意点**:
- `build-logic/` を `includeBuild` として `settings.gradle.kts` に追加
- `crabshell.feature` プラグインで kotlin-multiplatform, compose, koin bundle 等を共通設定
- 既存の `build.gradle.kts` をプラグイン適用に段階的に置き換え
- Gradle 9.x アップグレードと同時に進めると効率的

### 3.5 ~~High: CORS `anyHost()` が本番環境で使用されている~~ :white_check_mark: 対応済み

> **PR [#48](https://github.com/ptkNktq/CrabShell/pull/48)** (`fix/cors-env-var`)
>
> `CORS_ORIGINS` 環境変数で許可オリジンを制御するように変更。`docker-compose.yml` にも反映。

### 3.6 ~~Medium: Docker 最適化~~ :white_check_mark: 対応済み

> **PR [#51](https://github.com/ptkNktq/CrabShell/pull/51)** (`chore/docker-optimization`)
>
> `.dockerignore` 改善、`.git/` コピー廃止（`COMMIT_HASH` ビルド引数）、依存レイヤーキャッシュ、`eclipse-temurin:21-jre-alpine`、`HEALTHCHECK` 追加。

### 3.7 ~~Medium: `generateBuildConfig` のキャッシュ無効化~~ :white_check_mark: 対応済み

> **PR [#51](https://github.com/ptkNktq/CrabShell/pull/51)** (`chore/docker-optimization`) に含めて対応
>
> `outputs.upToDateWhen { false }` を `providers.exec` + `inputs.property` に置換。

### 3.8 ~~Medium: Renovate 設定の改善点~~ :white_check_mark: 対応済み

> **PR [#47](https://github.com/ptkNktq/CrabShell/pull/47)** (`chore/renovate-improvements`)
>
> `minimumReleaseAge: "3 days"` 追加、サーバー依存のグルーピング追加。

### 3.9 Low: セキュリティ強化 — :hourglass_flowing_sand: 未対応

- **レート制限**: 未認証のパスキーエンドポイントにレート制限なし（Ktor `RateLimit` プラグイン）
- **リクエストサイズ制限**: `call.receive<T>()` にボディサイズ上限なし
- **ペットデータの認可**: 認証済みユーザーなら任意の `petId` のデータにアクセス可能
- **FirebaseAdmin の例外握りつぶし**: `catch (e: Exception) { null }` でログなし
- **docker-compose.yml**: `restart`, `mem_limit`, `logging` 設定なし

**対応時の注意点**:
- レート制限は Ktor の `RateLimit` プラグイン（`install(RateLimit) { ... }`）で実装可能
- リクエストサイズ制限は `ContentNegotiation` の設定で対応
- ペットデータ認可は `pets` コレクションの `members` フィールドを参照してアクセス制御

---

## 4. 優先度サマリー（更新版）

### :white_check_mark: 対応済み（Critical + High）
1. ~~CI パイプライン追加~~ — PR #49
2. ~~`data/` を `.gitignore` に追加~~ — PR #45
3. ~~CORS を環境変数で制御~~ — PR #48
4. ~~Firestore ブロッキング呼び出しの非同期化~~ — PR #53
5. ~~Gradle parallel + caching 有効化~~ — PR #46
6. ~~フロントエンドのルートガード~~ — PR #50
7. ~~Docker 最適化~~ — PR #51
8. ~~Renovate 設定改善~~ — PR #47
9. ~~コード重複解消 (`toJstHHMM`)~~ — PR #52

### :hourglass_flowing_sand: 次に対応すべき（推奨順）
1. **Convention Plugin 導入** (3.4) — ビルドファイル重複排除。Gradle 9.x と同時推奨
2. **Exposed 1.0.0 マイグレーション** (1) — パッケージ名全面変更の専用 PR
3. **エラーハンドリング統一** (2.4) — sealed class 設計 → 段階的移行
4. **AuthStateHolder DI 化** (2.3) — Koin single 登録
5. **サーバーリポジトリ層導入** (2.6) — Firestore 初期化重複 (2.5) も同時解消
6. **セキュリティ強化** (3.9) — レート制限、リクエストサイズ制限、認可
7. **UI の細かい問題** (2.7) — リトライ機能、日付更新、可視性修飾子

### :pause_button: 外部要因待ち
- **Koin 4.2.0 stable** — 安定版リリース待ち
- **Gradle 9.x アップグレード** — Convention Plugin 導入後に段階的対応
