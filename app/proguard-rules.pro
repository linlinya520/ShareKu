# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ═══ Shizuku UserService 反射实例化（Shizuku 服务端以 shell 身份加载） ═══
# 必须保留类名 + 无参构造函数，否则 R8 混淆后 Shizuku 无法启动 UserService
-keep class com.linjing.shareku.service.ShizukuFileService { *; }
-keepclassmembers class com.linjing.shareku.service.ShizukuFileService {
    public <init>();
}

# ═══ Binder 接口（手写 Parcel 协议，通过 transact/onTransact 调用） ═══
-keep class com.linjing.shareku.service.IRemoteFileService { *; }
-keep class com.linjing.shareku.service.IRemoteFileService$Stub { *; }

# ═══ Ktor / kotlinx.serialization ═══
# Ktor 与 kotlinx.serialization 自带 consumer rules，一般无需额外配置；
# 若 R8 后 Ktor 报反射错误，可在此追加 keep。
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# R8: Android 无 java.lang.management（Ktor 调试探测引用）
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
