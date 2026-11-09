buildscript {
    dependencies {
        // AGP 9's built-in Kotlin uses the Kotlin Gradle plugin that is on the build
        // classpath. Pinning it here keeps :core (compiled with 2.4.21) and :app
        // (compiled by AGP's built-in Kotlin) on one Kotlin version — otherwise AGP would
        // fall back to the Kotlin version it ships with and refuse :core's metadata.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
    // AGP 9 compiles the :app module with its built-in Kotlin support. The Kotlin plugin
    // version that the root project puts on the build classpath (2.4.21) is the one the
    // built-in Kotlin support uses, so :core metadata and :app agree.
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
            jvmToolchain(17)
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "failed", "skipped")
            }
        }
    }
}
