---
name: schema-change
description: カタログのスキーマ（schema/catalog.schema.json）を変更するときのチェックリスト。フィールドの追加・削除・型変更、schemaVersion の更新を行うときに使う。
---

# スキーマ変更のチェックリスト

互換性のない変更（必須フィールドの追加・削除、型の変更、意味の変更）は `schemaVersion` を上げる。任意フィールドの追加だけなら上げなくてよいが、PR にその判断を書く。

同じ PR で次をすべて更新する:

1. `schema/catalog.schema.json`（`schemaVersion` の `const` を含む）
2. `docs/SPEC.md` §3.2（例の JSON と説明）、§3.5（manifest の `minAppSchema`）
3. `catalog/build_catalog.py` の出力と `catalog/validate_catalog.py`
4. `fixtures/catalog.sample.json`（と異常系フィクスチャ）
5. Android: `core:model` のデータクラスとデコードテスト（存在すれば）
6. iOS: Codable モデルとデコードテスト（存在すれば）
7. アプリの「対応する schemaVersion の範囲」の定数（SPEC §3.5: 範囲外なら更新しない）

確認:
- `cd catalog && uv run --python 3.12 --with-requirements requirements-dev.txt python -m pytest`
- 両アプリのテスト（存在すれば）
- `track.id` の形式を変えていないこと（お気に入りのキー）
