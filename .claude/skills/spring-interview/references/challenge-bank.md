# Challenge bank

## Design rules

**Part 1 (plain Java, about 20–25 minutes):**
- **Scope:** solvable in 20–25 minutes by a strong engineer. Keep it to one core idea, not a puzzle.
- **Domain:** generic business domains. Avoid cards and payments.
- **Planted edge cases:** include 2–3 that the statement deliberately doesn't settle, such as empty input, ties, duplicates, unsorted input, boundary inclusivity, time zones, or rounding. A strong candidate asks about them. Don't hint at them.
- **Room for Principal-level choices:**
  - immutability and records
  - interfaces where extension is likely
  - pure logic kept separate from state and IO
  - an injectable `Clock` for anything time-based
- **Signatures:** give a short Java interface sketch for the *collections* and *stateful* shapes. For the *modeling* shape, describe behaviors only, because the model is the exercise.
- **Examples:** include 1–2 small concrete examples with expected output.

**Part 2 (about 20–25 minutes, revealed only after Part 1 is done):**
- **Capabilities, not endpoints:** describe what the API must let clients do in plain language. The candidate picks paths, verbs, status codes and DTOs.
- **Baseline requirements:** DTOs separate from domain types, input validation, a consistent error format, appropriate status codes, and Part 1 logic reused rather than rewritten.
- **The twist:** exactly one, taken from the seed and mutated. It should create an API-design decision (idempotency, concurrency control, async, pagination, authz, or conditional requests).
- **Out of scope:** say explicitly that build files, `application.yml`, a real database and full security config aren't needed. An in-memory repository behind an interface is fine.

**Every seed has:**
- a Part 1 sketch
- planted edges
- a Part 2 sketch, which is the reference API for assessment and is **never shown** to the candidate
- the twist
- follow-up hooks, meaning the natural pressure points to probe in Phase 3

**Seeds are templates.** Always mutate them: rename the domain, change one rule, swap the twist for a sibling. Keep the difficulty the same.

---

## Seeds: collection processing

### C1. Event sessionizer
- **Part 1:** given `List<Event>` (userId, timestamp, path), split each user's events into sessions. A gap greater than 30 minutes starts a new session. Return per-user session summaries: start, end, eventCount, distinctPaths.
  - Sketch: `Map<String, List<Session>> sessionize(List<Event> events, Duration gap)`.
- **Planted edges:**
  - the input isn't sorted
  - a gap of exactly 30 minutes (same session or new?)
  - identical timestamps
  - a single-event session has zero duration
- **Part 2 sketch:** `POST /events` (batch ingest, 202 or 204) and `GET /users/{userId}/sessions?from&to&cursor`.
  - **Twist:** events arrive late and out of order across batches, so sessions have to be recomputed or merged.
- **Hooks:**
  - unbounded per-user memory
  - late data and recomputation cost
  - userId as a metric tag (cardinality)
  - users reading others' sessions (BOLA)
  - pagination of sessions

### C2. Two-feed reconciliation
- **Part 1:** reconcile `List<Order>` (orderId, sku, qty) from system A against `List<Shipment>` (shipmentId, orderId, sku, qtyShipped) from system B. Report:
  - matched
  - missing shipments
  - unexpected shipments (no matching order)
  - quantity mismatches (under or over)

  One order line may ship across several shipments.
  - Sketch: `ReconciliationReport reconcile(List<Order> orders, List<Shipment> shipments)`.
- **Planted edges:**
  - duplicate records within a feed
  - the same order with multiple SKUs
  - partial shipments that sum to the ordered quantity
  - over-shipment
- **Part 2 sketch:** `POST /reconciliations` returns 202 + Location; `GET /reconciliations/{id}` returns status and summary; `GET /reconciliations/{id}/discrepancies?type&cursor`.
  - **Twist:** feeds can hold millions of rows, so the work has to be async (202 + poll).
- **Hooks:**
  - memory at millions of rows (streaming, sort-merge)
  - job durability across restarts
  - idempotent resubmission
  - progress metrics
  - access control on reports

