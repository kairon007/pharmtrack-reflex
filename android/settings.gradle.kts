// Phase 3: pure JVM Kotlin core + headless runner. No Android SDK, no Skip, no Compose.
// kotlinx.serialization and kotlinx.coroutines only — the same constraint the Swift Core
// works under (contracts/runner.yaml#implementation_constraints.android).

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "ReflexAndroid"

include(":core")
include(":runnerkit")
include(":core-tests")
include(":tools:reflex-runner")

// The CI parity gate builds the headless JVM modules only. :app needs the Android SDK, which
// a runner without one cannot configure, so REFLEX_SKIP_ANDROID_APP=1 leaves it out.
if (System.getenv("REFLEX_SKIP_ANDROID_APP") != "1") {
    include(":app")
}
