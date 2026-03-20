package org.booktower.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AppConfig::class.java)

/**
 * Application configuration loaded from application.yaml with environment variable overrides.
 *
 * Environment variables follow the pattern: BOOKTOWER_{SECTION}_{FIELD}
 * Examples:
 *   BOOKTOWER_DB_URL          → app.database.url
 *   BOOKTOWER_JWT_SECRET      → app.security.jwtSecret
 *   BOOKTOWER_PORT            → app.port
 *   BOOKTOWER_SMTP_HOST       → app.smtp.host
 *
 * Hoplite automatically maps env vars to config fields via prefix stripping and camelCase conversion.
 */
data class AppConfig(
    val name: String = "BookTower",
    val host: String = "0.0.0.0",
    val port: Int = 9999,
    val database: DatabaseConfig = DatabaseConfig(),
    val security: SecurityConfig = SecurityConfig(),
    val storage: StorageConfig = StorageConfig(),
    val weblate: WeblateConfig = WeblateConfig(),
    val csrf: CsrfConfig = CsrfConfig(),
    val smtp: SmtpConfig = SmtpConfig(),
    val baseUrl: String = "",
    val registrationOpen: Boolean = true,
    val autoScanMinutes: Long = 60L,
    val oidc: OidcConfig = OidcConfig(),
    val metadata: MetadataConfig = MetadataConfig(),
    val demoMode: Boolean = false,
    val fts: FtsConfig = FtsConfig(),
) {
    companion object {
        /** Read an env var, returning null if absent or blank. */
        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

        fun load(): AppConfig {
            val config =
                ConfigLoaderBuilder
                    .default()
                    .addResourceSource("/application.yaml")
                    .build()
                    .loadConfigOrThrow<AppWrapper>()

            val app = config.app

            // Apply BOOKTOWER_* env var overrides (backward compat with existing deployments)
            val resolved =
                app.copy(
                    host = env("BOOKTOWER_HOST") ?: app.host,
                    port = env("BOOKTOWER_PORT")?.toIntOrNull() ?: app.port,
                    baseUrl = env("BOOKTOWER_BASE_URL") ?: app.baseUrl.ifBlank { "http://${app.host}:${app.port}" },
                    registrationOpen = env("BOOKTOWER_REGISTRATION_OPEN")?.lowercase() != "false" && app.registrationOpen,
                    autoScanMinutes = env("BOOKTOWER_AUTO_SCAN_MINUTES")?.toLongOrNull() ?: app.autoScanMinutes,
                    demoMode = env("BOOKTOWER_DEMO_MODE")?.lowercase() == "true" || app.demoMode,
                    database =
                        app.database.copy(
                            url = env("BOOKTOWER_DB_URL") ?: app.database.url,
                            username = env("BOOKTOWER_DB_USERNAME") ?: app.database.username,
                            password = env("BOOKTOWER_DB_PASSWORD") ?: app.database.password,
                            driver = env("BOOKTOWER_DB_DRIVER") ?: app.database.driver,
                        ),
                    security =
                        app.security.copy(
                            jwtSecret = env("BOOKTOWER_JWT_SECRET") ?: app.security.jwtSecret,
                        ),
                    storage =
                        app.storage.copy(
                            booksPath = env("BOOKTOWER_BOOKS_PATH") ?: app.storage.booksPath,
                            coversPath = env("BOOKTOWER_COVERS_PATH") ?: app.storage.coversPath,
                            tempPath = env("BOOKTOWER_TEMP_PATH") ?: app.storage.tempPath,
                        ),
                    fts =
                        app.fts.copy(
                            enabled = env("BOOKTOWER_FTS_ENABLED")?.lowercase() == "true" || app.fts.enabled,
                        ),
                    smtp =
                        app.smtp.copy(
                            host = env("BOOKTOWER_SMTP_HOST") ?: app.smtp.host,
                            port = env("BOOKTOWER_SMTP_PORT")?.toIntOrNull() ?: app.smtp.port,
                            username = env("BOOKTOWER_SMTP_USER") ?: app.smtp.username,
                            password = env("BOOKTOWER_SMTP_PASS") ?: app.smtp.password,
                            from = env("BOOKTOWER_SMTP_FROM") ?: app.smtp.from,
                        ),
                    oidc =
                        app.oidc.copy(
                            enabled = env("OIDC_ISSUER")?.isNotBlank() == true || app.oidc.enabled,
                            issuer = env("OIDC_ISSUER") ?: app.oidc.issuer,
                            clientId = env("OIDC_CLIENT_ID") ?: app.oidc.clientId,
                            clientSecret = env("OIDC_CLIENT_SECRET") ?: app.oidc.clientSecret,
                            redirectUri = env("OIDC_REDIRECT_URI") ?: app.oidc.redirectUri,
                            scope = env("OIDC_SCOPE") ?: app.oidc.scope,
                            forceOnlyMode = env("OIDC_FORCE_ONLY")?.lowercase() == "true" || app.oidc.forceOnlyMode,
                            adminGroupPattern = env("OIDC_ADMIN_GROUP_PATTERN") ?: app.oidc.adminGroupPattern,
                            groupsClaim = env("OIDC_GROUPS_CLAIM") ?: app.oidc.groupsClaim,
                        ),
                    metadata =
                        app.metadata.copy(
                            hardcoverApiKey = env("BOOKTOWER_HARDCOVER_API_KEY") ?: app.metadata.hardcoverApiKey,
                            comicvineApiKey = env("BOOKTOWER_COMICVINE_API_KEY") ?: app.metadata.comicvineApiKey,
                        ),
                    csrf =
                        app.csrf.copy(
                            allowedHosts =
                                env("BOOKTOWER_CSRF_ALLOWED_HOSTS")
                                    ?.split(",")
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotEmpty() }
                                    ?.toSet()
                                    ?: app.csrf.allowedHosts,
                        ),
                )

            // Validate JWT secret in production
            resolved.security.validate()

            logger.info(
                "Loaded configuration: appName=${resolved.name}, port=${resolved.port}, " +
                    "autoScanMinutes=${resolved.autoScanMinutes}, smtpEnabled=${resolved.smtp.enabled}, " +
                    "registrationOpen=${resolved.registrationOpen}",
            )
            return resolved
        }
    }
}

