# CLAUDE.md — CMSL Classical Player

広告なしでクラシックだけを聴ける iOS / Android アプリ。音源は CMSL クラシック名曲サウンドライブラリー（フリー素材、CC 表示 2.1 日本）の mp3 で、事前に生成したカタログ JSON をもとにストリーミング再生する。

## 最初に読むもの
1. `docs/SPEC.md` — 製品・技術仕様（正本）
2. `docs/PLAN.md` — フェーズ、タスク、完了条件。**番号順に進め、DoD を満たしてから次へ進む**

## リポジトリ構成
```
catalog/    Python: build_catalog.py, validate_catalog.py, make_manifest.py, overrides.json, genre_map.json, tests
schema/     catalog.schema.json（両アプリ共通の契約）
fixtures/   catalog.sample.json, search_cases.json（両アプリのテストで共有）
site/       GitHub Pages: v1/manifest.json, v1/catalog.json, privacy/, support/
android/    Kotlin + Compose + Media3（Gradle マルチモジュール）
ios/        Swift 6 + SwiftUI + AVFoundation（Xcode プロジェクト）
docs/       SPEC.md, PLAN.md, search-normalization.md, screens/
```

## コマンド
- カタログ: `cd catalog && python3 -m pytest` / `python3 build_catalog.py --max-pages 30`
- Android: `cd android && ./gradlew assembleDebug test lint`
- iOS（macOS のみ）: `cd ios && xcodebuild -scheme CMSLClassical -destination 'platform=iOS Simulator,name=iPhone 16' test`

## 守るべき原則（SPEC §1.2）
- 広告・解析・クラッシュ収集の SDK を**追加しない**。依存を追加するときは、ライセンス（MIT/Apache/BSD のみ）と通信の有無を PR に書く。
- アプリから CMSL の HTML にアクセスしない。アプリがアクセスするのは `site/v1/*` と `classical-sound.up.seesaa.net/*.mp3` だけ。
- 永続化してよい ID は `track.id` だけ（`composer.id` と `work.id` は変わることがある）。
- 再生や曲を課金で制限しない。寄付はアプリ内課金の消耗型チップだけ。外部の寄付リンクは置かない。
- CMSL のクレジット表示を消さない。
- ストリーミング専用の音源（`<audio>` などで埋め込まれたもの）をカタログに入れない。
- 巡回の間隔は 1 秒未満にしない。

## 作業の進め方
- 1タスク 1PR。PR には、該当する PLAN の番号、DoD の確認結果、スクリーンショット（UI の変更時）を載せる。
- スキーマを変更するときは `schemaVersion` を上げ、両アプリのデコーダーとフィクスチャを同じ PR で更新する。
- iOS をビルドできない環境では、コードを書いた上で「未ビルド」と明記し、CI（`ios.yml`）の結果で確認する。
- 実機・エミュレーターでしか確認できない項目（ロック画面の操作、割り込み処理など）は、`docs/manual-qa.md` のチェックリストに追記する。
- 分からない仕様は推測で埋めずに、SPEC に「TODO(owner)」として記録し、オーナーに確認する。

## コードスタイル
- Kotlin: ktlint の標準ルール。Compose は、状態を持たない Composable と ViewModel(StateFlow) に分ける。
- Swift: Swift API Design Guidelines に従い、`@Observable` の ViewModel と、`async/await` を使う。
- UI 文字列はすべてリソース化する（日本語と英語）。ハードコードしない。
