import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module, not an Android library. The packing engine must stay
// free of Android classes so its geometry can be tested without a device or emulator —
// making that a module boundary means the compiler enforces it, not a code review.

// Java 17 bytecode to match :app, produced by whichever JDK Gradle is running on. No
// toolchain declaration: pinning one makes the build demand a JDK the machine may not have.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // No production dependencies at all, on purpose. Cancellation and the time budget come
    // in through SolveBudget, so the engine needs no coroutines to be interruptible.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    // The computer photo test only (PhotoProbeTest): the same models and Python fitter the phone runs.
    // 1.17: newer Windows builds crash against the older msvcp140.dll that Android Studio's JDK loads first.
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.17.3")
    testImplementation("org.json:json:20240303")
}

tasks.withType<Test>().configureEach {
    useJUnit()
    // PhotoProbeTest runs only when given a folder of photos: -Pprobe.in=... -Pprobe.out=...
    listOf("probe.in", "probe.out").forEach { k -> findProperty(k)?.let { systemProperty(k, it) } }
    systemProperty("probe.root", rootDir.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
