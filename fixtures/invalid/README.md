# 異常系のフィクスチャ

`catalog.sample.json` を 1 か所だけ壊したもの（`catalog/extract_fixture.py` が作る）。`validate_catalog.py` はすべてを拒否する（`catalog/tests/test_validate_manifest.py`）。アプリのカタログ更新処理（SPEC §7）でも、これらを受け取ったら差し替えずに旧版を維持することのテストに使う。

| ファイル | 壊し方 | 検出 |
|---|---|---|
| `duplicate-track-id.json` | 2 曲目の `id` を 1 曲目と同じにした | ID の重複 |
| `dangling-work-ref.json` | 1 曲目の `workId` を存在しない作品にした | 参照切れ |
| `foreign-audio-domain.json` | 1 曲目の `audioUrl` を CMSL 以外のドメインにした | スキーマ |
| `schema-version-2.json` | `schemaVersion` を 2 にした | スキーマ（アプリは「対応範囲外」として扱う） |
| `trackcount-mismatch.json` | ジャンルの `trackCount` を実数と違う値にした | 整合性 |