### C3. Top-N per category over a time window
- **Part 1:** given `List<Sale>` (productId, category, quantity, unitPrice, soldAt), return the top N products by revenue for each category within `[from, to)`. Break ties by productId ascending.
  - Sketch: `Map<String, List<ProductRevenue>> topN(List<Sale> sales, Instant from, Instant to, int n)`.
- **Planted edges:**
  - a category with fewer than N products
  - ties
  - the half-open interval boundary
  - returns (negative quantity)
  - money as `BigDecimal` vs `double`
- **Part 2 sketch:** `POST /sales` and `GET /categories/{category}/top-products?from&to&limit`.
  - **Twist:** results may be up to 60 seconds stale, so the candidate has to design caching. Also validate the `limit` range.
- **Hooks:**
  - heap-based top-K complexity
  - cache invalidation and stampedes
  - precomputed or materialized aggregates
  - money precision
  - HTTP caching headers

### C4. Duplicate-contact grouping
- **Part 1:** given `List<Contact>` (id, name, email, phone), group the contacts that are duplicates. Two contacts match when they share a normalized email (trimmed, case-insensitive) **or** a normalized phone (digits only, with a leading country code `1` dropped). Matching is transitive: if A matches B by email and B matches C by phone, all three are one group.
  - Sketch: `List<Set<String>> duplicateGroups(List<Contact> contacts)`.
- **Planted edges:**
  - transitivity (needs union-find or a graph traversal, not pairwise checks)
  - null or blank fields
  - contacts with no duplicates (include or exclude?)
  - O(n²) pairwise vs O(n) hashing
- **Part 2 sketch:** `POST /contact-imports` (bulk upload), `GET /contact-imports/{id}`, `GET /contact-imports/{id}/duplicate-groups`.
  - **Twist:** some rows in a batch are invalid. Should the whole batch be rejected (422), or should the valid rows be accepted with per-row errors reported?
- **Hooks:**
  - PII in logs and at rest
  - batch-size limits (DoS)
  - the partial-failure contract
  - per-row error shape in ProblemDetail
  - async processing for large imports

### C5. Leaderboard aggregation
- **Part 1:** given `List<MatchResult>` (matchId, playerId, score, playedAt), build a leaderboard. A player's rating is the sum of their best 3 scores, and they need at least 3 matches to qualify. Return players ranked by rating.
  - Sketch: `List<Standing> leaderboard(List<MatchResult> results)`.
- **Planted edges:**
  - a duplicate matchId (a resubmission must not double-count)
  - fewer than 3 matches
  - the tie-ranking style (1,1,3 vs 1,1,2)
- **Part 2 sketch:** `POST /matches`, `GET /leaderboard?season&cursor&limit`, `GET /players/{id}/standing`.
  - **Twist:** match submission must be idempotent by matchId. The same payload again returns the same result; a different payload with the same matchId returns 409.
- **Hooks:**
  - recomputing vs incremental updates
  - Redis sorted sets at scale
  - cursor stability while ranks shift
  - read-heavy caching

---

## Seeds: stateful component

### S1. Per-client rate limiter
- **Part 1:** `RateLimiter.tryAcquire(String clientId) → boolean` allows at most N requests per window W per client, and limits vary by client tier. Inject a `Clock`.
- **Planted edges:**
  - fixed vs sliding window (a burst at the boundary)
  - thread safety
  - memory held for idle clients
  - an unknown tier
- **Part 2 sketch:** enforce the limiter on an existing `GET /reports/{id}` through a `HandlerInterceptor` or filter. Return 429 with `Retry-After` and `RateLimit-*` headers. Add `GET /admin/rate-limits/{clientId}` for inspection. The client is identified by an API-key header.
  - **Twist:** per-tier limits come from `@ConfigurationProperties` and must be changeable without a code change.
- **Hooks:**
  - state across replicas (Redis + Lua, token bucket)
  - per-key atomicity
  - gateway vs app enforcement
  - spoofed client identity
  - rejection metrics and alerting

