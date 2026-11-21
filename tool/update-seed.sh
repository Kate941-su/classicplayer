#!/usr/bin/env bash
# 同梱カタログ（シード。SPEC §2・§6）を差し替える。android/seed/catalog.json に置くと、
# 以降のビルド（tool/run.sh・./gradlew・iOS の xcodebuild）がそれをアプリに入れる（なければ fixtures/catalog.sample.json）。
#
#   tool/update-seed.sh                公開中のカタログ（GitHub Pages）を取得する
#   tool/update-seed.sh <catalog.json> 手元のファイルを使う（例: build_catalog.py の出力）
#   tool/update-seed.sh --clear        シードを消してフィクスチャに戻す
#
# どちらも validate_catalog.py で検査してから置く（比率の検査は外す）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/android/seed/catalog.json"
URL="https://kate941-su.github.io/classicplayer/v1/catalog.json"

die() { printf '\033[1;31m✘ %s\033[0m\n' "$*" >&2; exit 1; }

if [[ "${1:-}" == "--clear" ]]; then
  rm -f "$DEST"
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
