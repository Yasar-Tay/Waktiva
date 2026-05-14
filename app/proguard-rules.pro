# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

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

-dontwarn edu.umd.cs.findbugs.annotations.Nullable

# ──────────────────────────────────────────────────────────────────────────────
# Gson / Retrofit — keep all DTO data classes used for JSON deserialization.
# R8 renames Kotlin data class fields even when @SerializedName is present,
# because Gson reads fields via reflection. Without these rules the JSON parser
# silently returns null fields, breaking weather data and prayer time parsing.
# ──────────────────────────────────────────────────────────────────────────────

# Keep Gson annotations so @SerializedName is honoured at runtime
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep all DTO classes in the remote package (Gson reads them reflectively)
-keep class com.ybugmobile.waktiva.data.remote.dto.** { *; }

# Keep Retrofit API service interfaces (Retrofit generates implementations at runtime)
-keep interface com.ybugmobile.waktiva.data.remote.** { *; }

# Keep all domain model classes (used across Flows, StateFlows, and Glance widgets)
-keep class com.ybugmobile.waktiva.domain.model.** { *; }

# Keep WeatherCondition enum (used in Theme.kt gradient logic; if renamed the
# `when` branches silently fall through to UNKNOWN, hiding weather backgrounds)
-keepclassmembers enum com.ybugmobile.waktiva.domain.model.WeatherCondition { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin data classes — prevent R8 from removing synthetic copy/component methods
# ──────────────────────────────────────────────────────────────────────────────
-keepclassmembers class com.ybugmobile.waktiva.** {
    # data class generated methods
    public synthetic ** copy$default(...);
    public ** component*();
    public ** copy(...);
}

# ──────────────────────────────────────────────────────────────────────────────
# Hilt / Dagger — EntryPoint interfaces (used by WaktivaWidget without injection)
# ──────────────────────────────────────────────────────────────────────────────
-keep @dagger.hilt.InstallIn class * { *; }
-keep interface * extends dagger.hilt.EntryPoint { *; }