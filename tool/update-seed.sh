#!/usr/bin/env bash
# 同梱カタログ（シード。SPEC §2・§6）を差し替える。android/seed/catalog.json に置くと、
# 以降のビルド（tool/run.sh・./gradlew・iOS の xcodebuild）がそれをアプリに入れる（なければ fixtures/catalog.sample.json）。
#
#   tool/update-seed.sh                公開中のカタログ（GitHub Pages）を取得する
#   tool/update-seed.sh <catalog.json> 手元のファイルを使う（例: build_catalog.py の出力）
#   tool/update-seed.sh --clear        シードを消してフィクスチャに戻す
#
# どちらも validate_catalog.py で検査してから置く（比率の検査は外す）。
# シードの作曲家の肖像画（SPEC §4.10）も android/seed/portraits/ に置く（site/v1/portraits/ にあればそれ、
# なければ公開中のものを取得。どちらも sha256 を確かめる）。ビルドが assets/portraits/ に入れる。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/android/seed/catalog.json"
PORTRAITS="$ROOT/android/seed/portraits"
BASE="https://kate941-su.github.io/classicplayer/v1"
URL="$BASE/catalog.json"

die() { printf '\033[1;31m✘ %s\033[0m\n' "$*" >&2; exit 1; }

if [[ "${1:-}" == "--clear" ]]; then
  rm -f "$DEST"
  rm -rf "$PORTRAITS"
  echo "シードを消しました（fixtures/catalog.sample.json を使います）"
  exit 0
fi

command -v python3 > /dev/null || die "python3 が見つかりません"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [[ -n "${1:-}" ]]; then
  [[ -f "$1" ]] || die "ファイルがありません: $1"
  cp "$1" "$tmp/catalog.json"
else
  command -v curl > /dev/null || die "curl が見つかりません"
  curl -fsSL "$URL" -o "$tmp/catalog.json" || die "公開中のカタログを取得できません（${URL}）。まだ公開していない可能性があります（tool/update-catalog.sh → PR → マージで公開）"
fi

# 検査には jsonschema が要る（CLAUDE.md と同じく uv で用意する。なければ素の python3）
if command -v uv > /dev/null; then
  validate=(uv run --quiet --python 3.12 --with-requirements "$ROOT/catalog/requirements-dev.txt" python)
else
  validate=(python3)
fi
"${validate[@]}" "$ROOT/catalog/validate_catalog.py" "$tmp/catalog.json" --max-ratio 1 || die "カタログの検査に失敗しました"
mkdir -p "$(dirname "$DEST")"
cp "$tmp/catalog.json" "$DEST"
echo "シードを更新しました: $DEST"

# 肖像画（作曲家の id は空白や / を含まない。スキーマの url のパターン）
rm -rf "$PORTRAITS"
mkdir -p "$PORTRAITS"
python3 -I - "$tmp/catalog.json" > "$tmp/portraits.tsv" <<'PY'
import json, sys
for c in json.load(open(sys.argv[1], encoding="utf-8"))["composers"]:
    p = c.get("portrait")
    if p and p["url"] == f"portraits/{c['id']}.jpg":
        print(f"{c['id']}.jpg\t{p['sha256']}")
PY
sha_of() { shasum -a 256 "$1" | cut -d' ' -f1; }
bundled=0
missing=()
while IFS=$'\t' read -r name sha; do
  local_file="$ROOT/site/v1/portraits/$name"
  if [[ -f "$local_file" && "$(sha_of "$local_file")" == "$sha" ]]; then
    cp "$local_file" "$PORTRAITS/$name"
  elif curl -fsSL "$BASE/portraits/$(python3 -I -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$name")" -o "$tmp/p.jpg" \
      && [[ "$(sha_of "$tmp/p.jpg")" == "$sha" ]]; then
    mv "$tmp/p.jpg" "$PORTRAITS/$name"
    sleep 1  # 配信元への負担を抑える
  else
    missing+=("$name")
    continue
  fi
  bundled=$((bundled + 1))
done < "$tmp/portraits.tsv"
echo "肖像画: ${bundled} 枚を $PORTRAITS に置きました"
if (( ${#missing[@]} )); then
  printf '\033[1;33m! 見つからない・sha256 が違う肖像画（アプリが配信元から取ります）: %s\033[0m\n' "${missing[*]}"
fi
