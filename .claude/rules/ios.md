---
paths:
  - "ios/**"
---

# iOS の約束

- Swift 6（strict concurrency）、SwiftUI、iOS 17+。ViewModel は `@Observable`、非同期は `async/await`
- 依存ライブラリは原則ゼロ。追加する場合は MIT/Apache のみで、PR にライセンスと通信の有無を書く
- UI 文字列は String Catalog（`Localizable.xcstrings`、ja / en）に置く
- 共有フィクスチャは `../fixtures/` をテストバンドルに参照で追加する（コピーしない）
- 確認コマンド: `cd ios && xcodebuild -scheme CMSLClassical -destination 'platform=iOS Simulator,name=iPhone 16' test`。シミュレーター名が無い環境では `xcrun simctl list devices available` で選び直す
- ビルドできなかった場合は PR に「未ビルド」と明記する
- 実機でしか確認できない項目（ロック画面、割り込み、経路変更）は `docs/manual-qa.md` に追記する
