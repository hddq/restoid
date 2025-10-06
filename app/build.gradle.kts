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
        // First, check if Go is installed on the system.
        try {
            project.exec { commandLine("go", "version") }
        } catch (e: Exception) {
            throw GradleException("Go is not installed or not in your PATH. It's required to build the 'bundled' flavor.", e)
        }

        // These are the architectures defined in your splits block.
        val targetAbis = setOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")

        targetAbis.forEach { abi ->
            // Map Android ABI names to Go architecture names.
            val goArch = when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "amd64"
                "x86" -> "386"
                else -> null
            }

            if (goArch != null) {
                println("Building restic for $abi (GOARCH: $goArch)...")

                // The output directory now follows jniLibs structure
                val finalOutputDir = File(outputJniLibsDir, abi)
                finalOutputDir.mkdirs()
                // IMPORTANT: Prefix with "lib" and use ".so" extension for Android to recognize it
                val outputFile = File(finalOutputDir, "librestic.so")

                project.exec {
                    workingDir = rootProject.file("restic")
                    // Set environment variables for cross-compilation.
                    environment("GOOS", "linux") // Target is Android (Linux)
                    environment("GOARCH", goArch)
                    environment("CGO_ENABLED", "0") // Create a static binary

                    commandLine(
                        "go", "build",
                        "-ldflags=-s -w", // Strip debug info to reduce binary size
                        "-trimpath",
                        "-o", outputFile.absolutePath,
                        "./cmd/restic"
                    )
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
}

android {
    namespace = "io.github.hddq.restoid"
    compileSdk = 36

    flavorDimensions += "distribution"

    defaultConfig {
        applicationId = "io.github.hddq.restoid"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("vanilla") {
            dimension = "distribution"
            versionNameSuffix = "-vanilla"
            // We can add a build config field to distinguish flavors in code.
            buildConfigField("boolean", "IS_BUNDLED", "false")
        }
        create("bundled") {
            dimension = "distribution"
            versionNameSuffix = "-bundled"
            buildConfigField("boolean", "IS_BUNDLED", "true")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    // Remove the old assets sourceSets configuration
    // The jniLibs are automatically picked up from src/bundled/jniLibs

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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Add packaging options to prevent stripping our "fake" .so files
    packagingOptions {
        jniLibs {
            // Don't strip our restic binaries (they're not real .so files)
            keepDebugSymbols += "**/*.so"
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