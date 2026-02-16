# core:auth

Firebase 認証の JavaScript interop、AuthRepository、AuthState 管理を提供する。Koin モジュールで DI を構成。

## 依存関係

```mermaid
graph LR
  core:auth --> shared
```

## 主要ファイル

| ファイル | 説明 |
|---|---|
| `core/auth/AuthRepository.kt` | 認証リポジトリ |
| `core/auth/AuthState.kt` | 認証状態ホルダー・状態管理 |
| `core/auth/FirebaseInterop.kt` | Firebase JS SDK interop 層 |
| `core/auth/di/AuthModule.kt` | Koin DI モジュール |
