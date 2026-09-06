plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain JVM module, not an Android library. The packing engine must stay
// free of Android classes so its geometry can be tested without a device or emulator —
// making that a module boundary means the compiler enforces it, not a code review.

kotlin {
    jvmToolchain(17)
}

dependencies {
    // No dependencies at all, on purpose. Cancellation and the time budget come in through
    // SolveBudget, so the engine does not need coroutines to be interruptible or testable.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
