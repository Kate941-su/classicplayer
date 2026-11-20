# fixtures

両アプリとカタログのテストで共有するデータ。コピーせず、このディレクトリを参照する。

| ファイル | 内容 |
|---|---|
| `catalog.sample.json` | `schema/catalog.schema.json` を通るサンプルカタログ。**現在は暫定版**（PLAN 0.2。ID・URL は架空の `9000000xx`）で、PLAN 0.9 で実カタログからの 20 曲の抽出に置き換える |
| `search_cases.json` | 検索の正規化と照合のテストケース（規則は `docs/search-normalization.md`）。`queries` はこのディレクトリの `catalog.sample.json` に対して実行する。PLAN 0.9 でサンプルを実カタログに置き換えるときは、ID を合わせて更新する |

暫定版に含めているケース: ジャンル 3 つ（「その他」を含む）、3 楽章の作品、1 記事に mp3 が 2 つある記事（`{記事ID}-{連番}`）、楽章なしの単曲、作曲者不明、`titleEn` / `publishedAt` が `null` の曲。
