# Spring Boot

Framework layer for api-coding-challenge, read with one language layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` in the language file, then `### X` here. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and this file's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | spring-boot |
| aliases | spring, springboot |
| display | Spring Boot |
| languages | java |
| part2_noun | Spring Boot REST API |
| in_process_test | @WebMvcTest + MockMvc |
| part2_min | 20–25 |

## Not needed

- `application.yml`
- full Spring Security configuration

## Request dispatch

- Tomcat runs thread-per-request, with 200 threads by default (`server.tomcat.threads.max`).
- Controllers and services are singleton beans, so a mutable field is shared by every request thread.
- `spring.threads.virtual.enabled=true` switches request handling to virtual threads, with pinning caveats. WebFlux is an event loop instead, so a candidate who picks it must keep blocking calls off the loop.

## Mechanisms

| Mechanism | Idiom | Note |
|---|---|---|
| error mapping | `@RestControllerAdvice` returning `ProblemDetail` | extending `ResponseEntityExceptionHandler` gives Spring's own exceptions the same shape; `spring.mvc.problemdetails.enabled` covers only Spring's exceptions |
| middleware | `HandlerInterceptor` or a servlet `Filter` | an interceptor runs after routing and knows the handler; a filter wraps everything, before Spring MVC |
| validation | Bean Validation: `@Valid` plus constraint annotations | without `@Valid` on the parameter the constraints never run; failures raise `MethodArgumentNotValidException` |
| config binding | `@ConfigurationProperties` | binds at startup, so changing a value at runtime needs a restart, a Spring Cloud refresh, or a value read from a store per request |
| health | Actuator's `/actuator/health/liveness` and `/readiness` groups | the probe groups switch on automatically on Kubernetes (`management.endpoint.health.probes.enabled` elsewhere) |
| shutdown | `server.shutdown=graceful` | the default since Boot 3.4; `spring.lifecycle.timeout-per-shutdown-phase` caps the drain |

## Expected Part 1 adaptation

- The Part 1 class becomes, or is wrapped by, a `@Service` with constructor injection.
- The `Clock` is a `@Bean`, and the in-memory store sits behind a repository interface.
- Part 1 state that was single-threaded must become thread-safe, because the bean is a singleton.

## Built-ins to forbid

- **C1** (event sessionizer): none
- **C2** (two-feed reconciliation): none
- **C3** (top-N per category): none
- **C4** (duplicate-contact grouping): none
- **C5** (leaderboard): none
- **S1** (per-client rate limiter): Spring Cloud Gateway's `RequestRateLimiter` filter (moves the enforcement Part 2 asks for out of the service)
- **S2** (inventory holds): none
- **S3** (meeting-room booking): none
- **S4** (versioned key-value store): none
- **S5** (job queue with leases): none
- **M1** (promotion engine): none
- **M2** (approval workflow): none
- **M3** (parking garage): none
- **M4** (notification routing): none
- **M5** (access-policy evaluator): none

## Seed lines

- **C4** Hook: per-row error shape in ProblemDetail
- **S1** Part 2 sketch: enforce the limiter on an existing `GET /reports/{id}` through a `HandlerInterceptor` or filter. Return 429 with `Retry-After` and `RateLimit-*` headers. Add `GET /admin/rate-limits/{clientId}` for inspection. The client is identified by an API-key header.
- **S1** Twist: per-tier limits come from `@ConfigurationProperties` and must be changeable without a code change.
- **M2** Twist: an illegal transition returns 409 with a ProblemDetail listing the allowed transitions, and concurrent approvals must not lose a vote.
- **M2** Hook: optimistic locking with `@Version`

## Twist guards

- **conditional-requests**: `ShallowEtagHeaderFilter` derives ETags from the response body, not from the version → forbid.

## Trivia example

"what's the annotation for request-body validation?"

## Dim-3 anchor

Clean controller → service → domain layering. DTOs plus Bean Validation. `@RestControllerAdvice` producing a consistent `ProblemDetail`. Constructor injection. Thread-safe singleton state.

## Code hooks

- a non-thread-safe field in a singleton → every request thread shares it, so updates are lost (P2)
- a domain entity returned from a controller → the domain or persistence model becomes the public contract (A1)
- a missing `@Valid` on a request body → validation silently skipped (S2)
- field injection with `@Autowired` → hidden dependencies, harder tests (framework_impl)
- an `@ExceptionHandler(Exception.class)` that maps everything to 500 → client errors look like outages (A1)

