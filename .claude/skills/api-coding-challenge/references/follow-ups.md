# Follow-up question bank

## How to use this

- **Questions are templates.** Rewrite every opener so it points at the candidate's actual code: their class names, fields, endpoints and status codes. "Your `BookingService` keeps every booking in one in-memory map…" beats "How would you handle concurrency?"
- **Thread shape:** an opener, then 1–2 drill-downs chosen by the answer.
  - On vague or buzzword answers ("add a cache", "use Kafka"), go **narrower**: "Concretely, how? What's the key? What invalidates it?"
  - On strong answers, go **wider**: more replicas, more consumers, more teams, failure in the middle.
- **Order:** Performance → API design & versioning → Testing → Security → Observability → Resilience & scale-out. That runs from the code, to the contract, to verifying it, protecting it, operating it, and finally the whole system.
- **Budget:** about 2 threads per category. Testing gets 3, and **T3 (live functional tests) is mandatory**. If a seed's follow-up hooks fit a category, use them.
- **Scoring only:** the "Strong Principal answer includes" bullets are for scoring and for the debrief annotations. Never read them out or hint at them during the round.
- **This bank is stack-neutral; the layers add the stack.** For thread X, use the core thread below, then `### X` under Signals by thread ID in the language file, then `### X` in the framework file.
  - Layer bullets and `Drill-down:` lines add to the core's.
  - An `Opener:` line or a heading title replaces the core's, and the framework's wins if both give one.
  - `N/A:` marks something this stack doesn't have, so don't expect it.
  - For what a race means in this stack (P2, T4, R1), read the language file's Runtime & concurrency and the framework file's Request dispatch.
- **Thread IDs are frozen:** P1–P6, A1–A5, T1–T4, S1–S4, O1–O5, R1–R5. The layers refer to them, so never renumber or add one.

---

## Performance

### P1: Complexity and data structures
- **Opener:** "What's the time and space complexity of `<core method>`? Where's the hot path?"
- **Drill-downs:**
  - "N goes from thousands to 10 million. What breaks first?"
  - "Could `<op>` be O(log n) or O(1)? What would that cost you?"
- **Strong Principal answer includes:**
  - precise complexity per operation, and the dominant term
  - the right structure for the access pattern: an ordered map's floor/ceiling for intervals, a heap for top-K, hashing for grouping
  - memory as well as time
  - asks about real volumes and SLOs before optimizing, and doesn't over-engineer for scale nobody needs

### P2: Shared mutable state under concurrent requests
- **Opener:** "`<Service>` holds `<field>`, and every request shares it. What happens with 200 concurrent requests?"
- **Drill-downs:**
  - "Is a concurrent map enough? Walk me through `<check-then-act op>`."
  - "Where would the locking go, and what does it cost in throughput?"
- **Strong Principal answer includes:**
  - spots the check-then-act race
  - per-key atomicity via an atomic per-key update, or per-key or striped locks, *not* one lock around the whole service, which serializes everything
  - immutable snapshots for read-heavy data
  - knows that switching to lighter threads or async doesn't fix races
  - notes that at real scale the fix is moving state out of the process (a bridge to Resilience)

### P3: Unbounded growth
- **Opener:** "What stops `<map/list>` from growing forever?"
- **Strong Principal answer includes:**
  - eviction or TTL, bounded structures
  - lazy vs scheduled cleanup and the trade-off between them
  - sizing the runtime's memory to the container's limit
  - GC pressure from large maps
  - monitoring the size as a gauge

### P4: Data access at scale
- **Opener:** "This now sits on Postgres, or DynamoDB if you prefer. What does the access pattern look like, and how do you keep `<endpoint>` fast?"
- **Strong Principal answer includes:**
  - indexes that match the queries
  - avoiding N+1 (a join or a batched query instead of one query per row)
  - keyset pagination, projections and DTO queries
  - for DynamoDB: key design from the access patterns, hot partitions, GSIs
  - connection-pool size relative to request concurrency, and the DB's connection limit

### P5: Caching
- **Opener:** "Where would you cache in this design, and how does the cache get invalidated?"
- **Strong Principal answer includes:**
  - cache-aside vs read-through
  - TTL vs event-driven invalidation, with the staleness explicitly accepted with the product owner
  - local (in-process) vs distributed (Redis)
  - stampede protection
  - HTTP caching (`ETag`/`Cache-Control`/304)
  - what must never be cached (authz-dependent responses keyed without the user)

