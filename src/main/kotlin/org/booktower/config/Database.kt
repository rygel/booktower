package org.booktower.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("booktower.Database")

class Database private constructor(
    private val dataSource: HikariDataSource,
    private val jdbi: Jdbi,
) {
    companion object {
        private const val MAX_POOL_SIZE = 10
        private const val MIN_IDLE = 2
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L

        fun connect(config: DatabaseConfig): Database {
            logger.info("Connecting to database: ${config.url}")

            // PostgreSQL needs stringtype=unspecified so JDBI can bind Instant.toString()
            // to TIMESTAMP columns without explicit casting
            val effectiveUrl =
                if (config.url.startsWith("jdbc:postgresql") && !config.url.contains("stringtype=")) {
                    val separator = if (config.url.contains('?')) "&" else "?"
                    "${config.url}${separator}stringtype=unspecified"
                } else {
                    config.url
                }

            val hikariConfig =
                HikariConfig().apply {
                    jdbcUrl = effectiveUrl
                    username = config.username
                    password = config.password
                    driverClassName = config.driver
                    maximumPoolSize = MAX_POOL_SIZE
                    minimumIdle = MIN_IDLE
                    idleTimeout = IDLE_TIMEOUT_MS
                    connectionTimeout = CONNECTION_TIMEOUT_MS
                }

            val dataSource = HikariDataSource(hikariConfig)

            val flyway =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()

            logger.info("Running Flyway migrations...")
            flyway.migrate()
            logger.info("Database migrations completed")

            val jdbi = Jdbi.create(dataSource)
            jdbi.installPlugin(SqlObjectPlugin())

            return Database(dataSource, jdbi)
        }
    }

    fun <T> onDemand(clazz: Class<T>): T = jdbi.onDemand(clazz)

    fun getJdbi(): Jdbi = jdbi

    fun close() {
        if (!dataSource.isClosed) {
            logger.info("Closing database connection pool...")
            dataSource.close()
            logger.info("Database connection pool closed")
        }
    }
}
