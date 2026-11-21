#!/usr/bin/env bash
# 実機・エミュレーター・シミュレーターでデバッグ版を起動する。
#
#   tool/run.sh                 接続中の Android 実機があればそれ、なければ Android エミュレーター（なければ起動）
#   tool/run.sh android         Android エミュレーター（なければ起動）
#   tool/run.sh ios             iOS シミュレーター（なければ起動）
#   tool/run.sh --device <id>   デバイスを指定（adb のシリアル / シミュレーターや iPhone の UDID）
#   tool/run.sh --list          使えるデバイス・エミュレーター・シミュレーターの一覧
#   tool/run.sh --fresh         アプリのデータを消してから起動
#   tool/run.sh --seed <path>   同梱カタログ（シード）を差し替えてビルド（例: 手元で巡回した catalog.json）
#   tool/run.sh --logs          起動後に Android の logcat を表示（Ctrl-C で終了）
#   tool/run.sh -- <args>       残りの引数を ./gradlew（Android）/ xcodebuild（iOS）にそのまま渡す
#
# 環境変数: CMSL_AVD（使う AVD の名前）、ANDROID_HOME、JAVA_HOME
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_ID="app.cmsl.classical"
ACTIVITY="$APP_ID/.MainActivity"
IOS_PROJECT="$ROOT/ios/CMSLClassical.xcodeproj"
IOS_SCHEME="CMSLClassical"

info() { printf '\033[1;34m▶ %s\033[0m\n' "$*"; }
ok() { printf '\033[1;32m✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m⚠ %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m✘ %s\033[0m\n' "$*" >&2; exit 1; }

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

PLATFORM=""
DEVICE=""
FRESH=false
LIST=false
LOGS=false
SEED=""
EXTRA=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    android | ios) PLATFORM="$1" ;;
    --device) DEVICE="${2:?--device には ID が必要です}"; shift ;;
    --list) LIST=true ;;
    --fresh) FRESH=true ;;
    --logs) LOGS=true ;;
    --seed) SEED="${2:?--seed にはファイルのパスが必要です}"; shift ;;
    -h | --help) usage ;;
    --) shift; EXTRA=("$@"); break ;;
    *) die "不明な引数: $1（--help で使い方）" ;;
  esac
  shift
done

# ── 前提の確認 ─────────────────────────────────────────
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"

need_android() {
  if [[ -z "${JAVA_HOME:-}" ]]; then
    for j in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 /opt/homebrew/opt/openjdk; do
      [[ -x "$j/bin/java" ]] && export JAVA_HOME="$j" && break
    done
  fi
  [[ -x "${JAVA_HOME:-}/bin/java" ]] || die "JDK 17 以上が見つかりません。brew install openjdk@21 するか、JAVA_HOME を設定してください"
  [[ -x "$ADB" ]] || die "adb が見つかりません（${ADB}）。Android SDK の platform-tools を入れるか、ANDROID_HOME を設定してください"
  [[ -f "$ROOT/android/local.properties" ]] || echo "sdk.dir=$SDK" > "$ROOT/android/local.properties"
}

need_ios() {
  command -v xcrun > /dev/null || die "xcrun が見つかりません。Xcode とコマンドラインツールを入れてください"
  command -v xcodebuild > /dev/null || die "xcodebuild が見つかりません。Xcode を入れてください"
}

# ── 一覧 ───────────────────────────────────────────────
if [[ "$LIST" == true ]]; then
  if [[ -x "$ADB" ]]; then
    info "Android: 接続中のデバイス"; "$ADB" devices -l | tail -n +2
    [[ -x "$EMULATOR" ]] && { info "Android: エミュレーター（AVD）"; "$EMULATOR" -list-avds; }
  else
    warn "Android SDK が見つかりません（${SDK}）"
  fi
  if command -v xcrun > /dev/null; then
    info "iOS: シミュレーター"; xcrun simctl list devices available | grep -E "iPhone|iPad" || true
    info "iOS: 実機"; xcrun devicectl list devices 2> /dev/null | grep -i physical || echo "（なし）"
  fi
  exit 0
fi

# ── Android ────────────────────────────────────────────
android_serials() { "$ADB" devices | awk 'NR>1 && $2=="device" {print $1}'; }

start_emulator() {
  [[ -x "$EMULATOR" ]] || die "emulator が見つかりません（${EMULATOR}）。Android SDK の emulator を入れてください"
  local avd="${CMSL_AVD:-$("$EMULATOR" -list-avds | head -1)}"
  [[ -n "$avd" ]] || die "AVD がありません。Android Studio の Device Manager で作成してください"
  info "エミュレーターを起動します: $avd"
  # スナップショットは保存しない（RAM の内容を数 GB 書き出すため。ディスクの空きが少ない環境で埋まる）
  "$EMULATOR" -avd "$avd" -no-snapshot-save -netdelay none -netspeed full > /dev/null 2>&1 &
  "$ADB" wait-for-device
  for _ in $(seq 1 90); do
    [[ "$("$ADB" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" == "1" ]] && break
    sleep 2
  done
  [[ "$("$ADB" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" == "1" ]] || die "エミュレーターの起動がタイムアウトしました"
  ok "エミュレーターが起動しました"
}

