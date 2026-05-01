---
name: api-ktor-skill
description: Build Android networking layers with Ktor client using suspend functions, sealed result states, timeout configuration, and safe error parsing. Use when implementing API calls, repositories, remote data sources, or handling network errors in Kotlin Android projects.
disable-model-invocation: true
---

# API + Ktor Skill

Use Ktor client for networking.

## Rules

- Use suspend functions
- Return sealed class (Success, Error, Loading)
- Handle exceptions safely
- Add timeout and proper error parsing

## Implementation Guidelines

- Keep business logic in data/domain layers, not composables.
- Use unidirectional data flow with ViewModel state exposed via `StateFlow`.
- Map transport DTOs to domain models before exposing to UI.
- Configure request, connect, and socket timeouts.
- Convert API/network failures into typed error messages for UI.

## Preferred Result Wrapper

```kotlin
sealed class ApiResult<out T> {
    data object Loading : ApiResult<Nothing>()
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(
        val message: String,
        val code: Int? = null,
        val cause: Throwable? = null
    ) : ApiResult<Nothing>()
}
```

## Ktor Client Baseline

Use this baseline setup (adjust base URL and plugins per feature):

```kotlin
val httpClient = HttpClient(Android) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}
```

## Repository Flow Pattern

1. Emit `ApiResult.Loading`.
2. Execute API call inside `try/catch`.
3. If successful, emit `ApiResult.Success(mappedData)`.
4. If failed, parse error body when available and emit `ApiResult.Error`.
5. Never throw raw exceptions to UI layer.

## Safe Error Parsing Pattern

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: ClientRequestException) {
        val message = runCatching { e.response.bodyAsText() }.getOrNull()
            ?: "Client error"
        ApiResult.Error(message = message, code = e.response.status.value, cause = e)
    } catch (e: ServerResponseException) {
        val message = runCatching { e.response.bodyAsText() }.getOrNull()
            ?: "Server error"
        ApiResult.Error(message = message, code = e.response.status.value, cause = e)
    } catch (e: HttpRequestTimeoutException) {
        ApiResult.Error(message = "Request timeout", cause = e)
    } catch (e: IOException) {
        ApiResult.Error(message = "No internet connection", cause = e)
    } catch (e: Exception) {
        ApiResult.Error(message = "Unexpected error", cause = e)
    }
}
```

## Output Expectations

When applying this skill, generate:

- `ApiResult` sealed wrapper (or project-equivalent).
- Ktor client config with timeout.
- Suspend repository/remote data source functions.
- Safe exception mapping with parsed server error text when possible.
- ViewModel state updates through `StateFlow` using these result states.