## Signals by thread ID

### P2: Concurrency inside a singleton bean

Opener: "`<Service>` is a Spring singleton holding `<field>`. What happens with 200 concurrent requests?"
- Tomcat's 200 request threads all run through the same singleton bean, so `<field>` has up to 200 concurrent callers
- changing the bean's scope (request or prototype) isn't the fix, because the state is meant to be shared
- `spring.threads.virtual.enabled=true` changes the threads, not the sharing

### P4

- avoiding N+1 (fetch joins, batching)
- Hikari pool size relative to request threads, and the DB's connection limit
- `spring.jpa.open-in-view=false`, so lazy loading can't run queries while the response is being serialized

### P5

- Spring Cache (`@Cacheable` over a Caffeine or Redis `CacheManager`), with the key and TTL chosen deliberately
- `@Cacheable` works through a proxy, so a call from inside the same bean skips the cache
- `ShallowEtagHeaderFilter` saves bandwidth but still renders the body; a version-based `ETag` set with `ResponseEntity.eTag(...)` or checked with `WebRequest.checkNotModified` saves the work

### P6

- saturation metrics: Tomcat threads, Hikari pending, executor queues
- Java 21 virtual threads (`spring.threads.virtual.enabled=true`) for blocking IO, with pinning caveats

### A1

- one error contract for everything: RFC 9457 `ProblemDetail` from a `@RestControllerAdvice` (or `spring.mvc.problemdetails.enabled`), with stable machine-readable `type`/codes
- the advice extends `ResponseEntityExceptionHandler`, so Spring's own errors (malformed JSON, a missing parameter, an unsupported media type) get the same `ProblemDetail` shape
- `ResponseEntity.created(location)` for 201 + `Location`

### A5

- codegen (openapi-generator) vs springdoc code-first, with the trade-offs
- springdoc builds the spec from the controllers and Bean Validation annotations, so the spec drifts with the code unless CI diffs it
- openapi-generator's `spring` generator with `interfaceOnly`, so the controllers implement generated interfaces and the spec stays the source of truth

### T1

- plain JUnit 5 with no Spring context

### T2

- `@WebMvcTest` + MockMvc, mocking the service with `@MockitoBean` (`@MockBean` before Boot 3.4)
- asserting status, headers and the ProblemDetail body
- `@DataJpaTest`, or `@SpringBootTest` + Testcontainers for a real DB or Redis
- context caching and avoiding a full context for everything

### T3

Drill-down: "How is this different from your `@SpringBootTest` tests and from contract tests? Why have all three?"
- **Complements:**
  - consumer-driven contract tests (Pact / Spring Cloud Contract) catch breaking changes *before* deploy

### S1

- a Spring Security OAuth2 resource server validating the JWT (signature via JWKS, issuer, audience, expiry)
- coarse authz with scopes and roles (`requestMatchers`, `@PreAuthorize`)

### S2

- Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Positive`, …)
- safe deserialization (no default polymorphic typing)
- errors without stack traces or internals (`server.error.include-stacktrace=never`)
- Spring Boot's Jackson ignores unknown fields by default; `spring.jackson.deserialization.fail-on-unknown-properties=true` rejects them

### O1

- structured JSON logs (Boot 3.4+ `logging.structured.format.console`)
- trace and correlation IDs on every line through MDC, which Micrometer Tracing populates

### O2

- RED metrics per endpoint from `http.server.requests` (Actuator + Micrometer)
- business metrics via `Counter`/`Timer`/`Gauge`, such as holds expired, rejections and queue age

### O3

- Micrometer Tracing with the OpenTelemetry bridge (Sleuth is gone in Boot 3)
- propagating context across `@Async`, executors and reactive code (the context-propagation library)

### O4

- Actuator `/actuator/health/liveness` and `/readiness` probe groups
- `server.shutdown=graceful` with a preStop hook

### R1

- invariants enforced by DB constraints, conditional writes (DynamoDB `ConditionExpression`), optimistic locking (`@Version`) or atomic Redis ops (Lua, `INCR`)
- a `@Version` conflict surfaces as `ObjectOptimisticLockingFailureException`, which the advice maps to 409 or retries, never a 500

## Reference solution shape

- the domain logic, DTOs, and a controller and service
- `@RestControllerAdvice` returning `ProblemDetail`
- the twist
- one `@WebMvcTest`
