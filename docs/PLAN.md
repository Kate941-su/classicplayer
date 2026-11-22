# 開発プラン（Claude Code 向け）

仕様の正本は `docs/SPEC.md`。このファイルは「どの順で、何ができたら完了とするか」を定める。
各タスクは **1タスク = 1ブランチ = 1PR** を目安にし、完了条件（DoD）を満たしたら次へ進む。

凡例: 🐧 = Linux 環境の Claude Code で完結 / 🍎 = macOS + Xcode が必要 / 👤 = オーナーの作業

---

## Phase 0 — 基盤とカタログ（目安 2〜3日）

| # | タスク | 環境 | 完了条件（DoD） |
|---|---|---|---|
| 0.1 | モノレポを整える（`catalog/ schema/ fixtures/ ios/ android/ docs/ site/`）。ルートに LICENSE（MIT）、`.editorconfig`、`.gitignore` を置く | 🐧 | ディレクトリ構成が CLAUDE.md と一致する |
| 0.2 | `schema/catalog.schema.json`（Draft 2020-12）を SPEC §3.2 に合わせて仕上げる | 🐧 | `fixtures/catalog.sample.json` がスキーマ検証を通る |
| 0.3 | `build_catalog.py` に `overrides.json` と `genre_map.json` の適用を追加する。ジャンルに `nameEn` と `order` を付与する | 🐧 | ユニットテストを追加し、`python3 -m pytest catalog` が通る |
| 0.4 | 差分巡回: 既知の記事はキャッシュを使い、一覧ページだけを再取得する（`--refresh-index`） | 🐧 | 2回目の実行で取得するのが一覧ページだけになることをテストで確認する |
| 0.5 | `validate_catalog.py`（スキーマ検証、ID 重複、参照切れ、URL ドメイン、「その他」と「作曲者不明」の比率）と `make_manifest.py` | 🐧 | 異常系のフィクスチャで失敗することをテストする |
| 0.6 | 👤 オーナーが手元で `build_catalog.py` を実行し、`catalog.report.json` を共有する → 解析ルール、`genre_map.json`、`overrides.json` を調整する | 👤🐧 | 「その他」と「作曲者不明」が5%以下になる |
| 0.7 | 月次更新: オーナーの手元で `tool/update-catalog.sh`（巡回 → 検証 → `site/v1/` に出力）→ PR → マージで `pages.yml` が GitHub Pages へデプロイ。曲数が10%以上減ったら止める（2026-11-21 に方針変更。GitHub Actions からは巡回できないため） | 🐧 | マージ後に Pages で `manifest.json` と `catalog.json` が公開される |
| 0.8 | `site/` にプライバシーポリシーとサポートページ（日本語と英語の静的 HTML）を置く | 🐧 | Pages 上で閲覧できる |
| 0.9 | `fixtures/catalog.sample.json`（実カタログから20曲を抽出。ジャンル3以上、作品の楽章が複数あるものを含む）と `fixtures/search_cases.json` | 🐧 | 両プラットフォームのテストから参照できる |
| 0.10 | 👤 CMSL 運営者へ連絡する（アプリでの利用の報告、アプリ名に CMSL を使ってよいか、許諾メール、寄付の扱いの相談）。文面の下書きは Claude が用意する | 👤 | 返信を `docs/permissions/` に保存する（公開リポジトリならコミットしない） |
| 0.11 | 作曲家の肖像画（SPEC §3.6）: `fetch_portraits.py` のテスト（`HttpGet` をフェイクにする。ライセンスの採否、P18 の順序、切り抜き）、`build_catalog.py` が `portraits.json` を読んで `composers[].portrait` を出力、スキーマ・`validate_catalog.py`（`url` の形式、画像ファイルの存在と sha256）・フィクスチャの更新（`/schema-change`。`schemaVersion` は 2 のまま）、`.gitignore` | 🐧 | pytest が通る。`portrait` のない catalog と、ある catalog の両方が検証を通る |
| 0.12 | 👤 `--suggest-only` の候補を確認して `composer_wikidata.json` を `verified: true` にする → 画像を取得して `site/v1/portraits/` と `portraits.json` を PR でコミット | 👤🐧 | `portraits.report.json` の未対応・除外を確認済み。取得した肖像画をオーナーが目視で確認する |

---

## Phase 1 — Android MVP（目安 5〜7日）🐧

