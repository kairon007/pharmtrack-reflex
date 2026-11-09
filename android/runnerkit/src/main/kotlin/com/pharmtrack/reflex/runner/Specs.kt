package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.Instant
import com.pharmtrack.reflex.core.Support.MedicalCodec
import com.pharmtrack.reflex.core.Support.ReflexValue
import com.pharmtrack.reflex.core.Support.parseJSON
import com.pharmtrack.reflex.core.Support.parseUUID
import com.pharmtrack.reflex.core.Domain.reflexValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/**
 * Locates reflex/specs and loads the frozen inputs: the determinism manifest, the fixture
 * index and the fixture JSON bytes. Nothing here is emitted into the stream, so absolute
 * paths are fine internally (runner.yaml#determinism forbids them in the OUTPUT only).
 */
class Specs(
    val root: Path,
    val determinism: Determinism,
    val fixtureIndex: Map<String, ReflexValue>
) {
    val behaviorDirectory: Path get() = root.resolve("behavior")
    val fixturesDirectory: Path get() = root.resolve("fixtures")

    companion object {
        fun locate(): Specs {
            val override = System.getenv("REFLEX_SPECS_DIR")
            if (override != null && override.isNotEmpty()) {
                return load(Paths.get(override))
            }
            var directory: Path? = Paths.get("").toAbsolutePath()
            repeat(8) {
                val current = directory ?: return@repeat
                for (candidate in listOf(current.resolve("reflex/specs"), current.resolve("specs"))) {
                    if (Files.exists(candidate.resolve("behavior"))) {
                        return load(candidate)
                    }
                }
                directory = current.parent
            }
            throw RunnerError.Usage("cannot locate reflex/specs — set REFLEX_SPECS_DIR or run from the repository")
        }

        private fun load(root: Path): Specs {
            val fixtures = root.resolve("fixtures")
            val manifest = YAML.parse(fixtures.resolve("determinism.yaml"))
            val index = YAML.parse(fixtures.resolve("index.yaml"))
            return Specs(
                root = root,
                determinism = Determinism(manifest),
                fixtureIndex = index.objectValue?.get("fixtures")?.objectValue ?: emptyMap()
            )
        }
    }

    fun fixtureBytes(name: String): ByteArray {
        val entry = fixtureIndex[name] ?: throw RunnerError.Usage("unknown fixture '$name'")
        val file = entry.objectValue?.get("file")?.stringValue
            ?: throw RunnerError.Usage("fixture '$name' has no file")
        val url = fixturesDirectory.resolve(file)
        if (!Files.exists(url)) throw RunnerError.Usage("fixture file '$file' is missing")
        return Files.readAllBytes(url)
    }

    /**
     * The fixture as a PARSED value, used for `$fixture:` aliases. Decoding through the real
     * codec and re-rendering is what makes an explicit null compare equal to an omitted key
     * (runner.yaml#canonicalization). Fixtures that intentionally fail to decode fall back to
     * their raw JSON so a path alias into them still resolves.
     */
    fun fixtureValue(name: String): ReflexValue {
        val bytes = fixtureBytes(name)
        return try {
            MedicalCodec.decode(bytes).reflexValue
        } catch (error: Exception) {
            parseJSON(bytes)
        }
    }

    /**
     * The behavior directory also holds registries that are not journeys (`decisions.yaml`
     * declares `schema: reflex/decisions/1`). A glob of `*.yaml` must select the 13 journeys,
     * not everything with a .yaml suffix, so the schema is the filter.
     */
    fun journeyFiles(matching: String): List<Path> {
        val directory = behaviorDirectory
        if (!Files.isDirectory(directory)) throw RunnerError.Usage("cannot read the behavior directory")
        val entries = Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
        val matched = entries
            .filter { it.endsWith(".yaml") && Glob.matches(matching, it) }
            .filter { name ->
                val root = YAML.parse(directory.resolve(name))
                root.objectValue?.get("schema")?.stringValue == "reflex/behavior/1"
            }
        if (matched.isEmpty()) throw RunnerError.Usage("no journey matches '$matching'")
        return matched.map { directory.resolve(it) }
    }
}

class Determinism(
    val now: Instant,
    val zone: Instant.Zone,
    val reserved: Map<String, UUID>,
    val sequence: List<UUID>,
    val keyValues: Map<String, Boolean>
) {
    constructor(manifest: ReflexValue) : this(
        now = readNow(manifest),
        zone = readZone(manifest),
        reserved = readReserved(manifest),
        sequence = readSequence(manifest),
        keyValues = readKeyValues(manifest)
    )

    companion object {
        private fun readNow(manifest: ReflexValue): Instant {
            val clock = manifest.objectValue?.get("clock")?.objectValue ?: emptyMap()
            val text = clock["now"]?.stringValue
                ?: throw RunnerError.Usage("determinism.yaml#clock.now is missing or unparseable")
            return Instant.parse(text)
                ?: throw RunnerError.Usage("determinism.yaml#clock.now is missing or unparseable")
        }

        private fun readZone(manifest: ReflexValue): Instant.Zone {
            val clock = manifest.objectValue?.get("clock")?.objectValue ?: emptyMap()
            // The suite pins UTC. Accepting a zone name we cannot resolve would silently diverge
            // on day-boundary logic, so an unknown pin is a usage error rather than a fallback.
            val name = clock["timezone"]?.stringValue ?: "UTC"
            if (name != "UTC") {
                throw RunnerError.Usage("unsupported pinned timezone '$name' — only UTC is specified")
            }
            return Instant.Zone.utc
        }

        private fun readReserved(manifest: ReflexValue): Map<String, UUID> {
            val ids = manifest.objectValue?.get("ids")?.objectValue ?: emptyMap()
            val reserved = linkedMapOf<String, UUID>()
            for ((name, value) in ids["reserved"]?.objectValue ?: emptyMap()) {
                val text = value.stringValue
                    ?: throw RunnerError.Usage("determinism.yaml#ids.reserved.$name is not a UUID")
                reserved[name] = parseUUID(text)
                    ?: throw RunnerError.Usage("determinism.yaml#ids.reserved.$name is not a UUID")
            }
            return reserved
        }

        private fun readSequence(manifest: ReflexValue): List<UUID> {
            val ids = manifest.objectValue?.get("ids")?.objectValue ?: emptyMap()
            val values = ids["sequence"]?.arrayValue
                ?: throw RunnerError.Usage("determinism.yaml#ids.sequence is missing")
            return values.map { value ->
                val text = value.stringValue
                    ?: throw RunnerError.Usage("determinism.yaml#ids.sequence holds a non-UUID")
                parseUUID(text)
                    ?: throw RunnerError.Usage("determinism.yaml#ids.sequence holds a non-UUID")
            }
        }

        private fun readKeyValues(manifest: ReflexValue): Map<String, Boolean> {
            val keyValues = linkedMapOf<String, Boolean>()
            for ((key, value) in manifest.objectValue?.get("key_value")?.objectValue ?: emptyMap()) {
                val flag = value.boolValue
                if (flag != null) keyValues[key] = flag
            }
            return keyValues
        }
    }
}
