package org.booktower.config

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import java.nio.file.Path

object TemplateEngine {
    private val engine: TemplateEngine by lazy {
        val codeResolver = DirectoryCodeResolver(Path.of("src/main/jte"))
        TemplateEngine.create(codeResolver, Path.of("target/generated-sources/jte"), ContentType.Html, javaClass.classLoader)
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
