# http4k + HTMX Quick Start Guide

This guide helps you get started with the http4k + HTMX migration for BookTower.

## Prerequisites

- Java 17+ (for http4k)
- Kotlin 2.0+
- Gradle 8.5+
- Docker (optional, for testing)

## Project Setup

### 1. Create New Module

Create a new directory `booktower-web` in your project:

```bash
mkdir booktower-web
cd booktower-web
```

### 2. Gradle Configuration

Create `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("io.ktor.plugin") version "2.3.8" // For fat JAR
}

group = "org.booktower"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // http4k
    implementation(platform("org.http4k:http4k-bom:5.25.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-client-okhttp")
    implementation("org.http4k:http4k-template-pebble")
    implementation("org.http4k:http4k-format-jackson")
    implementation("org.http4k:http4k-security-oauth")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    
    // Validation
    implementation("io.konform:konform:0.5.0")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    
    // Typesafe Config
    implementation("com.typesafe:config:1.4.3")
    
    // Testing
    testImplementation("org.http4k:http4k-testing-approval")
    testImplementation("org.http4k:http4k-testing-hamkrest")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("org.booktower.web.BookTowerWebAppKt")
}

// Fat JAR for Docker
tasks.shadowJar {
    archiveClassifier.set("fat")
    manifest {
        attributes["Main-Class"] = "org.booktower.web.BookTowerWebAppKt"
    }
}
```

### 3. Directory Structure

```bash
mkdir -p src/main/kotlin/org/booktower/web/{config,handlers,models,services,utils}
mkdir -p src/main/resources/{templates,static/{css,js}}
mkdir -p src/test/kotlin/org/booktower/web
```

## Basic Implementation

### 4. Main Application

Create `src/main/kotlin/org/booktower/web/BookTowerWebApp.kt`:

```kotlin
package org.booktower.web

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.PebbleTemplates
import org.http4k.template.ViewModel
import org.http4k.template.viewModel

// View Model for template
data class HomePage(val title: String, val message: String) : ViewModel {
    override fun template() = "home.html"
}

fun main() {
    // Configure template engine
    val renderer = PebbleTemplates().CachingClasspath()
    
    // Routes
    val app: HttpHandler = routes(
        "/" bind GET to { req: Request ->
            val view = HomePage("BookTower", "Welcome to your library!")
            Response(OK).body(renderer(view))
        },
        
        "/static" bind staticRoutes(),
        
        "/health" bind GET to { Response(OK).body("OK") }
    )
    
    // Add filters
    val filteredApp = ServerFilters.CatchAll()
        .then(DebuggingFilters.PrintRequestAndResponse())
        .then(app)
    
    // Start server
    println("Starting server on http://localhost:8080")
    filteredApp.asServer(Jetty(8080)).start().block()
}

fun staticRoutes(): HttpHandler {
    // Serve static files from classpath
    return org.http4k.routing.static(Classpath("/static"))
}
```

### 5. Templates

Create `src/main/resources/templates/home.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{{ title }} - BookTower</title>
    <script src="https://unpkg.com/htmx.org@1.9.10"></script>
    <style>
        body { font-family: system-ui, sans-serif; max-width: 800px; margin: 0 auto; padding: 2rem; }
        .book-card { border: 1px solid #ddd; padding: 1rem; margin: 1rem 0; border-radius: 8px; }
    </style>
</head>
<body>
    <h1>{{ title }}</h1>
    <p>{{ message }}</p>
    
    <div id="content">
        <button hx-get="/books" hx-target="#content" hx-swap="innerHTML">
            Load Books
        </button>
    </div>
</body>
</html>
```

### 6. API Client

Create `src/main/kotlin/org/booktower/web/services/ApiClient.kt`:

```kotlin
package org.booktower.web.services

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters

class ApiClient(private val baseUrl: String) {
    
    private val client: HttpHandler
    private val mapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    
    init {
        client = ClientFilters.SetBaseUriFrom(baseUrl)
            .then(DebuggingFilters.PrintRequestAndResponse())
            .then(OkHttp())
    }
    
    fun getBooks(): List<Book> {
        val request = Request(Method.GET, "/api/v1/books")
        val response = client(request)
        
        return if (response.status == Status.OK) {
            mapper.readValue(response.bodyString(), mapper.typeFactory.constructCollectionType(List::class.java, Book::class.java))
        } else {
            emptyList()
        }
    }
    
    fun getBook(id: Long): Book? {
        val request = Request(Method.GET, "/api/v1/books/$id")
        val response = client(request)
        
        return if (response.status == Status.OK) {
            mapper.readValue(response.bodyString(), Book::class.java)
        } else {
            null
        }
    }
}

// Domain models
data class Book(
    val id: Long,
    val title: String,
    val authors: List<String> = emptyList(),
    val coverUrl: String? = null,
    val description: String? = null
)
```

### 7. Book Handlers

Create `src/main/kotlin/org/booktower/web/handlers/BookHandlers.kt`:

