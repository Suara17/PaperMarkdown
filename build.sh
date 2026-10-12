#!/bin/bash
set -e

PROJECT_DIR="/storage/emulated/0/Download/PaperMarkdown"
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$PROJECT_DIR/gen"
BIN_DIR="$PROJECT_DIR/bin"
ANDROID_JAR="$PROJECT_DIR/android-34.jar"
R8_JAR="$PROJECT_DIR/r8.jar"
KEYSTORE="$PROJECT_DIR/debug.keystore"
OUT_APK="$PROJECT_DIR/PaperMarkdown.apk"

echo "=== 1. 清理并初始化构建目录 ==="
rm -rf "$BUILD_DIR" "$GEN_DIR" "$BIN_DIR"
mkdir -p "$BUILD_DIR" "$GEN_DIR" "$BIN_DIR"

echo "=== 2. 生成 R.java 资源索引 ==="
aapt package -f -m \
    -J "$GEN_DIR" \
    -M "$PROJECT_DIR/AndroidManifest.xml" \
    -S "$PROJECT_DIR/res" \
    -I "$ANDROID_JAR"

echo "=== 3. 编译 Java 源码为 class 字节码 ==="
javac -encoding UTF-8 -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD_DIR" \
    $(find "$GEN_DIR" "$PROJECT_DIR/src" -name "*.java")

echo "=== 4. 转换 class 为 Dalvik dex (D8) ==="
java -cp "$R8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BIN_DIR" \
    $(find "$BUILD_DIR" -name "*.class")

echo "=== 5. 打包 APK 资源与 Assets ==="
aapt package -f \
    -M "$PROJECT_DIR/AndroidManifest.xml" \
    -S "$PROJECT_DIR/res" \
    -A "$PROJECT_DIR/assets" \
    -I "$ANDROID_JAR" \
    -F "$BIN_DIR/unsigned.apk"

cd "$BIN_DIR"
echo "=== 6. 添加 classes.dex ==="
aapt add "unsigned.apk" classes.dex
cd "$PROJECT_DIR"

echo "=== 7. 字节对齐 (zipalign) ==="
zipalign -f -p 4 "$BIN_DIR/unsigned.apk" "$BIN_DIR/aligned.apk"

echo "=== 8. 签名 APK ==="
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PaperMarkdown,O=Paper,C=CN"
fi

apksigner sign --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT_APK" \
    "$BIN_DIR/aligned.apk"

echo "=========================================="
echo "✅ 编译打包成功: $OUT_APK"
echo "=========================================="