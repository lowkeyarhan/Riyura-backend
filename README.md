# Riyura Backend

## Overview & About

This is a sophisticated backend service for Riyura that acts as the core orchestration layer for the modern media streaming platform. Built on **Spring Boot 4.x** with **Java 21**, it seamlessly serves rich media content, dynamic discovery features, personalized user experiences, and real-time collaborative watch parties. The system integrates with an external content API for media metadata, **Supabase** for identity/auth, **PostgreSQL** for persistent storage, and **Redis** for high-performance caching and ephemeral party state.

From managing trending banners and multi-provider stream URL resolution to synchronized watch parties with real-time chat, Riyura powers every essential feature of a full-stack entertainment hub — all while leveraging JDK 21 virtual threads for high concurrency without the complexity of traditional thread pools.

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
| Language      | Java 21 (virtual threads enabled)                              |
| Database      | PostgreSQL with HikariCP connection pooling                    |
| ORM           | Spring Data JPA / Hibernate                                    |
| Caching       | Redis (Spring Cache abstraction + custom RedisTemplate)        |
| Real-time     | STOMP over WebSocket (SockJS fallback)                         |
| Security      | Spring Security + OAuth2 Resource Server (Supabase JWT, HS256) |
| Async         | JDK 21 virtual threads + CompletableFuture                     |
| Content API   | External REST API (with retry logic)                           |
| Documentation | SpringDoc OpenAPI (Swagger UI)                                 |
| Build         | Maven (Maven Wrapper included)                                 |
| Utilities     | Lombok                                                         |

---

## Database & Persistence

Riyura uses PostgreSQL as its primary relational store, managed through Spring Data JPA with Hibernate. The schema covers three core entities: **stream providers** (configures available streaming providers with per-media-type URL templates, quality, priority, and an active toggle), **watchlist** (persists user-saved content with a metadata snapshot including title, poster, release date, and vote), and **watch history** (records full playback events with streaming context — provider used, stream ID, episode info, watch duration, and an anime flag for UI hints).

### Connection Pooling (HikariCP)

Database connections are managed by HikariCP with a maximum pool size of **30**, minimum idle of **5**, a connection timeout of **5 s**, and a max lifetime of **30 min**. The pool is intentionally sized to complement virtual thread concurrency — virtual threads park during I/O rather than blocking OS threads, so the pool doesn't need to match thread counts, but is still tuned to prevent connection exhaustion under high parallelism.

---

## Caching

Riyura uses **Redis** as its distributed cache via Spring's `@Cacheable` / `@CacheEvict` abstraction, with a custom `RedisCacheManager` configured in `CacheConfig`.

### Cache Strategy

- **Serialization**: String keys with JSON values (`GenericJackson2JsonRedisSerializer`)
- **Base TTL**: 1 day per cache entry
- **TTL Jitter**: Each key receives a random additional TTL of **1–6 hours** computed at write time to prevent **cache stampede** — a scenario where mass simultaneous expiry of related keys would flood the origin API
- **Null caching**: Disabled — absent values always fall through to the source
- **Synchronized access**: All `@Cacheable` calls use `sync = true`, ensuring that under concurrent load only one thread fetches from the source while others wait on the cached result

All content caches (`movies`, `tv`, `anime`, `banners`, `explore`, `search`, `streamUrls`) are keyed by their natural discriminator (e.g. `limit`, `contentId`, `query`) and expire by TTL. User-specific caches (`watchlist`, `history`) are keyed by `userId` and are explicitly evicted via `@CacheEvict` on any write operation, ensuring user data is never stale.

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

### Parallel Content Fetching (CompletableFuture)

Several services fire multiple content API calls in parallel using `CompletableFuture`, combining results after all futures complete. Because the underlying threads are virtual, parallel calls are cheap even at high fan-out:

| Service              | Parallel Operations           |
| -------------------- | ----------------------------- |
| `BannerService`      | Trending movies + trending TV |
| `ExploreService`     | Movies + TV                   |
| `AnimeService`       | Anime movies + anime TV       |
| `SearchService`      | Multi-search + company search |
| `MovieDetailService` | Movie details + credits       |
| `TvDetailsService`   | TV details + credits          |
| `TvPlayerService`    | All seasons with episodes     |

### Content API Client Retry

The content API client uses a built-in retry mechanism — **3 attempts** with a **150 ms backoff** — to gracefully handle transient API failures without propagating errors to the client.

---

## WebSocket & Watch Parties

