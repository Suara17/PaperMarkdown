#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$PROJECT_DIR/gen"
BIN_DIR="$PROJECT_DIR/bin"
OUT_APK="$PROJECT_DIR/PaperMarkdown.apk"
KEYSTORE="$PROJECT_DIR/debug.keystore"

# SDK 依赖目录：可用环境变量 ANDROID_SDK_DIR 覆盖，默认 /tmp/sdk
SDK_DIR="${ANDROID_SDK_DIR:-/tmp/sdk}"
ANDROID_JAR="$SDK_DIR/android.jar"
R8_JAR="$SDK_DIR/r8.jar"

# 兼容本地已放到项目根目录的 jar
[ -f "$PROJECT_DIR/android-34.jar" ] && ANDROID_JAR="$PROJECT_DIR/android-34.jar"
[ -f "$PROJECT_DIR/r8.jar" ] && R8_JAR="$PROJECT_DIR/r8.jar"

if [ ! -f "$ANDROID_JAR" ]; then
    echo "❌ 缺少 android.jar（期望 $ANDROID_JAR）"
    echo "   参考：mkdir -p $SDK_DIR && \\"
    echo "   curl -L -o $SDK_DIR/android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar"
    exit 1
fi
if [ ! -f "$R8_JAR" ]; then
    echo "❌ 缺少 r8.jar（期望 $R8_JAR，内含 D8）"
    echo "   可从 Google Maven com.android.tools:r8 获取"
    exit 1
fi

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
javac -encoding UTF-8 --release 8 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR" \
    $(find "$GEN_DIR" "$PROJECT_DIR/src" -name "*.java")

echo "=== 4. 打包 classes 并用 D8 转为 classes.dex ==="
python3 - "$BUILD_DIR" "$BIN_DIR" <<'PY'
import sys, zipfile, glob, os
build_dir, bin_dir = sys.argv[1], sys.argv[2]
jar_path = os.path.join(bin_dir, "classes.jar")
with zipfile.ZipFile(jar_path, "w") as z:
    for p in glob.glob(os.path.join(build_dir, "**", "*.class"), recursive=True):
        z.write(p, os.path.relpath(p, build_dir))
print("classes.jar:", jar_path)
PY

java -Xmx512m -cp "$R8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BIN_DIR" \
    "$BIN_DIR/classes.jar"

echo "=== 5. 打包 APK 资源与 Assets ==="
aapt package -f \
    -M "$PROJECT_DIR/AndroidManifest.xml" \
    -S "$PROJECT_DIR/res" \
    -A "$PROJECT_DIR/assets" \
    -I "$ANDROID_JAR" \
    -F "$BIN_DIR/unsigned.apk"

echo "=== 6. 注入 classes.dex ==="
python3 - "$BIN_DIR" <<'PY'
import sys, zipfile, os
bin_dir = sys.argv[1]
src = os.path.join(bin_dir, "unsigned.apk")
dst = os.path.join(bin_dir, "with_dex.apk")
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for item in zi.infolist():
        zo.writestr(item, zi.read(item.filename))
    zo.write(os.path.join(bin_dir, "classes.dex"), "classes.dex")
print("merged:", dst)
PY

echo "=== 7. 字节对齐 (zipalign) ==="
zipalign -f 4 "$BIN_DIR/with_dex.apk" "$BIN_DIR/aligned.apk"

echo "=== 8. 签名 APK ==="
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android \
        -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
        -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

apksigner sign --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$OUT_APK" \
    "$BIN_DIR/aligned.apk"

echo "=========================================="
echo "✅ 编译打包成功: $OUT_APK"
apksigner verify --print-certs "$OUT_APK" | head -2
echo "=========================================="
