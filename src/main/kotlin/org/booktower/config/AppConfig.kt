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
) {
    companion object {
        fun load(): AppConfig {
            val config = ConfigFactory.load()
            val app = config.getConfig("app")

            return AppConfig(
                name = app.getString("name"),
                host = app.getString("host"),
                port = app.getInt("port"),
                database = DatabaseConfig.load(app.getConfig("database")),
                security = SecurityConfig.load(app.getConfig("security")),
                storage = StorageConfig.load(app.getConfig("storage")),
                weblate = WeblateConfig.load(app.getConfig("weblate")),
                csrf = CsrfConfig.load(app.getConfig("csrf")),
            ).also {
                logger.info("Loaded configuration: appName=${it.name}, port=${it.port}")
            }
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val driver: String = "org.mariadb.jdbc.Driver",
) {
    companion object {
        fun load(config: com.typesafe.config.Config): DatabaseConfig {
            return DatabaseConfig(
                url = config.getString("url"),
                username = config.getString("username"),
                password = config.getString("password"),
                driver = config.getString("driver"),
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
                    throw IllegalStateException(
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
                booksPath = config.getString("books-path"),
                coversPath = config.getString("covers-path"),
                tempPath = config.getString("temp-path"),
            )
        }
    }

    fun ensureDirectories() {
        listOf(booksPath, coversPath, tempPath).forEach { path ->
            val dir = java.io.File(path)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Failed to create directory: $path")
            }
        }
    }
}

data class CsrfConfig(
    val allowedHosts: Set<String>,
) {
    companion object {
        fun load(config: com.typesafe.config.Config): CsrfConfig {
            return CsrfConfig(
                allowedHosts = config.getStringList("allowed-hosts").toSet(),
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
