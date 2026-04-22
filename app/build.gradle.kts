import java.io.File

// This task builds the restic binary from the submodule for each required Android architecture.
tasks.register<Exec>("buildResticForBundledFlavor") {
    group = "restic"
    description = "Builds restic binary from submodule using bash script"

    inputs.dir(rootProject.file("restic"))
    outputs.dir(file("src/bundled/jniLibs"))

    onlyIf("binaries not yet built") {
        !file("src/bundled/jniLibs/arm64-v8a/librestic.so").exists() ||
                !file("src/bundled/jniLibs/x86_64/librestic.so").exists()
    }

    workingDir = rootProject.projectDir
    commandLine = listOf("bash", "scripts/build_restic.sh")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

val baseAppVersionCode = 10

android {
    namespace = "io.github.hddq.restoid"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "io.github.hddq.restoid"
        minSdk = 33
        targetSdk = 36
        versionCode = 10
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }
    }

    productFlavors {
        create("vanilla") {
            dimension = "distribution"
            // Removed versionNameSuffix
            buildConfigField("boolean", "IS_BUNDLED", "false")
        }
        create("bundled") {
            dimension = "distribution"
            // Removed versionNameSuffix
            buildConfigField("boolean", "IS_BUNDLED", "true")
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
            isMinifyEnabled = false
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
    implementation(libs.apache.commons.compress)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    // Add AboutLibraries UI
    implementation(libs.aboutlibraries.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Hooks our new task into the build process for the 'bundled' flavor.
// This ensures restic is built before the app is assembled.
android.applicationVariants.all {
    if (flavorName == "bundled") {
        preBuildProvider.get().dependsOn(tasks.named("buildResticForBundledFlavor"))
    }
}
