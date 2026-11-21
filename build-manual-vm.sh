#!/usr/bin/env bash
# VM（ルカ環境）用 SDK 直接ビルド。Fedora 側の build-manual.sh の VM 適応版。
# 差分: SDK は ~/tools/android-sdk（platform android-34 / build-tools 34.0.0）を使う。
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/tools/android-sdk}"
BUILD_TOOLS="$SDK_ROOT/build-tools/34.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-34/android.jar"
APP_DIR="$PROJECT_DIR/app"
OUT_DIR="$APP_DIR/build/manual-vm"
DEBUG_KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"

for required in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$ANDROID_JAR" "$DEBUG_KEYSTORE"; do
    if [[ ! -e "$required" ]]; then
        printf 'Required file not found: %s\n' "$required" >&2
        exit 1
    fi
done

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/classes" "$OUT_DIR/dex" "$OUT_DIR/aligned" "$OUT_DIR/generated"

"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/src/main/res" -o "$OUT_DIR/res.zip"

"$BUILD_TOOLS/aapt2" link \
    -o "$OUT_DIR/app-unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
    --java "$OUT_DIR/generated" \
    --min-sdk-version 28 \
    --target-sdk-version 35 \
    --version-code 6 \
    --version-name 1.1.0 \
    "$OUT_DIR/res.zip"

mapfile -d '' JAVA_SOURCES < <(find "$APP_DIR/src/main/java" -type f -name '*.java' -print0)
mapfile -d '' GENERATED_SOURCES < <(find "$OUT_DIR/generated" -type f -name '*.java' -print0)
javac -encoding UTF-8 --release 11 -classpath "$ANDROID_JAR" -d "$OUT_DIR/classes" "${JAVA_SOURCES[@]}" "${GENERATED_SOURCES[@]}"

mapfile -d '' CLASS_FILES < <(find "$OUT_DIR/classes" -type f -name '*.class' -print0)
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 28 --output "$OUT_DIR/dex" "${CLASS_FILES[@]}"

cp "$OUT_DIR/app-unsigned.apk" "$OUT_DIR/app-with-dex.apk"
(cd "$OUT_DIR/dex" && zip -q -u "$OUT_DIR/app-with-dex.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT_DIR/app-with-dex.apk" "$OUT_DIR/aligned/BRAVIA-Remote.apk"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$DEBUG_KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT_DIR/aligned/BRAVIA-Remote-signed.apk" \
    "$OUT_DIR/aligned/BRAVIA-Remote.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_DIR/aligned/BRAVIA-Remote-signed.apk"

cp "$OUT_DIR/aligned/BRAVIA-Remote-signed.apk" "$PROJECT_DIR/BRAVIA-Remote-KJ-65X80J.apk"
sha256sum "$PROJECT_DIR/BRAVIA-Remote-KJ-65X80J.apk"
printf 'Built: %s\n' "$PROJECT_DIR/BRAVIA-Remote-KJ-65X80J.apk"
