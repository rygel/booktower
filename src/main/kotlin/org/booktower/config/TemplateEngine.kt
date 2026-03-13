package org.booktower.config

object TemplateEngine {
    fun render(template: String, model: Map<String, Any>): String {
        return when (template) {
            "home.kte" -> renderHome(model)
            "books.kte" -> renderBooks(model)
            else -> "<html><body>Template not found: $template</body></html>"
        }
    }
    
    private fun renderHome(model: Map<String, Any>): String {
        val isAuth = model["isAuthenticated"] as? Boolean ?: false
        val libraries = model["libraries"] as? List<*> ?: emptyList<Any>()
        
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BookTower</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <link href="https://cdn.jsdelivr.net/npm/tailwindcss@2.2.19/dist/tailwind.min.css" rel="stylesheet">
</head>
<body class="bg-gray-50 min-h-screen">
    <nav class="bg-white shadow-sm border-b">
        <div class="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
            <a href="/" class="text-2xl font-bold text-indigo-600">BookTower</a>
            <div>
                ${if (isAuth) 
                    """<a href="/settings" class="text-gray-600 mr-4">Settings</a>
                    <form action="/auth/logout" method="POST" class="inline">
                        <button type="submit" class="text-gray-600">Logout</button>
                    </form>"""
                    else
                    """<a href="/login" class="text-indigo-600 mr-4">Login</a>
                    <a href="/register" class="bg-indigo-600 text-white px-4 py-2 rounded">Sign Up</a>"""}
            </div>
        </div>
    </nav>
    <main class="max-w-7xl mx-auto px-4 py-8">
        ${if (isAuth) {
            val libsHtml = libraries.joinToString("") { """<div class="bg-white p-4 rounded shadow">${(it as? Map<*, *>)?.get("name") ?: "Unknown"}</div>""" }
            """<h1 class="text-2xl font-bold mb-4">Your Libraries</h1>
            <div class="grid grid-cols-3 gap-4">$libsHtml</div>"""
        } else {
            """<div class="text-center py-20">
                <h1 class="text-4xl font-bold mb-4">Welcome to BookTower</h1>
                <p class="text-xl text-gray-600 mb-8">Your personal digital library</p>
            </div>"""
        }}
    </main>
</body>
</html>
        """.trimIndent()
    }
    
    private fun renderBooks(model: Map<String, Any>): String {
        val books = model["books"] as? List<*> ?: emptyList<Any>()
        
        if (books.isEmpty()) {
            return """<div class="col-span-full text-center py-12 text-gray-500"><p>No books found</p></div>"""
        }
        
        return books.joinToString("") { book ->
            val title = (book as? Map<*, *>)?.get("title") ?: "Unknown"
            """<div class="book-card bg-white rounded-lg shadow p-4">
                <h3 class="font-medium">$title</h3>
            </div>"""
        }
    }
}
