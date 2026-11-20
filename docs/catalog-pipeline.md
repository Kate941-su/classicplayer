# カタログの更新（運用メモ）

| ワークフロー | いつ | 何をする |
|---|---|---|
| `catalog.yml` | 毎月 1 日 03:17 UTC / 手動 | 巡回（差分、1.5 秒間隔）→ 検証 → manifest 作成 → `site/` と合わせて Pages へデプロイ |
| `pages.yml` | `site/` か `schema/` を main に入れたとき / 手動 | 公開中の `v1/*.json` をそのまま使って `site/` だけ更新する（巡回しない） |
| `cache-keepalive.yml` | 毎週月曜 | HTML キャッシュを読み込むだけ（Actions のキャッシュは 7 日使わないと消えるため） |
| `catalog-test.yml` | すべての PR | pytest（main の必須チェック） |

公開 URL: `https://kate941-su.github.io/classicplayer/v1/manifest.json`、`…/v1/catalog.json`（スキーマも `v1/` に置く）

## 手動実行
```sh
gh workflow run catalog.yml                                   # 本番（検証に通れば公開）
gh workflow run catalog.yml -f publish=false                  # 巡回と検証だけ
gh workflow run catalog.yml -f max_pages=50                   # お試し（公開しない）
gh workflow run catalog.yml -f max_ratio=1 -f publish=false   # 比率の検査を外してレポートを取る（PLAN 0.6）
gh run download <run-id> -n catalog-output                    # catalog.json と catalog.report.json を取得
```

## 検証に失敗したとき
- 公開は止まり、Issue が作られる。公開中のカタログはそのまま（アプリは旧版を使い続ける）
- 曲数が 10% 以上減った → CMSL のサイト構造の変化を疑う。`catalog-output` のレポートで `noMp3` / `noCategory` を見る
- 比率の超過 → `/catalog-tune` で `genre_map.json` / `overrides.json` / 解析ルールを直す

## HTML キャッシュ
- CMSL の HTML は再配布しないため、公開アーティファクトにせず Actions のキャッシュ（`cmsl-html-*`）に置く
- キャッシュが消えた場合は、次の実行で全体を巡回し直す（数時間。ジョブの上限 350 分）
