package org.booktower

/**
 * Dynamically generated test password — avoids hardcoded credential scanner findings.
 * Generated once at class-load time so register+login pairs use the same value.
 */
object TestPasswords {
    val DEFAULT: String = "Tp${java.util.UUID.randomUUID().toString().take(16)}!"
}
