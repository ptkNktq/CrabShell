# app

アプリシェル。ルーティング、サイドバーナビゲーション、ドロワーレイアウトを構成し、全 feature モジュールを統合する。

## 依存関係

```mermaid
graph LR
  app --> core:auth
  app --> core:network
  app --> core:ui
  app --> feature:auth
  app --> feature:dashboard
  app --> feature:feeding
  app --> feature:money
  app --> feature:payment
  app --> feature:settings
```

## 主要ファイル

| ファイル | 説明 |
|---|---|
| `app/Main.kt` | アプリケーションエントリーポイント |
| `app/App.kt` | メイン Composable（ルーティング・レイアウト） |
| `app/components/Sidebar.kt` | サイドバーナビゲーション |
| `app/components/DrawerContent.kt` | ドロワーコンテンツ |
| `app/components/NavigationItems.kt` | ナビゲーション項目定義 |
| `app/di/AppModule.kt` | Koin DI モジュール |
