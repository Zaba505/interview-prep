# Transcript: Workshop Seat Holds

[Challenge](CHALLENGE.md) · [Feedback](FEEDBACK.md)
Started 2026-11-09 15:15 EST

## Part 1: Implementation

**[15:51] Q (you):** can you cancel a confirmed hold?
**A (interviewer):** No. Once it's confirmed it's a permanent enrollment, and its seats never go back to the pool, so `release` on a confirmed hold is a rejected operation. You choose how to signal that, the same as for your other rejections. Cancelling an enrollment (refunds and so on) is a separate flow and out of scope here.

**[16:08] Q (interviewer, Part 1 check):** Got it, 53 minutes for Part 1. Before Part 2, walk me through one scenario using your code.

Capacity is 10 and the clock is at 09:00. `hold("k8s-101", "alice", 2, Duration.ofMinutes(5))` returns `hold-1`, then `hold("k8s-101", "bob", 3, Duration.ofMinutes(30))` returns `hold-2`. At 09:10, `confirm("hold-2")` is called. At 09:40, what does `available("k8s-101")` return, and how does your code get there?
**A (you):** It should return available capacity of 7. It reaches this by taking the registered workshop capacity and subtracting the seat capacity for all "active" reservations. I implemented "active" reservations to be any reservation which is confirmed or under a unexpired hold. In the scenario you gave, hold-2 was confirmed before its 30 minute expiration but hold-1 was never confirmed and it only had a 5 minute expiration ttl, thus the `clock.now().isBefore(expiresAt)` check will return false for hold-1.

---
**[16:08] Part 1 complete** · epoch 1794258491 · 53 min

## Part 2: Implementation

---
**[16:46] Part 2 complete** · epoch 1794260789 · Part 2 33 min · total 91 min

_Candidate's note at Part 2 done:_ I completed part 2 but didn't add the code the Idempotency key checking. The way I'd do that is via an external cache (assuming this API is running a horizontally scaling service). The cache client would be constructor injected just like the SeatHolds service is.

## Walkthrough

**Q (interviewer):** Okay, Part 2 took 33 minutes, and the session is at 91 minutes overall. I've read through both files.

Give me a one-to-two-minute walkthrough of your solution, including the complexity of the core operations.
**A (you):** The key to my API implementation is mapping the seat holding service operations to RESTful endpoints. Instead of simply lifting the methods into endpoints (which would be a more RPC style API), I leveraged appropriate HTTP verbs to represent things like changing the reservation status to Confirmed via PUT or releasing a reservation via DELETE. All Controller methods represent happy path flow and are easy to read due to global exception handling being leveraged via a RestControllerAdvice class. Another change from the seat holds interface API is that the API does not make the caller responsible for workshop ids but instead generates ULIDs itself and returns the workshop id to the user. Currently that does make this service a bit of a monolith because its taking ownership of workshops and reservations but unless presented with a larger architecture, I think starting with a slight monolith approach is simpler.

## Follow-ups

### F1 · Performance: complexity of hold, confirm and available

**Q (interviewer):** Got it. Let's go through some follow-ups, starting with performance.

In `InMemorySeatHolds`, `confirm` and `release` both start with `getWorkshopByHoldId`, which streams every workshop's reservations. `available` filters a workshop's full reservation list on every call. What's the time and space complexity of `hold`, `confirm` and `available`, and which one is the hot path?
**A (you):** * `available` - time: O(2*n) -> O(n), space: O(2*n) -> O(n)
* `hold` - time: O(n)+O(n) -> O(n), space: O(n)
  I don't recall exactly the behaviour of `.add()` on List so I made the assumption that its O(n) in time and O(1) in space. Both would vary depending the backing implementation of the list though.
* `confirm` - time: O(n*m) + O(m), space: O(1)
  n = num of workshops, m = num of reservations per workshop. I put space as O(1) due to the only allocation being the new Record which is required since records are immutable

In regards to the hot path question, I don't think I can answer that without knowing the traffic pattern of the API. If we assume its a very popular workshop where many, many people try to reserve seats to it, then I'd expect `available` to be the hottest path and then `hold` to be the second hottest. In other words, most users would hit `available` and see no availability but for those who do see availability they would grab a hold immediately which would in turn call `available` again. Thus gaurunteeing `available` to always be hotter than `hold`. Nothing can really be said about `confirm` without more business context for who executes `confirm` and at what frequency

