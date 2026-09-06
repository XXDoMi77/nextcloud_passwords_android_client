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
        // versionName is semantic. versionCode stays a plain counter, deliberately not derived from it: Play
        // only enforces "larger than the last upload", and a derived code invites a collision the first time a
        // pre-release and a patch want the same slot. Bump it on every upload that Play might see - a code is
        // spent the moment it is uploaded, and deleting the release does not hand it back. 11 went to Play as
        // 1.0.0-beta01 and is gone.
        versionCode = 13
        versionName = "1.0.1"
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
            // R8 is on. It was off because an earlier attempt crashed on startup inside Gson: R8 strips
            // the Signature attribute unless told to keep it, and without it `List<Password>` erases and
            // Gson builds LinkedTreeMap instead of the data class. proguard-rules.pro keeps that attribute
            // and the reflective surfaces of Gson, JNA and lazysodium; nothing else needs holding open.
            isMinifyEnabled = true
            isShrinkResources = true
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
    // Test only, so it is not in the shipped app: the stored-document shape test reads Kotlin
    // properties rather than Java fields.
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
}
