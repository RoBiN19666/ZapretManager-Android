import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionFile = rootProject.file("version.txt")
val versionCodeFile = rootProject.file("version-code.txt")
val appVersionName = providers.gradleProperty("appVersionName").orElse(
    versionFile.takeIf { it.isFile }?.readText()?.trim()?.ifBlank { null } ?: "0.0.0"
).get()
val appVersionCode = providers.gradleProperty("appVersionCode").orElse(
    versionCodeFile.takeIf { it.isFile }?.readText()?.trim()?.ifBlank { null } ?: "1"
).get().toInt()
val ndkVersionUsed = "27.0.12077973"

// Подпись никогда не хранится в репозитории. Локально — keystore.properties
// (в .gitignore), в CI — переменные окружения из GitHub Secrets.
val signingProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orNull
        ?: System.getenv(env)
        ?: signingProperties.getProperty(property)

val releaseKeystorePath = signingValue("keystoreFile", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = signingValue("keystorePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
val releaseKeystore = releaseKeystorePath?.let { rootProject.file(it) }?.takeIf { it.isFile }
val hasReleaseSigning = releaseKeystore != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

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

    // Отдельные APK под каждую архитектуру: 6.4/6.6 МБ вместо 11 МБ универсального.
    // Универсальный тоже собираем — он нужен как запасной вариант для ручной установки
    // и для устройств с нестандартным набором ABI.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            // R8 обязателен: без него в APK попадают все иконки Material и
            // неиспользуемые зависимости. JNI-классы защищены proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

// jvmTarget задаётся через compilerOptions: kotlinOptions в KGP 2.x объявлен
// устаревшим и удаляется в следующей мажорной версии.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    // Настоящий org.json для JVM-тестов: в android.jar это заглушки.
    testImplementation("org.json:json:20240303")
}

// ---- tun2socks (hev-socks5-tunnel) через ndk-build -------------------------
// У hev-socks5-tunnel нет CMakeLists: upstream собирает его штатным ndk-build.
val hevOutDir = layout.buildDirectory.dir("hevLibs")

// Отдельная проверка вместо невнятной ошибки линковки: без сабмодулей
// byedpi и hev CMake/ndk-build падают с сообщениями про символы, а не про
// неинициализированный репозиторий.
val checkNativeSources by tasks.registering {
    group = "verification"
    description = "Проверяет, что сабмодули нативного слоя инициализированы"
    val byedpiMain = File(projectDir, "src/main/cpp/byedpi/main.c")
    val hevMake = File(projectDir, "src/main/jni/hev-socks5-tunnel/Android.mk")
    doLast {
        val missing = listOf(byedpiMain, hevMake).filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Не инициализированы сабмодули: ${missing.joinToString { it.relativeTo(rootDir).path }}. " +
                    "Выполните: git submodule update --init --recursive"
            )
        }
    }
}

tasks.named("preBuild").configure { dependsOn(checkNativeSources) }

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
    inputs.files(appMk, androidMk)
    outputs.dir(libsOut)
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

// Release нельзя собрать «молча без Telegram»: если Go-рантайм не собран,
// APK формально валиден, но функция Telegram не работает. Лучше упасть сразу.
val checkTgwsLibs by tasks.registering {
    group = "verification"
    description = "Проверяет наличие libtgwsproxy.so для всех ABI"
    doLast {
        val missing = listOf("arm64-v8a", "armeabi-v7a")
            .map { File(projectDir, "src/main/jniLibs/$it/libtgwsproxy.so") }
            .filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Не собрана libtgwsproxy.so: ${missing.joinToString { it.relativeTo(rootDir).path }}. " +
                    "Выполните scripts/build-tgws.ps1 (Windows) или scripts/build-tgws.sh (Linux), " +
                    "затем повторите сборку."
            )
        }
    }
}

// Debug-сборка без Go-рантайма остаётся рабочей (ускорение работает, Telegram
// нет). Молчать об этом нельзя: раньше приложение просто показывало ошибку на
// вкладке Telegram, а причина была неочевидна.
val warnMissingTgws by tasks.registering {
    group = "verification"
    description = "Предупреждает, что debug-сборка останется без Telegram-прокси"
    doLast {
        val missing = listOf("arm64-v8a", "armeabi-v7a")
            .map { File(projectDir, "src/main/jniLibs/$it/libtgwsproxy.so") }
            .filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            logger.warn(
                "libtgwsproxy.so не собрана (${missing.joinToString { it.relativeTo(rootDir).path }}): " +
                    "Telegram-прокси в этой сборке работать не будет. " +
                    "Запустите scripts/build-tgws.ps1 или scripts/build-tgws.sh."
            )
        }
    }
}

tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(warnMissingTgws)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkTgwsLibs)
}
