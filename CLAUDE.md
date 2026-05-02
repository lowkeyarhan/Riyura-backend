# Riyura Backend - Context & Knowledge Base

This document serves as the persistent memory and context for AI agents working on the Riyura Backend project. It encapsulates architectural decisions, security standards, and conventions established during the SDE-4 backend audit.

## Architecture & Technology Stack

- **Framework:** Spring Boot 3 with Java 21.
- **Concurrency:** Fully embraces **Virtual Threads** (JEP 444). `spring.threads.virtual.enabled` is `true`. All `@Async` tasks, WebSocket channels, and Cache Executors must use `Thread.ofVirtual()`.
- **Database:** PostgreSQL with Spring Data JPA. HikariCP is optimized for low-spec VPS environments (`maximum-pool-size: 10`).
- **Caching:** Redis using Spring Data Redis. Caching uses `BasicPolymorphicTypeValidator` for safe JSON deserialization.
- **Identity & Security:** Supabase for JWT Auth. All API endpoints require authentication except `/api/test/**` which is strictly restricted to `ROLE_ADMIN` for observability (with the exception of `/api/test/health` which is public for frontend banner checks).

## Coding Standards & Anti-Patterns to Avoid

1. **No `RestTemplate` leaks:** Always use the centralized `TmdbClient` with `@Retryable` and Circuit Breakers (Resilience4j). Do NOT construct raw `RestTemplate` beans inside loops.
2. **Resource Scoping & Caching:**
   - Instantiate expensive objects (like Gemini `Client`) only once, and cache them globally (e.g. `ConcurrentHashMap<String, Client>`).
   - Evict caches selectively using keys (`#userId + ':0'`) instead of `allEntries = true` which wipes the entire cache.
   - Close Redis connections immediately using `try-with-resources`.
3. **Database Efficiency (N+1 queries):**
   - Ensure entities like `Watchlist` and `WatchHistory` are indexed properly by `user_id`.
   - Avoid `@Data` on JPA Entities. Always use explicit `@Getter`, `@Setter` and provide custom `equals()`/`hashCode()` based on `@Id`.
4. **Code Quality:**
   - Use top-of-file imports. Do NOT use Fully Qualified Class Names (FQCN) in `implements` statements.
   - Always validate external APIs and sanitize incoming inputs (`@Validated`, `@NotBlank`, etc.).
   - Use utility classes like `TmdbUtils` for duplicate logic (e.g. `parseDate`).

## Observability Stack

The observability stack (Grafana, Prometheus, Loki) is **optional**. The backend must boot and run correctly even if these services are absent.

## Environment configuration

- Never commit production secrets in `.env`.
- Do not modify existing `.env` credentials through automated tools.
- `SUPABASE_JWT_SECRET` and API keys are managed externally by the admin in production.
