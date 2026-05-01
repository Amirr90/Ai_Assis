---
name: token-refresh
description: >-
  Implements token refresh using an interceptor-style pipeline on Ktor HttpClient:
  persists tokens in SessionManager, retries responses that fail with HTTP 401 after
  a successful refresh, and guards against infinite retry loops. Use when adding OAuth/JWT
  refresh, Bearer auth pipelines, fixing 401 handling, SessionManager-backed auth storage,
  or Kotlin Android API clients that must recover from expired access tokens.
disable-model-invocation: true
---

# Token Refresh Skill

Implement token refresh mechanism:

- Use interceptor pattern
- Automatically retry failed requests (401)
- Store tokens in SessionManager
- Avoid infinite retry loops

## Stack default

Assume **Kotlin**, **Ktor Client**, and **`SessionManager`** as the token source of truth (`getAccessToken`, `getRefreshToken`, `saveTokens`). If OkHttp Retrofit appears in the codebase, mirror the same rules using an `Authenticator`/`Interceptor`; the flow is identical conceptually.

## Interceptor pattern (Ktor)

- Install an **`HttpSend` interceptor** (or equivalent request/response pipeline hook) **after** features that attach the Bearer header, so retries pick up updated tokens.

```kotlin
client.plugin(HttpSend).intercept { request ->
    // 1. Execute request via proceed()
    // 2. If 401 → attempt refresh → update SessionManager → clone request with new header → proceed once more
}
```

- Keep **Bearer attachment** centralized: read access token from `SessionManager`, set `Authorization` on outgoing requests inside the interceptor (or delegate to one small helper used only here).

## Automatic retry after 401

1. **`proceed(originalRequest)`** and inspect `response.status`; if **not** 401 `return response`.
2. If **refresh token missing**, clear session and `return response` — do **not** loop refresh.
3. If request is the **refresh token endpoint**, `return response` — never recurse into refresh-from-refresh.
4. **`Mutex` single-flight**: only one concurrent refresh runs; callers await the same `Deferred`/`suspend` outcome.
5. On successful refresh **`SessionManager.saveTokens(...)`** then **`close()`** response body/build a new **`HttpRequestBuilder`** cloning URL, method, body, headers (omit stale `Authorization`) and **`proceed(newRequest)`** once.

## Store tokens in SessionManager

- **Read** tokens at send time from `SessionManager` (inject via constructor/DI).
- **Write** tokens only inside the interceptor after refresh succeeds (never only in memory elsewhere).
- If refresh fails (**401**, **network**, malformed body), **clear credentials** consistently and surface failure (no silent infinite retry).

## Avoid infinite retry loops

Enforce **all** of the following:

| Guard | Purpose |
|--------|---------|
| **Per-request flag** (`request.attributes` or mutable map keyed by correlation id) **`AlreadyRetried401`** | At most **one** automatic retry after refresh for that logical call. |
| **Refresh-path exclusion** | Skip refresh logic when URL matches `/oauth/token`, `/refresh`, or your project's refresh route. |
| **No retry on retry** | If the retried response is still 401, **return it** immediately. |
| **Refresh failure short-circuit** | Do not repeatedly call refresh API on repeated failures within the same request chain. |
| Optional: **marker header** stripped before send (`X-Skip-Token-Refresh: 1`) for internal/login calls |

## Pseudocode checklist

```
on send(request):
  if request is refresh endpoint → proceed only, no retry logic after 401 from here
  attach Bearer from SessionManager
  response = proceed(request)
  if response.status != 401 → return response
  if request.alreadyRetried401 → return response
  if no refresh token → clear session → return response
  sync(refresh Mutex):
    tokens = SessionManager.peek or read
    if refresh still returns 401/expired → clear session → return first 401 response
    SessionManager.saveTokens(new...)
  retryRequest = clone(request); mark alreadyRetried401; drop Authorization → re-attach Bearer
  return proceed(retryRequest)
```

## Verification

- [ ] Concurrent requests with expired access token trigger **one** refresh behind the Mutex.
- [ ] Refresh failure logs out user / clears SessionManager exactly once path.
- [ ] Calls to refresh URL never stack inside another refresh.
- [ ] No second automatic retry after a failed retry (loop safe).
