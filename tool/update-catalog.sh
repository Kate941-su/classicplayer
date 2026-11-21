#!/usr/bin/env bash
# カタログの月次更新（PLAN 0.7、SPEC §3.1）。オーナーの手元で実行する。
# GitHub Actions からは CMSL を巡回できない（robots.txt の判定で全面禁止になる）ため、手元で巡回し、
# 結果を site/v1/ に置いて PR にする。main にマージされると pages.yml が GitHub Pages に公開する。
#
#   tool/update-catalog.sh             巡回（差分。新しい記事だけ取得。間隔 1.5 秒）→ 検証 → site/v1/ に出力
#   tool/update-catalog.sh --offline   巡回せず、手元のキャッシュ（catalog/cache/）だけで作り直す
#                                      （overrides.json・genre_map.json・解析ルールを直したとき）
#   tool/update-catalog.sh --full      記事のキャッシュも取り直す（サイト全体。1,300 ページ超で 40 分ほど）
#
# 検証（validate_catalog.py）に通らなければ site/v1/ を変えずに止まる。曲数が前回（site/v1/manifest.json）より
# 10% 以上減った場合も止まる（CMSL のサイト構造が変わった可能性。PLAN「リスクと対策」）。
# 出力後は、表示される手順でブランチを切ってコミットし、PR を作る（main に直接 push しない）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CATALOG_DIR="$ROOT/catalog"
SITE_DIR="$ROOT/site/v1"
DELAY="1.5"

die() { printf '\033[1;31m✘ %s\033[0m\n' "$*" >&2; exit 1; }
info() { printf '\033[1;34m▶ %s\033[0m\n' "$*" >&2; }

mode="diff"
case "${1:-}" in
  "") ;;
  --offline) mode="offline" ;;
  --full) mode="full" ;;
  -h|--help) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) die "不明な引数: $1（--offline / --full / --help）" ;;
esac

command -v python3 > /dev/null || die "python3 が見つかりません（Python 3.9 以上が必要です）"
# 検証には jsonschema が要る（CLAUDE.md と同じく uv で用意する。なければ素の python3 に入っているものを使う）
if command -v uv > /dev/null; then
  py_dev=(uv run --quiet --python 3.12 --with-requirements "$CATALOG_DIR/requirements-dev.txt" python)
else
  python3 -c "import jsonschema" 2> /dev/null || die "検証に uv か jsonschema が必要です（brew install uv）"
  py_dev=(python3)
fi
if [[ "$mode" == "offline" && ! -d "$CATALOG_DIR/cache" ]]; then
  die "キャッシュ（catalog/cache/）がありません。先に引数なしで巡回してください"
fi

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

# 1. 巡回（build_catalog.py は標準ライブラリだけで動く）
args=(--cache "$CATALOG_DIR/cache" --delay "$DELAY" -o "$out/catalog.json")
case "$mode" in
  diff) args+=(--refresh-index); info "巡回します（差分。間隔 ${DELAY} 秒。中断しても、取得済みのページはキャッシュに残ります）" ;;
  full) rm -rf "$CATALOG_DIR/cache"; info "キャッシュを消して全体を巡回します（間隔 ${DELAY} 秒）" ;;
  offline) args+=(--offline); info "キャッシュだけで作り直します（通信しません）" ;;
esac
(cd "$CATALOG_DIR" && python3 build_catalog.py "${args[@]}")
# レポートは手元の調整用（catalog-tune スキル）。公開しない
cp "$out/catalog.report.json" "$CATALOG_DIR/catalog.report.json"

# 2. 検証（比率 5% 以下、前回からの曲数の減少 10% 未満）
info "検証します"
"${py_dev[@]}" "$CATALOG_DIR/validate_catalog.py" "$out/catalog.json" \
  --previous-manifest "$SITE_DIR/manifest.json" \
  --assets-dir "$SITE_DIR" \
  || die "検証に失敗しました。site/v1/ は変えていません（レポート: catalog/catalog.report.json）"

# 3. 中身が前回と同じ（generatedAt だけ違う）なら出力しない
if [[ -f "$SITE_DIR/catalog.json" ]] && python3 - "$SITE_DIR/catalog.json" "$out/catalog.json" <<'EOF'
import json, sys
a, b = (json.load(open(p, encoding="utf-8")) for p in sys.argv[1:3])
a.pop("generatedAt", None)
b.pop("generatedAt", None)
sys.exit(0 if a == b else 1)
EOF
then
  info "前回の公開（site/v1/）から変更はありません"
  exit 0
fi

# 4. manifest を作って site/v1/ に置く
mkdir -p "$SITE_DIR"
python3 "$CATALOG_DIR/make_manifest.py" "$out/catalog.json" -o "$out/manifest.json" --previous "$SITE_DIR/manifest.json"
cp "$out/catalog.json" "$out/manifest.json" "$SITE_DIR/"

version="$(python3 -c 'import json,sys; m=json.load(open(sys.argv[1])); print(m["catalogVersion"], m["trackCount"])' "$SITE_DIR/manifest.json")"
read -r catalog_version track_count <<< "$version"
cat >&2 <<EOF

$(printf '\033[1;32m✔ site/v1/ を更新しました（%s、%s 曲）\033[0m' "${catalog_version}" "${track_count}")
次の手順で PR にしてください（main に直接 push しない）:

  git switch -c catalog/${catalog_version}
  git add site/v1/catalog.json site/v1/manifest.json
  git commit -m "カタログ更新 ${catalog_version}（${track_count} 曲）"
  git push -u origin catalog/${catalog_version}
  gh pr create --fill

CI（catalog-test）が検証し、マージすると pages.yml が GitHub Pages に公開します。
EOF
