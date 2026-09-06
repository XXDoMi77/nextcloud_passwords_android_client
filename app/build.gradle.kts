import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseKeystorePropertiesPath =
    providers.gradleProperty("releaseKeystoreProperties").orNull ?: System.getenv("NPAC_KEYSTORE_PROPERTIES")
val releaseKeystoreProperties =
    releaseKeystorePropertiesPath?.let { path -> Properties().apply { file(path).inputStream().use(::load) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlinAndroidKsp)
    alias(libs.plugins.ktfmt)
}

// Formatting is enforced by the build, not just by the IDE: `.idea/ktfmt.xml` alone meant nothing
// checked formatting in a build or a pull request, and the codebase had already drifted.
ktfmt {
    kotlinLangStyle()
    maxWidth = 120
}

android {
    namespace = "com.dominikdomotor.nextcloudpasswords"
    compileSdk { version = release(36) }

    defaultConfig {
        applicationId = "com.dominikdomotor.nextcloudpasswords"
        minSdk = 29
        targetSdk = 36
        // versionName is semantic, with a pre-release tag while the 1.0 line settles; drop the suffix for the
        // stable release. versionCode stays a plain counter: Play only enforces "larger than the last upload",
        // and deriving it from the version number invites a collision the first time a beta and a patch want the
        // same slot. Bump it on every upload, including a re-upload of the same versionName.
        versionCode = 11
        versionName = "1.0.0-beta01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseKeystoreProperties?.let { properties ->
            create("release") {
                val configuredStoreFile = File(properties.getProperty("storeFile"))
                storeFile =
                    if (configuredStoreFile.isAbsolute) configuredStoreFile
                    else File(releaseKeystorePropertiesPath!!).parentFile.resolve(configuredStoreFile.path)
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 stays off: enabling it made the app crash on startup in Gson deserialisation.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only covers native code this project builds. libsodium and libjnidispatch arrive
            // pre-stripped from their AARs, so no symbol archive is produced today; kept so symbols
            // would appear automatically if NDK sources were ever added.
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs = freeCompilerArgs.get() + listOf("-Xjsr305=strict")
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        viewBinding = true
        // GlobalFunctions uses BuildConfig.DEBUG to keep request logging out of release builds.
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.browser)

    // UI
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.fastscroll)
    implementation(libs.androidx.core.splashscreen)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ProcessLifecycleOwner: distinguishes app foreground/background from activity restarts.
    implementation(libs.androidx.lifecycle.process)

    // Fragment — provides by viewModels() / by activityViewModels()
    implementation(libs.androidx.fragment.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lazysodium.android) { exclude(group = "net.java.dev.jna", module = "jna") }
    implementation(libs.jna) {
        // The AAR variant is the one that ships the packaged native libraries lazysodium loads.
        artifact {
            name = "jna"
            type = "aar"
        }
    }

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Utilities
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
