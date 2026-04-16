import java.io.File

// This task builds the restic binary from the submodule for each required Android architecture.
tasks.register("buildResticForBundledFlavor") {
    group = "restic"
    description = "Builds restic binary from submodule for bundled flavor ABIs"

    // Use the submodule as an input directory for Gradle's up-to-date checks.
    inputs.dir(rootProject.file("restic"))
    // Define the output directory for the compiled binaries - NOW USING jniLibs!
    val outputJniLibsDir = file("src/bundled/jniLibs")
    outputs.dir(outputJniLibsDir)

    doLast {
        // Build Restic for each architecture
        val targetAbis = setOf("x86_64", "arm64-v8a")

        targetAbis.forEach { abi ->
            // Map Android ABI names to Go architecture names.
            val goArch = when (abi) {
                "arm64-v8a" -> "arm64"
                "x86_64" -> "amd64"
                else -> null
            }

            if (goArch != null) {
                println("Building restic for $abi (GOARCH: $goArch)...")

                val finalOutputDir = File(outputJniLibsDir, abi)
                finalOutputDir.mkdirs()
                // IMPORTANT: Prefix with "lib" and use ".so" extension for Android to recognize it
                val outputFile = File(finalOutputDir, "librestic.so")

                // Using ProcessBuilder for the build command
                val buildProcess = ProcessBuilder(
                    "go", "build",
                    "-buildvcs=false",
                    "-ldflags=-s -w", // Strip debug info to reduce binary size
                    "-trimpath",
                    "-o", outputFile.absolutePath,
                    "./cmd/restic"
                )

                buildProcess.directory(rootProject.file("restic"))

                // Set environment variables cleanly
                val env = buildProcess.environment()
                env["GOOS"] = "linux" // Target is Android (Linux)
                env["GOARCH"] = goArch
                env["CGO_ENABLED"] = "0" // Create a static binary

                buildProcess.redirectErrorStream(true)
                val process = buildProcess.start()

                // Capture output to show if something goes wrong
                val buildOutput = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    throw GradleException("Failed to build restic for $abi.\nOutput: $buildOutput")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Apply AboutLibraries plugin
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

android {
    namespace = "io.github.hddq.restoid"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "io.github.hddq.restoid"
        minSdk = 33
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.4"

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