### S2. Inventory holds with TTL
- **Part 1:** `Inventory` with these operations:
  - `hold(sku, qty, ttl) → holdId`
  - `confirm(holdId)`
  - `release(holdId)`
  - `available(sku)`

  Expired holds give their stock back. Inject a `Clock`.
- **Planted edges:**
  - confirming an expired hold
  - releasing twice
  - quantity greater than available
  - lazy vs eager expiry
- **Part 2 sketch:** `POST /holds` (201 + Location), `POST /holds/{id}/confirmation`, `DELETE /holds/{id}`, `GET /skus/{sku}/availability`.
  - **Twist:** POST /holds takes an `Idempotency-Key`, so client retries never double-hold.
- **Hooks:**
  - overselling races
  - an atomic conditional update vs row locks
  - running scheduled expiry on N replicas
  - an audit trail
  - hot SKUs

### S3. Meeting-room booking
- **Part 1:** `BookingService` with these operations:
  - `book(roomId, start, end, organizer) → bookingId`, which rejects overlapping bookings
  - `cancel(bookingId)`
  - `findAvailableRooms(start, end, minCapacity)`
- **Planted edges:**
  - back-to-back bookings (end == start is not an overlap)
  - zero-length or negative intervals
  - `Instant` vs `LocalDateTime`/time zones
  - cancelling a booking that doesn't exist
- **Part 2 sketch:** `POST /rooms/{roomId}/bookings` (201 or 409), `DELETE /bookings/{id}`, `GET /rooms/availability?start&end&minCapacity`.
  - **Twist:** only the organizer can cancel, so the API needs authz. The 409 body should help the client without leaking other people's meetings.
- **Hooks:**
  - double-booking races across replicas (a DB exclusion constraint or conditional write)
  - `TreeMap` floor/ceiling for interval lookup
  - BOLA on cancel
  - information leakage in errors

### S4. Versioned key-value store
- **Part 1:** a store with these operations:
  - `put(key, value, expectedVersion) → newVersion`, using optimistic concurrency (a missing expectedVersion means create-only)
  - `get(key) → (value, version)`
  - `history(key, limit)`
  - `delete(key, expectedVersion)`
- **Planted edges:**
  - creating a key that already exists
  - after a delete and re-create, does the version restart or continue?
  - history bounds
  - concurrent writers
- **Part 2 sketch:** `PUT /configs/{key}` with `If-Match` (412 on mismatch), `If-None-Match: *` for create-only, `GET` returning an `ETag`, and `GET /configs/{key}/history`.
  - **Twist:** conditional requests: ETag, If-Match, 412, and 428 Precondition Required when If-Match is missing.
- **Hooks:**
  - compare-and-set (`ConcurrentHashMap.compute`)
  - DynamoDB conditional writes
  - history growth
  - 304 caching
  - audit of who changed what

### S5. Job queue with leases and dead-letter
- **Part 1:** `JobQueue` with these operations:
  - `submit(payload) → jobId`
  - `lease(workerId) → Optional<Lease>`, where the job stays invisible for the visibility timeout
  - `ack(leaseToken)`
  - `nack(leaseToken)`

  After maxAttempts a job is dead-lettered. An expired lease makes the job visible again.
- **Planted edges:**
  - acking after the lease expired and the job was re-leased elsewhere
  - nacking past the maximum
  - ordering and fairness
  - when expiry gets evaluated
- **Part 2 sketch:** `POST /jobs`, `POST /leases`, `POST /leases/{token}/ack|nack`, `GET /dead-letter`.
  - **Twist:** a stale worker's ack must be rejected with 409, because the lease token is fenced.
- **Hooks:**
  - at-least-once delivery and idempotent consumers
  - why not SQS (build vs buy)
  - poison messages
  - queue depth and age metrics
  - alerting

---

## Seeds: domain modeling / OOP

