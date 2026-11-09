package com.pharmtrack.reflex.runner

import com.pharmtrack.reflex.core.Support.ReflexValue

/** runner.yaml#exit_codes: 2 usage/malformed, 3 exhausted or unsupported fixture use. */
sealed class RunnerError(message: String) : Exception(message) {
    class Usage(message: String) : RunnerError(message)
    class Malformed(message: String) : RunnerError(message)
    class Exhausted(message: String) : RunnerError(message)

    val exitCode: Int
        get() = when (this) {
            is Usage, is Malformed -> 2
            is Exhausted -> 3
        }
}

/** The contract's globs are filename patterns like "vital_*.yaml" — `*` and `?` only. */
object Glob {

    fun matches(pattern: String, name: String): Boolean {
        val patternCharacters = pattern.toCharArray()
        val nameCharacters = name.toCharArray()
        var p = 0
        var n = 0
        var star = -1
        var mark = 0
        while (n < nameCharacters.size) {
            if (p < patternCharacters.size &&
                (patternCharacters[p] == '?' || patternCharacters[p] == nameCharacters[n])
            ) {
                p += 1
                n += 1
            } else if (p < patternCharacters.size && patternCharacters[p] == '*') {
                star = p
                mark = n
                p += 1
            } else if (star >= 0) {
                p = star + 1
                mark += 1
                n = mark
            } else {
                return false
            }
        }
        while (p < patternCharacters.size && patternCharacters[p] == '*') p += 1
        return p == patternCharacters.size
    }
}

/** Convenience for the "did the object hold a value?" checks the Swift code spells `orThrow`. */
fun <T> T?.orThrow(message: String): T = this ?: throw RunnerError.Malformed(message)
