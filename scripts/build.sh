#!/usr/bin/env bash
# Builds app-release.apk with the Android SDK command line tools only.
# No Gradle, no Maven, no network.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "error: set ANDROID_HOME or ANDROID_SDK_ROOT" >&2; exit 1; }
[ -d "$SDK" ] || { echo "error: Android SDK not found: $SDK" >&2; exit 1; }

BT="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -n1)"
PLATFORM="${PLATFORM:-android-36}"
JAR="$SDK/platforms/$PLATFORM/android.jar"
[ -f "$JAR" ] || JAR="$(find "$SDK/platforms" -maxdepth 2 -name android.jar | sort -V | tail -n1)"
[ -f "$JAR" ] || { echo "error: android.jar not found" >&2; exit 1; }
for tool in aapt2 d8 zipalign apksigner; do
  [ -x "$BT/$tool" ] || { echo "error: $BT/$tool missing" >&2; exit 1; }
done

echo "==> SDK $SDK"
echo "==> build-tools $(basename "$BT")"
echo "==> platform $(basename "$(dirname "$JAR")")"

rm -rf build dist
mkdir -p build/gen build/classes build/dex build/res build/test dist

echo "==> compiling resources"
"$BT/aapt2" compile --dir res -o build/res/resources.zip
"$BT/aapt2" link -o build/app-unsigned.apk \
  -I "$JAR" \
  --manifest AndroidManifest.xml \
  -R build/res/resources.zip \
  --java build/gen \
  --min-sdk-version 26 \
  --target-sdk-version 36 \
  --version-code 1 \
  --version-name 1.0.0 \
  --auto-add-overlay

echo "==> compiling Java"
find src build/gen -name '*.java' | sort > build/sources.txt
javac --release 8 -encoding UTF-8 \
  -classpath "$JAR" \
  -d build/classes \
  @build/sources.txt

echo "==> dexing"
mapfile -t CLASSES < <(find build/classes -name '*.class' | sort)
"$BT/d8" --lib "$JAR" --min-api 26 --output build/dex "${CLASSES[@]}"
(cd build/dex && zip -q -X ../app-unsigned.apk classes.dex)

echo "==> aligning"
"$BT/zipalign" -f -p 4 build/app-unsigned.apk build/app-aligned.apk

KEYSTORE="${KEYSTORE_PATH:-$HOME/.android/plugin251428.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  echo "==> creating signing key $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" \
    -alias plugin251428 -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Plugin251428, O=mmunki, C=US" >/dev/null 2>&1
fi

echo "==> signing"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias plugin251428 \
  --ks-pass pass:android --key-pass pass:android \
  --out dist/app-release.apk build/app-aligned.apk
"$BT/apksigner" verify --verbose dist/app-release.apk > build/verify.txt
grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" build/verify.txt \
  || grep -q "Verified using v1 scheme (JAR signing): true" build/verify.txt

echo "==> unit tests"
javac -encoding UTF-8 -d build/test \
  src/com/mmunki/plugin251428/core/DoublePressDetector.java \
  src/com/mmunki/plugin251428/core/VisionFrame.java \
  src/com/mmunki/plugin251428/core/GamePlan.java \
  src/com/mmunki/plugin251428/core/GameConfig.java \
  src/com/mmunki/plugin251428/core/SoccerVision.java \
  src/com/mmunki/plugin251428/core/SoccerBrain.java \
  src/com/mmunki/plugin251428/core/BotLog.java \
  tests/CoreTests.java
java -ea -cp build/test CoreTests

echo "==> built $(pwd)/dist/app-release.apk ($(wc -c < dist/app-release.apk) bytes)"
sha256sum dist/app-release.apk
