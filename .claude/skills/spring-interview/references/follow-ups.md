# Follow-up question bank

## How to use this

- **Questions are templates.** Rewrite every opener so it points at the candidate's actual code: their class names, fields, endpoints and status codes. "Your `BookingService` keeps a `HashMap<String, List<Booking>>`…" beats "How would you handle concurrency?"
- **Thread shape:** an opener, then 1–2 drill-downs chosen by the answer.
  - On vague or buzzword answers ("add a cache", "use Kafka"), go **narrower**: "Concretely, how? What's the key? What invalidates it?"
  - On strong answers, go **wider**: more replicas, more consumers, more teams, failure in the middle.
- **Order:** Performance → API design & versioning → Testing → Security → Observability → Resilience & scale-out. That runs from the code, to the contract, to verifying it, protecting it, operating it, and finally the whole system.
- **Budget:** about 2 threads per category. Testing gets 3, and **T3 (live functional tests) is mandatory**. If a seed's follow-up hooks fit a category, use them.
- **Scoring only:** the "Strong Principal answer includes" bullets are for scoring and for the debrief annotations. Never read them out or hint at them during the round.

---

## Performance

### P1: Complexity and data structures
- **Opener:** "What's the time and space complexity of `<core method>`? Where's the hot path?"
- **Drill-downs:**
  - "N goes from thousands to 10 million. What breaks first?"
  - "Could `<op>` be O(log n) or O(1)? What would that cost you?"
- **Strong Principal answer includes:**
  - precise complexity per operation, and the dominant term
  - the right structure for the access pattern: `TreeMap` floor/ceiling for intervals, a heap for top-K, hashing for grouping
  - memory as well as time
  - asks about real volumes and SLOs before optimizing, and doesn't over-engineer for scale nobody needs

### P2: Concurrency inside a singleton bean
- **Opener:** "`<Service>` is a Spring singleton holding `<field>`. What happens with 200 concurrent requests?"
- **Drill-downs:**
  - "Is `ConcurrentHashMap` enough? Walk me through `<check-then-act op>`."
  - "Where would the locking go, and what does it cost in throughput?"
- **Strong Principal answer includes:**
  - spots the check-then-act race
  - per-key atomicity via `compute`/`merge`, or per-key or striped locks, *not* `synchronized` on the whole service, which serializes everything
  - immutable snapshots for read-heavy data
  - knows that virtual threads don't fix races
  - notes that at real scale the fix is moving state out of the JVM (a bridge to Resilience)

### P3: Unbounded growth
- **Opener:** "What stops `<map/list>` from growing forever?"
- **Strong Principal answer includes:**
  - eviction or TTL (Caffeine), bounded structures
  - lazy vs scheduled cleanup and the trade-off between them
  - container heap sizing (`-XX:MaxRAMPercentage`)
  - GC pressure from large maps
  - monitoring the size as a gauge

### P4: Data access at scale
- **Opener:** "This now sits on Postgres, or DynamoDB if you prefer. What does the access pattern look like, and how do you keep `<endpoint>` fast?"
- **Strong Principal answer includes:**
  - indexes that match the queries
  - avoiding N+1 (fetch joins, batching)
  - keyset pagination, projections and DTO queries
  - for DynamoDB: key design from the access patterns, hot partitions, GSIs
  - Hikari pool size relative to request threads, and the DB's connection limit

### P5: Caching
- **Opener:** "Where would you cache in this design, and how does the cache get invalidated?"
- **Strong Principal answer includes:**
  - cache-aside vs read-through
  - TTL vs event-driven invalidation, with the staleness explicitly accepted with the product owner
  - local (Caffeine) vs distributed (Redis)
  - stampede protection
  - HTTP caching (`ETag`/`Cache-Control`/304)
  - what must never be cached (authz-dependent responses keyed without the user)

### P6: Finding a bottleneck
- **Opener:** "Under load, p99 on `<endpoint>` is 2 seconds while the average is fine. How do you find out why?"
- **Strong Principal answer includes:**
  - reproduce with a load test (Gatling, k6)
  - JFR or async-profiler, thread dumps, GC logs
  - saturation metrics: Tomcat threads, Hikari pending, executor queues
  - tail-latency causes: GC pauses, lock contention, a slow dependency, pool starvation
  - Java 21 virtual threads (`spring.threads.virtual.enabled=true`) for blocking IO, with pinning caveats

---

## API design & versioning

