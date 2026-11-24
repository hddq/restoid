plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

val signingKeystorePath = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE_FILE")
    .orElse(providers.gradleProperty("ANDROID_SIGNING_KEYSTORE_FILE"))
    .orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
    .orElse(providers.gradleProperty("ANDROID_SIGNING_STORE_PASSWORD"))
    .orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
    .orElse(providers.gradleProperty("ANDROID_SIGNING_KEY_ALIAS"))
    .orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
    .orElse(providers.gradleProperty("ANDROID_SIGNING_KEY_PASSWORD"))
    .orNull

val hasReleaseSigning = listOf(
    signingKeystorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

// Bump by 1 for every release tag, stable or pre-release (e.g. v0.9.0-beta.1 -> 29, v0.9.0 -> 30).
// Per-ABI APK version code: 10 * base + abiCode. Keep the defaultConfig version code below equal to
// this value as a literal: F-Droid's checkupdates regex-parses it and cannot resolve variables.
val baseAppVersionCode = 33

android {
    namespace = "io.github.hddq.restoid"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "io.github.hddq.restoid"
        minSdk = 29
        targetSdk = 37
        versionCode = 33
        versionName = "0.9.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86_64", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle("Release signing not configured; release APKs will be unsigned.")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    testOptions {
        // android.util.Log (used for abort diagnostics) returns defaults
        // instead of throwing in plain JVM unit tests.
        unitTests.isReturnDefaultValues = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
            useLegacyPackaging = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType.name == "ABI" }
                ?.identifier

            val abiCode = when (abi) {
                "arm64-v8a" -> 1
                "x86_64" -> 2
                else -> 0
            }

            output.versionCode.set((baseAppVersionCode * 10) + abiCode)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.libsu.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.aboutlibraries.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// This task builds the restic binary from the submodule for each required Android architecture.
tasks.register<Exec>("buildBundledRestic") {
    group = "restic"
    description = "Builds restic binary from submodule using bash script"

    inputs.files(
        rootProject.file("scripts/build_restic.sh"),
        rootProject.file(".go-version"),
    ).withPropertyName("resticBuildInputs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.fileTree("restic") {
            // Skip git metadata, Go tests/testdata, and unreadable/non-UTF-8 paths that fail Gradle hashing.
            exclude("**/.git", "**/.git/**", "**/testdata/**", "**/*_test.go")
            exclude { !it.file.exists() }
        }
    ).withPropertyName("resticSourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("ndkVersion", android.ndkVersion)
    inputs.property("minSdk", android.defaultConfig.minSdk ?: 0)

    outputs.files(
        file("src/main/jniLibs/arm64-v8a/librestic.so"),
        file("src/main/jniLibs/x86_64/librestic.so"),
    ).withPropertyName("resticBinaries")

    workingDir = rootProject.projectDir
    commandLine = listOf("bash", "scripts/build_restic.sh")
}

// This task builds the rclone binary from the submodule for each required Android architecture.
tasks.register<Exec>("buildBundledRclone") {
    group = "rclone"
    description = "Builds rclone binary from submodule using bash script"

    inputs.files(
        rootProject.file("scripts/build_rclone.sh"),
        rootProject.file(".go-version"),
    ).withPropertyName("rcloneBuildInputs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.fileTree("rclone") {
            // Skip git metadata, Go tests/testdata, excluded cmd/gui dist.zip, and non-UTF-8 paths.
            exclude("**/.git", "**/.git/**", "**/testdata/**", "**/*_test.go", "cmd/gui/dist.zip")
            exclude { !it.file.exists() }
        }
    ).withPropertyName("rcloneSourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("ndkVersion", android.ndkVersion)
    inputs.property("minSdk", android.defaultConfig.minSdk ?: 0)

    outputs.files(
        file("src/main/jniLibs/arm64-v8a/librclone.so"),
        file("src/main/jniLibs/x86_64/librclone.so"),
    ).withPropertyName("rcloneBinaries")

    workingDir = rootProject.projectDir
    commandLine = listOf("bash", "scripts/build_rclone.sh")
}

tasks.named("preBuild").configure {
    dependsOn(tasks.named("buildBundledRestic"))
    dependsOn(tasks.named("buildBundledRclone"))
}