### P6: Finding a bottleneck
- **Opener:** "Under load, p99 on `<endpoint>` is 2 seconds while the average is fine. How do you find out why?"
- **Strong Principal answer includes:**
  - reproduce with a load test (Gatling, k6)
  - a profiler, thread or stack dumps, GC or runtime logs
  - saturation metrics: busy request workers, connection-pool waiters, executor queues
  - tail-latency causes: GC pauses, lock contention, a slow dependency, pool starvation
  - cheaper concurrency for blocking IO (lightweight threads, async), with its caveats

---

## API design & versioning

### A1: Defend the API they built
- **Opener:** "Walk me through why you chose these paths, verbs and status codes."
- **Drill-down:** "What exactly does a client receive when `<validation fails / resource missing / conflict>`?"
- **Strong Principal answer includes:**
  - nouns for resources; verbs and status codes carry the semantics
  - status codes: 201 + `Location` on create, 202 for async, 204 for an empty body, 409 for conflicts, 412 for failed preconditions
  - a reasoned position on 400 vs 422, and on 404 vs 403 (don't leak existence)
  - one error contract for everything: RFC 9457 problem details from one central error mapping, with stable machine-readable `type`/codes
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
  - codegen (openapi-generator) vs generating the spec from the code, with the trade-offs
  - linting (Spectral)
  - a breaking-change diff in CI (openapi-diff)
  - publishing to a catalog or portal
  - examples in the spec

---

## Testing (unit + functional)

### T1: Unit testing the Part 1 logic
- **Opener:** "How would you unit test `<class>`? Name the first five test cases you'd write."
- **Strong Principal answer includes:**
  - plain unit tests of the Part 1 types, with no framework or HTTP in the way
  - the planted edge cases as explicit tests
  - table-driven or parameterized tests for the edge-case matrix
  - an injected clock for time logic
  - testing behavior rather than implementation
  - mocks only at true boundaries
  - property-based tests for invariants as a bonus
  - a concurrency test for stateful components

### T2: Testing the web layer and integration
- **Opener:** "How do you test your HTTP layer, validation and error responses? And the persistence layer?"
- **Strong Principal answer includes:**
  - an in-process HTTP test of the web layer, with the domain service faked
  - asserting status, headers and the problem-details body
  - integration tests against a real DB or Redis via Testcontainers
  - keeping test start-up cheap, rather than booting the whole app for every test
  - where each test sits on the pyramid

### T3: Live functional tests against a deployed lower environment (mandatory)
- **Opener:** "This service is deployed to a dev/QA environment, either on Kubernetes or as a Lambda behind API Gateway. Design the automated functional test suite that hits it live. Where does it live, how does it run, and what does it check?"
- **Drill-downs** (pick based on the answer):
  - "How does the suite know where to point, and how does it authenticate?"
  - "Two pipelines run the suite at the same time against the same environment. How do the tests avoid stepping on each other's data?"
  - "The suite fails about 5% of the time with no code change. What do you do?"
  - "Where does this sit in the pipeline, and what does a failure block?"
  - "How is this different from your in-process integration tests and from contract tests? Why have all three?"
  - "It's a Lambda. Does anything change?"
- **Strong Principal answer includes:**
  - **Structure:**
    - a separate test module, source set or repo, built and run apart from the unit tests
    - black-box over HTTP only
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
    - no fixed sleeps; poll with a timeout for async or eventually consistent behavior
    - retries only where eventual consistency is documented, never to mask bugs
    - flaky tests are quarantined and tracked, not ignored
    - on failure, capture request/response and trace IDs
  - **Pipeline:**
    - runs as a stage after deploy and the rollout/readiness check
    - gates promotion to the next environment
    - machine-readable test reports into Jenkins, with trends
  - **Scope:**
    - critical user journeys and contract-level behavior (status codes, error shapes, auth)
    - not exhaustive logic, which belongs in unit tests (the pyramid)
  - **Complements:**
    - consumer-driven contract tests (Pact) catch breaking changes *before* deploy
    - read-only post-deploy smoke tests in prod
    - synthetic monitoring
  - **Platform specifics:**
    - **Lambda:** test through the API Gateway URL so mappings and authorizers are covered; cold starts affect timeouts; target the right version or alias.
    - **k8s:** wait for the rollout to finish before testing; per-PR namespaces.

### T4: Proving a race is fixed
- **Opener:** "You fixed the race in `<op>`. How do you *prove* it with a test?"
- **Strong Principal answer includes:**
  - many concurrent actors released together, asserting the invariant afterwards
  - repeated runs, plus knowing such tests are probabilistic
  - for distributed state, testing against real Redis or a real DB via Testcontainers
  - tests at the functional level that fire concurrent requests at the deployed service

---

## Security

### S1: Authentication and authorization
- **Opener:** "Who is allowed to call `<endpoint>`, and where in your code is that enforced?"
- **Drill-down:** "User A has a valid token and calls `<GET/DELETE /x/{id}>` with user B's id. What happens?"
- **Strong Principal answer includes:**
  - an OAuth2 resource server validating the JWT (signature via JWKS, issuer, audience, expiry)
  - coarse authz with scopes and roles, declared at the edge
  - **object-level authorization** in the service layer: an ownership check (BOLA/IDOR, OWASP API #1)
  - 404 vs 403 to avoid leaking existence
  - service-to-service auth (client credentials, mTLS)

### S2: Hostile input
- **Opener:** "What can a malicious client send to `<endpoint>`, and what stops it?"
- **Strong Principal answer includes:**
  - validation at the edge: required fields, sizes and ranges on every input
  - limits on body size and collection size (batch DoS)
  - DTOs to prevent mass assignment
  - parameterized queries
  - safe deserialization: no type information taken from the payload, and unknown fields rejected
  - errors without stack traces or internals
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
  - SCA and dependency scanning (Dependabot, Snyk)
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
  - structured JSON logs
  - trace and correlation IDs on every line, attached by the logging context rather than by hand
  - logging at boundaries and decisions, not on every line
  - level discipline
  - no PII or secrets
  - centralized logging (CloudWatch, ELK, Splunk)
  - sampling at high volume

### O2: Metrics and alerts
- **Opener:** "What metrics does this service emit, and which ones page someone?"
- **Drill-down:** "Would you tag that metric with userId?"
- **Strong Principal answer includes:**
  - RED metrics (rate, errors, duration) per endpoint
  - percentile histograms and SLO buckets
  - runtime and connection-pool metrics
  - business metrics (counters, timers, gauges), such as holds expired, rejections and queue age
  - high-cardinality tags like userId explode storage, so avoid them
  - alerting on symptoms and SLO burn rate, not on causes

### O3: Tracing
- **Opener:** "A request goes gateway → your service → DB → a downstream service. How do you see where the time went?"
- **Strong Principal answer includes:**
  - OpenTelemetry tracing, through the SDK or auto-instrumentation
  - W3C `traceparent` propagation
  - auto-instrumented HTTP clients and DB calls
  - propagating trace context across async boundaries: thread pools, callbacks and reactive code
  - a sampling strategy
  - linking logs and traces

### O4: Health and operability
- **Opener:** "How does Kubernetes know your pod is healthy? What's the difference between liveness and readiness here?"
- **Strong Principal answer includes:**
  - separate liveness and readiness endpoints
  - readiness reflects the ability to serve; liveness must **not** include downstream dependencies, or you get cascading restarts
  - startup probes for slow boots
  - graceful shutdown that drains in-flight requests, with a preStop hook

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
  - invariants enforced by DB constraints, conditional writes (DynamoDB `ConditionExpression`), optimistic locking (a version column) or atomic Redis ops (Lua, `INCR`)
  - distributed locks are a last resort because of lease expiry; mentions fencing tokens

### R2: A slow downstream
- **Opener:** "Your service now calls `<downstream>` on every request, and it gets slow. What happens to *your* service?"
- **Strong Principal answer includes:**
  - connect and read timeouts on every call
  - thread or pool exhaustion turning into a cascading failure
  - retries only for idempotent operations, with exponential backoff, jitter and a retry budget
  - circuit breakers with a fallback or graceful degradation
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
