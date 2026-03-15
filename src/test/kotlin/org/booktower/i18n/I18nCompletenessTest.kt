package org.booktower.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.Properties
import kotlin.test.assertTrue

/**
 * Verifies that every i18n properties file is complete and consistent with
 * the English base file (messages.properties).
 *
 * Rules enforced:
 *  1. Every key present in messages.properties must exist in every language file.
 *  2. No value may be blank or empty in any file (catches half-translated placeholders).
 *  3. No language file may contain keys that don't exist in the base file
 *     (catches typos in key names that would silently go unused).
 *
 * To add a new language: drop messages_XX.properties in src/main/resources and
 * add the locale code to the @ValueSource list below — the test will immediately
 * verify it is complete.
 */
class I18nCompletenessTest {

    private val baseLocale = "messages"
    private val languageLocales = listOf("fr", "de")

    private fun loadProperties(resourceName: String): Properties {
        val props = Properties()
        val stream = javaClass.classLoader.getResourceAsStream("$resourceName.properties")
            ?: error("Properties file not found on classpath: $resourceName.properties")
        stream.use { props.load(it) }
        return props
    }

    @Test
    fun `base messages file loads and is non-empty`() {
        val base = loadProperties(baseLocale)
        assertTrue(base.isNotEmpty(), "messages.properties must not be empty")
    }

    @ParameterizedTest(name = "messages_{0}.properties has all base keys")
    @ValueSource(strings = ["fr", "de"])
    fun `language file contains every key from the base file`(locale: String) {
        val base = loadProperties(baseLocale)
        val lang = loadProperties("messages_$locale")

        val missingKeys = base.keys
            .map { it as String }
            .filter { !lang.containsKey(it) }
            .sorted()

        assertTrue(
            missingKeys.isEmpty(),
            "messages_$locale.properties is missing ${missingKeys.size} key(s):\n" +
                missingKeys.joinToString("\n") { "  - $it" },
        )
    }

    @ParameterizedTest(name = "messages_{0}.properties has no blank values")
    @ValueSource(strings = ["fr", "de"])
    fun `language file has no blank or empty values`(locale: String) {
        val lang = loadProperties("messages_$locale")

        val blankKeys = lang.entries
            .filter { (_, v) -> (v as String).isBlank() }
            .map { it.key as String }
            .sorted()

        assertTrue(
            blankKeys.isEmpty(),
            "messages_$locale.properties has ${blankKeys.size} blank value(s):\n" +
                blankKeys.joinToString("\n") { "  - $it" },
        )
    }

    @Test
    fun `base file itself has no blank values`() {
        val base = loadProperties(baseLocale)

        val blankKeys = base.entries
            .filter { (_, v) -> (v as String).isBlank() }
            .map { it.key as String }
            .sorted()

        assertTrue(
            blankKeys.isEmpty(),
            "messages.properties has ${blankKeys.size} blank value(s):\n" +
                blankKeys.joinToString("\n") { "  - $it" },
        )
    }

    @ParameterizedTest(name = "messages_{0}.properties has no unknown keys")
    @ValueSource(strings = ["fr", "de"])
    fun `language file contains no keys absent from base file`(locale: String) {
        val base = loadProperties(baseLocale)
        val lang = loadProperties("messages_$locale")

        val extraKeys = lang.keys
            .map { it as String }
            .filter { !base.containsKey(it) }
            .sorted()

        assertTrue(
            extraKeys.isEmpty(),
            "messages_$locale.properties has ${extraKeys.size} extra key(s) not in base:\n" +
                extraKeys.joinToString("\n") { "  + $it" },
        )
    }
}
