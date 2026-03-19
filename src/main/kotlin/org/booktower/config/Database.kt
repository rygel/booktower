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
        private const val DEFAULT_MAX_POOL_SIZE = 10
        private const val DEFAULT_MIN_IDLE = 2
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L

        fun connect(config: DatabaseConfig): Database {
            logger.info("Connecting to database: ${config.url}")

            val maxPool = System.getenv("BOOKTOWER_DB_POOL_MAX")?.toIntOrNull() ?: DEFAULT_MAX_POOL_SIZE
            val minIdle = System.getenv("BOOKTOWER_DB_POOL_MIN_IDLE")?.toIntOrNull() ?: DEFAULT_MIN_IDLE
            logger.info("Connection pool: max=$maxPool, minIdle=$minIdle")

            val hikariConfig =
                HikariConfig().apply {
                    jdbcUrl = config.url
                    username = config.username
                    password = config.password
                    driverClassName = config.driver
                    maximumPoolSize = maxPool
                    minimumIdle = minIdle
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
