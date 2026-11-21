#!/usr/bin/env bash
# 広告・解析・クラッシュ収集の SDK が Android アプリの依存に入っていないことを確かめる（SPEC §1.2・§12、PLAN 1.10）。
# 推移的な依存も含めて、リリースと debug の実行時クラスパスを調べる。CI（android.yml）で毎回実行する。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# group / artifact の部分一致。追加するときはコメントに理由を書く
FORBIDDEN=(
  'com\.google\.firebase'                      # Analytics / Crashlytics / Performance など
  'com\.google\.android\.gms:play-services-(ads|analytics|measurement|tagmanager)'
  'com\.google\.android\.ump'                   # 広告の同意 SDK
  'com\.crashlytics' 'io\.fabric'
  'io\.sentry' 'com\.bugsnag' 'com\.instabug' 'com\.newrelic' 'com\.datadoghq' 'com\.microsoft\.appcenter'
  'com\.facebook\.(android|infer\.annotation)' 'com\.appsflyer' 'com\.adjust' 'io\.branch'
  'com\.amplitude' 'com\.mixpanel' 'com\.segment' 'com\.flurry' 'com\.posthog'
  'com\.applovin' 'com\.unity3d\.ads' 'com\.ironsource' 'com\.mopub' 'com\.vungle' 'com\.chartboost'
  'com\.inmobi' 'com\.yandex\.(mobile|android)' 'com\.bytedance' 'com\.pangle'
)
pattern="$(IFS='|'; echo "${FORBIDDEN[*]}")"

out="$(mktemp)"
trap 'rm -f "$out"' EXIT
for conf in releaseRuntimeClasspath debugRuntimeClasspath; do
  (cd "$ROOT/android" && ./gradlew -q :app:dependencies --configuration "$conf") >> "$out"
done

[[ -s "$out" ]] || { echo "依存ツリーを取得できませんでした" >&2; exit 1; }
if grep -Eq "$pattern" "$out"; then
  echo "✘ 入れてはいけない SDK が依存に含まれています（SPEC §1.2）:" >&2
  grep -E "$pattern" "$out" | sed 's/^[ |+\\-]*//' | sort -u >&2
  exit 1
fi
echo "✔ 広告・解析・クラッシュ収集の SDK は含まれていません（$(grep -cE -- '--- ' "$out") 行を確認）"
