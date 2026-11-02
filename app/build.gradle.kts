import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.offplayer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.offplayer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 44
        versionName = "1.17.1"

        // Тестовая сборка: -PtestVersion=1.16.0.1.1 — отдельный пакет, ставится рядом с основным OFFPlayer
        // и не меняет нумерацию релизов. Пакет можно переопределить через -PtestPackage=…
        (findProperty("testVersion") as String?)?.let { v ->
            applicationId = (findProperty("testPackage") as String?) ?: "com.offplayer.build.app"
            versionName = v
        }
    }

    // Собственная подпись релиза: ключ в keystore/, пароли в keystore.properties (не коммитятся)
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // yt-dlp/python/ffmpeg поставляются как нативные библиотеки — отдельный APK на каждую ABI + универсальный
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor использует java.nio и java.time, которых нет на Android ниже 13
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // youtubedl-android запускает бинарники из nativeLibraryDir, поэтому .so должны распаковываться
        jniLibs.useLegacyPackaging = true
    }
}

// Имена APK: OFFPlayer-1.1.0-arm64-v8a.apk, OFFPlayer-1.1.0-universal.apk и т.д.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier ?: "universal"
            val suffix = if (variant.buildType == "debug") "-debug" else ""
            (output as VariantOutputImpl).outputFileName.set(
                output.versionName.map { "OFFPlayer-$it-$abi$suffix.apk" }
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-service:2.9.1")

    val media3 = "1.7.1"
    // Вкладка YouTube: списки, поиск, каналы и потоки роликов (как в NewPipe)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("androidx.media3:media3-exoplayer:$media3")
    // Прямые эфиры YouTube во вкладке YouTube приходят как HLS
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
}
