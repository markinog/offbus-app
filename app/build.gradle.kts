plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace  = "com.offbus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offbus"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("mbtiles", "geojson")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // MapLibre Native Android — mapas vetoriais offline
    implementation("org.maplibre.gl:android-sdk:11.8.4")

    // Servidor HTTP local para servir tiles MBTiles ao MapLibre
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Coroutines — operações assíncronas sem bloquear a UI
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Gson — leitura dos arquivos JSON de horários
    implementation("com.google.code.gson:gson:2.11.0")


    // UI
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.15.0")
}