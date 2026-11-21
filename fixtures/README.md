# fixtures

両アプリとカタログのテストで共有するデータ。コピーせず、このディレクトリを参照する。

| ファイル | 内容 |
|---|---|
| `catalog.sample.json` | 実カタログ（2026-11-21 の全件巡回、520 曲。schemaVersion 2）から抜き出した 20 曲（PLAN 0.9）。`schema/catalog.schema.json` と `validate_catalog.py`（比率の上限 5% を含む）を通る。音源 URL は実在するので、アプリの既定のシードとして再生もできる。作曲家の `portrait`（SPEC §3.6）は公開中のカタログのまま（8 人とも肖像画あり。画像は `site/v1/portraits/`。キーのない古いカタログは `extract_fixture.py` が `null` で補う） |
| `manifest.sample.json` | `catalog.sample.json` に対する manifest（`make_manifest.py` の出力） |
| `invalid/` | `catalog.sample.json` を 1 か所だけ壊した異常系（`invalid/README.md`） |
| `search_cases.json` | 検索の正規化と照合のテストケース（規則は `docs/search-normalization.md`）。`queries` はこのディレクトリの `catalog.sample.json` に対して実行する |

## 作り直し方

`catalog.sample.json` と `invalid/` は `catalog/extract_fixture.py` で作る。抜き出す曲 ID は同じファイルの `SAMPLE_TRACK_IDS`。

```sh
cd catalog
python3 extract_fixture.py <巡回で作った catalog.json>
python3 make_manifest.py ../fixtures/catalog.sample.json -o ../fixtures/manifest.sample.json
```

作り直したら、ID や作品名を参照しているテスト（`search_cases.json`、Android の `CatalogIndexTest`・`SearchIndexTest`・`HomeToPlaybackTest` など）を合わせる。

## 含めている観点

| 観点 | 曲 |
|---|---|
| ジャンル 6 つ（「その他」を含む）| 交響曲・協奏曲・ピアノ・器楽・声楽・その他 |
| 楽章が複数ある作品。旧録音（2015）は `replacedTracks` で 2023 の曲に読み替える | ベートーヴェン「運命」2023（3 曲） |
| 新しい録音（https）と制作者の旧サーバーの録音（`http://classical-music.aki.gs` など）が楽章ごとに混ざり、楽章順に並ぶ作品 | ベートーヴェン 7 番（`499616036-0`、`493005331`（2022、作品名の「Op92」の表記ゆれを同じ作品にまとめたもの）、`222058571`（旧サーバー）、`499616036-1`） |
| 1 記事に mp3 が複数（`{記事ID}-{連番}`）。楽章はファイル名から | `500111088-0`・`-1`、`499616036-0`・`-1` |
| まとめ記事（作曲家・ジャンルが曲ごとに違う）、`titleEn` が `null`。2023 の第1楽章と同じ作品 | `476156249-1`〜`-3` と `499179250`（ブラームス 4 番）、`476991508-3`（別れの曲） |
| 楽章なしの単曲 | ショパン「ノクターン」「別れの曲」、タレガ、プッチーニ、アメイジング・グレイス |
| 英語タイトルの書式（全角コロン、`I.`、`3. … - 4. …`） | `474143449`、`499179250`、`500150417` |
| 英語名のアクセント記号（`Antonín Dvořák`） | `499579424-0` |

実カタログには `publishedAt` が `null` の曲、英語名が `null` の作曲家、「作曲者不明」がない（2026-11-21 時点）。これらはテストの中でフィクスチャを書き換えて確かめる。
