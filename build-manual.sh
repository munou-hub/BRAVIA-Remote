#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-/home/matumotokennsinn/Android/Sdk}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-36.0.0}"
ANDROID_API="${ANDROID_API:-37}"
MIN_API="${MIN_API:-28}"
TARGET_API="${TARGET_API:-35}"
BUILD_TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$SDK_ROOT/platforms/android-$ANDROID_API.0/android.jar"
APP_DIR="$PROJECT_DIR/app"
OUT_PARENT="$APP_DIR/build"
ANDROID_USER_DIR="${ANDROID_USER_HOME:-${HOME}/.android}"
DEBUG_KEYSTORE="$ANDROID_USER_DIR/debug.keystore"

for required in "$BUILD_TOOLS/aapt2" "$BUILD_TOOLS/d8" "$BUILD_TOOLS/zipalign" "$BUILD_TOOLS/apksigner" "$ANDROID_JAR" "$DEBUG_KEYSTORE"; do
    if [[ ! -e "$required" ]]; then
        printf 'Required file not found: %s\n' "$required" >&2
        exit 1
    fi
done

mkdir -p "$OUT_PARENT"
OUT_DIR="${OUT_DIR:-$(mktemp -d "$OUT_PARENT/manual-handoff.XXXXXX")}"
mkdir -p "$OUT_DIR/classes" "$OUT_DIR/dex" "$OUT_DIR/aligned" "$OUT_DIR/resources" "$OUT_DIR/generated"

"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/src/main/res" -o "$OUT_DIR/resources"
mapfile -d '' RESOURCE_FILES < <(find "$OUT_DIR/resources" -type f -name '*.flat' -print0)
RESOURCE_ARGS=()
for resource in "${RESOURCE_FILES[@]}"; do RESOURCE_ARGS+=(-R "$resource"); done

"$BUILD_TOOLS/aapt2" link \
    -o "$OUT_DIR/app-unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
    "${RESOURCE_ARGS[@]}" \
    --java "$OUT_DIR/generated" \
    --min-sdk-version "$MIN_API" \
    --target-sdk-version "$TARGET_API" \
    --version-code 6 \
    --version-name 1.1.0

mapfile -d '' JAVA_SOURCES < <(find "$APP_DIR/src/main/java" -type f -name '*.java' -print0)
mapfile -d '' GENERATED_SOURCES < <(find "$OUT_DIR/generated" -type f -name '*.java' -print0)
javac --release 11 -classpath "$ANDROID_JAR" -d "$OUT_DIR/classes" "${JAVA_SOURCES[@]}" "${GENERATED_SOURCES[@]}"

mapfile -d '' CLASS_FILES < <(find "$OUT_DIR/classes" -type f -name '*.class' -print0)
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api "$MIN_API" --output "$OUT_DIR/dex" "${CLASS_FILES[@]}"

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
