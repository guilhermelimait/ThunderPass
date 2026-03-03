// Top-level build file — plugin declarations only (no dependencyResolutionManagement here;
// that belongs in settings.gradle.kts).
plugins {
    id("com.android.application")         version "8.5.2"  apply false
    id("org.jetbrains.kotlin.android")    version "2.0.21" apply false
    // Compose compiler plugin is separate from the Kotlin plugin in Kotlin 2.x
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
