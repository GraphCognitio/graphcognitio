# GraphCognitio Backend

Backend API for GraphCognitio, a graph-oriented social platform where posts are nodes and replies are directed `REPLY` edges (`reply -> parent`).

## Tech Stack

- Java 17
- Spring Boot 3.5.11
- Maven
- PostgreSQL 18
- Spring Data JPA
- Flyway
- Spring Security + JWT (JJWT 0.13.0)
- MapStruct 1.6.3
- Lombok
- springdoc-openapi 2.8.15
- Testcontainers
- Docker Compose

## Architecture

The code follows a layered structure:

- `controller`: HTTP contracts and validation boundaries
- `service`: business rules and transactional orchestration
- `repository`: data access via JPA
- `entity`: relational model mapping
- `dto`/`mapper`: API payloads isolated from persistence model
- `security`: JWT parsing, auth filter, and security configuration
- `exception`: centralized RFC 7807 `ProblemDetail` responses

## Domain Model

- `users` (`role`: `USER`, `MOD`, `ADMIN`)
- `posts` (`parent_id`, `root_id`, `reply_count`, `like_count`)
- `edges` (`source_post_id`, `target_post_id`, `type=REPLY`)
- `post_likes` (unique `post_id + user_id`)
- `audit_log` (`actor_user_id`, `action`, `entity_type`, `entity_id`, `metadata`, `created_at`)

Core rules:

- Root post: `parent_id = null`, `root_id = id`
- Reply: `parent_id = target post`, `root_id` inherited from parent
- Reply flow increments parent `reply_count`
- Reply flow persists `REPLY` edge with `source=reply`, `target=parent`

## Environment Configuration

The app is environment-driven. No runtime secret should be committed.

Create a local `.env` from `.env.example`:

```bash
cp .env.example .env
```

`application.yml` expects:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET` (required)
- `APP_JWT_EXP_MINUTES` (optional, default `60`)
- `APP_BOOTSTRAP_ADMIN_EMAIL` (optional; promotes matching user to `ADMIN` during Flyway migration)
- `APP_RATE_LIMIT_ENABLED` (optional, default `true`)
- `APP_RATE_LIMIT_*` tuning variables for each protected route group (see `.env.example`)
- `APP_IDEMPOTENCY_ENABLED` (optional, default `true`)
- `APP_IDEMPOTENCY_TTL_MINUTES` (optional, default `120`)
- `APP_CACHE_MAX_SIZE` (optional, default `1000`)
- `APP_CACHE_TTL_SECONDS` (optional, default `30`)
- `ENABLE_SEARCH` (optional, default `true`)
- `ENABLE_GRAPH_EXPAND` (optional, default `true`)

## Running Locally

1. Start PostgreSQL:

```bash
docker compose up -d postgres
```

2. Export environment variables from `.env` in your shell (or use your preferred loader).

3. Start the API:

```bash
./mvnw spring-boot:run
```

4. Open API docs:

- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:8080/v3/api-docs`

Profiles:

- default profile: `dev`
- `application-dev.yml`: local-friendly docs enabled
- `application-test.yml`: docs disabled for test runs
- `application-prod.yml`: docs disabled by default unless explicitly enabled

## API Surface

### Auth

- `POST /auth/register`
- `POST /auth/login`

### Posts

- `POST /posts` (create root)
- `POST /posts/{id}/reply`
- `GET /posts/{id}`
- `PATCH /posts/{id}` (author-only edit)
- `DELETE /posts/{id}` (author delete, or `MOD`/`ADMIN` moderation delete)
- `POST /posts/{id}/likes`
- `DELETE /posts/{id}/likes`

Idempotency support (`Idempotency-Key` header, optional) is available for:
- `POST /posts`
- `POST /posts/{id}/reply`
- `POST /posts/{id}/likes`
- `DELETE /posts/{id}/likes`

Delete constraint:
- `DELETE /posts/{id}` returns `409 Conflict` when the target post has replies.

### Admin

- `PATCH /admin/users/{id}/role` (admin-only role management)

### Feed

- `GET /feed?cursor=&limit=`
- root posts only (`parent_id IS NULL`)
- stable ordering: `created_at DESC, id DESC`
- cursor format: URL-safe Base64 of `createdAt|id`

### Search

- `GET /search?q=&cursor=&limit=`
- response shape matches feed: `{ items, nextCursor }`
- PostgreSQL full-text search over post content
- ranking order: `searchScore DESC, created_at DESC, id DESC`
- search cursor format: URL-safe Base64 of `searchScore|createdAt|id`

