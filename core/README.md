# Http client diagram

```mermaid
                                +-------------------------------------+
                                |    ApiClientFactory                 |
                                | - configures ApiConfig              |
                                | - creates AdapterHttpClient         |
                                | - returns ApiCaller        |
                                |   (or returns HttpClient directly)  |
                                +----------+--------------------------+
                                           |
                       factory builds      | (injects)
                       AdapterHttpClient   v
                                +-----------------------------------------------+
                                |   AdapterHttpClient                           |  <--- central execution concerns
                                |   (implements HttpClient)                     |
                                | Responsibilities:                             |
                                |  - mergeConfig(base + per-request)            |
                                |  - serialize request body (BodySerializer)    |
                                |  - inject Content-Type header                 |
                                |  - call adapter.execute(...) (transport only) |
                                |  - apply RetryPolicy around transport         |
                                |  - deserialize response body (BodySerializer) |
                                +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
                                   |  |           |   ^
                                   |  |           |   |
            (A) Typical generated  |  |           |   | (B) Direct HttpClient usage
            flow (recommended)     |  |           |   |     (caller uses HttpClient)
                                   v  |           |   |
        +----------------------+   |  |           |   |
        | ApiCaller   |---+  |           |   |
        | - builds RequestData |------/           |   |
        | - supplies ResponseMapper -> maps final |   |
        | - NO retry/transport logic              |   |
        +----------------------+                  |   |
                                                  |   |
                                                  |   |
                         transport call           |   |
                         (raw RequestData)        |   |
                                  +---------------+   |
                                  |                   |
                                  v                   |
                        +------------------------+    |
                        | HttpClientAdapter      |    |
                        | (interface)            |    |
                        | - transport-only: send |
                        |   serialized bytes/str |
                        | - returns ResponseData |
                        |   (status/headers/body)|
                        +----+---------------+----+
                             |               |
                             |               |
                +------------+               +--------------+
                |                                           |
                v                                           v
    +---------------------------+               +---------------------------+
    | KtorHttpClientAdapter     |               | CustomHttpClientAdapter   |
    | - implements transport    |               | - any other transport impl|
    | - builds request for Ktor |               +---------------------------+
    +---------------------------+
```

Detailed execution sequence for path (A) ApiCaller -> AdapterHttpClient:
1) ApiCaller constructs RequestData (urlPath, method, headers, body, contentType, perRequestConfig)
2) Calls HttpClient method (get/post/...) on AdapterHttpClient
3) AdapterHttpClient.mergeConfig(baseCfg, request.perRequestConfig)
4) AdapterHttpClient serializes request body via resolved.bodySerializer -> SerializedBody(payload, contentType)
5) AdapterHttpClient injects Content-Type into headers and composes adapterRequest
6) AdapterHttpClient creates `call` closure that:
   a) invokes adapter.execute(adapterRequest, resolved)  <-- transport-only
   b) receives raw ResponseData (raw body + contentType)
   c) deserializes raw body via resolved.bodySerializer -> final body
   d) returns ResponseData with deserialized body
7) If resolved.retryPolicy != null -> AdapterHttpClient executes retryPolicy.retry(call) else call()
8) AdapterHttpClient returns ResponseData to ApiCaller
9) ApiCaller invokes ResponseMapper.map(response) -> typed result returned to user

Other possible paths:
- (B) Direct use of HttpClient:
    * User obtains AdapterHttpClient (or custom HttpClient) from factory and calls httpClient.get(...) directly.
    * Similar flow 3..9 above; caller must map ResponseData -> typed value (ResponseMapper or BodySerializer directly).
- (C) Legacy / discouraged: generated code calls HttpClientAdapter.execute(...) directly
    * Bypasses AdapterHttpClient -> misses centralized merge/serialize/retry/deserialize.
    * Results: duplicated logic, inconsistent headers/content-type, no retry applied centrally.
- (D) Custom Generated Caller:
    * ApiClientFactory can inject custom callerFactory(cfg, httpClient) -> returns a ApiCaller that may add instrumentation or alternate mapping.
    * Still should delegate to provided HttpClient to keep retry/serialization centralized.
- (E) Custom transport or no-serializer mode:
    * ApiConfig.bodySerializer = null -> AdapterHttpClient will forward payload as-is and not attempt serialize/deserialize.
    * AdapterHttpClient still does mergeConfig and retryPolicy wrapping.

Legend / Notes:
- "AdapterHttpClient" = central place for config merge, serialization, content-type, retry, deserialization.
- "HttpClientAdapter" implementations (KtorHttpClientAdapter) = transport-only; MUST NOT handle retries/serialization concerns.
- Prefer paths A or B. Avoid C (direct adapter.execute) because it breaks centralization.
- ResponseMapper is where ResponseData -> typed object mapping happens (remains generic and useful for ApiCaller).

Other types referenced:
- `RequestData.kt` : call properties (urlPath, method, headers, body, contentType, perRequestConfig)
- `ResponseData.kt`: status, headers, body (raw)
- `BodySerializer.kt` / `DefaultBodySerializer.kt`: serialize/deserialize logic (used only by `AdapterHttpClient`)
- `ResponseMapper.kt`: maps `ResponseData` -> typed result (used by `ApiCaller`)
- `OptionalSerializer.kt`: serialization helper (unrelated to transport)

Legend (flow):
- Caller builds a `RequestData` -> calls `ApiCaller.call(...)`
- `ApiCaller` delegates to `HttpClient` methods (get/post/...)
- `AdapterHttpClient` performs merge/serialize/retry/deserialize -> delegates transport to `HttpClientAdapter`
- `HttpClientAdapter` implementations (like `KtorHttpClientAdapter`) perform network I/O and return raw `ResponseData`
