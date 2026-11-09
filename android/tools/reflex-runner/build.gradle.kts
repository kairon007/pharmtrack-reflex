plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":runnerkit"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

application {
    mainClass.set("com.pharmtrack.reflex.runner.MainKt")
    applicationName = "reflex-runner"
}