```kotlin
package org.booktower.web.handlers

import org.booktower.web.services.ApiClient
import org.booktower.web.services.Book
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.template.PebbleTemplates
import org.http4k.template.ViewModel

data class BookListPage(val books: List<Book>) : ViewModel {
    override fun template() = "books/list.html"
}

data class BookDetailPage(val book: Book) : ViewModel {
    override fun template() = "books/detail.html"
}

fun bookRoutes(apiClient: ApiClient): HttpHandler {
    val renderer = PebbleTemplates().CachingClasspath()
    
    return routes(
        "/books" bind GET to { req: Request ->
            val books = apiClient.getBooks()
            val isHtmx = req.header("HX-Request") != null
            
            if (isHtmx) {
                // Return partial for HTMX
                Response(OK).body(renderBookGrid(renderer, books))
            } else {
                // Return full page
                Response(OK).body(renderer(BookListPage(books)))
            }
        },
        
        "/books/{id}" bind GET to { req: Request ->
            val id = req.path("id")?.toLongOrNull()
                ?: return@to Response(Status.BAD_REQUEST).body("Invalid ID")
            
            val book = apiClient.getBook(id)
                ?: return@to Response(Status.NOT_FOUND).body("Book not found")
            
            Response(OK).body(renderer(BookDetailPage(book)))
        }
    )
}

fun renderBookGrid(renderer: org.http4k.template.TemplateRenderer, books: List<Book>): String {
    return books.joinToString("\n") { book ->
        """
        <div class="book-card" hx-get="/books/${book.id}" hx-target="#content" hx-push-url="true">
            <h3>${book.title}</h3>
            <p>${book.authors.joinToString(", ")}</p>
        </div>
        """.trimIndent()
    }
}
```

### 8. Configuration

Create `src/main/resources/application.conf`:

```hocon
app {
  name = "BookTower Web"
  port = 8080
  
  api {
    base-url = "http://localhost:6060"
    base-url = ${?API_BASE_URL}
  }
  
  security {
    jwt-secret = ${?JWT_SECRET}
    session-timeout = 24h
  }
}
```

Create `src/main/kotlin/org/booktower/web/config/AppConfig.kt`:

```kotlin
package org.booktower.web.config

import com.typesafe.config.ConfigFactory
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

data class AppConfig(
    val appName: String,
    val port: Int,
    val apiBaseUrl: String,
    val jwtSecret: String,
    val sessionTimeout: Long
) {
    companion object {
        fun load(): AppConfig {
            val config = ConfigFactory.load()
            
            return AppConfig(
                appName = config.getString("app.name"),
                port = config.getInt("app.port"),
                apiBaseUrl = config.getString("app.api.base-url"),
                jwtSecret = config.getString("app.security.jwt-secret"),
                sessionTimeout = config.getDuration("app.security.session-timeout").toMinutes()
            ).also {
                logger.info { "Loaded configuration: apiUrl=${it.apiBaseUrl}, port=${it.port}" }
            }
        }
    }
}
```

## Running the Application

### Local Development

```bash
# From booktower-web directory
./gradlew run

# Or build and run JAR
./gradlew shadowJar
java -jar build/libs/booktower-web-1.0-SNAPSHOT-fat.jar
```

### Docker

Create `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY build/libs/*-fat.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  booktower-web:
    build: ./booktower-web
    ports:
      - "8080:8080"
    environment:
      - API_BASE_URL=http://booktower-api:6060
      - JWT_SECRET=your-secret-key
    depends_on:
      - booktower-api
  
  booktower-api:
    # Your existing Spring Boot app
    image: booktower-api:latest
    ports:
      - "6060:6060"
    environment:
      - DATABASE_URL=jdbc:mariadb://mariadb:3306/booktower
      # ... other env vars
  
  mariadb:
    image: mariadb:11
    environment:
      - MYSQL_ROOT_PASSWORD=booktower
      - MYSQL_DATABASE=booktower
```

## Testing

Create a simple test `src/test/kotlin/org/booktower/web/BookHandlerTest.kt`:

```kotlin
package org.booktower.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.booktower.web.handlers.bookRoutes
import org.booktower.web.services.ApiClient
import org.booktower.web.services.Book
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status

class BookHandlerTest : StringSpec({
    
    "GET /books returns book list" {
        val apiClient = mockk<ApiClient>()
        every { apiClient.getBooks() } returns listOf(
            Book(1, "Test Book", listOf("Author"), null, null)
        )
        
        val handler = bookRoutes(apiClient)
        val response = handler(Request(GET, "/books"))
        
        response.status shouldBe Status.OK
        response.bodyString() shouldBe "Test Book"
    }
})
```

## Next Steps

1. **Add Authentication**: JWT validation, session management
2. **Add Forms**: Create/edit books with validation
3. **Add Filters**: Search, pagination
4. **Add Readers**: Integrate PDF.js, Foliate
5. **Add Real-time**: SSE for notifications

## Key HTMX Patterns

### 1. Basic GET Request
```html
<button hx-get="/books" hx-target="#result">Load</button>
<div id="result"></div>
```

### 2. Form Submission
```html
<form hx-post="/books" hx-target="#result" hx-swap="outerHTML">
    <input name="title" required>
    <button type="submit">Save</button>
</form>
```

### 3. Infinite Scroll
```html
<div hx-get="/books?page=2" 
     hx-trigger="revealed"
     hx-swap="beforeend">
</div>
```

### 4. Debounced Search
```html
<input type="search"
       hx-get="/books/search"
       hx-target="#results"
       hx-trigger="keyup changed delay:300ms">
```

### 5. Confirm Action
```html
<button hx-delete="/books/1"
        hx-confirm="Are you sure?"
        hx-target="closest .book-card">
    Delete
</button>
```

## Resources

- [http4k Documentation](https://www.http4k.org/)
- [HTMX Documentation](https://htmx.org/docs/)
- [Pebble Templates](https://pebbletemplates.io/)
- [HTMX Examples](https://htmx.org/examples/)

## Troubleshooting

### CORS Issues
If connecting to Spring Boot API, add CORS configuration to `application.yaml`:
```yaml
app:
  cors:
    allowed-origins: "http://localhost:8080"
```

### Template Not Found
Ensure templates are in `src/main/resources/templates/` and classpath is correctly configured.

### API Connection Failed
Check that Spring Boot API is running and `API_BASE_URL` is correctly set.
