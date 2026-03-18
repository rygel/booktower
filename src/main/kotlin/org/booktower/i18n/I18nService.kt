package org.booktower.i18n

import org.slf4j.LoggerFactory
import java.text.MessageFormat
import java.util.*

class I18nService(
    private val bundleName: String,
) {
    private val logger = LoggerFactory.getLogger(I18nService::class.java)
    private var locale = Locale.getDefault()
    private var bundle = ResourceBundle.getBundle(bundleName, locale)

    fun setLocale(newLocale: Locale) {
        this.locale = newLocale
        this.bundle = ResourceBundle.getBundle(bundleName, locale)
    }

    fun translate(
        key: String,
        vararg args: Any,
    ): String =
        try {
            val message = bundle.getString(key)
            if (args.isEmpty()) {
                message
            } else {
                MessageFormat.format(message, *args)
            }
        } catch (e: MissingResourceException) {
            logger.warn("Missing translation key: {} for locale: {}. Error: {}", key, locale, e.message)
            key
        }

    companion object {
        fun create(bundleName: String): I18nService = I18nService(bundleName)
    }
}
