---
name: catalog-tune
description: catalog.report.json を読んで、タイトル解析・genre_map.json・overrides.json を調整し、「その他」と「作曲者不明」を 5% 以下にする（PLAN 0.6）。オーナーがレポートを共有したとき、「カタログを調整して」と言われたときに使う。
---

# カタログの調整

1. レポートの場所を確認する（オーナーが共有した `catalog.report.json` と `catalog.json`）。巡回は Claude からは実行しない（オーナーの手元で `tool/update-catalog.sh` が実行する。CI からは巡回できない。間隔は 1 秒以上）。キャッシュ `catalog/cache/` があれば `tool/update-catalog.sh --offline` で作り直せる
2. 集計する: 全曲数、`noComposer`、`noCategory`、ジャンル「その他」の曲数と比率、マップにないカテゴリ名
3. 原因ごとに分ける:
   - タイトルの書式の揺れ → `parse_title` の正規表現を直し、その書式のテストケースを追加する
   - カテゴリ名 → `genre_map.json` に追加する
   - 個別の例外 → `overrides.json`（`tracks` / `composerAliases` / `composerEn` / `exclude`）
4. 手で直す件数が多いもの（10 件以上の同じパターン）は、overrides ではなく解析ルールで直す
5. `--offline` で作り直し、比率が 5% 以下になったことを数値で報告する
6. 直した結果 `track.id` が変わっていないことを確認する