| # | タスク | 完了条件 |
|---|---|---|
| 1.1 | プロジェクトを作成する（Compose、Material 3、Version Catalog、ktlint/detekt）。パッケージ名 `jp.vntech.classic`（iOS の Bundle ID も同じ） | `./gradlew assembleDebug lint` が通る |
| 1.2 | `core:model`: カタログのデータクラス（kotlinx.serialization）。サンプルフィクスチャのデコードテスト | テストが通る |
| 1.3 | `core:data`: CatalogRepository（シード → ローカル → manifest/ETag → 検証 → 差し替え）。SPEC §7 | 更新、失敗時のロールバック、24時間制限のテスト |
| 1.4 | `core:search`: 正規化とインデックス。`search_cases.json` をすべて通す | テストが通る |
| 1.5 | `core:playback`: Media3 の MediaSessionService、キュー生成（作品順、シャッフル、指定曲から）、状態の復元、エラーのスキップ | キュー生成のユニットテスト。エミュレーターでのバックグラウンド再生は手動確認項目として記録する |
| 1.6 | `core:db`: Room（お気に入り、履歴）、DataStore（最近の検索、キュー） | DAO のテスト |
| 1.7 | UI: ホーム、ジャンル、作曲家、作品、検索、ライブラリ、情報、ミニプレイヤーとフルプレイヤー（SPEC §4） | Compose UI テスト1本（ホーム → 作品 → 再生開始）。スクリーンショットを `docs/screens/android/` に保存する |
| 1.8 | 生成アートワーク（SPEC §4.10） | 同じ作曲家なら常に同じ見た目になるテスト |
| 1.9 | 日本語と英語のリソース、ダークテーマ、アクセシビリティラベル | lint の警告なし。TalkBack の確認項目を記録する |
| 1.10 | 依存監査: 解析・広告 SDK が入っていないことを CI で検査する（`./gradlew :app:dependencies` を grep） | CI ジョブが通る |
| 1.11 | 肖像画の表示（SPEC §4.4・§4.8・§4.9・§4.10・§6）: 作曲家の一覧と詳細、ミニ／フルプレイヤー、MediaSession（ロック画面・通知）のアートワーク。シードの作曲家の肖像画の同梱（`tool/update-seed.sh` が `site/v1/portraits/` からシードの分を `android/seed/portraits/` に置き、両アプリのビルドが入れる）、キャッシュ（sha256）、生成アートワークへのフォールバック。情報タブの「画像のクレジット」画面と、作曲家詳細・「この曲について」のクレジット。プライバシーポリシー（`site/privacy/`）の 2-1 に肖像画の取得を書き足す | 肖像画あり・なし・取得失敗のテスト。スクリーンショットを更新する |
| 1.12 | ダウンロード（オフライン再生。SPEC §5.1）: Android 版。iOS の 2.11 と同じ仕様 | 取得・削除・置き換え時の削除・再生時にファイルを使うことのテスト |
| 1.13 | スリープタイマー（SPEC §5.2）: Android 版。iOS の 2.12 と同じ仕様 | タイマーのテスト（時間・この曲の終わりまで・取り消し） |

## Phase 2 — iOS MVP（目安 5〜7日）🍎

> Linux の Claude Code でも Swift のソースは書けるが、ビルドとテストは macOS が必要。**macOS 上の Claude Code で実施する**か、GitHub Actions の `macos-latest` ランナーで `xcodebuild test` を回して確認する。

| # | タスク | 完了条件 |
|---|---|---|
| 2.1 | Xcode プロジェクト `ios/CMSLClassical.xcodeproj`（SwiftUI、iOS 17+）。Background Modes の Audio を有効にする。SwiftLint は任意 | `xcodebuild build` が通る |
| 2.2 | Model と Catalog Decoding（Codable）。フィクスチャのテスト | テストが通る |
| 2.3 | CatalogStore（Android の 1.3 と同じ仕様） | 同じテストケース |
| 2.4 | SearchIndex（`search_cases.json` を共有） | テストが通る |
| 2.5 | PlaybackController: AVQueuePlayer、NowPlaying、RemoteCommand、割り込みと経路変更の処理、状態の復元 | キュー生成のテスト。実機での手動確認項目を記録する |
| 2.6 | SwiftData（お気に入り、履歴） | テストが通る |
| 2.7 | UI 一式（SPEC §4）。NavigationStack と TabView、ミニプレイヤーはタブバーの上に表示する | UI テスト1本。スクリーンショットを `docs/screens/ios/` に保存する |
| 2.8 | アートワーク、日本語と英語（String Catalog）、Dynamic Type、VoiceOver | 確認項目を記録する |
| 2.9 | CI: GitHub Actions `ios.yml`（macos-latest でビルドとテスト） | PR で緑になる |
| 2.10 | 肖像画の表示（Android の 1.11 と同じ仕様） | 同じテストケース。スクリーンショットを更新する |
| 2.11 | ダウンロード（オフライン再生。SPEC §5.1）: 曲・作品ごとのダウンロード、1 曲ずつ順番に取得、Wi-Fi のときだけの設定、ライブラリの一覧と削除、再生時はファイルを使う、置き換え・削除された曲のファイルを消す。プライバシーポリシーの更新 | 取得・失敗・削除・置き換え時の削除・再生時にファイルを使うことのテスト。機内モードでの再生を手動確認 |
| 2.12 | スリープタイマー（SPEC §5.2）: 時間（15〜90 分）と「この曲の終わりまで」、音量を下げてから一時停止 | タイマーのテスト（時間・この曲の終わりまで・取り消し）。画面ロック中の動作を手動確認 |

