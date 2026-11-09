# Java

Language layer for api-coding-challenge, read with one framework layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` here, then `### X` in the framework file. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and the framework's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | java |
| aliases | java, jdk |
| display | Java |
| fence | java |
| extensions | *.java |
| ignore | target/, build/, .gradle/, *.class |
| unit_test | JUnit 5 + Mockito |
| live_test | REST Assured + JUnit 5 |
| part1_min | 20–25 |

## Ground rules

- Write your Java in this folder, with any file names and layout you like.
- Pseudo-syntax is fine. You don't need imports, a package declaration, `main` or build files; go straight to classes and methods.
- Minor syntax slips don't matter. Records, Lombok and `// assume X exists` stubs are all fine.

## Tolerance

Tolerance covers ceremony, never decisions.

- **Free:** missing imports, package, `main` or build files; getters, setters or boilerplate; Lombok or records; not remembering an exact annotation name (syntax questions are free); `throws` clauses for checked exceptions in pure Part 1 logic.
- **Scored:** a `catch` that logs and drops the exception, or a bare `catch (Exception e)` on an IO or HTTP path, which counts as swallowed or over-broad exception handling; a class used as a map key or set element without `equals` and `hashCode` (a record has them), which breaks lookups and dedupe.

## Type mapping

| Concept | Idiom | Note |
|---|---|---|
| instant / duration | `Instant`, `Duration`; not `LocalDateTime` for instants | `Duration` arithmetic, not raw `long` millis |
| optional | `Optional<T>` as a return type | not for fields or parameters; `Optional.get()` without a check just moves the failure to the caller |
| money | `BigDecimal` with explicit rounding, never `double` | `setScale` with a `RoundingMode`; compare with `compareTo`, because `equals` says `2.0` and `2.00` differ |
| ordered map | `TreeMap` / `NavigableMap` | `floorEntry`/`ceilingEntry` in O(log n); not thread-safe, so `ConcurrentSkipListMap` when shared |
| heap | `PriorityQueue` | a min-heap capped at K for top-K; its iteration order isn't sorted |
| value type | `record` | room for immutability and records; a record is only as immutable as its components, so copy lists with `List.copyOf` |
| clock | an injected `java.time.Clock` | an injectable `Clock` for anything time-based; `Clock.fixed` in tests |
| per-key atomic update | `ConcurrentHashMap.compute` / `merge` | the remapping function runs under the bin's lock, so keep it short and free of IO |
| extension point | an `interface`, as a strategy | a `sealed` interface when the set of variants is closed |
| stub idiom | `// assume X exists` | a stub method with a comment is fine too |
| local and zoned time | `LocalTime` / `LocalDateTime` plus a `ZoneId`, giving a `ZonedDateTime` (seeds S3, M4) | DST gaps and overlaps resolve silently, so a strong candidate says which way |
| sum type | an `enum` for plain states; a `sealed` interface of `record`s with a pattern-matching `switch` (seeds M2, M5) | a `switch` over a sealed type fails to compile when a case is missing |

## Runtime & concurrency

- Platform threads, one OS thread each; virtual threads (Java 21) are cheap, but they share memory exactly as platform threads do.
- Under the Java memory model, an unsynchronized race silently loses updates or reads stale values. Nothing crashes and nothing fails to compile, so the bug shows up only as wrong numbers.
- Primitives: `synchronized`, `ReentrantLock`, `AtomicLong` / `LongAdder`, `ConcurrentHashMap` with its atomic `compute` / `merge`, and immutable snapshots published through a `volatile` field or an `AtomicReference`.
- Race tooling: an executor plus a `CountDownLatch` start gate, repeated runs, and jcstress for memory-model stress tests.
- Container knobs: `-XX:MaxRAMPercentage` sizes the heap from the container limit; GC pauses show up in p99; before JDK 24, a virtual thread pins its carrier inside a `synchronized` block.

## Seed calibration

- **C1** (event sessionizer): ok
- **C2** (two-feed reconciliation): ok
- **C3** (top-N per category): ok
- **C4** (duplicate-contact grouping): ok
- **C5** (leaderboard): ok
- **S1** (per-client rate limiter): ok
- **S2** (inventory holds): ok
- **S3** (meeting-room booking): ok
- **S4** (versioned key-value store): ok
- **S5** (job queue with leases): ok
- **M1** (promotion engine): ok
- **M2** (approval workflow): ok
- **M3** (parking garage): ok
- **M4** (notification routing): ok
- **M5** (access-policy evaluator): ok

## Seed lines