/** Wrapper for the top-level 'app' key in YAML. */
internal data class AppWrapper(
    val app: AppConfig,
)

data class DatabaseConfig(
    val url: String = "jdbc:h2:file:./data/booktower;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    val username: String = "sa",
    val password: String = "",
    val driver: String = "org.h2.Driver",
)

data class SecurityConfig(
    val jwtSecret: String = "booktower-secret-key-change-in-production",
    val jwtIssuer: String = "booktower",
    val sessionTimeout: Int = 86400,
) {
    companion object {
        private const val DEFAULT_SECRET = "booktower-secret-key-change-in-production"
    }

    fun validate() {
        if (jwtSecret == DEFAULT_SECRET) {
            val isDev = System.getenv("BOOKTOWER_ENV")?.lowercase() != "production"
            if (isDev) {
                logger.warn("Using default JWT secret. Set BOOKTOWER_JWT_SECRET for production.")
            } else {
                error("Default JWT secret cannot be used in production. Set BOOKTOWER_JWT_SECRET environment variable.")
            }
        }
    }
}

data class StorageConfig(
    val booksPath: String = "./data/books",
    val coversPath: String = "./data/covers",
    val tempPath: String = "./data/temp",
) {
    fun ensureDirectories() {
        listOf(booksPath, coversPath, tempPath).forEach { path ->
            val dir = java.io.File(path)
            if (!dir.exists() && !dir.mkdirs()) {
                error("Failed to create directory: $path")
            }
        }
    }
}

data class CsrfConfig(
    val allowedHosts: Set<String> = setOf("localhost", "127.0.0.1", "0.0.0.0"),
)

data class WeblateConfig(
    val url: String = "",
    val apiToken: String = "",
    val component: String = "booktower/translations",
    val enabled: Boolean = false,
)

data class OidcConfig(
    val enabled: Boolean = false,
    val issuer: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val scope: String = "openid email profile",
    val forceOnlyMode: Boolean = false,
    val adminGroupPattern: String? = null,
    val groupsClaim: String = "groups",
)

data class MetadataConfig(
    val hardcoverApiKey: String = "",
    val comicvineApiKey: String = "",
)

data class FtsConfig(
    val enabled: Boolean = false,
    val throttleMs: Long = 300,
)

data class SmtpConfig(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "",
    val tls: Boolean = true,
) {
    val enabled: Boolean get() = host.isNotBlank() && from.isNotBlank()
}
