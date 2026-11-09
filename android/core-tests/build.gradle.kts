// The Phase 3 exit gate, headless: the frozen suite runs against Core with the fixture
// service graph. No emulator, no filesystem, no wall clock, no notification permission.
plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":runnerkit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test"))
}