※ Phase 1 と Phase 2 は並行して進めてよい。どちらも Phase 0 の 0.2・0.9 だけに依存する。

---

## Phase 3 — チップ（寄付）（目安 2日）

| # | タスク | 環境 | 完了条件 |
|---|---|---|---|
| 3.1 | 👤 App Store Connect と Play Console に消耗型アイテム3つを登録する（`tip.small` / `tip.medium` / `tip.large`） | 👤 | 製品 ID が確定する |
| 3.2 | Android: Play Billing。購入 → consume → お礼画面 → 応援者フラグ | 🐧 | Billing をフェイクにしたユニットテスト。内部テストトラックで購入を確認する |
| 3.3 | iOS: StoreKit 2 と StoreKit Configuration ファイルによるローカルテスト | 🍎 | StoreKitTest でのテスト |
| 3.4 | 寄付の導線（情報タブと、50曲再生時の1回だけのカード）。SPEC §9 | 🐧🍎 | 再生中や起動時に表示されないことを確認する |

---

## Phase 4 — リリース準備（目安 3〜5日）

| # | タスク | 環境 |
|---|---|---|
| 4.1 | アプリアイコン（オリジナルの抽象デザイン）とストア用スクリーンショット | 🐧🍎 |
| 4.2 | ストア掲載文（日本語と英語）、キーワード、審査メモ（ライセンスと許諾の説明。SPEC §10.2） | 🐧 |
| 4.3 | プライバシー表示（iOS: Data Not Collected / Play: データセーフティ） | 👤 |
| 4.4 | 署名の設定（Android: Play App Signing / iOS: 自動署名） | 👤 |
| 4.5 | ベータ配信（TestFlight / Play 内部テスト）→ SPEC §12 の受け入れ基準をすべて確認 | 👤🍎🐧 |
| 4.6 | 審査に提出し、指摘があれば対応する | 👤 |

---

## Phase 5 — リリース後
- カタログの月次更新を監視する（Actions の失敗通知）。
- v1.1 の候補（SPEC §11）は、利用状況ではなく**ストアのレビューとお問い合わせ**をもとに優先度を決める（解析 SDK を入れないため）。

---

## リスクと対策

| リスク | 影響 | 対策 |
|---|---|---|
| CMSL のサイト構造が変わる | カタログが壊れる | 減少率のガード、レポートの監視、overrides で一時的に補正する |
| CMSL が音源の公開を止める、または URL を変更する | 再生できない曲が出る | 月次更新でカタログから外す。お気に入りはグレー表示で残す |
| App Store 審査で第三者コンテンツについて指摘される（5.2.3） | リリースの遅れ | 事前に許諾メールを取得し、審査メモに添付する |
| 寄付リンクで規約違反になる | リジェクト | アプリ内課金に限定し、外部リンクを置かない |
| mp3 のギャップレス再生が不完全 | 楽章間に無音が入る | 先読みで緩和する。完全なギャップレスは目標にしない |
| Seesaa への負荷 | 運営者との関係が悪化する | 巡回は月1回・1.5秒間隔。アプリは HTML にアクセスせず、mp3 だけを取得する |
| GitHub Actions から CMSL を巡回できない（2026-11-21 の手動実行で、robots.txt の判定で全面禁止となり 0 ページ） | 月次更新（0.7）が動かない | 巡回側は判定を回避しない。オーナーの手元で巡回し、結果を PR で公開する運用にした（2026-11-21、`tool/update-catalog.sh`） |
