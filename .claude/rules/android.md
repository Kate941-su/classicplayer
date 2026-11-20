---
paths:
  - "android/**"
---

# Android の約束

- Kotlin + Compose（Material 3）+ Media3。Version Catalog（`gradle/libs.versions.toml`）で依存を管理する
- 依存を追加するときは、PR に「ライセンス（MIT/Apache/BSD のみ）」と「通信の有無」を書く。広告・解析・クラッシュ収集（Firebase Analytics/Crashlytics、Sentry など）は入れない
- Composable は状態を持たない形にし、状態は ViewModel の `StateFlow` に置く
- UI 文字列は `res/values/strings.xml`（英語）と `res/values-ja/strings.xml`（日本語）に置く。ハードコードしない
- 共有フィクスチャは `../fixtures/` をテストリソースとして参照する（コピーしない）
- 確認コマンド: `cd android && ./gradlew assembleDebug test lint`
- エミュレーターでしか確認できない項目は `docs/manual-qa.md` に追記する
