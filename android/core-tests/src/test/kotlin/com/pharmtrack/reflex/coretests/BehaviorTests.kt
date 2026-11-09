package com.pharmtrack.reflex.coretests

import com.pharmtrack.reflex.core.App.NodeID
import com.pharmtrack.reflex.core.Introspection.NodeRegistry
import com.pharmtrack.reflex.runner.Executor
import com.pharmtrack.reflex.runner.JourneyParser
import com.pharmtrack.reflex.runner.NDJSON
import com.pharmtrack.reflex.runner.RunnerError
import com.pharmtrack.reflex.runner.Specs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Phase 3 exit gate, headless: the frozen suite runs against Core with the fixture service
 * graph. No emulator, no filesystem, no wall clock, no notification permission.
 *
 * These tests use the SAME executor and the SAME canonicalization as `reflex-runner`, so a
 * green `./gradlew :core-tests:test` and a green runner are the same claim, not two
 * approximations of it.
 */
class BehaviorTests {

    private fun specs(): Specs = Specs.locate()

    @Test
    fun everyJourneyParses() {
        val files = specs().journeyFiles(matching = "*.yaml")
        assertTrue(files.isNotEmpty(), "no journeys found")
        var caseCount = 0
        for (file in files) {
            val text = String(file.toFile().readBytes(), Charsets.UTF_8)
            val journey = JourneyParser.parse(text, file.fileName.toString())
            assertEquals(
                file.fileName.toString().removeSuffix(".yaml"),
                journey.id,
                "journey id must be the filename stem"
            )
            assertTrue(journey.cases.isNotEmpty(), "${journey.id} has no cases")
            caseCount += journey.cases.size
        }
        assertTrue(caseCount > 0)
    }

    @Test
    fun suitePasses() = runBlocking {
        val specs = specs()
        val out = NDJSON.inMemory()
        val code = Executor(specs, out).run(glob = "*.yaml", caseFilter = null)
        assertEquals(0, code, failureReport(out.lines))
    }

    /**
     * runner.yaml#determinism: two runs of the same binary with the same spec must produce
     * byte-identical output. This is the property the whole cross-platform diff rests on.
     */
    @Test
    fun streamIsDeterministic() = runBlocking {
        val specs = specs()
        val first = NDJSON.inMemory()
        val second = NDJSON.inMemory()
        Executor(specs, first).run(glob = "*.yaml", caseFilter = null)
        Executor(specs, second).run(glob = "*.yaml", caseFilter = null)

        assertEquals(first.lines.size, second.lines.size, "record count differs between runs")
        for (index in first.lines.indices) {
            if (first.lines[index] != second.lines[index]) {
                throw AssertionError("line $index differs between runs")
            }
        }
    }

    @Test
    fun unknownCaseIsAUsageError() = runBlocking {
        val specs = specs()
        val error = assertFailsWith<RunnerError> {
            Executor(specs, NDJSON.inMemory()).run(glob = "*.yaml", caseFilter = "no_such_case_anywhere")
        }
        assertEquals(2, error.exitCode)
    }

    @Test
    fun internalActionsAreNotSendable() {
        // commitData and friends are reachable only from another reducer's effect list
        // (nodes.yaml). The executor validates against `actions`, so an overlap would let a
        // journey drive an internal transition directly and mask a missing public path.
        for (schema in NodeRegistry.schemas) {
            for (name in schema.internalActions) {
                assertTrue(
                    !schema.actions.contains(name),
                    "${schema.id.rawValue}.$name must not be a public action"
                )
            }
        }
        assertEquals(NodeID.entries.size, NodeRegistry.schemas.size)
    }

    /**
     * runner.yaml#failure_report: the journey, the case, the step and the PATH. Never a whole
     * state object — those are large and the path is what a human needs.
     */
    private fun failureReport(lines: List<String>): String {
        var journey = ""
        var caseID = ""
        val report = mutableListOf<String>()
        for (line in lines) {
            val record = com.pharmtrack.reflex.core.Support.parseJSON(line.toByteArray())
                .objectValue ?: continue
            when (record["type"]?.stringValue) {
                "case_start" -> {
                    journey = record["journey"]?.stringValue ?: ""
                    caseID = record["case"]?.stringValue ?: ""
                }

                "assertion" -> {
                    if (record["passed"]?.boolValue == false) {
                        val step = record["step"]?.intValue ?: -1
                        val path = record["path"]?.stringValue ?: ""
                        val op = record["operator"]?.stringValue ?: ""
                        report.add("$journey#$caseID step $step: $path $op")
                    }
                }

                else -> continue
            }
        }
        return if (report.isEmpty()) {
            "the suite failed without recording a failing assertion"
        } else {
            report.take(40).joinToString("\n")
        }
    }
}