- **C1** Sketch: given `List<Event>` (userId, timestamp, path), split each user's events into sessions. A gap greater than 30 minutes starts a new session. Return per-user session summaries: start, end, eventCount, distinctPaths.
- **C1** Sketch: `Map<String, List<Session>> sessionize(List<Event> events, Duration gap)`.
- **C2** Sketch: reconcile `List<Order>` (orderId, sku, qty) from system A against `List<Shipment>` (shipmentId, orderId, sku, qtyShipped) from system B. Report: matched, missing shipments, unexpected shipments (no matching order) and quantity mismatches (under or over).
- **C2** Sketch: `ReconciliationReport reconcile(List<Order> orders, List<Shipment> shipments)`.
- **C3** Sketch: given `List<Sale>` (productId, category, quantity, unitPrice, soldAt), return the top N products by revenue for each category within `[from, to)`. Break ties by productId ascending.
- **C3** Sketch: `Map<String, List<ProductRevenue>> topN(List<Sale> sales, Instant from, Instant to, int n)`.
- **C3** Planted edge: money as `BigDecimal` vs `double`
- **C4** Sketch: given `List<Contact>` (id, name, email, phone), group the contacts that are duplicates. Two contacts match when they share a normalized email (trimmed, case-insensitive) **or** a normalized phone (digits only, with a leading country code `1` dropped). Matching is transitive: if A matches B by email and B matches C by phone, all three are one group.
- **C4** Sketch: `List<Set<String>> duplicateGroups(List<Contact> contacts)`.
- **C5** Sketch: given `List<MatchResult>` (matchId, playerId, score, playedAt), build a leaderboard. A player's rating is the sum of their best 3 scores, and they need at least 3 matches to qualify. Return players ranked by rating.
- **C5** Sketch: `List<Standing> leaderboard(List<MatchResult> results)`.
- **S1** Sketch: `RateLimiter.tryAcquire(String clientId) → boolean` allows at most N requests per window W per client, and limits vary by client tier. Inject a `Clock`.
- **S2** Sketch: Expired holds give their stock back. Inject a `Clock`.
- **S3** Planted edge: `Instant` vs `LocalDateTime`/time zones
- **S3** Hook: `TreeMap` floor/ceiling for interval lookup
- **S4** Hook: compare-and-set (`ConcurrentHashMap.compute`)
- **S5** Sketch: `lease(workerId) → Optional<Lease>`, where the job stays invisible for the visibility timeout
- **M1** Planted edge: rounding and `BigDecimal`

## Twist guards

none

## Dim-2 anchor

Immutability and records where it fits. Pure logic separated from state, time and IO (injected `Clock`).

## Code hooks

- `double` used for money → rounding errors (correctness)
- a `HashMap` or `ArrayList` field mutated on every request → lost updates, or a `ConcurrentModificationException` (P2)
- a `containsKey` or `get` followed by a separate `put` on a `ConcurrentHashMap` → the check-then-act race a concurrent map doesn't fix (P2)
- `LocalDateTime` used for an instant → wrong across time zones and DST (correctness)
- `BigDecimal.equals` used to compare amounts → `2.0` and `2.00` count as different (correctness)

## Signals by thread ID

### P1

- the right structure for the access pattern: `TreeMap` floor/ceiling for intervals, a heap for top-K, hashing for grouping
- a `PriorityQueue` capped at K for top-K is O(n log K), where sorting everything is O(n log n)
- boxed values cost an object header and a pointer each, so a `Map<String, Long>` with 10 million entries costs far more than the raw numbers

### P2

Drill-down: "Is `ConcurrentHashMap` enough? Walk me through `<check-then-act op>`."
- per-key atomicity via `compute`/`merge`, or per-key or striped locks, *not* `synchronized` on the whole service, which serializes everything
- knows that virtual threads don't fix races
- notes that at real scale the fix is moving state out of the JVM (a bridge to Resilience)

### P3

- eviction or TTL (Caffeine), bounded structures
- container heap sizing (`-XX:MaxRAMPercentage`)

### P5

- local (Caffeine) vs distributed (Redis)

### P6

- JFR or async-profiler, thread dumps, GC logs
- a thread dump with many threads `BLOCKED` on one monitor, or waiting in `getConnection`, points at lock contention or pool starvation

### T1

- `@ParameterizedTest` with `@MethodSource`/`@CsvSource`
- an injected `Clock` for time logic
- Mockito only at true boundaries
- property-based tests for invariants (jqwik) as a bonus
- a concurrency test (executor + `CountDownLatch`) for stateful components

### T3

- **Structure:**
  - a separate source set or module (e.g. `src/functionalTest`) or a separate repo
  - black-box over HTTP only (REST Assured + JUnit 5, or Karate)
- **Determinism:**
  - no `Thread.sleep`; poll with Awaitility for async or eventually consistent behavior
- **Pipeline:**
  - JUnit XML reports into Jenkins, with trends

### T4

- many threads released together with a `CountDownLatch`, asserting the invariant afterwards
- repeated runs, plus knowing such tests are probabilistic (jcstress exists)

### S4

- SCA and dependency scanning (Dependabot, Snyk, OWASP dependency-check)
- a minimal runtime image: a distroless Java base or a `jlink`-trimmed JRE, run as a non-root user

### O1

- SLF4J over Logback or Log4j2, with a JSON encoder for structured output
- MDC is thread-local, so its values must be copied onto executor and `CompletableFuture` threads, or the trace ID drops off those lines

### O2

- JVM and pool metrics

### O3

- auto-instrumented HTTP clients and JDBC
- the OpenTelemetry Java agent (`-javaagent`) instruments HTTP clients, JDBC and executors without code changes

### O4

- startup probes for slow JVM boots

### R2

- circuit breakers (Resilience4j) with a fallback or graceful degradation
- the JDK `HttpClient` has no connect or request timeout unless one is set (`connectTimeout` on the builder, `timeout` on each `HttpRequest`)
- a bounded executor or a Resilience4j `Bulkhead` per dependency, so one slow call can't take every thread
