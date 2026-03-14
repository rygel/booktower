# Jackson Data Class Deserialization Explained

## The Problem

You're right to question why we need Jackson Data Class Deserialization. Let me explain the issue and the solution.

### Why This Happens

Kotlin data classes don't have a no-args constructor by default:

```kotlin
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
)
```

Jackson ObjectMapper expects either:
1. A no-args constructor
2. Explicit annotations (@JsonCreator, @JsonProperty)
3. Kotlin module that understands Kotlin data classes

Without any of these, Jackson throws:
```
InvalidDefinitionException: Cannot construct instance of CreateUserRequest 
(no Creators, like default constructor, exist)
```

### Solutions

## Option 1: Jackson Kotlin Module (What we tried)

Add Jackson Kotlin module to teach Jackson about Kotlin data classes:

```kotlin
import com.fasterxml.jackson.module.kotlin.KotlinModule

val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
```

**Pros**: Works with existing code
**Cons**: Extra dependency, extra complexity

## Option 2: Manual JSON Parsing (Too complex)

Write custom JSON parsing to avoid Jackson entirely:

```kotlin
private fun <T> parseJson(json: String): T {
    val map = parseJsonObject(json)
    // ... complex manual parsing
}
```

**Pros**: No extra dependencies
**Cons**: Error-prone, doesn't handle edge cases, reinventing the wheel

## Option 3: http4k Native JSON (What we attempted)

Use http4k's built-in JSON handling:

```kotlin
import org.http4k.format.Gson.auto

private val gson = Gson.auto
val request = gson.fromJson(json, CreateUserRequest::class.java)
```

**Pros**: Framework-native, no extra deps
**Cons**: API complexity, http4k version compatibility issues

## The Right Solution

**Use the Jackson Kotlin Module** because:

1. **Least complexity** - One line of code
2. **Most reliable** - Well-tested library
3. **Existing ecosystem** - Jackson is standard for JSON
4. **Minimal overhead** - Just one small dependency

### Current Status

The application currently works with:
- Jackson for JSON parsing
- Jackson Kotlin module for data class support
- http4k for HTTP routing
- JTE for HTML templates

This is a standard, production-ready stack.

## Why We Can't Remove Jackson

1. **http4k doesn't provide built-in JSON parsing that's simpler** - The native approach has API complexity
2. **Manual JSON is too error-prone** - Need to handle all edge cases
3. **Kotlin data classes need special handling** - This is a known limitation of Kotlin + Jackson

## Conclusion

Using Jackson Kotlin Module is the **right solution** because:
- ✅ Simple (1 line of code)
- ✅ Reliable (well-tested)
- ✅ Standard (industry practice)
- ✅ Minimal overhead (small dependency)

The alternatives are:
- More complex (http4k native)
- More error-prone (manual parsing)
- Less maintainable (custom code)

This is not adding unnecessary complexity - it's using the **simplest working solution** for a known Kotlin + Jackson interoperability issue.
