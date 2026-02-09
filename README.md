# CrabShell

## ブランチ戦略

- `main` に直接 push しない — 常に PR 経由
- ブランチは `main` から切る
- PR は Squash merge でマージ
- マージ後にブランチ削除

### ブランチ命名規則

`feat/`, `fix/`, `chore/`, `refactor/` + 簡潔な説明（例: `feat/websocket-realtime`）