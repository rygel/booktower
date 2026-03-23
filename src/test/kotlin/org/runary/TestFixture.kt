package org.runary

import org.runary.config.AppConfig
import org.runary.config.Database
import org.runary.config.TemplateRenderer

object TestFixture {
    val config: AppConfig by lazy { AppConfig.load() }
    val database: Database by lazy { Database.connect(config.database) }

    // Shared across all tests — the JTE dynamic engine uses a child classloader per instance;
    // creating one per test causes class-name conflicts in the JVM.
    val templateRenderer: TemplateRenderer by lazy { TemplateRenderer() }
}
