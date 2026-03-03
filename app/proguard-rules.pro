# Add project specific ProGuard rules here.
# By default, the flags in this file are applied after the default ProGuard
# rules from the Android Gradle plugin (proguard-android-optimize.txt).

# Keep ThunderPass-specific classes
-keep class com.thunderpass.** { *; }

# Room — keep generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
