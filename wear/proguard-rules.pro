# Preserve line number information for readable crash stacks (Play Console)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# OkHttp 5.x Rules
# ============================================
# OkHttp 5 ships its own R8/ProGuard rules via META-INF.
# These suppress warnings for optional platform-specific TLS providers.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================
# Media3 / ExoPlayer Rules
# ============================================
# Media3 ships consumer rules for the classes it loads by reflection.
-dontwarn androidx.media3.**

# ============================================
# Kotlin Coroutines
# ============================================
-dontwarn kotlinx.coroutines.**

# ============================================
# Aggressive optimizations for release
# ============================================
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Strip out Kotlin metadata and runtime null-check intrinsics
-dontwarn kotlin.Metadata
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
}

# Remove unused Compose debugging info
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    public static boolean isTraceInProgress();
    public static void traceEventStart(...);
    public static void traceEventEnd();
}
