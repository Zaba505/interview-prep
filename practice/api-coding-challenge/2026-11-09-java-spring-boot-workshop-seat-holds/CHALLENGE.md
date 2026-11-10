# Workshop Seat Holds

| | |
|---|---|
| Skill | api-coding-challenge |
| Language | java |
| Framework | spring-boot |
| Shape | Stateful component |
| Started | 2026-11-09 15:15 EST |
| Start epoch | 1794255339 |

## Ground rules

- Write your Java in this folder, with any file names and layout you like.
- Pseudo-syntax is fine. You don't need imports, a package declaration, `main` or build files; go straight to classes and methods.
- Minor syntax slips don't matter. Records, Lombok and `// assume X exists` stubs are all fine.
- Ask clarifying questions at any time; asking them is part of the exercise.
- Say **"part 1 done"** when you're ready to move on. Ask for a hint if you're stuck; hints are noted in the debrief, as they would be on a real panel.

## Part 1: Seat holds (~20–25 min)

A training company sells seats in instructor-led workshops. During checkout, the storefront places a temporary hold on seats so nobody else can buy them while the customer pays. Build the in-memory component that manages those holds.

- Each workshop has a fixed seat capacity, registered with `addWorkshop(workshopId, capacity)`.
- `hold(workshopId, customerId, seats, ttl)` reserves `seats` for `ttl` and returns a hold ID. It's rejected if there aren't enough seats available.
- A customer may hold at most `maxSeatsPerCustomer` seats in a single workshop. The limit is set when the component is created (4 in the example below).
- `confirm(holdId)` turns a hold into a permanent enrollment; its seats are never returned.
- `release(holdId)` cancels a hold and returns its seats.
- A hold that isn't confirmed before its TTL runs out expires, and its seats go back to the pool.
- `available(workshopId)` returns how many seats can still be held right now.
- Anything time-based uses an injected `java.time.Clock`.
- How you signal a rejected operation is up to you; change the return types in the sketch if your approach needs it.

### Example

`maxSeatsPerCustomer = 4`, clock at `2026-11-09T09:00:00Z`, `addWorkshop("k8s-101", 10)`. Hold IDs are illustrative.

| Time | Call | Result | `available("k8s-101")` after |
|---|---|---|---|
| 09:00 | `hold("k8s-101", "alice", 3, Duration.ofMinutes(10))` | `"h-1"` | 7 |
| 09:00 | `hold("k8s-101", "bob", 4, Duration.ofMinutes(10))` | `"h-2"` | 3 |
| 09:01 | `confirm("h-1")` | ok | 3 |
| 09:02 | `hold("k8s-101", "carol", 5, Duration.ofMinutes(10))` | rejected: over the per-customer limit | 3 |
| 09:02 | `hold("k8s-101", "dave", 4, Duration.ofMinutes(10))` | rejected: only 3 seats available | 3 |
| 09:12 | `available("k8s-101")` (bob's hold has expired) | 7 | 7 |

### Interface sketch

```java
public interface SeatHolds {
    void addWorkshop(String workshopId, int capacity);
    String hold(String workshopId, String customerId, int seats, Duration ttl);
    void confirm(String holdId);
    void release(String holdId);
    int available(String workshopId);
}

// e.g. new InMemorySeatHolds(Clock clock, int maxSeatsPerCustomer)
```

## Part 2: Expose it as a Spring Boot REST API (~20–25 min)

_Revealed at 16:13._

Wrap your Part 1 code in a Spring Boot REST API. Keep the Part 1 logic reusable: the API should be a layer around it, not a rewrite.

### Clients need to be able to

- place a hold on some seats in a workshop for a customer, for a limited time, and get back a way to refer to that hold later
- look up a hold and see its current state, including whether it has been confirmed or has expired
- confirm a hold, turning it into an enrollment
- release a hold they no longer need
- see how many seats a workshop has available right now

### Requirements

- Request and response DTOs, so domain types aren't exposed directly
- Input validation with meaningful 4xx responses
- A consistent error response format
- Appropriate status codes
- **The storefront runs on flaky mobile networks, and clients retry requests that time out. Clients send an `Idempotency-Key` header when placing a hold. A retried request with the same key must never take seats twice, and it gets back the same result as the original. Reusing a key with a different request is a client error.**

### Not needed

Build files, a real database (an in-memory store behind a repository interface is fine), `application.yml`, full Spring Security configuration, or an API for creating workshops (seed them at startup).
