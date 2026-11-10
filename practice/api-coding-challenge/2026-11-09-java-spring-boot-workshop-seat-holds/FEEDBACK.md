# Feedback: Workshop Seat Holds

**Verdict (Principal bar): No Hire**

Against the Principal bar this reads as a capable Senior engineer: many instincts were right, but they came after probing, and the code didn't satisfy its own rules. The best signal came from design reasoning in the follow-ups. You asked for the traffic pattern before naming a hot path, moved the idempotency key into the transactional store to remove the eviction risk, and reasoned about metric cardinality. The biggest risk is correctness around shared state. Part 1 skipped the per-customer limit and has a bug that confirms or releases the wrong hold. The oversell race came out only after a drill-down, and the proposed Postgres fix for it wouldn't stop an oversell.

## Timeline

| Phase | Actual | Target |
|---|---|---|
| Part 1 | 53 min | 20–25 |
| Part 2 | 33 min | 20–25 |
| Follow-ups | ~133 min | 15–20 |
| **Total** | 224 min | 55–70 |

Hints used: 0. There was one Part 1 check: the candidate described what the code was meant to do, but didn't trace what it actually does, so the confirm/release index bug went unnoticed.

## Scorecard

| Dimension | Score | Gist |
|---|---|---|
| Problem solving & correctness | 1 | The per-customer limit isn't implemented, `confirm`/`release` act on the wrong reservation once an earlier hold has expired, and a double release or unknown workshop throws an NPE. Part 1 took 53 min. |
| Code quality & modeling | 2 | A sealed `ReservationStatus` with records and an injected `Clock` are good instincts. `Confirmed` drops the hold's identity, data is mutated by list index, there's no holdId index, and the lookup helpers are near-duplicates. |
| Spring Boot implementation | 1 | The layering and constructor injection are sound, but the twist isn't coded, the advice is only comments (no `ProblemDetail`), creates return 200, "look up a hold" was skipped, and the main request DTO has no constraints. |
| Communication | 2 | Honest about uncertainty, and took pushback well (revised the status codes). Only one clarifying question. The walkthrough left out complexity, and some answers addressed a different question (F7, F10). |
| Performance | 2 | Mostly correct complexity, and asked about traffic first. The O(1) idea used a thread per hold. The check-then-act race needed a drill-down, and the HashMap risk was framed as contention rather than correctness. |
| API design & versioning | 2 | Fixed 201/204 on reflection and argued PUT/DELETE reasonably. Mapped sold out to 400/422 or even 500. The idempotency design started with a lookup-then-act race and missed the different-payload rule, but ended at storing the key with the reservation. |
| Testing (unit + functional) | 2 | An injectable clock, plus a separate post-deploy pipeline with a fresh workshop per run. Unit cases were happy-path heavy, the fuzz oracle doesn't hold under concurrency, live expiry relied on `sleep`, and the race test had no start gate. |
| Security | 2 | Spotted the missing authz and moved identity to a token, with the ownership check in the service. Answered 401 for BOLA (it should be 404). Caught the negative-seat and unbounded-TTL abuse, but didn't raise rate limiting or guessable IDs. |
| Observability | 2 | Request logging, MDC keys and good cardinality reasoning. Expiry logging relied on the hypothetical thread-per-hold design, and paging "on HTTP status code" would page on normal 409s. |
| Resilience & scale-out | 2 | Picked Postgres and a circuit breaker with a timeout. The capacity invariant was enforced on the wrong row (with SKIP LOCKED and ON CONFLICT mixed up), thread exhaustion wasn't mentioned, and the fail-open vs fail-closed reasoning was indirect. |

Not assessed: none

## Code review

### Strengths
- `ReservationStatus` is a sealed interface over records (`InMemorySeatHolds.java:7-13`), so the states are a sum type rather than flags.
- The `Clock` is injected and expiry is evaluated lazily against it (`InMemorySeatHolds.java:19-22`, `:59`), which makes expiry testable without sleeps. You used this in F5.
- The controller depends on the `SeatHolds` interface through constructor injection (`InMemorySeatHoldController.java:5-11`), with record DTOs, a versioned `/v1` path and server-generated ULIDs.
- You asked whether a confirmed hold can be released, which is one of the rules a strong candidate pins down.
- In the follow-ups you asked about traffic before naming a hot path (F1), moved the idempotency key into the reservation store (F4), and weighed metric cardinality (F11).

### Issues

