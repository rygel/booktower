package org.booktower.services

import org.booktower.config.SmtpConfig
import org.booktower.i18n.I18nService
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.EmailService")

open class EmailService(
    val config: SmtpConfig,
) {
    private val i18n = I18nService.create("messages")

    private val mailer: Mailer? =
        if (config.enabled) {
            val strategy = if (config.tls) TransportStrategy.SMTP_TLS else TransportStrategy.SMTP
            MailerBuilder
                .withSMTPServer(config.host, config.port, config.username.ifBlank { null }, config.password.ifBlank { null })
                .withTransportStrategy(strategy)
                .buildMailer()
        } else {
            null
        }

    open fun sendBook(
        toEmail: String,
        bookTitle: String,
        filename: String,
        fileBytes: ByteArray,
    ) {
        if (mailer == null) {
            logger.warn("SMTP not configured — cannot send book to $toEmail")
            return
        }
        val mimeType =
            when (filename.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "epub" -> "application/epub+zip"
                "mobi", "azw3" -> "application/x-mobipocket-ebook"
                "cbz" -> "application/zip"
                "cbr" -> "application/x-rar-compressed"
                else -> "application/octet-stream"
            }
        val email =
            EmailBuilder
                .startingBlank()
                .from(config.from)
                .to(toEmail)
                .withSubject("Your book: $bookTitle")
                .withPlainText("Please find attached: $bookTitle")
                .withAttachment(filename, fileBytes, mimeType)
                .buildEmail()
        mailer.sendMail(email)
        logger.info("Book '$bookTitle' sent to $toEmail (${fileBytes.size} bytes)")
    }

    fun sendPasswordReset(
        toEmail: String,
        resetLink: String,
    ) {
        if (mailer == null) {
            logger.warn("SMTP not configured — cannot send password reset email to $toEmail")
            return
        }

        val subject = i18n.translate("email.password_reset.subject")
        val body = i18n.translate("email.password_reset.body", resetLink)

        val email =
            EmailBuilder
                .startingBlank()
                .from(config.from)
                .to(toEmail)
                .withSubject(subject)
                .withPlainText(body)
                .buildEmail()

        mailer.sendMail(email)
        logger.info("Password reset email sent to $toEmail")
    }
}
