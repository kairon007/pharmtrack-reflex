package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.App.ActionError
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * runner.yaml#cli. This binary IS the Android implementation, so it rejects --platform ios
 * rather than silently emitting an Android stream under an iOS label.
 */
fun main(args: Array<String>) {
    try {
        val options = Options.parse(args)
        val specs = Specs.locate()
        val out = if (options.outputPath != null) NDJSON.file(options.outputPath) else NDJSON.standardOutput()
        val code = runBlocking { Executor(specs, out).run(options.spec, options.caseID) }
        out.finish()
        exitProcess(code)
    } catch (error: RunnerError) {
        fail(error.message ?: "", error.exitCode)
    } catch (error: ActionError) {
        fail(error.message ?: "", 2)
    } catch (error: Exception) {
        fail(error.toString(), 2)
    }
}

private fun fail(message: String, code: Int): Nothing {
    System.err.println("reflex-runner: $message")
    exitProcess(code)
}

class Options(
    val spec: String,
    val caseID: String?,
    val outputPath: Path?
) {
    companion object {
        val USAGE = """
            usage: reflex-runner --platform android --spec <glob> [--case <id>] [--out <file>]
              --platform  required, must be android for this binary
              --spec      glob relative to reflex/specs/behavior/, e.g. "vital_*.yaml"
              --case      run only this case id
              --out       write NDJSON here instead of stdout
            Set REFLEX_SPECS_DIR to point at reflex/specs when running outside the repository.
        """.trimIndent()

        fun parse(arguments: Array<String>): Options {
            var platform: String? = null
            var spec: String? = null
            var caseID: String? = null
            var outputPath: Path? = null

            // Kotlin's `args` does not carry the program name, so it starts at zero.
            var index = 0
            while (index < arguments.size) {
                val flag = arguments[index]
                when (flag) {
                    "--platform", "--spec", "--case", "--out" -> {
                        if (index + 1 >= arguments.size) {
                            throw RunnerError.Usage("$flag needs a value\n$USAGE")
                        }
                        val value = arguments[index + 1]
                        when (flag) {
                            "--platform" -> platform = value
                            "--spec" -> spec = value
                            "--case" -> caseID = value
                            else -> outputPath = Paths.get(value)
                        }
                        index += 2
                    }

                    "--help", "-h" -> throw RunnerError.Usage(USAGE)
                    else -> throw RunnerError.Usage("unknown flag $flag\n$USAGE")
                }
            }

            val platformValue = platform ?: throw RunnerError.Usage("--platform is required\n$USAGE")
            if (platformValue != "android") {
                throw RunnerError.Usage("this binary is the android implementation, not $platformValue")
            }
            val specValue = spec ?: throw RunnerError.Usage("--spec is required\n$USAGE")
            return Options(spec = specValue, caseID = caseID, outputPath = outputPath)
        }
    }
}