| Severity | Location | Issue | Better approach |
|---|---|---|---|
| High | `InMemorySeatHolds.java:69-72`, `:107-108` | The index comes from the filtered `activeReservations` list but is used on the full `workshop.reservations` list. After an earlier hold expires, `confirm` confirms the wrong reservation and `release` removes the wrong one. | Keep a `Map<String, Hold>` keyed by holdId, and look up and update holds by ID, never by position. |
| High | `InMemorySeatHolds.java:4`, `:21`, `:28-46` | `maxSeatsPerCustomer` is stored but never checked, so one of the stated rules is missing. | Before holding, sum the customer's active seats in that workshop (and state whether confirmed seats count), then reject if over the limit. |
| High | `InMemorySeatHolds.java:17`, `:29-43`, `:38` | A singleton keeps a `HashMap` and lists and runs check-then-act in `hold`. Concurrent requests oversell, lose writes and can throw `ConcurrentModificationException`, and `holdCounter += 1` produces duplicate IDs. | Make check-and-write atomic per workshop (`compute` over an immutable `Workshop`, or a per-workshop lock), and use an `AtomicLong` or UUIDs for IDs. |
| High | `InMemorySeatHoldController.java:38-42`, `:62-81`; `InMemorySeatHolds.java:39` | `customerId` comes from the request body, confirm and release have no ownership check, and IDs are sequential. Anyone can hold seats as someone else, or confirm or release their holds (BOLA). | Take identity from the JWT `sub`, check ownership in the service, return 404 on a mismatch, and use random IDs. |
| High | `InMemorySeatHoldController.java:47-53` | The `Idempotency-Key` twist isn't implemented, so a retried hold takes seats twice. | Claim `(client, key)` with a conditional insert before holding, store the request hash and response, replay on a match, return 422 on a different payload, and keep the key in the reservation's transaction. |
| Medium | `InMemorySeatHoldController.java:84-91` | The advice is a stub, and the plan maps domain rejections to 500. | `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`, returning `ProblemDetail`: 409 for sold out or over the limit, 404 for unknown or not-yours, 409 or 410 for expired. |
| Medium | `InMemorySeatHoldController.java:25`, `:52`, `:68`, `:80` | Both creates return 200, and confirm and release return 200 with an empty record. | Return 201 with `Location` on create and 204 on empty responses. |
| Medium | `InMemorySeatHoldController.java:38-42`, `:50` | `seats` and `ttl` have no constraints and `@RequestBody` is missing. Negative seats push availability above capacity, and an unbounded TTL ties up the whole workshop. | `@NotNull @Min(1) @Max(limit)` on `seats`. Set the TTL on the server, or clamp it. |
| Medium | `InMemorySeatHolds.java:9`; `InMemorySeatHoldController.java:55-57` | `Confirmed` drops the holdId, so a confirmed hold can't be found, and the "look up a hold" capability was skipped. | Keep the ID on the hold with the status as a field, and add `find(holdId)` to `SeatHolds`. Extending Part 1 for a new capability is fine. |
| Medium | `InMemorySeatHolds.java:49`, `:105-109` | An unknown workshop, or a second release, causes an NPE, which becomes a 500. | Throw domain exceptions (`WorkshopNotFound`, `HoldNotFound`) that map to 404. |
| Medium | `InMemorySeatHolds.java:53-62`, `:75-86` | Expired holds are never removed, so memory grows without bound. `available` is O(R) per call, and `confirm`/`release` scan every workshop, which is O(W·R). | Index holds by ID, keep per-workshop counters, and drain expiries lazily from a min-heap ordered by `expiresAt`. |
| Medium | `InMemorySeatHoldController.java:62-81` | The `{workshopId}` path variable is ignored, so `/workshops/B/reservations/{a hold in A}` works. | Check that the hold belongs to the workshop in the path, or make holds a top-level resource. |
| Low | `InMemorySeatHolds.java:3-4`, `:59`, `:69`/`:88`/`:107` | The fields aren't `final`, `clock` is read once per element (use `clock.instant()` once per call), and three lookup helpers have mismatched names. | Make the fields `final`, read `now` once, and use one `findHold(holdId)` helper. |

## Follow-ups

Each question, your verbatim answer, and a strong Principal answer are in [TRANSCRIPT.md](TRANSCRIPT.md#follow-ups). Clarifying questions worth asking are in [Debrief notes](TRANSCRIPT.md#debrief-notes).

## Study before the real loop

1. **Invariants on shared state in a singleton.** This was the root of the High issues in Part 1 and of threads F2, F7 and F12. Practice rewriting `hold` with `ConcurrentHashMap.compute` over an immutable `Workshop`. Then write a 100-thread test with a `CountDownLatch` start gate under `@RepeatedTest`, and watch it fail on the current code before it passes on the fix.
2. **Enforcing capacity in Postgres.** Write the SQL for hold, confirm and expire three ways: `SELECT … FOR UPDATE` on the workshop row, a conditional `UPDATE … WHERE capacity - held - confirmed >= :n` with a row-count check, and `@Version`. Learn what `SKIP LOCKED` (work queues) and `ON CONFLICT` (unique-key dedupe) are actually for.
3. **HTTP semantics and the error contract.** Use 409 for state conflicts, 404 for another user's resource, 401 vs 403 correctly, and never 5xx for a business rule. Read RFC 9457 (Problem Details) and the IETF `Idempotency-Key` draft. Then sketch the full idempotency protocol from memory: claim first, request hash, replay, in-progress, and a different payload.

Pacing also cost you: Part 1 took twice its budget. Use the first 3–4 minutes to list every rule and ambiguous case from the statement as a checklist, and ask about them. Implement every rule before you polish anything.
