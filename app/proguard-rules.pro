# Add project specific ProGuard rules here.
# By default, the flags in this file are applied after the default ProGuard
# rules from the Android Gradle plugin (proguard-android-optimize.txt).

# ─── Room ────────────────────────────────────────────────────────────────────
# Entity field names appear verbatim in SQL — they must not be obfuscated.
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
# DAO interfaces are implemented by Room-generated *_Impl classes.
-keep @androidx.room.Dao class * { *; }
# Room-generated database and DAO implementations (loaded by class name at runtime).
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl { *; }

# ─── Kotlin Coroutines ───────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ─── Strip all android.util.Log calls from release APK ───────────────────────
# Prevents MAC addresses, rotating IDs, and internal state from leaking to
# any app holding READ_LOGS permission or via `adb logcat`.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
