# 保留美境特权 Instrumentation 组件与反射入口
-keep class app.stateside.env.privileged.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }

# libadb & SPAKE2 JNI 原生桥接（防止 Release R8 混淆导致无线配对 JNI 崩溃）
-keep class io.github.muntashirakon.adb.** { *; }
-keep class io.github.muntashirakon.crypto.spake2.** { *; }
-dontwarn io.github.muntashirakon.adb.**
-dontwarn io.github.muntashirakon.crypto.spake2.**

# Android 系统原生 Conscrypt TLS 1.3 + exportKeyingMaterial 反射入口
-keep class com.android.org.conscrypt.** { *; }
-dontwarn com.android.org.conscrypt.**
-dontwarn org.conscrypt.**

# BouncyCastle ASN.1 X.509 证书构建
-keep class org.bouncycastle.asn1.** { *; }
-dontwarn org.bouncycastle.**
