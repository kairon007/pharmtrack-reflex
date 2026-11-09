// The runner library: YAML loading, journey parsing, the executor and the NDJSON emitter.
// YAML is a TOOL concern here, exactly as it is in the Swift RunnerKit — Core never sees a
// YAML node, only plain ReflexValues.
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.yaml:snakeyaml:2.3")
}