pick_android() {
  if [[ -n "$DEVICE" ]]; then
    android_serials | grep -qx "$DEVICE" || die "Android デバイス $DEVICE が見つかりません（--list で確認）"
    SERIAL="$DEVICE"; return
  fi
  local serials; serials="$(android_serials)"
  if [[ "$PLATFORM" != "android" ]]; then
    local physical; physical="$(grep -v '^emulator-' <<< "$serials" | head -1 || true)"
    [[ -n "$physical" ]] && { SERIAL="$physical"; info "実機を使います: $SERIAL"; return; }
  fi
  local emu; emu="$(grep '^emulator-' <<< "$serials" | head -1 || true)"
  if [[ -z "$emu" ]]; then
    start_emulator
    emu="$(android_serials | grep '^emulator-' | head -1)"
  fi
  SERIAL="$emu"
  info "エミュレーターを使います: $SERIAL"
}

run_android() {
  need_android
  pick_android
  local args=(":app:installDebug" "--console=plain")
  if [[ -n "$SEED" ]]; then
    [[ -f "$SEED" ]] || die "シードのファイルがありません: $SEED"
    args+=("-Pcmsl.seedCatalog=$(cd "$(dirname "$SEED")" && pwd)/$(basename "$SEED")")
  fi
  info "ビルドしてインストールします"
  (cd "$ROOT/android" && ANDROID_SERIAL="$SERIAL" ./gradlew "${args[@]}" ${EXTRA[@]+"${EXTRA[@]}"}) || die "ビルドかインストールに失敗しました"
  if [[ "$FRESH" == true ]]; then
    info "アプリのデータを消します"
    "$ADB" -s "$SERIAL" shell pm clear "$APP_ID" > /dev/null
  fi
  "$ADB" -s "$SERIAL" shell am start -n "$ACTIVITY" > /dev/null
  ok "起動しました（${SERIAL}）"
  if [[ "$LOGS" == true ]]; then
    sleep 1
    local pid; pid="$("$ADB" -s "$SERIAL" shell pidof -s "$APP_ID" | tr -d '\r')"
    info "logcat を表示します（Ctrl-C で終了）"
    "$ADB" -s "$SERIAL" logcat --pid="$pid"
  fi
}

# ── iOS ────────────────────────────────────────────────
run_ios() {
  need_ios
  [[ -d "$IOS_PROJECT" ]] || die "iOS アプリはまだありません（PLAN Phase 2 で ios/CMSLClassical.xcodeproj を作成します）"
  local seed_args=()
  if [[ -n "$SEED" ]]; then
    [[ -f "$SEED" ]] || die "シードのファイルがありません: $SEED"
    seed_args=("CMSL_SEED_CATALOG=$(cd "$(dirname "$SEED")" && pwd)/$(basename "$SEED")")
  fi

  local udid="$DEVICE" physical=false
  if [[ -n "$udid" ]]; then
    xcrun devicectl list devices 2> /dev/null | grep -q "$udid" && physical=true
  else
    udid="$(xcrun simctl list devices booted | grep -Eo '\(([0-9A-F-]{36})\) \(Booted\)' | head -1 | grep -Eo '[0-9A-F-]{36}' || true)"
    if [[ -z "$udid" ]]; then
      udid="$(xcrun simctl list devices available | grep -E '^\s+iPhone' | head -1 | grep -Eo '[0-9A-F-]{36}' || true)"
      [[ -n "$udid" ]] || die "使える iPhone シミュレーターがありません。Xcode の Settings > Components で iOS のランタイムを入れてください"
      info "シミュレーターを起動します: $udid"
      xcrun simctl boot "$udid"
    fi
    # Xcode 27 からは Simulator.app の代わりに DeviceHub.app で画面を表示する
    open -a Simulator 2> /dev/null || open -a DeviceHub 2> /dev/null || warn "シミュレーターの画面を開けませんでした（起動は続けます）"
  fi

  local derived="$ROOT/ios/build/run"
  local sdk="iphonesimulator" dest="id=$udid" config_dir="Debug-iphonesimulator"
  [[ "$physical" == true ]] && { sdk="iphoneos"; config_dir="Debug-iphoneos"; }
  info "ビルドします（${sdk}）"
  xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" -configuration Debug -sdk "$sdk" \
    -destination "$dest" -derivedDataPath "$derived" ${seed_args[@]+"${seed_args[@]}"} ${EXTRA[@]+"${EXTRA[@]}"} build -quiet || die "ビルドに失敗しました"
  local app; app="$(find "$derived/Build/Products/$config_dir" -maxdepth 1 -name '*.app' | head -1)"
  [[ -n "$app" ]] || die "ビルドした .app が見つかりません"
  local bundle; bundle="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")"

  if [[ "$physical" == true ]]; then
    [[ "$FRESH" == true ]] && xcrun devicectl device uninstall app --device "$udid" "$bundle" > /dev/null 2>&1 || true
    xcrun devicectl device install app --device "$udid" "$app" > /dev/null
    xcrun devicectl device process launch --device "$udid" "$bundle" > /dev/null
  else
    [[ "$FRESH" == true ]] && xcrun simctl uninstall "$udid" "$bundle" || true
    xcrun simctl install "$udid" "$app"
    xcrun simctl launch "$udid" "$bundle" > /dev/null
  fi
  ok "起動しました（${udid}）"
}

case "$PLATFORM" in
  ios) run_ios ;;
  *) run_android ;;
esac
