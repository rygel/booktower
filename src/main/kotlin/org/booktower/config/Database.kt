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
        fun connect(config: DatabaseConfig): Database {
            logger.info("Connecting to database: ${config.url}")

            val hikariConfig =
                HikariConfig().apply {
                    jdbcUrl = config.url
                    username = config.username
                    password = config.password
                    driverClassName = config.driver
                    maximumPoolSize = 10
                    minimumIdle = 2
                    idleTimeout = 600000
                    connectionTimeout = 30000
                }

            val dataSource = HikariDataSource(hikariConfig)

            val flyway =
                Flyway.configure()
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
