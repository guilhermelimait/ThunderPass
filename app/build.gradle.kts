plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.thunderpass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thunderpass"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // RetroAchievements API credentials — set in local.properties or CI secrets
        buildConfigField("String", "RA_API_KEY",
            "\"${(project.findProperty("ra.apiKey") as String?) ?: ""}\"")
        buildConfigField("String", "RA_API_USER",
            "\"${(project.findProperty("ra.apiUser") as String?) ?: ""}\"")

        // Supabase — set in local.properties (supabase.url / supabase.anonKey)
        buildConfigField("String", "SUPABASE_URL",
            "\"${(project.findProperty("supabase.url") as String?) ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${(project.findProperty("supabase.anonKey") as String?) ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room schema export directory — keep schemas in source control for migration audits
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    // Compose BOM — pins all Compose library versions consistently
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle + ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room (local DB for encounters)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    // org.json is part of Android's runtime — no extra dep needed

    // Coil 3 — image loading with SVG support for DiceBear avatars
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Retrofit + Moshi — RetroAchievements API client
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Jetpack Glance — home screen widget
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Encrypted SharedPreferences — secure storage for RA API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Supabase — cloud auth (Email OTP) + profile sync
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.1.4")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    // Ktor HTTP client engine required by Supabase SDK on Android
    implementation("io.ktor:ktor-client-android:3.0.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // MockK for faking DAOs without needing a real Android runtime
    testImplementation("io.mockk:mockk:1.13.12")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
