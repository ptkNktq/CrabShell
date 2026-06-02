# Third-Party Licenses

CrabShell には以下の OSS が同梱されています。本ファイルは MIT / BSD 等の
ライセンスが要求する「再配布時のコピーライト通知保持」のための一覧です。

## RapiDoc

- **Path**: `server/src/main/resources/static/vendor/rapidoc-<version>/`
- **Author**: Mrinmoy Majumdar
- **Project**: <https://github.com/rapi-doc/RapiDoc>
- **License**: MIT

bundled 依存（buffer, js-yaml, ieee754, JSON-Patch, repeat-string 他）の
ライセンス通知は同ディレクトリの `rapidoc-min.js.LICENSE.txt` に webpack が
自動生成した形式で含まれます。`rapidoc-min.js` 冒頭のバナーコメント
`/*! RapiDoc <ver> | Author - Mrinmoy Majumdar | ... */` も保持しています。

### MIT License (RapiDoc)

```
The MIT License (MIT)

Copyright (c) 2018-2024 Mrinmoy Majumdar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## その他

ビルド時のみ取得される依存（Gradle / Compose Multiplatform / Ktor 等）は
本ファイルの対象外です（実行成果物として CrabShell に **同梱されない**
パッケージはそのライセンス遵守を Gradle 側に委ねます）。

GeoLite2 City データベース (`data/GeoLite2-City.mmdb`、運用者が別途配置)
は MaxMind 提供で、CC BY-SA 4.0 です。クライアント UI の Credits セクション
で Attribution を表示しています。
