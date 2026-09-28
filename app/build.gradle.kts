import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

val cloudProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun cloudSetting(name: String, fallback: String = ""): String =
    (System.getenv(name) ?: cloudProperties.getProperty(name, fallback))
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\r", "\\r").replace("\n", "\\n")

android {
    namespace = "com.packabunch"
    compileSdk = 36

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"" + cloudSetting("SUPABASE_URL") + "\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"" + cloudSetting("SUPABASE_PUBLISHABLE_KEY") + "\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + cloudSetting("GOOGLE_WEB_CLIENT_ID") + "\"")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"" + cloudSetting("REVENUECAT_API_KEY") + "\"")
        // TODO: confirm before the first Play upload — the application id is permanent.
        applicationId = "com.packabunch"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Python (the measuring engine) ships per processor type; these cover phones and the emulator.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Room writes the schema out so migrations can be written against a real diff rather
    // than from memory. These files belong in version control.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Release speed on a test phone: judge animations here, debug Compose drops frames.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            matchingFallbacks += "release"
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
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(project(":packing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.arcore)
    implementation(libs.revenuecat)
    implementation(libs.okhttp)
    implementation(libs.play.review)
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.objects)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

// The measuring engine, app/src/main/python/packscan.py, runs on Python with NumPy and OpenCV.
// Building needs a Python 3.11 on this computer for Chaquopy to install them; point
// PACKSCAN_BUILD_PYTHON in local.properties at it if it is not found on its own
// (e.g. C:/Users/you/AppData/Local/Programs/Python/Python311/python.exe).
// OpenCV is left out by default (Chaquopy has no Android build of it for Python 3.11): measuring
// runs on NumPy; only the picture outline tracing is skipped and the depth outline is used instead.
// PACKSCAN_OPENCV=true adds it, for a Python version Chaquopy does have it for.
chaquopy {
    defaultConfig {
        version = "3.11"
        cloudProperties.getProperty("PACKSCAN_BUILD_PYTHON")?.let { buildPython(it) }
        pip {
            // Only Chaquopy's ready-built Android packages: without this pip picks the newest OpenCV,
            // which is source only, and tries to compile it for Windows (it needs Visual Studio, and
            // would not run on a phone anyway).
            options("--only-binary", ":all:")
            install("numpy")
            // Chaquopy has no Android build of OpenCV for Python 3.11, so it is off unless asked for.
            if (cloudProperties.getProperty("PACKSCAN_OPENCV", "false") == "true") install("opencv-python")
        }
    }
}