**Q (interviewer, drill-down):** Okay. Let's stay on the popular-workshop case you described, where most people call `available` and most holds expire unconfirmed. Could `available` be O(1)? What would you change in `InMemorySeatHolds`, and what would that cost you?
**A (you):** To achieve O(1), my immediate thought is to have a collection of Threads where each spread it instantiated and started when a hold is created. Each thread decrements a shared atomic counter at its startup, sleeps for duration ttl and if the status of its hold is still Unconfirmed then it increments the atomic counter. The amount dec/inc by is the number of seats and `available` would simply be changed to load and return the value of the atomic counter which is a amortized O(1) time complexity. Note this scales cpu load by O(reservations)*O(workshops) since there would be a thread per reservation. This could cheaper if virtual threads are used but the complexity analysis doesn't change, you can just scale higher before things break.

> **Strong Principal answer:** As the code stands, `available` is O(R) time **and** O(R) space, where R is every reservation the workshop has ever had: expired holds are never removed, and `activeReservations` allocates a filtered copy. `hold` is O(R) (an `available` call plus an amortized O(1) `ArrayList.add`). `confirm` and `release` are O(W·R), because `getWorkshopByHoldId` scans every workshop, and they add another O(R) copy, so their space isn't O(1) either. Asking about the traffic pattern before naming a hot path was the right move. For O(1) `available`: index holds in a `Map<String, Hold>` (O(1) confirm and release), keep `held` and `confirmed` counters per workshop so that `available = capacity − held − confirmed`, and expire lazily from a `PriorityQueue<Hold>` ordered by `expiresAt`. At the start of each operation, pop every hold whose `expiresAt <= now`, skip any that were already confirmed or released, and subtract the rest from `held`. Each hold costs O(log H) amortized, with no threads. The cost is more state that has to change together atomically: the counters, the index and the heap. A thread per hold means 50,000 sleeping threads for one launch, all lost on restart, and its check-status-then-increment races with a concurrent `confirm`. A single `ScheduledExecutorService` or the lazy heap does the same job. Finally, ask for real volumes: at tens or hundreds of seats per workshop, O(R) may be fine until a flash sale.

### F2 · Performance: shared state in the singleton bean

**Q (interviewer):** Got it. Next one.

