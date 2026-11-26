plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = providers.gradleProperty("appVersionName").orElse("1.0.0").get()
val appVersionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()
val ndkVersionUsed = "27.0.12077973"

android {
    namespace = "io.github.robin19666.zapretmanager"
    compileSdk = 35
    ndkVersion = ndkVersionUsed

    defaultConfig {
        applicationId = "io.github.robin19666.zapretmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                // byedpi собирается как libzapretnative.so
                arguments += listOf("-DANDROID_STL=none")
                cFlags += listOf("-O2")
            }
        }
        buildConfigField("String", "SELF_REPO", "\"RoBiN19666/ZapretManager-Android\"")
        buildConfigField("String", "BYEDPI_REPO", "\"romanvht/ByeByeDPI\"")
        buildConfigField("String", "TGWS_REPO", "\"Regstar2/tg-ws-proxy-android\"")
        buildConfigField("String", "APP_VERSION", "\"$appVersionName\"")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val ks = rootProject.file("keystore/zapretmanager.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = providers.gradleProperty("keystorePassword").orElse("zapretmanager").get()
                keyAlias = providers.gradleProperty("keyAlias").orElse("zapretmanager").get()
                keyPassword = providers.gradleProperty("keyPassword").orElse("zapretmanager").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (rootProject.file("keystore/zapretmanager.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

// ---- tun2socks (hev-socks5-tunnel) через ndk-build -------------------------
// У hev-socks5-tunnel нет CMakeLists: upstream собирает его штатным ndk-build.
val hevOutDir = layout.buildDirectory.dir("hevLibs")

val buildHevTunnel by tasks.registering(Exec::class) {
    group = "native"
    description = "Собирает libhev-socks5-tunnel.so из сабмодуля (ndk-build)"
    val sdkDir = android.sdkDirectory
    val ndkDir = File(sdkDir, "ndk/$ndkVersionUsed")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    workingDir = projectDir
    val appMk = File(projectDir, "src/main/jni/Application.mk")
    val androidMk = File(projectDir, "src/main/jni/Android.mk")
    val libsOut = hevOutDir.get().asFile
    doFirst {
        require(ndkDir.exists()) { "NDK $ndkVersionUsed не найден в $sdkDir. Установите: sdkmanager 'ndk;$ndkVersionUsed'" }
        require(File(projectDir, "src/main/jni/hev-socks5-tunnel/Android.mk").exists()) {
            "Сабмодуль hev-socks5-tunnel не инициализирован: git submodule update --init --recursive"
        }
        libsOut.mkdirs()
    }
    executable = File(ndkDir, if (isWindows) "ndk-build.cmd" else "ndk-build").absolutePath
    args(
        "NDK_PROJECT_PATH=${layout.buildDirectory.get().asFile.resolve("ndkProject")}",
        "APP_BUILD_SCRIPT=${androidMk.absolutePath}",
        "NDK_APPLICATION_MK=${appMk.absolutePath}",
        "NDK_LIBS_OUT=${libsOut.absolutePath}",
        "-j4",
    )
}

android.sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("hevLibs"))

tasks.named("preBuild").configure { dependsOn(buildHevTunnel) }

// ---- Go-рантайм TG WS Proxy ----------------------------------------------
// Собирается отдельным скриптом (нужен Go 1.25+ и NDK), потому что Gradle
// не умеет тянуть Go-тулчейн. Скрипт: scripts/build-tgws.ps1
val buildTgwsNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Собирает libtgwsproxy.so (нужен Go 1.25+). Запускается вручную или из CI"
    onlyIf { providers.gradleProperty("buildTgws").isPresent }
    workingDir = rootDir
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "scripts/build-tgws.ps1")
    } else {
        commandLine("bash", "scripts/build-tgws.sh")
    }
}