Riyura supports real-time synchronized watch parties powered by **STOMP over WebSocket** with a SockJS fallback. The WebSocket endpoint is exposed at `/ws`; party state lives in Redis and messaging is handled via a simple in-memory broker broadcasting to `/topic` destinations. Transport limits are set to a 128 KB message size, 512 KB send buffer, and a 20 s send time limit.

### Authentication

`WebSocketAuthInterceptor` intercepts every `CONNECT` frame, extracts the JWT from the `Authorization: Bearer` header, and validates it against the Supabase secret. On success it writes `userId` and `userName` into the STOMP session attributes and assigns a unique principal (`userId-sessionId`) per connection — allowing a user to hold multiple simultaneous connections.

### Party Messaging

`PartyWebSocketController` handles all inbound STOMP messages under `/app/party/{partyId}/`. Participants can join a party, send chat messages, report buffering state, and send heartbeats. The host additionally controls playback sync and can toggle strict sync mode. All events are broadcast to `/topic/party/{partyId}` so every member receives them in real time. Per-user acknowledgments (e.g. heartbeat ACK) are sent to the user's private queue.

### Party Lifecycle & Features

- **Host migration**: When the host disconnects, `WebSocketEventListener` automatically promotes the next participant to host and broadcasts `NEW_HOST_ASSIGNED`
- **Zombie eviction**: On each WebSocket heartbeat, participants inactive for more than **45 seconds** are removed from the party
- **Buffering sync**: When a participant reports buffering, `PartyService` tracks all buffering participants. In strict sync mode this triggers a `FORCE_PAUSE` to all members; once all are ready a `RESUME` is broadcast
- **Strict sync mode**: When enabled (host only), any participant buffering pauses playback for everyone
- **Latency compensation**: Sync commands carry a timestamp; the service applies a compensation offset based on round-trip time before broadcasting the target playback position
- **Chat history**: The last 50 chat messages are stored in the Redis party state and replayed on join
- **Party cleanup**: When the last participant leaves or the party TTL expires, the party is removed from Redis

---

## Security & Authentication

Riyura uses **Spring Security** configured as an **OAuth2 Resource Server** with Supabase as the identity provider.

- **Algorithm**: HS256 (HMAC-SHA256) with a custom `NimbusJwtDecoder`
- **Issuer validation**: Supabase project URL
- **JWT subject**: Used as `userId` throughout the system (UUID format)
- **CORS**: Configured for frontend origin (`FRONTEND_URL`, defaults to `http://localhost:3000`)

Content discovery endpoints (`/api/movies/**`, `/api/tv/**`, `/api/anime/**`, `/api/search/**`, `/api/banner/**`, `/api/explore/**`) are fully public. User-specific endpoints (`/api/profile/**`, `/api/watchlist/**`, `/api/party/**`) require a valid Supabase-issued JWT in the `Authorization` header. The WebSocket endpoint (`/ws/**`) is publicly reachable at the HTTP level, but authentication is enforced at the STOMP CONNECT frame by `WebSocketAuthInterceptor`.

---

## Key Workflows

### Dynamic Content Discovery

Clients request categorized media using limit-controlled endpoints. The `Content` module fetches from the content API, applies genre/language mapping, assembles standardized DTOs, and returns paginated results — all served from Redis cache on subsequent calls.

### Anime Detection

Anime is identified from the standard movie/TV dataset by inspecting `original_language` (`ja`) combined with presence of genre ID `16` (Animation). Genre utilities and mappers handle this logic centrally, ensuring consistent anime classification across content services, stream URL resolution, and watch history recording.

### Streaming Orchestration

`StreamUrlService` queries the `stream_providers` table for active providers ordered by priority. For each provider it selects the appropriate URL template (movie, TV, or anime) and performs parameter substitution (content ID, season, episode, etc.). The resolved URLs are returned to the client, which handles actual media fetching directly — keeping stream bandwidth off the backend.

### Search & Discovery

`SearchService` fires multi-search and company search calls in parallel. Results are scored and sorted before being returned in a unified `SearchResponse`, providing a seamless cross-entity search experience.

### Watch History

Every playback event (movie start, episode watched) is recorded via the profile history endpoint. The history entry stores full streaming context (provider, stream ID, episode info, duration, anime flag) enabling the frontend to resume content or display "Continue Watching" rows.

---

## API Documentation

Once the application is running, interactive OpenAPI documentation is available at:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec:

```
http://localhost:8080/v3/api-docs
```

---

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL instance
- Redis instance
- Content provider API key
- Supabase project (for JWT secret)

