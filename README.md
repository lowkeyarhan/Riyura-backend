# Riyura Backend

## Contents

- [Overview & About](#overview--about)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Database & Persistence](#database--persistence)
- [Caching](#caching)
- [Concurrency](#concurrency)
- [WebSocket & Watch Parties](#websocket--watch-parties)
- [Security & Authentication](#security--authentication)
- [Rate Limiting](#rate-limiting)
- [Health Check](#health-check)
- [Performance](#performance)

---

## Overview & About

This is a sophisticated backend service for Riyura that acts as the core orchestration layer for the modern media streaming platform. Built on **Spring Boot 4.x** with **Java 21**, it seamlessly serves rich media content, dynamic discovery features, personalized user experiences, and real-time collaborative watch parties. The system integrates with an external content API for media metadata, **Supabase** for identity/auth, **PostgreSQL** for persistent storage, and **Redis** for high-performance caching, ephemeral party state, and distributed rate limiting.

From managing trending banners and multi-provider stream URL resolution to synchronized watch parties with real-time chat, Riyura powers every essential feature of a full-stack entertainment hub.

---

## System Architecture

The application follows a **Modular Monolith** architecture that emphasizes high cohesion and loose coupling. Internal structure is organized into distinct, feature-driven modules — each owning its own controllers, services, repositories, models, and DTOs — making the platform highly maintainable, testable, and primed for a seamless migration to microservices if scale demands it.

### Core Modules

| Module       | Responsibility                                                                                                                                                                       |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Content**  | Central discovery engine — processes, filters, caches, and serves movies, TV shows, anime, banners, explore, and search. Orchestrates content API fetches with parallel async calls. |
| **Identity** | Protected user context — manages watchlists and watch history. All data is bound strictly to the authenticated user's JWT identity.                                                  |
| **Party**    | Real-time watch party engine — Redis-backed party state, STOMP WebSocket messaging, host migration, buffering sync, and live chat.                                                   |

## Tech Stack

| Layer         | Technology                                                     |
| ------------- | -------------------------------------------------------------- |
| Framework     | Spring Boot 4.x                                                |
| Language      | Java 21                                                        |
| Database      | PostgreSQL with HikariCP connection pooling                    |
| ORM           | Spring Data JPA / Hibernate                                    |
| Caching       | Redis (Spring Cache abstraction + custom RedisTemplate)        |
| Rate Limiting | Bucket4J + Redis (Lettuce) — distributed, fail-open, tiered    |
| Real-time     | STOMP over WebSocket (SockJS fallback)                         |
| Security      | Spring Security + OAuth2 Resource Server (Supabase JWT, HS256) |
| HTTP Client   | JDK 21 `HttpClient` with connection pooling and timeouts       |
| Async         | JDK 21 virtual threads + CompletableFuture (with timeouts)     |
| Content API   | External REST API (with retry logic)                           |
| Documentation | SpringDoc OpenAPI (Swagger UI)                                 |
| Build         | Maven (Maven Wrapper included)                                 |
| Utilities     | Lombok                                                         |

---

## Database & Persistence

Riyura uses PostgreSQL as its primary relational store, managed through Spring Data JPA with Hibernate. The schema covers three core entities: **stream providers** (configures available streaming providers with per-media-type URL templates, quality, priority, and an active toggle), **watchlist** (persists user-saved content with a metadata snapshot including title, poster, release date, and vote), and **watch history** (records full playback events with streaming context — provider used, stream ID, episode info, watch duration, and an anime flag for UI hints).

### Data Integrity

Both `watchlist` and `watch_history` tables enforce **unique constraints** on `(user_id, tmdb_id, media_type)` at the database level. This prevents duplicate entries even under concurrent requests (e.g. a user double-tapping "Add to Watchlist"). Services handle `DataIntegrityViolationException` gracefully — either returning the existing record or ignoring the duplicate — rather than surfacing a 500 error.

Per-user size limits are enforced in application code:

| Collection        | Max Size |
| ----------------- | -------- |
| **Watchlist**     | 500      |
| **Watch History** | 1000     |

### Pagination

User-specific list endpoints (watchlist, watch history) are paginated using Spring Data's `Pageable` with a default page size of **10 items**. Search results are paginated at **15 items per page**. Cache keys include the page number to avoid serving incorrect slices.

### Connection Pooling (HikariCP)

Database connections are managed by HikariCP with a maximum pool size of **30**, minimum idle of **5**, a connection timeout of **5 s**, and a max lifetime of **30 min**. The pool is intentionally sized to complement virtual thread concurrency — virtual threads park during I/O rather than blocking OS threads, so the pool doesn't need to match thread counts, but is still tuned to prevent connection exhaustion under high parallelism.

---

## Caching

Riyura uses **Redis** as its distributed cache, combining Spring's `@Cacheable` / `@CacheEvict` abstraction with a custom **cache stampede guard** (`CacheStampedeGuard`) that implements four complementary protections against thundering-herd effects when popular keys expire.

### Cache Stampede Prevention

| Technique                                   | Implementation                                                                                                                                 | Where Applied                                                                              |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| **Distributed mutex**                       | Redis `SETNX` lock — only one node recomputes at a time; others wait and retry                                                                 | XFetch, SWR, and **cold misses** (empty cache)                                             |
| **Cold-miss wait loop**                     | If the cache is empty and multiple threads arrive, one wins the lock and computes; others `sleep(50)` and retry until the value appears        | Both `xfetch` and `staleWhileRevalidate`                                                   |
| **XFetch** (Probabilistic Early Expiration) | Recomputes _before_ TTL expires when `beta × delta × -ln(rand) > remainingTTL` — expensive loaders refresh early while the cache is still warm | MovieService, TvService, AnimeService, MovieDetailService, TvDetailsService, SearchService |
| **Stale-While-Revalidate**                  | Soft TTL (fresh window) + hard TTL; when stale, serve value instantly and refresh in background so users never see latency spikes              | BannerService, ExploreService                                                              |
| **Proportional TTL jitter**                 | 10–20 % of base TTL added at write time — scales correctly for any TTL (5 min → 30–60 s spread; 7 days → 16–33 h spread)                       | All caches via `CacheStampedeGuard` and `CacheConfig`                                      |
| **`@Cacheable(sync = true)`**               | Spring's per-JVM mutex for annotation-based caches — single-threaded recompute under concurrent load                                           | WatchlistService, HistoryService                                                           |

### Cache Strategy

- **Serialization**: String keys with JSON values (`GenericJackson2JsonRedisSerializer`)
- **Null caching**: Disabled — absent values are never cached so transient errors don't poison the cache
- **Background refresh pool**: Dedicated `cacheRefreshExecutor` (4–16 threads) for SWR background refreshes; `CallerRunsPolicy` provides back-pressure if the queue is full

Content caches use `CacheStampedeGuard` and are keyed by their natural discriminator (e.g. `limit`, `query`, `id`). User-specific caches (`watchlist`, `history`) use `@Cacheable` with `sync = true` and are keyed by `userId + ':' + page` for pagination-aware caching. Writes trigger `@CacheEvict(allEntries = true)` to invalidate all pages for the affected user, ensuring consistency after additions or deletions.

### Redis Party State

Beyond Spring Cache, Redis is also used directly for **watch party state** (via `RedisTemplate`). Party objects are serialized as JSON and stored with a fixed party TTL constant defined in `RedisConfig`. This keeps party state distributed and resilient without requiring an in-memory server-side session.

---

## Concurrency

### Virtual Threads (JDK 21)

Virtual threads are enabled globally via:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This configures both the **Tomcat** request handler thread pool and all **`@Async`** tasks to run on JDK 21 virtual threads. Virtual threads park instead of blocking during I/O (API calls, DB queries, Redis ops), meaning thousands of concurrent requests can be handled with minimal OS thread overhead — no executor tuning required.

### WebSocket Executors

All three STOMP channel executors (broker, inbound, outbound) in `WebSocketConfig` are explicitly backed by a virtual thread executor:

```java
executor = Executors.newVirtualThreadPerTaskExecutor()
```

This ensures WebSocket message handling inherits the same non-blocking scalability as the HTTP layer.

### Dedicated Virtual Thread Executors

All services that use `CompletableFuture` for parallel API calls supply a **dedicated virtual thread executor** (`Executors.newVirtualThreadPerTaskExecutor()`) rather than relying on the `ForkJoinPool.commonPool()`. This prevents content API I/O from starving the shared pool used by framework internals and other CompletableFuture operations.

### Parallel Content Fetching (CompletableFuture)

Several services fire multiple content API calls in parallel using `CompletableFuture`, combining results after all futures complete. Every future carries an **8-second timeout** (`orTimeout(8, SECONDS)`) to prevent a single slow upstream call from blocking the entire request indefinitely. Because the underlying threads are virtual, parallel calls are cheap even at high fan-out:

| Service              | Parallel Operations                 |
| -------------------- | ----------------------------------- |
| `BannerService`      | Trending movies + trending TV       |
| `ExploreService`     | Movies + TV                         |
| `AnimeService`       | Anime movies + anime TV             |
| `SearchService`      | Multi-search + company search       |
| `MovieDetailService` | Movie details + credits             |
| `TvDetailsService`   | TV details + credits                |
| `TvPlayerService`    | All seasons with episodes           |
| `HistoryService`     | TV show metadata + episode metadata |

### Content API Client Retry

The content API client (`TmdbClient`) uses a built-in retry mechanism — **3 attempts** with a **150 ms backoff** — to gracefully handle transient API failures without propagating errors to the client. All content services route their external API calls through `TmdbClient.fetchWithRetry()` to benefit from this resilience.

---

## WebSocket & Watch Parties

Riyura supports real-time synchronized watch parties powered by **STOMP over WebSocket** with a SockJS fallback. The WebSocket endpoint is exposed at `/ws`; party state lives in Redis and messaging is handled via a simple in-memory broker broadcasting to `/topic` destinations. Transport limits are set to a 128 KB message size, 512 KB send buffer, and a 20 s send time limit.

### Authentication

`WebSocketAuthInterceptor` intercepts every `CONNECT` frame, extracts the JWT from the `Authorization: Bearer` header, and validates it against the Supabase secret. On success it writes `userId` and `userName` into the STOMP session attributes and assigns a unique principal (`userId-sessionId`) per connection — allowing a user to hold multiple simultaneous connections.

### Party Messaging

`PartyWebSocketController` handles all inbound STOMP messages under `/app/party/{partyId}/`. Participants can join a party, send chat messages, report buffering state, and send heartbeats. The host additionally controls playback sync and can toggle strict sync mode. All events are broadcast to `/topic/party/{partyId}` so every member receives them in real time. Per-user acknowledgments (e.g. heartbeat ACK) are sent to the user's private queue.

### Party Lifecycle & Features

- **Host migration**: When the host disconnects, `WebSocketEventListener` automatically promotes the next participant to host and broadcasts `NEW_HOST_ASSIGNED`
- **Participant cap**: Parties are limited to a maximum of **20 participants** — join attempts beyond this limit receive a 400 error
- **Party ID validation**: All party IDs are validated against a strict alphanumeric regex (`^[A-Za-z0-9]{1,20}$`) to prevent Redis key injection
- **Zombie eviction**: On each WebSocket heartbeat, participants inactive for more than **45 seconds** are removed from the party
- **Buffering sync**: When a participant reports buffering, `PartyService` tracks all buffering participants. In strict sync mode this triggers a `FORCE_PAUSE` to all members; once all are ready a `RESUME` is broadcast
- **Strict sync mode**: When enabled (host only), any participant buffering pauses playback for everyone
- **Latency compensation**: Sync commands carry a `clientTime` timestamp that is validated for plausibility (positive, not in the future, within 30 s of server time). The service applies a compensation offset based on round-trip time before broadcasting the target playback position
- **Chat history**: The last 50 chat messages are stored in the Redis party state and replayed on join
- **Party cleanup**: When the last participant leaves or the party TTL expires, the party is removed from Redis

---

## Security & Authentication

Riyura uses **Spring Security** configured as an **OAuth2 Resource Server** with Supabase as the identity provider.

- **Algorithm**: HS256 (HMAC-SHA256) with a custom `NimbusJwtDecoder`
- **Issuer validation**: Supabase project URL
- **JWT subject**: Used as `userId` throughout the system (UUID format)
- **CORS**: Configured globally via `SecurityConfig` using the `APP_FRONTEND_URL` environment variable — no per-controller `@CrossOrigin` annotations. Allowed headers are narrowed to `Authorization`, `Content-Type`, and `Accept`

Content discovery endpoints (`/api/movies/**`, `/api/tv/**`, `/api/anime/**`, `/api/search/**`, `/api/banner/**`, `/api/explore/**`) are fully public. User-specific endpoints (`/api/profile/**`, `/api/watchlist/**`, `/api/party/**`) require a valid Supabase-issued JWT in the `Authorization` header. The WebSocket endpoint (`/ws/**`) is publicly reachable at the HTTP level, but authentication is enforced at the STOMP CONNECT frame by `WebSocketAuthInterceptor`.

### Input Validation

All controllers are annotated with `@Validated` to enable Jakarta Bean Validation on both path variables and query parameters. Request bodies use `@Valid` to trigger DTO-level validation. Key validations include:

| Layer              | Validation                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------ |
| **Path variables** | TMDB IDs validated as `Long` (rejects non-numeric input), limits bounded with `@Min` / `@Max`                |
| **Query params**   | Page numbers bounded (`@Min(0)` or `@Min(1)`), limits bounded (`@Min(1) @Max(50)`)                           |
| **Request DTOs**   | `@Positive` on IDs, `@Size` on strings, `@Min(0)` on numeric fields, `@Pattern` on enum-like fields          |
| **Party IDs**      | Validated against `^[A-Za-z0-9]{1,20}$` regex to prevent Redis key injection                                 |
| **Search queries** | URL-encoded via `URLEncoder.encode()` before being passed to the content API, preventing URL/query injection |
| **Language codes** | 2-letter passthrough codes validated as strictly alphabetic                                                  |

### Global Exception Handling

A centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) intercepts all exceptions and returns a consistent JSON error response — preventing stack traces, internal class names, and other sensitive details from leaking to clients.

| Exception Type                        | HTTP Status | Behavior                                          |
| ------------------------------------- | ----------- | ------------------------------------------------- |
| `MethodArgumentNotValidException`     | 400         | Returns field-level validation error messages     |
| `ConstraintViolationException`        | 400         | Returns parameter-level constraint violations     |
| `MethodArgumentTypeMismatchException` | 400         | Returns type conversion error (e.g. "abc" → Long) |
| `ResponseStatusException`             | Varies      | Forwards the status and reason from the service   |
| All other `Exception`                 | 500         | Generic "An unexpected error occurred" message    |

### IP Sanitization

The `ClientIdentifierProvider` extracts client IP from `X-Forwarded-For` for rate-limiting keys. The extracted IP is validated against a strict regex (`[0-9a-fA-F.:]+`) and length check (`≤ 45` characters for IPv6) to prevent header injection attacks that could manipulate rate-limit bucket keys.

---

## Rate Limiting

Riyura uses **Bucket4J** backed by **Redis (Lettuce)** for distributed, highly available rate limiting across all HTTP traffic. The implementation is production-ready, fail-open when Redis is unavailable, and compatible with JDK 21 virtual threads.

### Architecture

| Component                  | Responsibility                                                                                                              |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `LettuceBasedProxyManager` | Distributed bucket store — fetches or creates per-key buckets in Redis via atomic CAS operations                            |
| `ClientIdentifierProvider` | Resolves caller identity: authenticated user ID (JWT `sub`) or client IP; handles `X-Forwarded-For` with regex sanitization |
| `RateLimitTierService`     | Maps request URIs to rate-limit tiers with distinct bucket configurations                                                   |
| `RateLimitFilter`          | `OncePerRequestFilter` at order 1 — runs after Security so JWT context is available for per-user scoping                    |

### Rate Limit Tiers

All limits use **greedy refill** (tokens replenish continuously over the window, not in a burst at reset), which smooths traffic and avoids thundering-herd spikes.

| Tier        | Endpoints                                   | Limit                 |
| ----------- | ------------------------------------------- | --------------------- |
| **DEFAULT** | All other `/api/**` routes                  | 100 requests / minute |
| **HEAVY**   | `/api/explore`, `/api/search`, `/api/anime` | 30 requests / minute  |
| **PARTY**   | `/api/party`, `/ws/**`                      | 10 requests / minute  |

Heavy endpoints proxy expensive external API calls (TMDB, etc.); party endpoints cover WebSocket handshakes and party creation — both are more resource-intensive and thus throttled more aggressively.

### Redis Key Design

Keys follow the format `rate_limit:{identity}:{tier}`:

- **Authenticated** → `rate_limit:user:{supabase-uuid}:default`
- **Anonymous** → `rate_limit:ip:{client-ip}:heavy`

This isolates limits per user (or per IP for anonymous traffic) and per tier, so one client exhausting explore does not affect their movie or party quota.

### Memory Leak Prevention

`LettuceBasedProxyManager` is configured with an `ExpirationAfterWriteStrategy` that assigns a Redis TTL to each bucket key based on the refill period plus a buffer. Inactive buckets expire and are evicted automatically — no unbounded key growth in Redis.

### Response Format

**Allowed request**: The filter attaches `X-RateLimit-Remaining` to the response and proceeds. No JSON body is modified.

**Blocked request (429 Too Many Requests)**:

- HTTP status: `429`
- `Retry-After` header: seconds until refill (derived from Bucket4J's `nanosToWaitForRefill`)
- JSON body (strict format):

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "You have been rate limited. Try again after 42 second(s)."
}
```

The filter short-circuits the request; the controller is never reached.

### Excluded Paths

The following prefixes are **not** rate-limited (to avoid Swagger and health checks consuming quota or failing under load):

- `/swagger-ui`
- `/v3/api-docs`
- `/actuator`
- `/favicon.ico`

### Fail-Open Behavior

The Redis evaluation is strictly isolated in its own `try/catch` block. The result (allowed, blocked, or fail-open) is stored in local variables, and the `try` block is closed **before** `chain.doFilter()` is called. This two-phase design is intentional:

- If Redis is unreachable or times out, the filter logs a `WARN`, sets a `failOpen` flag, and allows the request through without touching the response.
- Because `chain.doFilter()` is outside the `try` block, any exception thrown by a controller propagates normally up the filter chain. It is never misidentified as a Redis failure and can never trigger a double execution on an already-committed response (which would crash Tomcat with `IllegalStateException: response already committed`).

### Topology-Aware Redis Connection

`RateLimitConfig` inspects the Lettuce native client with `instanceof` before opening the dedicated Bucket4J connection, rather than blindly casting to `RedisClient`:

```
RedisClusterClient  → used in AWS ElastiCache Cluster Mode, Redis Enterprise
RedisClient         → used in standalone / Sentinel deployments
```

If neither matches (e.g. a future Lettuce client type), the application fails fast at startup with a clear `IllegalStateException` rather than throwing a cryptic `ClassCastException` at runtime under load. This makes the configuration safe to deploy to any Redis topology without code changes.

### Virtual Thread Safety

Bucket4J's Lettuce integration issues async Redis commands via Lettuce's non-blocking pipeline. The custom filter contains no `synchronized` blocks, so carrier threads are never pinned during Redis I/O — safe for high-concurrency virtual-thread workloads.

---

## Health Check

Riyura exposes a lightweight, **unauthenticated** liveness endpoint at `GET /api/health`. It is designed to be polled by the frontend immediately after a user lands on the login page — if the backend is unreachable or returns `503`, a downtime modal is shown before any auth flow is attempted.

### Probe Strategy

Each probe is executed synchronously and timed independently so that latency regressions are visible before they become hard outages.

| Component    | Probe                                                                                              | Failure Severity            |
| ------------ | -------------------------------------------------------------------------------------------------- | --------------------------- |
| **Database** | `SELECT 1` via `JdbcTemplate` — validates the HikariCP connection pool and PostgreSQL reachability | **Critical → DOWN**         |
| **Redis**    | `PING` command via `StringRedisTemplate` — validates the Lettuce connection                        | **Non-critical → DEGRADED** |

Redis is deliberately treated as non-critical: if it is unreachable, caching, rate limiting, and party state are degraded but core playback and profile features still function.

### Aggregate Status (Worst-Wins)

```
DB = DOWN              → aggregate = DOWN
DB = UP, Redis = DOWN  → aggregate = DEGRADED
DB = UP, Redis = UP    → aggregate = UP
```

### HTTP Status Contract

| Aggregate Status | HTTP Status               | Frontend action                       |
| ---------------- | ------------------------- | ------------------------------------- |
| `UP`             | `200 OK`                  | Proceed normally                      |
| `DEGRADED`       | `200 OK`                  | Proceed (degraded UX banner optional) |
| `DOWN`           | `503 Service Unavailable` | Show downtime modal                   |

### Response Envelope

```json
{
  "status": "UP",
  "components": {
    "database": { "status": "UP", "latencyMs": 4 },
    "redis": { "status": "UP", "latencyMs": 1 }
  },
  "checkedAt": "2026-03-04T01:14:07Z"
}
```

When a component is unhealthy the `errorMessage` field is included in that component's object:

```json
{
  "status": "DOWN",
  "components": {
    "database": {
      "status": "DOWN",
      "latencyMs": 5002,
      "errorMessage": "Database is unreachable: Connection refused"
    },
    "redis": { "status": "UP", "latencyMs": 1 }
  },
  "checkedAt": "2026-03-04T01:14:07Z"
}
```

The `errorMessage` field is omitted from JSON when `null` (healthy components) so that clean responses stay noise-free.

### Security

`/api/health` is listed in the `SecurityConfig` permit-all block — no `Authorization` header is required. This endpoint should never return sensitive internal state; error messages are intentionally limited to connection-level failures.

### Rate Limiting

The health endpoint falls under the **DEFAULT** rate-limit tier (100 req/min per client). It is exempt from the Redis fail-open concern because the health check itself reports Redis availability — a design that avoids a circular dependency where the rate limiter silently passes through while health falsely reports `UP`.

### Frontend Integration (Conceptual)

```ts
// Run before auth flow on the login page
async function checkHealth(): Promise<boolean> {
  try {
    const res = await fetch("/api/health", {
      signal: AbortSignal.timeout(5000),
    });
    return res.ok; // false for 503
  } catch {
    return false; // network unreachable
  }
}
```

---

## Performance

The backend includes several production-oriented performance optimizations that improve throughput, latency, and resilience under load.

### HTTP Connection Pooling & Timeouts

The `RestTemplate` bean is backed by JDK 21's built-in `java.net.http.HttpClient` via `JdkClientHttpRequestFactory`. This provides automatic HTTP connection pooling (connection reuse across requests) with explicit timeouts:

| Setting         | Value |
| --------------- | ----- |
| Connect timeout | 5 s   |
| Read timeout    | 10 s  |

Without these, a single unresponsive upstream API could stall a request thread indefinitely and cascade into thread pool exhaustion.

### CompletableFuture Timeouts

Every `CompletableFuture` used for parallel upstream API calls carries an **8-second timeout** via `.orTimeout(8, TimeUnit.SECONDS)`. This acts as a circuit breaker — if any individual API call hangs, the future completes exceptionally rather than blocking forever. This applies to all parallel fetches in BannerService, ExploreService, AnimeService, SearchService, MovieDetailService, TvDetailsService, TvPlayerService, and HistoryService.

### Response Compression

Gzip compression is enabled at the server level for JSON, XML, HTML, and plain text responses above 1 KB:

```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/plain
    min-response-size: 1024
```

This reduces payload sizes significantly for API responses, especially list-heavy endpoints like explore and search.

### SQL Logging Disabled

Hibernate's `show-sql` and `format_sql` are disabled in production configuration. These options log every SQL statement to stdout, which causes measurable overhead from synchronized I/O under concurrent load.

### Structured Logging

All services use SLF4J (`@Slf4j`) instead of `System.out.println` / `System.err.println`. This avoids the performance penalty of synchronized stdout writes and enables proper log-level filtering, structured output, and integration with log aggregation tools.

### CORS Configuration

CORS is handled exclusively by the global `SecurityConfig` bean with a centralized `CorsConfigurationSource`. The allowed origin is read from the `APP_FRONTEND_URL` environment variable rather than being hardcoded. Allowed headers are explicitly narrowed to `Authorization`, `Content-Type`, and `Accept` instead of using a wildcard. Per-controller `@CrossOrigin` annotations are not used, avoiding redundant CORS header processing and ensuring consistent origin policy from a single configuration point.

---