### A1: Defend the API they built
- **Opener:** "Walk me through why you chose these paths, verbs and status codes."
- **Drill-down:** "What exactly does a client receive when `<validation fails / resource missing / conflict>`?"
- **Strong Principal answer includes:**
  - nouns for resources; verbs and status codes carry the semantics
  - status codes: 201 + `Location` on create, 202 for async, 204 for an empty body, 409 for conflicts, 412 for failed preconditions
  - a reasoned position on 400 vs 422, and on 404 vs 403 (don't leak existence)
  - one error contract for everything: RFC 9457 `ProblemDetail` from a `@RestControllerAdvice` (or `spring.mvc.problemdetails.enabled`), with stable machine-readable `type`/codes
  - DTOs that are separate from domain types

### A2: Idempotency and retries
- **Opener:** "The client's POST times out and it retries. What happens in your system?"
- **Strong Principal answer includes:**
  - an `Idempotency-Key` header, storing key → (request hash, response) with a TTL, scoped per client
  - the same key with a different payload returns 409/422
  - concurrent requests with the same key are handled by an in-flight lock or a conditional insert
  - PUT/DELETE are naturally idempotent
  - the general habit of designing for at-least-once

### A3: Pagination
- **Opener:** "A big customer's call to `<list endpoint>` returns 2 million items. What changes?"
- **Strong Principal answer includes:**
  - cursor/keyset vs offset: deep offsets are slow, and offsets are unstable under inserts
  - opaque cursors
  - a maximum page size
  - total counts are expensive, so make them optional
  - the next-cursor in the body or a `Link` header
  - a stable sort key

### A4: Evolving the contract
- **Opener:** "Another team needs `<new field / changed semantics>`, and five teams already consume this API. How do you ship it?"
- **Drill-downs:**
  - "What exactly counts as a breaking change?"
  - "How do you retire v1?"
- **Strong Principal answer includes:**
  - additive changes plus tolerant readers on the client side
  - breaking changes, named: removing, renaming, type changes, semantic changes, tightened validation, new required fields, new enum values for strict clients
  - a reasoned choice between URI, header and media-type versioning
  - `Deprecation`/`Sunset` headers
  - per-consumer usage telemetry so you know who's still on v1
  - a migration window with consumer outreach
  - contract tests to catch accidental breaks

### A5: Contract-first
- **Opener:** "How do consumers learn this contract? Is it contract-first or code-first, and why?"
- **Strong Principal answer includes:**
  - OpenAPI as the source of truth, reviewed before implementation (API design review)
  - codegen (openapi-generator) vs springdoc code-first, with the trade-offs
  - linting (Spectral)
  - a breaking-change diff in CI (openapi-diff)
  - publishing to a catalog or portal
  - examples in the spec

---

## Testing (unit + functional)

### T1: Unit testing the Part 1 logic
- **Opener:** "How would you unit test `<class>`? Name the first five test cases you'd write."
- **Strong Principal answer includes:**
  - plain JUnit 5 with no Spring context
  - the planted edge cases as explicit tests
  - `@ParameterizedTest` with `@MethodSource`/`@CsvSource`
  - an injected `Clock` for time logic
  - testing behavior rather than implementation
  - Mockito only at true boundaries
  - property-based tests for invariants (jqwik) as a bonus
  - a concurrency test (executor + `CountDownLatch`) for stateful components

### T2: Testing the web layer and integration
- **Opener:** "How do you test your controller, validation and error responses? And the persistence layer?"
- **Strong Principal answer includes:**
  - `@WebMvcTest` + MockMvc, mocking the service with `@MockitoBean` (`@MockBean` before Boot 3.4)
  - asserting status, headers and the ProblemDetail body
  - `@DataJpaTest`, or `@SpringBootTest` + Testcontainers for a real DB or Redis
  - context caching and avoiding a full context for everything
  - where each test sits on the pyramid

### T3: Live functional tests against a deployed lower environment (mandatory)
- **Opener:** "This service is deployed to a dev/QA environment, either on Kubernetes or as a Lambda behind API Gateway. Design the automated functional test suite that hits it live. Where does it live, how does it run, and what does it check?"
- **Drill-downs** (pick based on the answer):
  - "How does the suite know where to point, and how does it authenticate?"
  - "Two pipelines run the suite at the same time against the same environment. How do the tests avoid stepping on each other's data?"
  - "The suite fails about 5% of the time with no code change. What do you do?"
  - "Where does this sit in the pipeline, and what does a failure block?"
  - "How is this different from your `@SpringBootTest` tests and from contract tests? Why have all three?"
  - "It's a Lambda. Does anything change?"
- **Strong Principal answer includes:**
  - **Structure:**
    - a separate source set or module (e.g. `src/functionalTest`) or a separate repo
    - black-box over HTTP only (REST Assured + JUnit 5, or Karate)
    - no access to app internals
  - **Config:**
    - the base URL and environment come from env vars or profiles
    - the same suite runs against dev, QA and ephemeral per-PR namespaces
    - secrets come from a secrets manager
  - **Auth:**
    - real tokens via an OAuth2 client-credentials test client
    - explicit 401/403 cases
  - **Test data:**
    - every test creates its own data with unique IDs (a run-ID prefix), through the API rather than direct DB writes
    - cleanup in teardown, plus TTL or janitor jobs for leaks
    - no dependence on shared, pre-existing fixtures
  - **Determinism:**
    - no `Thread.sleep`; poll with Awaitility for async or eventually consistent behavior
    - retries only where eventual consistency is documented, never to mask bugs
    - flaky tests are quarantined and tracked, not ignored
    - on failure, capture request/response and trace IDs
  - **Pipeline:**
    - runs as a stage after deploy and the rollout/readiness check
    - gates promotion to the next environment
    - JUnit XML reports into Jenkins, with trends
  - **Scope:**
    - critical user journeys and contract-level behavior (status codes, error shapes, auth)
    - not exhaustive logic, which belongs in unit tests (the pyramid)
  - **Complements:**
    - consumer-driven contract tests (Pact / Spring Cloud Contract) catch breaking changes *before* deploy
    - read-only post-deploy smoke tests in prod
    - synthetic monitoring
  - **Platform specifics:**
    - **Lambda:** test through the API Gateway URL so mappings and authorizers are covered; cold starts affect timeouts; target the right version or alias.
    - **k8s:** wait for the rollout to finish before testing; per-PR namespaces.

### T4: Proving a race is fixed
- **Opener:** "You fixed the race in `<op>`. How do you *prove* it with a test?"
- **Strong Principal answer includes:**
  - many threads released together with a `CountDownLatch`, asserting the invariant afterwards
  - repeated runs, plus knowing such tests are probabilistic (jcstress exists)
  - for distributed state, testing against real Redis or a real DB via Testcontainers
  - tests at the functional level that fire concurrent requests at the deployed service

---

## Security

### S1: Authentication and authorization
- **Opener:** "Who is allowed to call `<endpoint>`, and where in your code is that enforced?"
- **Drill-down:** "User A has a valid token and calls `<GET/DELETE /x/{id}>` with user B's id. What happens?"
- **Strong Principal answer includes:**
  - a Spring Security OAuth2 resource server validating the JWT (signature via JWKS, issuer, audience, expiry)
  - coarse authz with scopes and roles (`requestMatchers`, `@PreAuthorize`)
  - **object-level authorization** in the service layer: an ownership check (BOLA/IDOR, OWASP API #1)
  - 404 vs 403 to avoid leaking existence
  - service-to-service auth (client credentials, mTLS)

### S2: Hostile input
- **Opener:** "What can a malicious client send to `<endpoint>`, and what stops it?"
- **Strong Principal answer includes:**
  - Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Positive`, …)
  - limits on body size and collection size (batch DoS)
  - DTOs to prevent mass assignment
  - parameterized queries
  - safe deserialization (no default polymorphic typing)
  - errors without stack traces or internals (`server.error.include-stacktrace=never`)
  - rate limiting

### S3: Sensitive data
- **Opener:** "What data in this service is sensitive, and where could it leak?"
- **Strong Principal answer includes:**
  - classifying the PII
  - masking in logs, and never logging request bodies by default
  - TLS in transit; encryption at rest with KMS
  - secrets in Secrets Manager or Vault, not in config
  - least-privilege IAM (IRSA on EKS)
  - retention and deletion policies

### S4: Staying secure over time
- **Opener:** "Six months from now, how do you know this service is still secure?"
- **Strong Principal answer includes:**
  - SCA and dependency scanning (Dependabot, Snyk, OWASP dependency-check)
  - SAST and image scanning
  - minimal, non-root container images
  - a patch cadence
  - threat modeling new endpoints
  - CSRF: not relevant to stateless token APIs, and saying why
  - narrowly configured CORS
  - security tests in the functional suite

---

## Observability

### O1: Logs
- **Opener:** "One customer says their request failed yesterday. What do your logs let you reconstruct?"
- **Strong Principal answer includes:**
  - structured JSON logs (Boot 3.4+ `logging.structured.format.console`)
  - trace and correlation IDs on every line through MDC, which Micrometer Tracing populates
  - logging at boundaries and decisions, not on every line
  - level discipline
  - no PII or secrets
  - centralized logging (CloudWatch, ELK, Splunk)
  - sampling at high volume

### O2: Metrics and alerts
- **Opener:** "What metrics does this service emit, and which ones page someone?"
- **Drill-down:** "Would you tag that metric with userId?"
- **Strong Principal answer includes:**
  - RED metrics per endpoint from `http.server.requests` (Actuator + Micrometer)
  - percentile histograms and SLO buckets
  - JVM and pool metrics
  - business metrics via `Counter`/`Timer`/`Gauge`, such as holds expired, rejections and queue age
  - high-cardinality tags like userId explode storage, so avoid them
  - alerting on symptoms and SLO burn rate, not on causes

### O3: Tracing
- **Opener:** "A request goes gateway → your service → DB → a downstream service. How do you see where the time went?"
- **Strong Principal answer includes:**
  - Micrometer Tracing with the OpenTelemetry bridge (Sleuth is gone in Boot 3)
  - W3C `traceparent` propagation
  - auto-instrumented HTTP clients and JDBC
  - propagating context across `@Async`, executors and reactive code (the context-propagation library)
  - a sampling strategy
  - linking logs and traces

### O4: Health and operability
- **Opener:** "How does Kubernetes know your pod is healthy? What's the difference between liveness and readiness here?"
- **Strong Principal answer includes:**
  - Actuator `/actuator/health/liveness` and `/readiness` probe groups
  - readiness reflects the ability to serve; liveness must **not** include downstream dependencies, or you get cascading restarts
  - startup probes for slow JVM boots
  - `server.shutdown=graceful` with a preStop hook

### O5: An incident
- **Opener:** "It's 2am. p99 has doubled and the error rate is 3%. Walk me through your first 15 minutes."
- **Strong Principal answer includes:**
  - acknowledge the alert and check the SLO dashboards
  - scope the blast radius: one endpoint, pod, AZ or consumer?
  - recent deploys or flag flips, with **rollback first**
  - exemplar traces of slow requests
  - the health of downstream dependencies
  - open an incident channel and communicate
  - a blameless postmortem with owned action items afterwards

---

## Resilience & scale-out

### R1: N replicas
- **Opener:** "We now run 3 replicas behind a load balancer. What breaks in your design?"
- **Drill-downs:**
  - "Why not sticky sessions?"
  - "Where does the state go, and how do you hold `<invariant>` across instances?"
- **Strong Principal answer includes:**
  - in-memory state is per instance: answers are wrong and state is lost on restart or deploy
  - externalize it (Redis or a DB)
  - sticky sessions break on scaling and deploys, and they skew load
  - invariants enforced by DB constraints, conditional writes (DynamoDB `ConditionExpression`), optimistic locking (`@Version`) or atomic Redis ops (Lua, `INCR`)
  - distributed locks are a last resort because of lease expiry; mentions fencing tokens

### R2: A slow downstream
- **Opener:** "Your service now calls `<downstream>` on every request, and it gets slow. What happens to *your* service?"
- **Strong Principal answer includes:**
  - connect and read timeouts on every call
  - thread or pool exhaustion turning into a cascading failure
  - retries only for idempotent operations, with exponential backoff, jitter and a retry budget
  - circuit breakers (Resilience4j) with a fallback or graceful degradation
  - bulkheads
  - load shedding

### R3: Reliable events
- **Opener:** "After `<state change>`, other services need to know. How do you publish that reliably?"
- **Strong Principal answer includes:**
  - names the dual-write problem
  - transactional outbox, plus a poller or CDC (Debezium)
  - at-least-once delivery, so consumers must be idempotent
  - ordering per key
  - eventual consistency made explicit in the contract

### R4: Deploys and failure mid-request
- **Opener:** "A pod is killed mid-request, or you're rolling out a new version. What happens to in-flight work?"
- **Strong Principal answer includes:**
  - graceful shutdown, with readiness dropping first (preStop)
  - clients retry idempotently
  - durable state for async work
  - expand/contract schema migrations for zero-downtime deploys
  - blue-green or canary with fast rollback

### R5: Overload
- **Opener:** "Traffic goes up 10x for an hour. What does your system do?"
- **Strong Principal answer includes:**
  - HPA limits and scaling lag
  - load shedding and rate limiting (429/503 with `Retry-After`)
  - queues as buffers
  - protecting the DB (pool caps)
  - degrading non-critical features
  - capacity and load testing ahead of time