`InMemorySeatHolds` is a Spring singleton that holds `workshops` (a `HashMap`), each workshop's `reservations` list, and `holdCounter`. Tomcat sends 200 concurrent requests through it. What happens?
**A (you):** First observation is that hold ids could be duplicated due to no locking around the reading and writing for holdCounter. For more possibilities, I would need more info about the requests hitting InMemorySeatHolds. One thing to note, is that I don't believe HashMap has an internal on its buckets (that should ConcurrentHashMap) so there shouldn't be any potential lock contention between `workshops.put` and workshops.get`. I don't recall if `List.add()` is synchronized though so I can't confidently say anything about that

**Q (interviewer, drill-down):** Okay, let's push on that. Say you swap `workshops` for a `ConcurrentHashMap` and make the counter an `AtomicLong`. Is that enough? `k8s-101` has 3 seats left, and two customers call `hold("k8s-101", …, 3, …)` at the same moment. Walk me through what your `hold` does for each of them.
**A (you):** No its not enough because now in that scenario your bigger problem is that the entire method needs to be an encapsulated transaction. Specifically, the availability check would pass for both when in reality one of them should be blocked until the other hold is completed. Then the second availability check would fail once its `hold` execution is resumed

> **Strong Principal answer:** All 200 Tomcat request threads run through the one singleton `InMemorySeatHolds`. The problem is correctness, not lock contention. `HashMap` and `ArrayList` aren't thread-safe, so concurrent `add` calls can lose reservations, and the stream in `activeReservations` can throw `ConcurrentModificationException` mid-iteration. `holdCounter += 1` loses increments, which produces duplicate hold IDs. The serious bug is that `hold` is check-then-act: `available()` and then `add`. Two threads both see 3 seats, both hold 3, and the workshop is oversold. A `ConcurrentHashMap` can't fix that, because the race spans two operations. Make the check and the write atomic **per workshop**, with `workshops.compute(workshopId, (id, w) -> w.withHold(...))` over an immutable `Workshop` value, or a per-workshop `ReentrantLock`. Don't put `synchronized` on the whole service, which would serialize every workshop behind one lock. Use an `AtomicLong` or UUIDs for IDs. Virtual threads don't change any of this. At real scale the state leaves the JVM, and the invariant moves into the database (see F12).

### F3 · API design & versioning: verbs, status codes and the error contract

**Q (interviewer):** Got it. Let's move to API design.

In your controller, `POST /v1/workshops/{workshopId}/reservations` returns 200 with the hold ID. Confirming is a `PUT` on that same reservation URL with no request body, and release is a `DELETE` that returns 200 with an empty record. Your advice comment maps seat-hold errors to 404 or 500. Walk me through why you chose those verbs and status codes.
**A (you):** Oh now that I'm taking a second to review it based on your question, I have some minor notes for changing statuses along with answering your reasoning question:
- POST reservations would better to be 201 since its creating a new instance of the reservation resource. the hold id is returned to follow up requests can be made on that resource
- PUT should be 204. PUT was selected as its best practice for operations that modify a resource instead of creating. Note confirmation just modifies and existing reservations status to confirmed. The lack of request body is simply because resource ids should go in request paths and for this operation I only needed the resource id (aka the hold id)
- DELETE should actually be 204. DELETE was selected due to the challenge requirements that define releasing as effectively deleting the reservation. Thus that operation translated seamlessly to the existing DELETE verb
- 404 would be returned if a workshop id or hold id does not return a resource
- 400 would be used for any request validation failures
- 500 would be used for any internal exceptions e.g. InterruptedException, NullPointerException, etc.
- 500 might be used for when expired reservations are attempted to be confirmed/released but there might be a better status code which already is meant to be used for expired resources. Note regardless of the returned code, the InMemorySeatHolds implementation would need to be tweaked to throw distinct exceptions for "id not found" and "reservation expired". I purposfully skipped that because its a minor differentiation.

**Q (interviewer, drill-down):** Okay. Here's a case that isn't on that list. A client sends `POST /v1/workshops/{workshopId}/reservations` for 4 seats when only 3 are left. What exactly does it get back, both the status and the body?
**A (you):** I would need to double check the status code definitions but I think either "Bad Request" or "Unprocessable Entity" code could be returned because they should both be 400 status codes which means the client did something incorrect. In this case, its the clients fault because REST is meant to be stateless and the client should first check availability before trying to make a reservation. Note in practice multiple users would be making calls simultaneously so this is still a common occurrence even if the availability is checked first. This is why many implementors would map this to 500 since most clients treat 4XX as non-retryable and 5XX as retryable since 5XX should be the servers fault. An alternative approach is the 4XX code with a error response body that the client can check for retryability.

> **Strong Principal answer:** `POST …/reservations` → `201 Created` with `Location: /v1/holds/{holdId}` and a body (holdId, seats, expiresAt, status). Confirming is a state transition, not a replacement, so `POST /holds/{id}/confirmation` reads more clearly than a bodyless `PUT`. A `PUT` with `{"status":"CONFIRMED"}` is defensible because it's idempotent. Release is `DELETE` → `204`, and releasing a confirmed hold is `409`. For "4 seats requested, 3 left", the request is well-formed and valid, but it conflicts with current state, so the answer is **`409 Conflict`**, not 400 or 422 (malformed or semantically invalid input), and **never 500**. A 5xx tells clients "our fault, retry", so they hammer a sold-out workshop, and it pages on-call for normal launch-day traffic. The body is an RFC 9457 `ProblemDetail`: `type: /problems/insufficient-seats`, `status: 409`, `detail`, plus extension fields `requested: 4, available: 3`. Clients decide on retries from the stable `type` (and `Retry-After` where it applies), not from the status class. An expired hold → `409` or `410 Gone` with its own `type`, and an unknown hold → `404`. Use one `@RestControllerAdvice` that extends `ResponseEntityExceptionHandler` and maps `InsufficientSeatsException`, `CustomerLimitExceededException`, `HoldNotFoundException` and `HoldExpiredException`, so Spring's own errors (malformed JSON, a missing body) come back in the same shape.

### F4 · API design & versioning: idempotent hold creation

**Q (interviewer):** Got it. Now the twist you left out of the code.

You said you'd handle `Idempotency-Key` with an external cache, injected into the controller the same way `SeatHolds` is. A client's `POST /v1/workshops/{workshopId}/reservations` times out, and it retries with the same key. Walk me through what your controller does on the first request and on the retry. What goes into the cache, under what key, and at what point?
**A (you):** The first step is always lookup the idempotency key in the cache. Depending on system requirements, the cache key can be as simple as the idempotency key directly. If there is a cache miss, its assumed to be the first request (or the cache was purged) and the standard flow is followed. If there is a cache hit then the value should be the hold id so the API just immediately returns the hold id. Note an external cache is usable only if some risk of duplication is tolerable. The cache purged/key eviction scenario could allow a hold to be duplicated. If that small risk is not accetaple then the SeatHolds service would need to be updated to store the idempotency key along with the reservation since the SeatHold service would ideally be a durable storage solution in production.

**Q (interviewer, drill-down):** Okay. The client's timeout is shorter than your server's processing time, so the retry arrives while the first request is still inside `seatHolds.hold(...)`, before anything has been written to the cache. What happens?
**A (you):** The cache would miss and the hold would be duplicated

**Q (interviewer, drill-down):** Right. How do you close that gap?
**A (you):** Implement a transaction-esque workflow in the cache or migrate to storing the idempotency key in the SeatHolds service. My recommendation is in the SeatHolds service if that a real concern since then a service like Postgres could be used for managing reservations and native transaction support could be used for row locking

> **Strong Principal answer:** Store `(clientId, Idempotency-Key) → {requestHash, state: IN_PROGRESS | COMPLETE, status, body}` with a TTL longer than the client's retry window (say 24h). **Claim the key before doing the work**, with a conditional insert: Redis `SET k v NX PX ttl`, or a unique constraint on `(client_id, idem_key)`. Then call `hold`, then record the response. On a retry: if the entry is `COMPLETE` with the same hash, replay the original `201` and body exactly, even if the hold has since expired. If the hash differs, return **`422` (or `409`) with a ProblemDetail**; the twist required this, and the answer didn't cover it. If the entry is `IN_PROGRESS`, return `409` with `Retry-After`. If the header is missing, return `400`/`428` when the key is mandatory. Scope keys to the authenticated client, so one client can't collide with or replay another's key. The candidate's end state was the strongest option: put the key in the **same Postgres transaction** as the reservation (`reservations.idempotency_key`, unique per client). A crash between "hold written" and "key recorded" can't double-hold, there's no eviction risk, and no extra dependency can fail (see F13).

### F5 · Testing: unit tests for InMemorySeatHolds

**Q (interviewer):** Got it. On to testing.

How would you unit test `InMemorySeatHolds`? Name the first five test cases you'd write.
**A (you):** 1. Single thread happy path for confirm on a single reservation, this would hit all methods
2. Single thread happy path for release on a single reservation, this would hit all methods
3. Single thread expiration scenario, would validate all methods in this one test, available, confirm, release. Note would validate that confirm and release throw. Would pass in a clock implementation that I can fast forward instead of using sleep in my test
4. Multiple thread happy path for confirm+release+expiration (these should probably be individual tests)
5. Multiple thread fuzz test to help quickly explore the testing state space of all possible usage possibilities.

**Q (interviewer, drill-down):** Okay. Take test 5. A fuzz test needs an oracle. After thousands of random `hold`, `confirm`, `release` and clock advances across threads, what exactly do you assert?
**A (you):** Just about everything would be an input parameter which means I can have the fuzz test execution first compute the expected availability there should be at the end of the test. Then the test would instantiate a InMemorySeatHolds, spin up the configured number of threads (which would be a fuzz parameter), let them complete and assert `available()` returns the expected amount. Note since the clock is accelerated instead of sleep call, that makes `available()` deterministic (assuming no race conditions or other logic bugs)

> **Strong Principal answer:** Use plain JUnit 5 with no Spring context and a controllable `Clock` (a mutable test clock, or a new `Clock.fixed` per step). The first five tests pin down the rules, not the happy paths: (1) a hold within capacity reduces `available`; (2) a hold larger than `available` is rejected and `available` doesn't change; (3) the per-customer limit, where 4 is allowed, 5 is rejected, and a test documents whether confirmed seats count; (4) the expiry boundary: still held at `expiresAt − 1ns`, expired at exactly `expiresAt`, `confirm` after that is rejected, and the seats come back; (5) releasing twice, releasing a confirmed hold, and confirming an unknown ID each get a specific rejection. A `@ParameterizedTest` with `@CsvSource` covers the invalid-input matrix (seats ≤ 0, ttl ≤ 0, an unknown workshop). For fuzzing, use property-based tests (jqwik) whose oracle is an **invariant checked after every step**, not a precomputed final count: `0 ≤ available ≤ capacity`, `held + confirmed + available == capacity`, no customer's active holds above the limit, and a confirmed hold never returns seats. Alternatively, compare against a trivially correct single-threaded model. Under concurrency the exact final count depends on scheduling, so a concurrent test asserts invariants, not an expected number.

### F6 · Testing: live functional tests against QA

**Q (interviewer):** Got it. Next one.

Your seat-holds API is deployed to a QA environment, either on Kubernetes or as a Lambda behind API Gateway. Design the automated functional test suite that hits it live. Where does it live, how does it run, and what does it check?
**A (you):** The code for the tests would live in the same maven module as the main app and would be runnable via a maven profile, functional. It could be manually ran from your local but would have its own pipeline that would be triggered after the main CD pipeline finishes deploying the API to QA. The core tests would be the happy path and expiration scenarios I listed above. Fuzzing wouldn't be implemented as a functional test. Along with those cases, other core API validation tests would be implemented for checking bad request handling, idempotency, etc. It would assert http status codes and response body content based on the received code for each respective endpoint

**Q (interviewer, drill-down):** Okay. In your unit tests, the expiration scenario relies on a clock you can fast-forward. Against the deployed QA service, you can't touch its clock. How does the live expiration test work?
**A (you):** For simplicity it would rely on sleep and overshoot it by a second or so. A more sophisticated approach would be looking into leveraging Fault Injection Testing where a header would carry test context that would tell the API to fail it as if its expired.

**Q (interviewer, drill-down):** Got it. Two pipelines run this suite against QA at the same moment, and your tests assert on `available` for a workshop. How do the two runs avoid stepping on each other's data?
**A (you):** Since I'm the owner of the service and am assuming to be the owner of the SeatHolds data store, I would have the tests generate a fresh workshop during each run. Note this would then require a clean up process to be implemented but helps avoid collisions due to common ids

> **Strong Principal answer:** **Structure:** a separate `src/functionalTest` source set or module, built and run apart from the unit tests, black-box over HTTP only (REST Assured + JUnit 5), with no access to internals. **Config:** `BASE_URL` and the environment come from env vars, so the same suite runs against dev, QA and per-PR namespaces. **Auth:** a real token from an OAuth2 client-credentials test client, with secrets from a secrets manager, plus explicit 401, 403 and 404 cases (Alice can't touch Bob's hold). **Data:** every run creates its own workshop through the API (or a test-only seeding path, since there's no public create) with a run-ID prefix, cleans up in teardown, and relies on a TTL janitor for leaks. Two pipelines then never share a workshop's `available`. **Determinism:** no fixed `Thread.sleep`. For expiry, create a hold with the shortest TTL that QA config allows (say 2s) and poll with Awaitility until it shows EXPIRED or the seats are back, with a timeout. Don't add a request header that forces expiry: if prod ever honors it, that's a security hole. Flaky tests are quarantined and tracked, and a failure captures the request, the response and the trace ID. **Pipeline:** a stage after deploy and the rollout/readiness check that **gates promotion** to the next environment, with JUnit XML into Jenkins and trends. **Scope:** critical journeys (hold → confirm, hold → release, hold → expire, sold out → 409, an idempotent retry returning the same holdId, BOLA → 404) and the ProblemDetail shapes. The logic matrix belongs in unit tests. **Complements:** `@SpringBootTest` with real wiring in-process, consumer-driven contract tests (Pact or Spring Cloud Contract) to catch breaks *before* deploy, read-only prod smoke tests, and synthetic monitoring. On k8s, wait for the rollout first. On Lambda, go through the API Gateway URL so mappings and authorizers are covered, and allow for cold starts.

### F7 · Testing: proving the hold race is fixed

**Q (interviewer):** Okay. Last testing question.

Say you've fixed the race in `hold` that you described, where two customers both pass the availability check. How do you *prove* it's fixed with a test?
**Q (you):** Is this meant to be a functional test or unit test? Can I access the private state of InMemorySeatHolds?
**A (interviewer):** That's your call. Pick whichever level you think proves it best, and tell me why. On private state, do whatever you'd do in your real codebase.

**A (you):** So to trigger the race you need concurrent requests but that is always non-deterministic so without changing InMemorySeatHolds to possibly gauruntee it could never happen via its API, I believe the best way to approach proof is with the fuzz test execution. Note I don't have any proposals for a better SeatHolds API at the moment but suspect adding a language level paradigm like `synchronized` would at least help boost confidence. Regardless its the concurrent calling that introduces the non-determinism so I would want someway to inject the map storage so I could provide a implementation that logs all writes to the map. Then I'd validate that only 1 write ever happens since the second thread would fail the available seats check. I'm still not convinced this would gauruntee it but would help with validating in the fuzz test runs

**Q (interviewer, drill-down):** Okay. If you start two threads normally, the first one usually finishes `hold` before the second one even begins, so the race never triggers. How would you make sure many threads reach the availability check at the same moment?
**A (you):** Like I mentioned, `synchronized` could be put on the method or a mutex could be grabbed at the beginning of the method and release at the end. Thus ensuring only 1 thread can invoke `hold` at a time

> **Strong Principal answer:** The test isn't the fix, and it shouldn't need private state; assert through the public API. Use a workshop with capacity 10, an `ExecutorService` with 100 threads and a **`CountDownLatch start = new CountDownLatch(1)` as the start gate**. Each task calls `start.await()`, then `hold("k8s-101", "c" + i, 1, ttl)`, and records success or rejection. Call `start.countDown()` so they all hit the check together, wait on a `done` latch, then assert exactly 10 successes, 90 rejections and `available == 0`. Run it under `@RepeatedTest(500)`, because it's probabilistic: a pass is evidence, not proof. Run it against today's code first to watch it fail, which shows that the test can catch the bug. jcstress covers memory-model-level checks. For the Postgres version, run the same test against a real database via Testcontainers, plus a functional test that fires concurrent requests at QA and asserts no oversell.

### F8 · Security: who can release a hold

**Q (interviewer):** Got it. Let's move to security.

`AddReservationRequest` takes `customerId` from the request body. Confirm and release take only a `holdId`, and those IDs look like `hold-1`, `hold-2` and so on. Who is allowed to call `DELETE /v1/workshops/{workshopId}/reservations/{holdId}`, and where in your code is that enforced?
**A (you):** The customer should be allowed to call DELETE but its currently no validated anywhere. Also it wasn't asked earlier nor in the requirements but instead of passing customer id, I'd prefer to leverage a access token for identity management. This makes the assumption that no one is allowed to reserve holds for another user which I think is a fair assumption

**Q (interviewer, drill-down):** Okay, so you'd move identity to an access token. Alice has a valid token, holds `hold-42`, and calls `DELETE /v1/workshops/k8s-101/reservations/hold-43`, which is Bob's hold. Walk me through what happens and what she gets back.
**A (you):** The SeatHolds service would need to be updated to also perform data auth which would then cause an exception thrown when Alice tries to release Bob's hold. This would then cause the API to return a 401 since Alice doesn't have authority over Bob's data

> **Strong Principal answer:** A Spring Security OAuth2 resource server validates the JWT (signature via JWKS, issuer, audience, expiry). The customer comes from the token's `sub`, never from the body, so `AddReservationRequest.customerId` goes away. Coarse authz uses scopes (`holds:write`) via `requestMatchers` or `@PreAuthorize`. **Object-level authorization** (BOLA, OWASP API #1) lives in the service: load hold-43, compare its owner with the caller, and on a mismatch return **`404`**, the same as for a hold that doesn't exist, so Alice can't learn it exists. `401` means unauthenticated (no token, or a bad one), which Alice isn't, and `403` would confirm the hold exists. Sequential `hold-N` IDs make enumeration trivial, so use UUIDs or ULIDs, but as defense in depth, not authz. Also check that the hold belongs to the `{workshopId}` in the path, which the controller currently ignores.

### F9 · Security: hostile input on hold creation

**Q (interviewer):** Got it. Next one.

`AddReservationRequest` has `seats` and `ttl` with comments saying "set appropriate min/max", but no constraints on them yet. What can a malicious client send to `POST /v1/workshops/{workshopId}/reservations`, and what should stop it?
**A (you):** negative values could be sent and in the case of `seats` that would cause `available` to grow greater than `capacity`. This would be stopped by a `@Min` annotation.

**Q (interviewer, drill-down):** Okay. Say `seats` now has `@Min(1)`. What can a client do with a perfectly valid seat count and a `ttl` it chooses itself?
**A (you):** If no max is placed on `ttl` then the client would be able to make a reservation that would effectively never expire before the workshop is held

> **Strong Principal answer:** Put constraints on every field: `@NotNull @Min(1) @Max(<maxSeatsPerCustomer>)` on `seats`, and `@Valid @RequestBody` on the parameter. For `ttl`, the strongest answer is **don't let the client choose it**: the server sets the hold window (say 10 minutes), or clamps a requested value to a range. Without that, one request holds the whole workshop until it runs, which is inventory denial of service. Even with valid values, a script with many accounts can re-hold every seat every 10 minutes (seat scalping), so add a cap on concurrent active holds per account, per-client rate limiting (429 with `Retry-After`) and bot detection at the edge. Also: body size limits, rejecting unknown fields (`spring.jackson.deserialization.fail-on-unknown-properties=true`), no default polymorphic typing, and errors without internals (`server.error.include-stacktrace=never`).

### F10 · Observability: reconstructing what happened to a hold

**Q (interviewer):** Got it. On to observability.

Alice contacts support and says her confirm failed yesterday afternoon, and now her seats are gone. What do your logs let you reconstruct about what happened to her hold?
**A (you):** Currently there is no logging but I would enable request/response logging (not the raw content but select headers, the path, method, at minimum) along with logging the caught exception in the ExceptionHandler. MDC would also be configured to inject things like workspaceId, holdId, customerId so that logging is invoked those are attached. This would allow me to search by the customerId or holdId and then see what concrete exception was encountered.

**Q (interviewer, drill-down):** Okay. In your design, a hold expires without any request or code running. It just stops passing the `isBefore(expiresAt)` filter. When support looks up Alice's `holdId`, what in the logs shows that her hold expired, and when?
**A (you):** The threads that increment and decrement the availability based on expiration sleep timers would log her customerId and holdId.

> **Strong Principal answer:** Use structured JSON logs (Boot 3.4 `logging.structured.format.console`), with trace and correlation IDs on every line through MDC, populated by Micrometer Tracing. MDC is thread-local, so copy it onto executor threads. Log decisions at the boundary: hold created (holdId, workshopId, seats, expiresAt), confirmed, released, and rejected with a reason. The code's expiry is lazy, so **no event happens** when a hold expires. Make expiry a real transition: a sweeper, or the lazy drain inside `hold`/`available`, marks the hold EXPIRED, logs it and persists it. Better still, keep an append-only **audit trail** of hold state transitions (created, confirmed, released, expired, with timestamp and actor), so support can query "what happened to hold X" without searching logs. Log an opaque internal customer ID, never an email or a name, and set a retention period. The advice logs each exception once, with 4xx at INFO or WARN and 5xx at ERROR.

### F11 · Observability: metrics and paging

**Q (interviewer):** Got it. Next one.

What metrics should the seat-holds service emit, and which of them should page someone?
**A (you):** The main metric I would add is a up-down counter for reservation holds where workshopId would be a attribute. It would be incremented and decremented by the number of seats in the reservations. Beyond just the SeatHolds service, I'd enable the standard HTTP OTEL metrics and those would be what I page on based on http status code.

**Q (interviewer, drill-down):** Okay. You've tagged that counter with `workshopId`, and the company runs about 20,000 workshops a year. Would you keep that tag? Would you also add `customerId`?
**A (you):** I'm expecting customerId to be a factor higher so that would be too high cardinality. The 20,000 per year could be doable but would need to performance test which ever metric storage is selected. I would also need to weigh it against purging of old metrics, especially if older metrics are transferred to a data lake before purging. I would also need to consider workshop sizes. If workshops are always small then monitoring per workshop spikes probably isn't necessary and a overall reservation spike monitoring would only be needed. Thus allowing the workshopId attribute to be dropped.

> **Strong Principal answer:** Per-endpoint RED metrics come from `http.server.requests` (Actuator + Micrometer) with percentile histograms. Business metrics are `Counter`s for holds created, confirmed, released and expired; rejections tagged by a bounded `reason` (`sold_out`, `customer_limit`, `validation`); hold-to-confirm conversion; and idempotent replays. Add JVM, Tomcat thread and Hikari pool metrics. **Page on symptoms against SLOs**: 5xx rate and p99 burn rate on hold and confirm. Never page on 4xx: 409 sold-out responses are *expected* during a launch, so "page on HTTP status code" needs exactly that distinction. Cardinality: the candidate rightly kept `customerId` out. `workshopId` grows without bound too (new IDs every year), so keep tags to bounded sets (endpoint, outcome, reason) and answer per-workshop questions from the database, logs or traces.

### F12 · Resilience & scale-out: three replicas

**Q (interviewer):** Got it. Last category: resilience and scale-out.

We now run the service as 3 replicas behind a load balancer, and each replica has its own `InMemorySeatHolds`. What breaks?
**A (you):** Unless content based load balancing is used, any request could go to any replica which would then lead to possible duplicates or 404s

**Q (interviewer, drill-down):** Okay. Say you move the state out of the JVM instead of routing around it. Where does it go? And how do you guarantee that two replicas handling `hold` for the last 3 seats of `k8s-101` at the same moment can't oversell?
**A (you):** Postgres, with a transaction that row-locks and I'd make 2 tables: Workshops + Reservations where Reservations has a column to reference the workshop it belongs to. This would allow locking just a single reservation when performing updates

**Q (interviewer, drill-down):** Got it. Both replicas are *creating* a new reservation for `k8s-101`, so neither reservation row exists yet. In that transaction, which row gets locked, and what stops both inserts from committing?
**A (you):** If I recall the transaction insert would take a write lock for the entire page or its the atomic execution engine in postgres that pipelines the transactions sequentially. I'm blanking on the exact phrasing in Postgres but its something like "ON SKIP LOCKED DO NOTHING" which tells the transaction that if a record with that primary key already exists then do nothing.

> **Strong Principal answer:** Each replica keeps its own copy of the state. Each one believes it has the full capacity, so a workshop can be oversold up to 3×. A hold created on A returns 404 on B, and everything disappears on a deploy. Sticky or content-based routing doesn't fix this: it breaks on scale-out and deploys, skews load, and still loses state on restart. Externalize the state to Postgres. To hold the invariant **on insert**, you can't lock rows that don't exist yet, so **lock the parent**: `SELECT … FROM workshops WHERE id = ? FOR UPDATE`, sum the active held and confirmed seats, insert, commit. That serializes holds for one workshop only. Alternatively, run a **conditional update** on a counter, `UPDATE workshops SET held = held + :n WHERE id = :id AND capacity - held - confirmed >= :n`, and check the row count (1 means held, 0 means sold out), then insert the reservation in the same transaction. Or use `@Version` optimistic locking on `Workshop`, mapping `ObjectOptimisticLockingFailureException` to a retry or a 409, never a 500. `FOR UPDATE SKIP LOCKED` is for work queues (it suits the expiry sweeper), and `INSERT … ON CONFLICT DO NOTHING` only dedupes on a unique key, which suits the idempotency key but not capacity. The counter needs expiry to decrement it: a scheduled sweeper with ShedLock, so only one replica runs it, or compute held seats as `SUM(seats) WHERE status = 'HELD' AND expires_at > now()` under the workshop lock. A popular workshop becomes a hot row, which is fine at seat-level volumes. In DynamoDB, the same idea is a conditional write with a `ConditionExpression`.

### F13 · Resilience & scale-out: a slow idempotency cache

**Q (interviewer):** Okay. Last thread.

Your controller now calls the external idempotency cache on every hold request. One afternoon that cache gets slow, and its p99 climbs to 3 seconds. What happens to *your* service?
**A (you):** It depends but per best practices, I'd have the cache service behind a circuit breaker in the code (note retries would probably be skipped due to the purpose of the cache service should be speed but this is requires real performance monitoring to decide). Along with the circuit breaker there would be a client timeout, if the timeout < 3 seconds then the circuit breaker would trip and all requests would fail. If timeout > 3 seconds, then our API would take longer and most likely the upstream client has a timeout which would lead to them retrying our service.

**Q (interviewer, drill-down):** Got it. With the breaker open, every hold request fails, so nobody can buy a seat while the cache is degraded. The alternative is to keep creating holds without the idempotency check and accept some duplicates. Which would you choose, and how would you decide?
**A (you):** It all depends on the `confirm` process. If the confirm process is automated by another system and low frequency, then duplicates could be cleaned up automatically. If the confirm is invoked by a human (e.g. the workshop instructor) the the unpredictability would probably be better to be avoided.

> **Strong Principal answer:** Before the breaker trips, every hold request waits on the cache, and Tomcat's 200 threads fill up with blocked calls. Then `available` and `confirm` stall too, even though they never touch the cache: a cascading failure. Set a **tight timeout** on the cache call (tens of milliseconds, well inside the hold endpoint's budget), and use a Resilience4j `CircuitBreaker` plus a **`Bulkhead`** so cache calls can't take every thread. Don't retry into a slow dependency, or do it only with backoff, jitter and a budget. Decide the fallback with the product owner **ahead of time**, not mid-incident. For holds, duplicates are bounded and self-healing, because they expire with the TTL and the per-customer limit caps them, so **failing open** (creating holds without the dedupe) is usually right, with a metric and an alert on the bypass. `confirm` should fail closed. The better design removes the dependency: make the idempotency key a unique column in the same Postgres transaction (F4), so there's no separate cache that can be slow.

## Debrief notes

### Clarifying questions worth asking

- **Do confirmed seats count toward `maxSeatsPerCustomer`, or only active holds?** The statement deliberately leaves this open, and it decides what the limit check sums. The rule wasn't implemented at all, so asking would also have kept it on the checklist.
- **Is a hold still valid at exactly `expiresAt`?** The code chose `isBefore` (expired at `expiresAt`), which is reasonable, but it was never stated. Boundaries like this need an explicit decision and a test.
- **What should confirming or releasing twice do: succeed idempotently or be rejected?** This shapes both the domain API and the Part 2 status codes, and today a second release throws an NPE.
- **What happens for an unknown workshop?** Today `available` and `hold` throw an NPE, which becomes a 500.
- **What are the valid ranges for `seats` and `ttl`, and should the client choose the TTL at all?** It came up only in F9. A server-owned hold window closes an inventory-DoS hole.
- **What are the volumes: seats per workshop, and concurrent buyers during a launch?** The answer decides whether O(R) scans are fine and how much locking matters. The candidate asked this well in F1, but only after coding.
- **Part 2: how are customers identified, and who may confirm or release a hold?** It's the BOLA question, raised only when security came up.
- **Part 2: on an idempotent retry after the hold has expired, does the client get the original 201 again?** It decides whether you store just the holdId or the full response.
