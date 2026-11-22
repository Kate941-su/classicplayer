#!/usr/bin/env bash
# ストアの掲載用スクリーンショットを作る（store-listing スキル。fastlane/STORE_LISTING.md）。
#   1. 素の画面を撮る（公開中のカタログ site/v1/catalog.json を同梱して撮る）
#      - Android: Robolectric（StoreScreenshotsTest）→ build/store_screenshots/raw-android/<ja|en>/<phone|tablet>/
#      - iOS: UI テスト（MainFlowUITests）を iPhone 6.9" と iPad 13" のシミュレーターで → build/store_screenshots/raw-ios/
#   2. 見出しと端末の枠を付ける（fastlane/store_assets.json）→ fastlane/screenshots/ios/・fastlane/metadata/android/*/images/
#
# 使い方: tool/store_screenshots.sh [android|ios|compose]...（引数なしは全部）
# iOS の player はフルプレイヤーで音源を読み込むので、ネットワークが必要。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/store_screenshots"
IPHONE="${STORE_IPHONE:-iPhone 18 Pro Max}"      # 6.9 インチ
IPAD="${STORE_IPAD:-iPad Pro 13-inch (M5)}"       # 13 インチ
SHOTS=(home genre composer work player)

need() { command -v "$1" >/dev/null || { echo "エラー: $1 がありません（$2）" >&2; exit 1; }; }

android() {
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "(17|21)'; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
  rm -rf "$OUT/raw-android"
  (cd "$ROOT/android" && ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotsTest' -Pcmsl.storeScreenshots --console=plain -q)
  echo "Android: $(find "$OUT/raw-android" -name '*.png' | wc -l | tr -d ' ') 枚"
}

ios_device() { # <シミュレーター名> <phone|tablet>
  local name="$1" kind="$2" udid tmp
  udid="$(xcrun simctl list devices available | grep -F "    $name (" | head -1 | grep -Eo '[0-9A-F-]{36}')" ||
    { echo "エラー: シミュレーター「$name」がありません（xcrun simctl list devices available。STORE_IPHONE / STORE_IPAD で変えられる）" >&2; exit 1; }
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  # ストアの画像らしいステータスバー（9:41・電波と電池は満タン）
  xcrun simctl status_bar "$udid" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 \
    --cellularMode notSupported --batteryState charged --batteryLevel 100
  tmp="$OUT/tmp-$kind"
  rm -rf "$tmp" && mkdir -p "$tmp"
  # 撮り終えたあと（情報タブの操作）で失敗しても、必要な画面がそろっていればよい
  (cd "$ROOT/ios" && TEST_RUNNER_SCREENSHOTS_DIR="$tmp" xcodebuild -scheme CMSLClassical -destination "id=$udid" \
    -derivedDataPath "$OUT/DerivedData" CMSL_SEED_CATALOG="$ROOT/site/v1/catalog.json" \
    -only-testing:CMSLClassicalUITests/MainFlowUITests/testBrowseAndPlayJapaneseLight \
    -only-testing:CMSLClassicalUITests/MainFlowUITests/testBrowseAndPlayEnglishLight \
    -quiet test) || echo "注意: $name の UI テストが失敗しました。撮れた画面を確認します"
  xcrun simctl status_bar "$udid" clear
  for lang in ja en; do
    mkdir -p "$OUT/raw-ios/$lang/$kind"
    for s in "${SHOTS[@]}"; do
      [[ -f "$tmp/$lang-light-$s.png" ]] || { echo "エラー: $name で $lang-light-$s.png が撮れていません" >&2; exit 1; }
      cp "$tmp/$lang-light-$s.png" "$OUT/raw-ios/$lang/$kind/$s.png"
    done
  done
  rm -rf "$tmp"
}

ios() {
  need xcrun "Xcode を入れてください"
  rm -rf "$OUT/raw-ios"
  ios_device "$IPHONE" phone
  ios_device "$IPAD" tablet
  echo "iOS: $(find "$OUT/raw-ios" -name '*.png' | wc -l | tr -d ' ') 枚"
}

compose() {
  need uv "brew install uv"
  (cd "$ROOT" && uv run -q --with pillow python "$HOME/.claude/skills/store-listing/scripts/compose.py" fastlane/store_assets.json)
  (cd "$ROOT" && uv run -q --with pillow python "$HOME/.claude/skills/store-listing/scripts/check.py")
}

steps=("$@")
[[ ${#steps[@]} -gt 0 ]] || steps=(android ios compose)
for s in "${steps[@]}"; do
  case "$s" in
    android|ios|compose) "$s" ;;
    *) echo "使い方: $0 [android|ios|compose]..." >&2; exit 2 ;;
  esac
done
