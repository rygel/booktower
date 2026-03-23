package org.runary.services

import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import org.runary.config.SmtpConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailServiceTest {
    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withUser("test@runary.local", "testpass"))
    }

    private fun smtpConfig() =
        SmtpConfig(
            host = "localhost",
            port = ServerSetupTest.SMTP.port,
            username = "test@runary.local",
            password = "testpass",
            from = "noreply@runary.local",
            tls = false,
        )

    @Test
    fun `sendPasswordReset delivers email to recipient`() {
        val service = EmailService(smtpConfig())
        service.sendPasswordReset("user@example.com", "http://localhost:9999/reset-password?token=abc123")

        val messages = greenMail.receivedMessages
        assertEquals(1, messages.size)
        val msg = messages[0]
        assertEquals("user@example.com", msg.allRecipients[0].toString())
        assertEquals("noreply@runary.local", msg.from[0].toString())
        assertTrue(msg.subject.contains("Password Reset"))
    }

    @Test
    fun `sendPasswordReset includes reset link in body`() {
        val service = EmailService(smtpConfig())
        val link = "http://localhost:9999/reset-password?token=xyz789"
        service.sendPasswordReset("user@example.com", link)

        val messages = greenMail.receivedMessages
        assertEquals(1, messages.size)
        val body = messages[0].content as String
        assertTrue(body.contains(link))
    }

    @Test
    fun `sendPasswordReset does nothing when SMTP is not configured`() {
        val service = EmailService(SmtpConfig("", 587, "", "", "", true))
        // Should not throw even though SMTP is disabled
        service.sendPasswordReset("user@example.com", "http://localhost:9999/reset-password?token=noop")
        assertEquals(0, greenMail.receivedMessages.size)
    }

    @Test
    fun `EmailService config enabled is false when host is blank`() {
        val config = SmtpConfig("", 587, "", "", "", true)
        assertTrue(!config.enabled)
    }

    @Test
    fun `EmailService config enabled is false when from is blank`() {
        val config = SmtpConfig("smtp.example.com", 587, "", "", "", true)
        assertTrue(!config.enabled)
    }

    @Test
    fun `EmailService config enabled is true when host and from are set`() {
        val config = SmtpConfig("smtp.example.com", 587, "", "", "noreply@example.com", true)
        assertTrue(config.enabled)
    }
}
