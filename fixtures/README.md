# fixtures

両アプリとカタログのテストで共有するデータ。コピーせず、このディレクトリを参照する。

| ファイル | 内容 |
|---|---|
| `catalog.sample.json` | 実カタログ（2026-11-21 の全件巡回、353 曲）から抜き出した 20 曲（PLAN 0.9）。`schema/catalog.schema.json` と `validate_catalog.py`（比率の上限 5% を含む）を通る。音源 URL は実在するので、アプリの既定のシードとして再生もできる |
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
| ジャンル 7 つ（「その他」を含む）| 交響曲・管弦楽曲・協奏曲・ピアノ・器楽・声楽・その他 |
| 楽章が複数ある作品。同じ作品名の録音が 2 つ（作品名に「（2015）」「（2023）」） | ベートーヴェン「運命」2015（3 曲）、2023（3 曲） |
| 1 記事に mp3 が複数（`{記事ID}-{連番}`）。楽章はファイル名から | `500111088-0`・`-1`、`476156249-0`〜`-4` |
| まとめ記事（作曲家・ジャンルが曲ごとに違う）、`titleEn` が `null` | `476156249-*`（ブラームス 4 番 + フォーレ「パヴァーヌ」） |
| 楽章なしの単曲 | ショパン「ノクターン」「別れの曲」、タレガ、プッチーニ、アメイジング・グレイス |
| 英語タイトルの書式（全角コロン、`I:`、`3. … - 4. …`） | `474143449`、`467532689`、`500150417` |
| 英語名のアクセント記号（`Antonín Dvořák`） | `467532689` |

実カタログには `publishedAt` が `null` の曲、英語名が `null` の作曲家、「作曲者不明」がない（2026-11-21 時点）。これらはテストの中でフィクスチャを書き換えて確かめる。
