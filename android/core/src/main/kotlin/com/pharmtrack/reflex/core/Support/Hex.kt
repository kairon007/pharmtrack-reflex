package com.pharmtrack.reflex.core.Support

/** Kotlin's `Char` has no `isHexDigit`; Swift's accepts 0-9, a-f and A-F. */
fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
