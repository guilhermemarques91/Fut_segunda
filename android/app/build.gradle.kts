plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.futsegunda.live"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.futsegunda.live"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sem keystore própria ainda — assina com a debug key só pra dar pra instalar
            // por fora (sideload) sem passo extra. Troque por uma signingConfig de verdade
            // antes de reinstalar num celular novo no lugar do antigo (ver android/README.md).
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

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Imagens (foto de jogador, logo)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Rede — sem converter factory: JsonCodec (kotlinx.serialization) serializa/desserializa
    // RequestBody/ResponseBody à mão (ver network/JsonCodec.kt).
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Fila local (offline) e sincronização em segundo plano
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Token de login guardado de forma criptografada
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}

// ── Copia o APK pra uma pasta na raiz do projeto, já com nome fixo
// (app-fut-android-<versão>[-debug].apk) — em vez de caçar dentro de
// app/build/outputs/apk/.../  toda vez que gera uma build nova.
val distDir = rootProject.projectDir.parentFile?.resolve("builds") ?: rootProject.projectDir.resolve("builds")

tasks.register<Copy>("copyReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) { include("app-release.apk") }
    into(distDir)
    rename { "app-fut-android-${android.defaultConfig.versionName}.apk" }
}

tasks.register<Copy>("copyDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) { include("app-debug.apk") }
    into(distDir)
    rename { "app-fut-android-${android.defaultConfig.versionName}-debug.apk" }
}