### M1. Stackable promotion engine
- **Part 1:** price a cart. Each cart line has sku, category, unitPrice and qty. Promotions include:
  - percent off a category
  - buy-X-get-Y on a sku
  - an amount off orders over a threshold

  Promotions have a priority, and some are exclusive (they don't stack). Return a line-level and total breakdown that shows which promotions applied.
- **Planted edges:**
  - order of application
  - rounding and `BigDecimal`
  - exclusivity conflicts
  - the total must never go below 0
- **Part 2 sketch:** `POST /quotes` (cart in, priced quote with breakdown out), `GET /quotes/{id}`, and admin `POST /promotions`.
  - **Twist:** quotes expire and must be reproducible, so promotion rules are versioned and a quote pins the version it used.
- **Hooks:**
  - open/closed design for new promotion types (strategy plus a registry)
  - polymorphic JSON and contract evolution as new types are added
  - authz on the admin endpoint
  - caching promotions

### M2. Approval workflow state machine
- **Part 1:** model a document approval workflow:
  - DRAFT → SUBMITTED → APPROVED or REJECTED
  - REJECTED → DRAFT on revision
  - documents above an amount threshold need two *distinct* approvers
  - submitters can't approve their own document
  - an audit trail records every transition
- **Planted edges:**
  - the same approver approving twice
  - approving after a rejection
  - illegal transitions
  - concurrent approvals
- **Part 2 sketch:** `POST /documents`, `POST /documents/{id}/transitions` (or one action endpoint per transition), `GET /documents/{id}/history`.
  - **Twist:** an illegal transition returns 409 with a ProblemDetail listing the allowed transitions, and concurrent approvals must not lose a vote.
- **Hooks:**
  - authz combining role with "not your own doc"
  - optimistic locking with `@Version`
  - publishing approval events (outbox)
  - audit immutability

### M3. Parking garage
- **Part 1:** a garage with three spot sizes: motorcycle, compact and large.
  - Motorcycles fit anywhere, cars fit compact or large, trucks fit large only.
  - `enter(plate, vehicleType) → ticket` assigns a spot.
  - `exit(ticketId, time) → fee`.
  - Fees: the first 30 minutes are free, then there's an hourly rate by vehicle type, with a daily cap.
- **Planted edges:**
  - the garage is full
  - the same plate enters twice
  - rounding of partial hours
  - multi-day stays and the cap
- **Part 2 sketch:** `POST /tickets`, `POST /tickets/{id}/exit`, `GET /garages/{id}/occupancy`.
  - **Twist:** pricing rules change without a redeploy, and a ticket is charged under the rules that applied when it entered.
- **Hooks:**
  - concurrent spot assignment
  - strategy pattern for pricing
  - occupancy as a gauge metric
  - config rollout safety

### M4. Notification routing
- **Part 1:** given an event (type, severity, userId) and user preferences, decide which channels to deliver on and when (now, or deferred until quiet hours end). Preferences are:
  - channels per event type (EMAIL/SMS/PUSH)
  - quiet hours in the user's time zone
  - CRITICAL events override quiet hours
- **Planted edges:**
  - quiet hours that cross midnight
  - DST and time zones
  - missing preferences (defaults)
  - the CRITICAL override
- **Part 2 sketch:** `PUT /users/{id}/notification-preferences`, `POST /events` (202), `GET /users/{id}/notifications`.
  - **Twist:** channel providers are slow and flaky, so delivery has to be async with retries.
- **Hooks:**
  - circuit breakers and bulkheads per channel
  - outbox
  - idempotent delivery
  - phone numbers and emails in logs

### M5. Access-policy evaluator
- **Part 1:** `PolicyEngine.evaluate(subject, action, resource) → Decision(ALLOW|DENY, reason)`.
  - Subjects have roles and attributes. Resources have an owner, a type and attributes.
  - Policies are role-based allows plus attribute conditions (subject.dept == resource.dept).
  - An explicit deny overrides an allow, and the default is deny.
- **Planted edges:**
  - deny-overrides precedence
  - no matching policy
  - wildcard actions
  - multiple roles that conflict
- **Part 2 sketch:** `POST /decisions` (batch), `GET /policies`, `PUT /policies/{id}`.
  - **Twist:** other services call this on every request with a p99 budget under 5 ms, and an "explain" mode must not leak policy internals to callers.
- **Hooks:**
  - indexing policies by action and resource type
  - caching decisions and invalidating them
  - audit logging of decisions
  - safe policy rollout

---

## CHALLENGE.md template

````markdown
# <Title>

| | |
|---|---|
| Shape | <Collection processing / Stateful component / Domain modeling> |
| Started | <YYYY-MM-DD HH:MM TZ> |
| Start epoch | <epoch seconds> |

## Ground rules

- Write your Java in this folder, with any file names and layout you like.
- Pseudo-syntax is fine. You don't need imports, a package declaration, `main` or build files; go straight to classes and methods.
- Minor syntax slips don't matter. Records, Lombok and `// assume X exists` stubs are all fine.
- Ask clarifying questions at any time; asking them is part of the exercise.
- Say **"part 1 done"** when you're ready to move on. Ask for a hint if you're stuck; hints are noted in the debrief, as they would be on a real panel.

## Part 1: <short name> (~20–25 min)

<Problem statement: context in 1–2 sentences, then precise behavior.>

### Example

<1–2 small inputs → expected outputs>

### Interface sketch

<For collections and stateful only. Omit for modeling.>

```java
<signatures>
```
````

## Part 2 template (append to CHALLENGE.md at "part 1 done")

```markdown
## Part 2: Expose it as a Spring API (~20–25 min)

_Revealed at <HH:MM>._

Wrap your Part 1 code in a Spring Boot REST API. Keep the Part 1 logic reusable: the API should be a layer around it, not a rewrite.

### Clients need to be able to

- <capability in plain language, no paths or verbs>
- <capability>
- <capability>

### Requirements

- Request and response DTOs, so domain types aren't exposed directly
- Input validation with meaningful 4xx responses
- A consistent error response format
- Appropriate status codes
- **<The twist, stated as a requirement>**

### Not needed

Build files, `application.yml`, a real database (an in-memory store behind a repository interface is fine), or full Spring Security configuration.
```

---

## Transcript format

Get timestamps with `date '+%H:%M'` and epochs with `date +%s`. Append every entry with the `__QA_END__` heredoc described in SKILL.md.

### Skeleton (written in Phase 1)

```markdown
# Transcript: <Title>

[Challenge](CHALLENGE.md) · [Feedback](FEEDBACK.md)
Started <YYYY-MM-DD HH:MM TZ>

## Part 1: Implementation

```

### Entries during implementation

The candidate asks a clarifying question or a syntax question:

```markdown
**[HH:MM] Q (you):** <verbatim>
**A (interviewer):** <verbatim>

```

The candidate asks for a hint. Number hints across the session.

```markdown
**[HH:MM] Hint #<n> (level <1|2|3>), requested:** <verbatim request>
**A (interviewer):** <verbatim hint>

```

The Part 1 correctness probe:

```markdown
**[HH:MM] Q (interviewer, Part 1 check):** <verbatim>
**A (you):** <verbatim>

```

### Milestones

```markdown
---
**[HH:MM] Part 1 complete** · epoch <s> · <m> min

## Part 2: Implementation

```

```markdown
---
**[HH:MM] Part 2 complete** · epoch <s> · Part 2 <m> min · total <m> min

```

### Walkthrough and follow-ups

```markdown
## Walkthrough

**Q (interviewer):** <verbatim>
**A (you):** <verbatim>

## Follow-ups

```

Each follow-up thread has its own heading, numbered F1, F2, … across the session. Drill-downs stay inside their thread. Append each Q/A pair when the answer arrives. When the thread closes, append the placeholder line, which is replaced at debrief.

```markdown
### F<n> · <Category>: <short topic>

**Q (interviewer):** <verbatim>
**A (you):** <verbatim>

**Q (interviewer, drill-down):** <verbatim>
**A (you):** <verbatim>

> **Strong Principal answer:** _added at debrief_ <!-- F<n> -->

```

If the candidate skips a question, record their answer as `**A (you):** _skipped_`.

### Debrief notes (appended at the end)

```markdown
## Debrief notes

### Clarifying questions worth asking

- **<question>**: <why it matters / how it changes the design>
```
