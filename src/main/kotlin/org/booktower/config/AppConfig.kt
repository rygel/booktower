package org.booktower.config

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AppConfig::class.java)

data class AppConfig(
    val name: String,
    val host: String,
    val port: Int,
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val storage: StorageConfig,
    val weblate: WeblateConfig,
    val csrf: CsrfConfig,
    val smtp: SmtpConfig,
    val baseUrl: String,
    val registrationOpen: Boolean = true,
    val autoScanMinutes: Long = 60L,
    val oidc: OidcConfig = OidcConfig(),
    val metadata: MetadataConfig = MetadataConfig(),
    val demoMode: Boolean = false,
) {
    companion object {
        fun load(): AppConfig {
            val config = ConfigFactory.load()
            val app = config.getConfig("app")
            val host = System.getenv("BOOKTOWER_HOST") ?: app.getString("host")
            val port = System.getenv("BOOKTOWER_PORT")?.toIntOrNull() ?: app.getInt("port")

            return AppConfig(
                name = app.getString("name"),
                host = host,
                port = port,
                database = DatabaseConfig.load(app.getConfig("database")),
                security = SecurityConfig.load(app.getConfig("security")),
                storage = StorageConfig.load(app.getConfig("storage")),
                weblate = WeblateConfig.load(app.getConfig("weblate")),
                csrf = CsrfConfig.load(app.getConfig("csrf")),
                smtp = SmtpConfig.load(),
                baseUrl = System.getenv("BOOKTOWER_BASE_URL") ?: "http://$host:$port",
                registrationOpen = System.getenv("BOOKTOWER_REGISTRATION_OPEN")?.lowercase() != "false",
                autoScanMinutes = System.getenv("BOOKTOWER_AUTO_SCAN_MINUTES")?.toLongOrNull() ?: 60L,
                oidc = OidcConfig.load(),
                metadata = MetadataConfig.load(),
                demoMode = System.getenv("BOOKTOWER_DEMO_MODE")?.lowercase() == "true",
            ).also {
                logger.info("Loaded configuration: appName=${it.name}, port=${it.port}, autoScanMinutes=${it.autoScanMinutes}, smtpEnabled=${it.smtp.enabled}, registrationOpen=${it.registrationOpen}")
            }
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val driver: String = "org.h2.Driver",
) {
    companion object {
        fun load(config: com.typesafe.config.Config): DatabaseConfig {
            return DatabaseConfig(
                url = System.getenv("BOOKTOWER_DB_URL") ?: config.getString("url"),
                username = System.getenv("BOOKTOWER_DB_USERNAME") ?: config.getString("username"),
                password = System.getenv("BOOKTOWER_DB_PASSWORD") ?: config.getString("password"),
                driver = System.getenv("BOOKTOWER_DB_DRIVER") ?: config.getString("driver"),
            )
        }
    }
}

data class SecurityConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val sessionTimeout: Int = 86400,
) {
    companion object {
        private const val DEFAULT_SECRET = "booktower-secret-key-change-in-production"

        fun load(config: com.typesafe.config.Config): SecurityConfig {
            val secret = System.getenv("BOOKTOWER_JWT_SECRET")
                ?: config.getString("jwt-secret")

            if (secret == DEFAULT_SECRET) {
                val isDev = System.getenv("BOOKTOWER_ENV")?.lowercase() != "production"
                if (isDev) {
                    logger.warn("Using default JWT secret. Set BOOKTOWER_JWT_SECRET env var for production.")
                } else {
                    error(
                        "Default JWT secret cannot be used in production. Set BOOKTOWER_JWT_SECRET environment variable.",
                    )
                }
            }

            return SecurityConfig(
                jwtSecret = secret,
                jwtIssuer = config.getString("jwt-issuer"),
                sessionTimeout = config.getInt("session-timeout"),
            )
        }
    }
}

data class StorageConfig(
    val booksPath: String,
    val coversPath: String,
    val tempPath: String,
) {
    companion object {
        fun load(config: com.typesafe.config.Config): StorageConfig {
            return StorageConfig(
                booksPath = System.getenv("BOOKTOWER_BOOKS_PATH") ?: config.getString("books-path"),
                coversPath = System.getenv("BOOKTOWER_COVERS_PATH") ?: config.getString("covers-path"),
                tempPath = System.getenv("BOOKTOWER_TEMP_PATH") ?: config.getString("temp-path"),
            )
        }
    }

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
    val allowedHosts: Set<String>,
) {
    companion object {
        fun load(config: com.typesafe.config.Config): CsrfConfig {
            val envHosts = System.getenv("BOOKTOWER_CSRF_ALLOWED_HOSTS")
            return CsrfConfig(
                allowedHosts = if (envHosts != null) {
                    envHosts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else {
                    config.getStringList("allowed-hosts").toSet()
                },
            )
        }
    }
}

data class WeblateConfig(
    val url: String,
    val apiToken: String,
    val component: String,
    val enabled: Boolean = false,
) {
    companion object {
        fun load(config: com.typesafe.config.Config): WeblateConfig {
            return WeblateConfig(
                url = config.getString("url"),
                apiToken = config.getString("api-token"),
                component = config.getString("component"),
                enabled = config.getBoolean("enabled"),
            )
        }
    }
}

data class OidcConfig(
    val enabled: Boolean = false,
    val issuer: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val scope: String = "openid email profile",
    // When true, local username/password login is disabled; users must authenticate via OIDC
    val forceOnlyMode: Boolean = false,
    // Regex pattern matched against OIDC groups claim to grant admin role (e.g. "^booktower-admin$")
    val adminGroupPattern: String? = null,
    // Name of the claim in the userinfo response that contains group memberships (e.g. "groups")
    val groupsClaim: String = "groups",
) {
    companion object {
        fun load(): OidcConfig = OidcConfig(
            enabled = System.getenv("OIDC_ISSUER")?.isNotBlank() == true,
            issuer = System.getenv("OIDC_ISSUER") ?: "",
            clientId = System.getenv("OIDC_CLIENT_ID") ?: "",
            clientSecret = System.getenv("OIDC_CLIENT_SECRET") ?: "",
            redirectUri = System.getenv("OIDC_REDIRECT_URI") ?: "",
            scope = System.getenv("OIDC_SCOPE") ?: "openid email profile",
            forceOnlyMode = System.getenv("OIDC_FORCE_ONLY")?.lowercase() == "true",
            adminGroupPattern = System.getenv("OIDC_ADMIN_GROUP_PATTERN")?.takeIf { it.isNotBlank() },
            groupsClaim = System.getenv("OIDC_GROUPS_CLAIM") ?: "groups",
        )
    }
}

data class MetadataConfig(
    val hardcoverApiKey: String = "",
    val comicvineApiKey: String = "",
) {
    companion object {
        fun load(): MetadataConfig = MetadataConfig(
            hardcoverApiKey = System.getenv("BOOKTOWER_HARDCOVER_API_KEY") ?: "",
            comicvineApiKey = System.getenv("BOOKTOWER_COMICVINE_API_KEY") ?: "",
        )
    }
}

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val tls: Boolean,
) {
    val enabled: Boolean get() = host.isNotBlank() && from.isNotBlank()

    companion object {
        fun load(): SmtpConfig = SmtpConfig(
            host = System.getenv("BOOKTOWER_SMTP_HOST") ?: "",
            port = System.getenv("BOOKTOWER_SMTP_PORT")?.toIntOrNull() ?: 587,
            username = System.getenv("BOOKTOWER_SMTP_USER") ?: "",
            password = System.getenv("BOOKTOWER_SMTP_PASS") ?: "",
            from = System.getenv("BOOKTOWER_SMTP_FROM") ?: "",
            tls = System.getenv("BOOKTOWER_SMTP_TLS")?.lowercase() != "false",
        )
    }
}
