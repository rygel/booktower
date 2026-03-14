package org.booktower

import org.booktower.config.AppConfig
import org.booktower.config.Database

object TestFixture {
    val config: AppConfig by lazy { AppConfig.load() }
    val database: Database by lazy { Database.connect(config.database) }
}
