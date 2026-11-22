# ストア掲載情報

fastlane で送る既定の掲載情報の置き場所と、fastlane では送れない（ストアの画面で入力する）項目のメモ。

## 作り直し方

```sh
tool/store_screenshots.sh            # 素の画面を撮る（Android: Robolectric、iOS: シミュレーターの UI テスト）→ 画像を作る → 確認
tool/store_screenshots.sh compose    # 文言・配色だけ変えたとき（撮り直さない）
```

- 素の画面は公開中のカタログ（`site/v1/catalog.json`）を同梱して撮る。`build/store_screenshots/raw-{android,ios}/<ja|en>/<phone|tablet>/`（コミットしない）
- 見出しの文言・配色は [store_assets.json](store_assets.json)。画像の合成は `store-listing` スキル（`~/.claude/skills/store-listing/scripts/compose.py`）
- iOS のフルプレイヤーは音源を読み込むので、撮るときはネットワークが必要

## 送り方

鍵は `fastlane/.env`（コミットしない。ひな形は [.env.example](.env.example)）で渡す。

```sh
fastlane ios check_auth        # API キーとアプリの有無を確かめる（読むだけ）
fastlane android check_auth    # サービスアカウントの鍵を確かめる（読むだけ）
fastlane ios metadata          # App Store Connect に文面とスクリーンショットを送る（審査には出さない）
fastlane android metadata      # Google Play に文面・アイコン・フィーチャーグラフィック・スクリーンショットを送る
```

- App Store Connect: 先に「マイ App → 新規 App」で Bundle ID `jp.vntech.classic` のアプリを作る（Bundle ID は Certificates, Identifiers & Profiles で登録）
- Google Play: Play Console でアプリを作り、最初の AAB を一度手で（内部テストなどに）上げてから、fastlane で掲載情報を送れる。サービスアカウントに、そのアプリの「ストアの掲載情報の管理」権限が要る

## 中身

| 場所 | 内容 |
|---|---|
| `metadata/android/<ja-JP,en-US>/` | タイトル（30）・簡単な説明（80）・詳しい説明（4000） |
| `metadata/android/<locale>/images/` | アイコン 512×512・フィーチャーグラフィック 1024×500・スマホ 1080×1920 × 5・10 インチ 1600×2560 × 5 |
| `metadata/ios/<ja,en-US>/` | 名前（30）・サブタイトル（30）・キーワード（100）・プロモーションテキスト（170）・説明（4000）・サポート / プライバシー / マーケティング URL |
| `metadata/ios/copyright.txt` `primary_category.txt` | 著作権の表記・カテゴリ（ミュージック） |
| `screenshots/ios/<ja,en-US>/` | iPhone 6.9 インチ 1320×2868 相当 × 5・iPad 13 インチ 2064×2752 × 5 |

App Store のアイコン（1024×1024）はアプリ本体（`ios/CMSLClassical/Resources/Assets.xcassets`）から取られるので、ここには置かない。

## ストアの画面で入力するもの

- **Google Play のカテゴリ**: アプリ → 音楽＆オーディオ
- **Google Play のタグ**（最大 5 個。Play Console → ストアでの表示 → ストアの設定）: クラシック音楽／音楽プレーヤー／音楽ストリーミング（候補一覧から近いものを選ぶ）
- **連絡先**（Play）: ウェブサイト `https://kate941-su.github.io/classicplayer/`。メールアドレスは必須（公開される）→ オーナーが決める
- **年齢区分**（Play の IARC アンケート／App Store の年齢制限）: 全年齢。暴力・ギャンブル・ユーザー間のやり取りなし、広告なし、ウェブの閲覧なし
- **データセーフティ／App Privacy**: 「収集なし・共有なし」／「データの収集なし」（SPEC §10.1）。通信先はカタログ（GitHub Pages）と CMSL の音源サーバーだけ
- **App Store の審査メモ**: 音源の権利（CC 表示 2.1 日本、商用利用可）、CMSL の利用条件ページの URL、運営者の許諾メール（SPEC §10.2）
- **価格**: 無料（チップを入れたら、アプリ内課金の商品を各ストアで登録する。PLAN Phase 3）

## 未確定（出す前に確認）

- **アプリ名**: SPEC §10.1 の TODO(owner)。今は「クラシック名曲プレイヤー」/ "Classical Masterpieces Player"（`site/` と同じ）。App Store の名前は全体で重複できないので、使えなければ「クラシック名曲プレイヤー – CMSL」などにする（CMSL の名前は運営者の了承が要る）
- **著作権の表記**（`metadata/ios/copyright.txt`）: 「2026 Kaito Kitaya」（サポートページの運営者名と同じ）
- **サポートページの記述が古い**: 「オフライン保存には対応していません」とあるが、ダウンロード（SPEC §5.1）は実装済み。出す前に `site/support/` を直す
- 英語版でも作品名の多くは日本語で表示される（カタログに英語名が少ないため）。英語の説明文にもそう書いている
