---
paths:
  - "catalog/**"
  - "schema/**"
  - "fixtures/**"
  - "site/v1/**"
---

# カタログ・スキーマ・フィクスチャの約束

- `build_catalog.py` は**標準ライブラリだけ**で動かす（オーナーが手元の素の Python 3.9+ で実行するため）。`validate_catalog.py` とテストは開発用依存（`catalog/requirements-dev.txt`）を使ってよい
- 実行は `cd catalog && uv run --python 3.12 --with-requirements requirements-dev.txt python -m pytest`。CI も同じ Python を使う
- 巡回の `--delay` は 1 秒未満を受け付けない。テストは必ず `--offline` とキャッシュのフィクスチャで行い、**テストから CMSL に通信しない**
- `track.id` は記事 ID 由来で恒久。生成ロジックを変えるときは、既存 ID が変わらないことをテストで確認する
- `<audio>` / `<embed>` だけで提供される mp3（ストリーミング専用）は収録しない
- スキーマを変えたら `/schema-change` スキルに従う（`schemaVersion`、両アプリのデコーダー、フィクスチャを同じ PR で更新）
- 肖像画（`fetch_portraits.py`、SPEC §3.6）: Pillow を使ってよい（`build_catalog.py` とは別。`uv run --with Pillow`）。テストは `HttpGet` を差し替えて行い、**テストから Wikidata / Commons に通信しない**。`composer_wikidata.json` の `verified` は人が確認するもので、Claude が `true` にしない（候補を提案するだけ）。採用するライセンスを広げない（PD / CC0 / CC BY / CC BY-SA）
- `composer_wikidata.json`・`portraits.json`・`site/v1/portraits/*.jpg` はコミットする。`portraits.report.json` と `cache/wikimedia/` はコミットしない。`--write-catalog` で `site/v1/catalog.json` を書き換えない
- `cache/`、`catalog.json`、`catalog.report.json` のローカル生成物はコミットしない。公開物は `tool/update-catalog.sh` が `site/v1/` に出力し、PR でコミットする（`site/v1/catalog.json` と `manifest.json` は手で書き換えない）
