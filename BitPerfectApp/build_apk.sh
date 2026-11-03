#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

export PATH=/home/soju/.local/jdk-17/bin:$PATH
BUILD_TOOLS=/home/soju/.local/android-sdk/build-tools/34.0.0
ANDROID_JAR=/home/soju/.local/android-sdk/platforms/android-34/android.jar

echo "=== 1. Cleaning build directory ==="
rm -rf build gen obj compiled_res.zip unaligned.apk aligned.apk BitPerfectMonitor.apk classes.dex
mkdir -p build/gen build/obj build/res_compiled

echo "=== 2. Compiling resources (aapt2 compile) ==="
"$BUILD_TOOLS/aapt2" compile --dir res -o compiled_res.zip

echo "=== 3. Linking resources (aapt2 link) ==="
"$BUILD_TOOLS/aapt2" link -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -o unaligned.apk \
    --java build/gen \
    compiled_res.zip

echo "=== 4. Compiling Java sources (javac) ==="
javac -d build/obj \
    -cp "$ANDROID_JAR" \
    --release 17 \
    $(find src build/gen -name "*.java")

echo "=== 5. Converting to Dalvik Executable (d8) ==="
"$BUILD_TOOLS/d8" --min-api 26 \
    --output . \
    $(find build/obj -name "*.class")

echo "=== 6. Packaging DEX into APK ==="
jar -uf unaligned.apk classes.dex

echo "=== 7. Aligning APK (zipalign) ==="
"$BUILD_TOOLS/zipalign" -f -p 4 unaligned.apk aligned.apk

echo "=== 8. Signing APK (apksigner) ==="
if [ ! -f debug.keystore ]; then
    keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign --ks debug.keystore \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out ../BitPerfectMonitor.apk \
    aligned.apk

echo "=== BUILD SUCCESSFUL ==="
ls -lh ../BitPerfectMonitor.apk
