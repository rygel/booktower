package org.booktower.config

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import java.nio.file.Path

class TemplateRenderer {
    private val engine: TemplateEngine by lazy {
        // In development (BOOKTOWER_ENV != production and source tree present) use the dynamic
        // engine so template changes are picked up without a full rebuild.
        // In all other cases — including fat-JAR and native-image deployments — use the
        // precompiled classes that the jte-maven-plugin (generate goal) + Kotlin compiler
        // have already compiled into the classpath under gg.jte.generated.precompiled.
        val isDev = System.getenv("BOOKTOWER_ENV")?.lowercase() != "production"
        val sourceDir = Path.of("src/main/jte")

        if (isDev && sourceDir.toFile().isDirectory) {
            TemplateEngine.create(
                DirectoryCodeResolver(sourceDir),
                Path.of("target/generated-sources/jte"),
                ContentType.Html,
                javaClass.classLoader,
            )
        } else {
            TemplateEngine.createPrecompiled(ContentType.Html)
        }
    }

    fun render(
        template: String,
        model: Map<String, Any?>,
    ): String {
        val output = StringOutput()
        engine.render(template, model, output)
        return output.toString()
    }
}

// Keep backward compatibility for tests that use TemplateEngine directly
object TemplateEngine {
    private val renderer = TemplateRenderer()

    fun render(
        template: String,
        model: Map<String, Any?>,
    ): String = renderer.render(template, model)
}
