plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.futsegunda.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.futsegunda.wear"
        minSdk = 30 // Wear OS 3 (Galaxy Watch4 = API 30+)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sem keystore própria ainda — assina com a debug key só pra dar pra instalar
            // por fora (sideload via adb) sem passo extra. Ver wearos/README.md.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Wear Compose (independente do compose-bom do app de celular)
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")

    // Entrada de texto (teclado/voz) para o login na tela pequena
    implementation("androidx.wear:wear-input:1.1.0")

    // Play Services (necessário mesmo em app "standalone" para APIs do Wear)
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Rede — sem converter factory: JsonCodec (kotlinx.serialization) serializa/desserializa
    // RequestBody/ResponseBody à mão (ver network/JsonCodec.kt).
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Fila offline e sincronização em segundo plano
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}

// ── Copia o APK pra pasta builds/ na raiz do projeto, nome fixo
// (app-fut-watch-<versão>[-debug].apk) — mesmo esquema do app/build.gradle.kts do celular.
val distDir = rootProject.projectDir.parentFile?.resolve("builds") ?: rootProject.projectDir.resolve("builds")

tasks.register<Copy>("copyReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("app-release.apk") }
    into(distDir)
    rename { "app-fut-watch-${android.defaultConfig.versionName}.apk" }
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) { include("app-debug.apk") }
    into(distDir)
    rename { "app-fut-watch-${android.defaultConfig.versionName}-debug.apk" }
}