### Graph

- `GET /graphs/conversation/{rootId}?depth=&limit=`
- BFS by layers with batched child queries (`WHERE parent_id IN (...)`)
- no N+1 traversal pattern

### Observability

- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`

## Security

- JWT required on all routes except:
  - `POST /auth/register`
  - `POST /auth/login`
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
- `Authorization` header format: `Bearer <accessToken>`
- JWT payload includes role claim used by the API authorization context
- Validation and domain errors are returned as RFC 7807 `application/problem+json`

## RBAC and Ownership

- `USER`: can edit/delete own posts, can create/like/reply
- `MOD`: can remove posts from other users (still blocked by `409` when replies exist)
- `ADMIN`: can manage user roles through `/admin/users/{id}/role`

All role updates and write operations are auditable via `audit_log`.

## Rate Limiting

In-memory fixed-window rate limiting is applied by endpoint category:

- `POST /auth/register` (anti-abuse)
- `POST /auth/login` (anti brute-force)
- `POST /posts`
- `POST /posts/{id}/reply`
- `POST|DELETE /posts/{id}/likes`

When a limit is exceeded, API returns:

- status `429 Too Many Requests`
- RFC 7807 body (`application/problem+json`)
- headers: `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

## Observability

- Actuator endpoints enabled: `health`, `metrics`, `prometheus`
- Structured JSON logging enabled for console output
- Correlation header: `X-Request-Id` (accepted from client or generated)
- MDC fields included in logs:
  - `requestId`
  - `userId` (when request is authenticated)
- Custom HTTP metrics:
  - `graphcognitio.http.server.latency` (timer by method/uri/status)
  - `graphcognitio.http.server.errors` (counter by method/uri/statusClass)

## Idempotency

- Header: `Idempotency-Key`
- Scope: per user + endpoint + payload hash
- Storage: `idempotency_keys` table with TTL (`expires_at`)
- Behavior:
  - repeated request with same key and same payload returns stored response
  - same key with different payload returns `409 Conflict`
  - expired key is discarded and request executes again

## Cache

- Caffeine in-memory cache with short TTL for hot reads:
  - `feedCache` key: `viewerId|limit|cursor`
  - `graphCache` key: `rootId|depth|limit`
- Write operations invalidate cache entries to avoid stale reads:
  - root/reply creation
  - likes/unlikes
  - post updates/deletes
- Metrics available via Micrometer:
  - `cache.gets` with `cache=feedCache|graphCache` and `result=hit|miss`
  - `cache.puts` and `cache.evictions` for cache behavior tracking

## Search Ranking

- Full-text index: generated `tsvector` column (`search_vector`) with GIN index
- Query parser: `plainto_tsquery('simple', q)`
- Score formula:
  - `ts_rank_cd(search_vector, tsquery)`
  - `+ like_count` bonus (capped)
  - `+ reply_count` bonus (capped)
- Recency is applied as secondary ordering with `created_at DESC`
- Search cursor uses `(searchScore, createdAt, id)` to keep pagination stable

## Feature Flags

- `ENABLE_SEARCH=false`
  - `/search` is not registered and returns `404 Not Found`
- `ENABLE_GRAPH_EXPAND=false`
  - `/graphs/conversation/{rootId}` still works with the default depth
  - requests with `depth > 2` return `400 Bad Request`

## Testing

Test suite includes:

- Unit tests (services)
- WebMvc tests (controllers)
- Integration test with Testcontainers (`postgres:18`)

Run all tests:

```bash
./mvnw test
```

## Engineering Decisions (Short)

1. Cursor pagination uses `(created_at, id)` for deterministic ordering and stable pagination windows.
2. Graph retrieval uses BFS with batched DB reads to avoid N+1 and keep predictable load.
3. DTO/entity separation protects API contracts from persistence refactors.
4. JWT is stateless to keep auth simple for MVP and horizontal scaling.
5. Flyway is mandatory to keep schema evolution reproducible.

## Threat Model (High-Level)

Primary risks and current controls:

1. Unauthorized access to private operations
- Control: JWT authentication + route-level protection in Spring Security.

2. Input abuse and invalid payloads
- Control: Bean Validation + global `ProblemDetail` handler.

3. Credential leakage via repository
- Control: `.env` ignored by git, required secrets provided via environment variables.

4. Graph/feed query amplification
- Control: bounded `limit`, depth constraints, and batched traversal.

Residual risks for next hardening phase:

- Missing security automation in CI (SCA/CodeQL)
