package org.booktower.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("booktower.Database")

/** Known migration files — keep in sync with src/main/resources/db/migration/ */
private val MIGRATION_FILES =
    listOf(
        "V1__initial_schema.sql",
        "V2__linked_books.sql",
        "V3__performance_indexes.sql",
        "V4__book_sharing.sql",
        "V5__collections.sql",
        "V6__fts_metadata_search.sql",
        "V7__webhooks.sql",
    )

class Database private constructor(
    private val dataSource: HikariDataSource,
    private val jdbi: Jdbi,
) {
    companion object {
        private const val DEFAULT_MAX_POOL_SIZE = 10
        private const val DEFAULT_MIN_IDLE = 2
        private const val IDLE_TIMEOUT_MS = 600_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val MAX_LIFETIME_MS = 1_800_000L
        private const val LEAK_DETECTION_MS = 60_000L
        private const val VALIDATION_TIMEOUT_MS = 5_000L

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

            val maxPool = System.getenv("BOOKTOWER_DB_POOL_MAX")?.toIntOrNull() ?: DEFAULT_MAX_POOL_SIZE
            val minIdle = System.getenv("BOOKTOWER_DB_POOL_MIN_IDLE")?.toIntOrNull() ?: DEFAULT_MIN_IDLE
            logger.info("Connection pool: max=$maxPool, minIdle=$minIdle")

            val hikariConfig =
                HikariConfig().apply {
                    jdbcUrl = effectiveUrl
                    username = config.username
                    password = config.password
                    driverClassName = config.driver
                    maximumPoolSize = maxPool
                    minimumIdle = minIdle
                    idleTimeout = IDLE_TIMEOUT_MS
                    connectionTimeout = CONNECTION_TIMEOUT_MS
                    maxLifetime = MAX_LIFETIME_MS
                    validationTimeout = VALIDATION_TIMEOUT_MS
                    // Leak detection: log warning if connection held > 60s (disabled in test)
                    leakDetectionThreshold =
                        if (System.getProperty("app.name")?.contains("test") == true) 0 else LEAK_DETECTION_MS
                    // Use PostgreSQL's built-in validation instead of test query when available
                    connectionTestQuery =
                        if (config.driver.contains("postgresql", ignoreCase = true)) null else "SELECT 1"
                    poolName = "booktower-pool"
                }

            val dataSource = HikariDataSource(hikariConfig)

            logger.info("Running Flyway migrations...")
            val locations = resolveMigrationLocations()
            val flyway =
                Flyway
                    .configure()
                    .dataSource(dataSource)
                    .locations(*locations)
                    .baselineOnMigrate(true)
                    .load()
            val result = flyway.migrate()
            logger.info("Database migrations completed (${result.migrationsExecuted} applied)")

            val jdbi = Jdbi.create(dataSource)
            jdbi.installPlugin(SqlObjectPlugin())

            return Database(dataSource, jdbi)
        }

        /**
         * Resolves Flyway migration locations. Uses classpath by default.
         * In GraalVM native image, Flyway's classpath scanner can't enumerate
         * resources, so we extract migration SQL files to a temp directory
         * and use filesystem: location instead.
         */
        private fun resolveMigrationLocations(): Array<String> {
            // Try classpath first — works on JVM
            val testResource = Database::class.java.getResource("/db/migration/${MIGRATION_FILES.first()}")
            if (testResource != null && testResource.protocol != "resource") {
                return arrayOf("classpath:db/migration")
            }

            // GraalVM native image: extract to temp directory
            logger.info("Extracting migrations for native image (filesystem mode)")
            val tempDir = File(System.getProperty("java.io.tmpdir"), "booktower-migrations")
            if (!tempDir.mkdirs() && !tempDir.isDirectory) {
                logger.warn("Could not create migration temp directory: ${tempDir.absolutePath}")
            }

            for (name in MIGRATION_FILES) {
                val target = File(tempDir, name)
                if (target.exists()) continue
                val content = Database::class.java.getResourceAsStream("/db/migration/$name")
                if (content == null) {
                    logger.warn("Migration resource not found: $name")
                    continue
                }
                content.use { input -> target.outputStream().use { input.copyTo(it) } }
            }

            return arrayOf("filesystem:${tempDir.absolutePath}")
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
