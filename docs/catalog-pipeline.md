# カタログの更新（運用メモ）

GitHub Actions からは CMSL を巡回できない（2026-11-21、robots.txt の判定で全面禁止になり 0 ページ）。巡回側の判定は回避しない。
そのため、**オーナーの手元で巡回し、結果を PR でコミットして公開する**（オーナーの決定、2026-11-21。SPEC §3.1）。

## 月次の手順（毎月 1 日ごろ）
```sh
git switch main && git pull
tool/update-catalog.sh        # 差分の巡回（新しい記事だけ。間隔 1.5 秒）→ 検証 → site/v1/ に出力
```
終わると、ブランチ作成・コミット・PR の手順が表示される（`catalog/<catalogVersion>` ブランチ）。
CI（`catalog-test`）が通ったらマージする。`pages.yml` が GitHub Pages に公開し、アプリは次回の確認（24 時間ごと）で取得する。
中身が前回と同じ（新しい記事がない）なら「変更はありません」と出て、何も変えない。

| コマンド | 使うとき |
|---|---|
| `tool/update-catalog.sh` | 月次の更新（目録ページだけ取り直し、新しい記事を取得） |
| `tool/update-catalog.sh --offline` | `overrides.json`・`genre_map.json`・解析ルールを直したあと（通信しない） |
| `tool/update-catalog.sh --full` | 古い記事の書き換えも拾いたいとき（キャッシュを消して全体を巡回。40 分ほど） |

## ワークフロー
| ワークフロー | いつ | 何をする |
|---|---|---|
| `catalog-test.yml` | すべての PR | pytest（`site/v1/` の検証と manifest の一致を含む）、公開中のカタログからの曲数の減少（10% 以上で失敗）。main の必須チェック |
| `pages.yml` | `site/` か `schema/` を main に入れたとき / 手動 | `site/v1/` を検証してから、`site/` 全体とスキーマを Pages に公開する（巡回しない） |

公開 URL: `https://kate941-su.github.io/classicplayer/v1/manifest.json`、`…/v1/catalog.json`（スキーマも `v1/` に置く）

## 検証に失敗したとき
- `tool/update-catalog.sh` は `site/v1/` を変えずに止まる。公開中のカタログはそのまま（アプリは旧版を使い続ける）
- 曲数が 10% 以上減った → CMSL のサイト構造の変化を疑う。`catalog/catalog.report.json` の `noMp3` / `noCategory` を見る
- 比率の超過 → `/catalog-tune` で `genre_map.json` / `overrides.json` / 解析ルールを直し、`--offline` で作り直す

## HTML キャッシュ
- `catalog/cache/`（gitignore 済み）。CMSL の HTML は再配布しないので、コミットも公開もしない
- 消えた場合は次の実行で全体を巡回し直す（1,300 ページ超、40 分ほど）。途中で止めても取得済みのページは残る
- 巡回の間隔は 1 秒未満にしない（`build_catalog.py` が受け付けない）

## 作曲家の肖像画（SPEC §3.6）
新しい作曲家が増えたときと、肖像画を差し替えたいときに手元で実行する（毎月でなくてよい）。Wikimedia の API は 1 リクエスト/秒、応答は `catalog/cache/wikimedia/` にキャッシュする。
```sh
cd catalog
# 1. 未登録の作曲家に Wikidata の候補を付ける（画像は取得しない）
uv run --python 3.12 --with Pillow python fetch_portraits.py --catalog ../site/v1/catalog.json --suggest-only
# 2. composer_wikidata.json を確認する（人の作業）
#    - 正しい候補なら "qid" を入れて "verified": true
#    - 肖像画を使わない作曲家は "exclude": true
#    - P18 以外の画像を使うなら "file": "<Commons のファイル名>"、顔が切れるなら "focus": [横, 縦]（0〜1）
# 3. 画像を取得して site/v1/portraits/ と portraits.json を出力する
uv run --python 3.12 --with Pillow python fetch_portraits.py --catalog ../site/v1/catalog.json \
  --contact https://github.com/kate941-su/classicplayer
```
- `portraits.report.json`（コミットしない）の `unmapped` / `unverified` / `noImage` / `rejected` / `errors` を確認する。取得した画像は目視で確かめる（別人・切り抜きの失敗がないか）
- `--write-catalog` は使わない。`portrait` は `build_catalog.py` が `portraits.json` を読んで埋める（PLAN 0.11。それまでは `catalog.json` に `portrait` は入らない）
- `composer_wikidata.json`・`portraits.json`・`site/v1/portraits/*.jpg` を、カタログと同じく PR でコミットする
- `--accept-unverified` は使わない（同名の別人の肖像画が出るおそれがある）
