# Reference solution: Workshop Seat Holds

This solution follows the same ground rules as the session: pseudo-syntax is fine, and imports, `main` and build files are left out. It's one Principal-quality answer, not the only one. Where the statement left a decision open, the assumption is stated next to the code.

**Assumptions** (each should be confirmed with the interviewer up front):
- The per-customer limit counts **active holds** only. Counting confirmed seats too would be the anti-scalping variant, and it changes just one line.
- A hold is expired **at** `expiresAt`, not after it (`!now.isBefore(expiresAt)`).
- `release` is idempotent: releasing a hold that's already released or expired is a no-op. Releasing a confirmed hold is a conflict.
- `confirm` is idempotent for a hold that's already confirmed. Confirming an expired or released hold is a conflict.
- Terminal holds stay readable, so clients can see *why* a hold is gone.

## Part 1: domain (plain Java)

**Complexity:** `available`, `confirm`, `release` and `find` are O(1), and `hold` is O(log H), all amortized. Each hold is expired exactly once, at O(log H), by a lazy drain from a min-heap at the start of every operation. No threads or timers are needed. Space is O(H).

**Concurrency:** state is partitioned per workshop, and each workshop is guarded by its own monitor. Every check-then-act (capacity, the customer limit, status transitions) is atomic within one workshop, and different workshops never contend.

```java
public enum HoldStatus { HELD, CONFIRMED, RELEASED, EXPIRED }

public record Hold(String id, String workshopId, String customerId, int seats,
                   Instant expiresAt, HoldStatus status) {
    Hold withStatus(HoldStatus next) {
        return new Hold(id, workshopId, customerId, seats, expiresAt, next);
    }
}

public interface SeatHolds {
    void addWorkshop(String workshopId, int capacity);
    Hold hold(String workshopId, String customerId, int seats, Duration ttl);
    Hold confirm(String holdId);
    Hold release(String holdId);
    Optional<Hold> find(String holdId);     // added for Part 2's "look up a hold"
    int available(String workshopId);
}

// Domain rejections. Sealed, so the HTTP mapping's switch is checked for exhaustiveness.
public abstract sealed class SeatHoldException extends RuntimeException
        permits WorkshopNotFound, HoldNotFound, InsufficientSeats, CustomerLimitExceeded, HoldNotActive {
    SeatHoldException(String message) { super(message); }
}
public final class WorkshopNotFound extends SeatHoldException { /* workshopId */ }
public final class HoldNotFound extends SeatHoldException { /* holdId */ }
public final class InsufficientSeats extends SeatHoldException { final int requested, available; /* ctor */ }
public final class CustomerLimitExceeded extends SeatHoldException { final int limit, alreadyHeld; /* ctor */ }
public final class HoldNotActive extends SeatHoldException { final HoldStatus status; /* ctor */ }

public final class InMemorySeatHolds implements SeatHolds {
    private final Clock clock;
    private final int maxSeatsPerCustomer;
    private final ConcurrentHashMap<String, Ledger> ledgers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Ledger> ledgerByHold = new ConcurrentHashMap<>();

    public InMemorySeatHolds(Clock clock, int maxSeatsPerCustomer) {
        this.clock = clock;
        this.maxSeatsPerCustomer = maxSeatsPerCustomer;
    }

    @Override public void addWorkshop(String workshopId, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        if (ledgers.putIfAbsent(workshopId, new Ledger(capacity)) != null)
            throw new IllegalStateException("workshop already exists: " + workshopId);
    }

    @Override public Hold hold(String workshopId, String customerId, int seats, Duration ttl) {
        if (seats < 1) throw new IllegalArgumentException("seats must be >= 1");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        Ledger l = ledger(workshopId);
        synchronized (l) {                                    // per-workshop critical section
            Instant now = clock.instant();
            l.expireUpTo(now);
            int alreadyHeld = l.heldByCustomer.getOrDefault(customerId, 0);
            if (alreadyHeld + seats > maxSeatsPerCustomer)
                throw new CustomerLimitExceeded(maxSeatsPerCustomer, alreadyHeld);
            if (seats > l.available())
                throw new InsufficientSeats(seats, l.available());
            Hold h = new Hold(UUID.randomUUID().toString(), workshopId, customerId, seats, now.plus(ttl), HoldStatus.HELD);
            l.add(h);
            ledgerByHold.put(h.id(), l);
            return h;
        }
    }

    @Override public Hold confirm(String holdId) { return transition(holdId, HoldStatus.CONFIRMED); }
    @Override public Hold release(String holdId) { return transition(holdId, HoldStatus.RELEASED); }

    @Override public Optional<Hold> find(String holdId) {
        Ledger l = ledgerByHold.get(holdId);
        if (l == null) return Optional.empty();
        synchronized (l) {
            l.expireUpTo(clock.instant());
            return Optional.of(l.holds.get(holdId));
        }
    }

    @Override public int available(String workshopId) {
        Ledger l = ledger(workshopId);
        synchronized (l) {
            l.expireUpTo(clock.instant());
            return l.available();
        }
    }

    private Hold transition(String holdId, HoldStatus target) {
        Ledger l = Optional.ofNullable(ledgerByHold.get(holdId)).orElseThrow(() -> new HoldNotFound(holdId));
        synchronized (l) {
            l.expireUpTo(clock.instant());
            Hold h = l.holds.get(holdId);
            boolean noOp = h.status() == target
                    || (target == HoldStatus.RELEASED && h.status() == HoldStatus.EXPIRED);
            if (noOp) return h;                                // idempotent repeat
            if (h.status() != HoldStatus.HELD) throw new HoldNotActive(h.status());
            l.unhold(h);
            if (target == HoldStatus.CONFIRMED) l.confirmed += h.seats();
            Hold next = h.withStatus(target);
            l.holds.put(holdId, next);
            return next;
        }
    }

    private Ledger ledger(String workshopId) {
        Ledger l = ledgers.get(workshopId);
        if (l == null) throw new WorkshopNotFound(workshopId);
        return l;
    }

    /** One workshop's state. Every field is guarded by the Ledger's own monitor. */
    private static final class Ledger {
        final int capacity;
        int held, confirmed;
        final Map<String, Hold> holds = new HashMap<>();
        final Map<String, Integer> heldByCustomer = new HashMap<>();
        final PriorityQueue<Hold> byExpiry = new PriorityQueue<>(Comparator.comparing(Hold::expiresAt));

        Ledger(int capacity) { this.capacity = capacity; }

        int available() { return capacity - held - confirmed; }

        void add(Hold h) {
            holds.put(h.id(), h);
            byExpiry.add(h);
            held += h.seats();
            heldByCustomer.merge(h.customerId(), h.seats(), Integer::sum);
        }

        void unhold(Hold h) {
            held -= h.seats();
            heldByCustomer.computeIfPresent(h.customerId(), (c, n) -> n == h.seats() ? null : n - h.seats());
        }

        /** Lazy expiry: pop holds whose time is up, skipping any already confirmed or released. */
        void expireUpTo(Instant now) {
            while (!byExpiry.isEmpty() && !now.isBefore(byExpiry.peek().expiresAt())) {
                Hold current = holds.get(byExpiry.poll().id());
                if (current.status() == HoldStatus.HELD) {
                    unhold(current);
                    holds.put(current.id(), current.withStatus(HoldStatus.EXPIRED));
                }
            }
        }
    }
}
```

A caveat on `synchronized`: before JDK 24, a virtual thread pins its carrier inside a `synchronized` block. If `spring.threads.virtual.enabled=true` is on an older JDK, use a `ReentrantLock` per `Ledger` instead.

## Part 2: Spring Boot REST API

### Resource model

| Capability | Request | Success | Rejections |
|---|---|---|---|
| Place a hold | `POST /v1/workshops/{workshopId}/holds` + `Idempotency-Key` | `201` + `Location: /v1/holds/{id}` | `400` validation · `404` workshop · `409` insufficient-seats / customer-limit-exceeded / request-in-flight · `422` key reused with a different body |
| Look up a hold | `GET /v1/holds/{holdId}` | `200` | `404` (missing **or not yours**) |
| Confirm | `POST /v1/holds/{holdId}/confirmation` | `200` (the hold, now `CONFIRMED`) | `404` · `409` hold-expired / hold-released |
| Release | `DELETE /v1/holds/{holdId}` | `204` | `404` · `409` hold-confirmed |
| Availability | `GET /v1/workshops/{workshopId}/availability` | `200` | `404` |

Design notes:
- Holds are a top-level resource, so one ID addresses a hold, and a path's workshop can never disagree with the hold's.
- Confirmation is a POST to a sub-resource because it's a state transition, not a replacement.
- The customer comes from the JWT, never from the body. Another customer's hold is a `404`, so its existence doesn't leak.
- The **server owns the TTL** (`seat-holds.hold-ttl`). A client-chosen TTL would let one request lock up a workshop.

### Configuration and wiring

```java
@ConfigurationProperties("seat-holds")
public record SeatHoldProperties(
        @DefaultValue("10m") Duration holdTtl,
        @DefaultValue("4") int maxSeatsPerCustomer,
        @DefaultValue("24h") Duration idempotencyTtl,
        Map<String, Integer> workshops) {}             // seeded at startup: id -> capacity

@Configuration
@EnableConfigurationProperties(SeatHoldProperties.class)
class SeatHoldConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean SeatHolds seatHolds(Clock clock, SeatHoldProperties props) {
        var holds = new InMemorySeatHolds(clock, props.maxSeatsPerCustomer());
        props.workshops().forEach(holds::addWorkshop);
        return holds;                                  // swap in a JDBC-backed SeatHolds for production
    }
}
```

### DTOs

```java
public record CreateHoldRequest(@NotNull @Positive Integer seats) {}

public record HoldResponse(String holdId, String workshopId, int seats, String status, Instant expiresAt) {
    static HoldResponse from(Hold h) {
        return new HoldResponse(h.id(), h.workshopId(), h.seats(), h.status().name(), h.expiresAt());
    }
}

public record AvailabilityResponse(String workshopId, int availableSeats) {}

record HoldResult(HoldResponse response, boolean replayed) {}
```

### The twist: idempotent hold creation

The rules:
- The key is **claimed before any work is done**, atomically, so a retry that arrives mid-flight can't slip through.
- Only a successful hold is replayed. A rejected attempt took no seats, so its claim is dropped and the retry re-evaluates.

```java
public sealed interface Claim {
    record Acquired() implements Claim {}
    record Replay(HoldResponse response) implements Claim {}
    record InFlight() implements Claim {}
    record Mismatch() implements Claim {}
}

public interface IdempotencyStore {
    Claim claim(String clientId, String key, String fingerprint);    // atomic: the first caller wins
    void complete(String clientId, String key, HoldResponse response);
    void abandon(String clientId, String key);
}

@Component
class InMemoryIdempotencyStore implements IdempotencyStore {
    private record Entry(String fingerprint, HoldResponse response, Instant expiresAt) {}  // response == null: in flight

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    InMemoryIdempotencyStore(Clock clock, SeatHoldProperties props) {
        this.clock = clock;
        this.ttl = props.idempotencyTtl();
    }

    @Override public Claim claim(String clientId, String key, String fingerprint) {
        Instant now = clock.instant();
        Entry fresh = new Entry(fingerprint, null, now.plus(ttl));
        Entry current = entries.compute(scoped(clientId, key),
                (k, old) -> old == null || !now.isBefore(old.expiresAt()) ? fresh : old);
        if (current == fresh) return new Claim.Acquired();
        if (!current.fingerprint().equals(fingerprint)) return new Claim.Mismatch();
        return current.response() == null ? new Claim.InFlight() : new Claim.Replay(current.response());
    }

    @Override public void complete(String clientId, String key, HoldResponse response) {
        entries.computeIfPresent(scoped(clientId, key),
                (k, e) -> new Entry(e.fingerprint(), response, e.expiresAt()));
    }

    @Override public void abandon(String clientId, String key) { entries.remove(scoped(clientId, key)); }

    private static String scoped(String clientId, String key) { return clientId + '\u0000' + key; }
}

public final class IdempotencyKeyReused extends RuntimeException {}
public final class IdempotencyRequestInFlight extends RuntimeException {}
```

### Application service: ownership and idempotency

```java
@Service
public class HoldService {
    private final SeatHolds seatHolds;
    private final IdempotencyStore idempotency;
    private final SeatHoldProperties props;

    public HoldService(SeatHolds seatHolds, IdempotencyStore idempotency, SeatHoldProperties props) {
        this.seatHolds = seatHolds;
        this.idempotency = idempotency;
        this.props = props;
    }

    public HoldResult create(String customerId, String idemKey, String workshopId, CreateHoldRequest req) {
        String fingerprint = workshopId + ':' + req.seats();           // canonical form of the request
        return switch (idempotency.claim(customerId, idemKey, fingerprint)) {
            case Claim.Replay r -> new HoldResult(r.response(), true);
            case Claim.Mismatch m -> throw new IdempotencyKeyReused();
            case Claim.InFlight f -> throw new IdempotencyRequestInFlight();
            case Claim.Acquired a -> {
                try {
                    var response = HoldResponse.from(
                            seatHolds.hold(workshopId, customerId, req.seats(), props.holdTtl()));
                    idempotency.complete(customerId, idemKey, response);
                    yield new HoldResult(response, false);
                } catch (RuntimeException e) {
                    idempotency.abandon(customerId, idemKey);           // nothing was taken; let the retry re-run
                    throw e;
                }
            }
        };
    }

    public HoldResponse find(String caller, String holdId) { return HoldResponse.from(owned(caller, holdId)); }

    public HoldResponse confirm(String caller, String holdId) {
        owned(caller, holdId);                                         // a hold's owner never changes, so this check can't go stale
        return HoldResponse.from(seatHolds.confirm(holdId));
    }

    public void release(String caller, String holdId) {
        owned(caller, holdId);
        seatHolds.release(holdId);
    }

    public AvailabilityResponse availability(String workshopId) {
        return new AvailabilityResponse(workshopId, seatHolds.available(workshopId));
    }

    private Hold owned(String caller, String holdId) {
        return seatHolds.find(holdId)
                .filter(h -> h.customerId().equals(caller))
                .orElseThrow(() -> new HoldNotFound(holdId));           // the same 404 for missing and not-yours
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/v1")
@Validated
class HoldController {
    private final HoldService holds;

    HoldController(HoldService holds) { this.holds = holds; }

    @PostMapping("/workshops/{workshopId}/holds")
    ResponseEntity<HoldResponse> create(@PathVariable String workshopId,
                                        @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idemKey,
                                        @Valid @RequestBody CreateHoldRequest body,
                                        @AuthenticationPrincipal Jwt caller) {
        HoldResult result = holds.create(caller.getSubject(), idemKey, workshopId, body);
        return ResponseEntity.created(URI.create("/v1/holds/" + result.response().holdId()))
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.response());
    }

    @GetMapping("/holds/{holdId}")
    HoldResponse get(@PathVariable String holdId, @AuthenticationPrincipal Jwt caller) {
        return holds.find(caller.getSubject(), holdId);
    }

    @PostMapping("/holds/{holdId}/confirmation")
    HoldResponse confirm(@PathVariable String holdId, @AuthenticationPrincipal Jwt caller) {
        return holds.confirm(caller.getSubject(), holdId);
    }

    @DeleteMapping("/holds/{holdId}")
    ResponseEntity<Void> release(@PathVariable String holdId, @AuthenticationPrincipal Jwt caller) {
        holds.release(caller.getSubject(), holdId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/workshops/{workshopId}/availability")
    AvailabilityResponse availability(@PathVariable String workshopId) {
        return holds.availability(workshopId);
    }
}
```

### One error contract: `ProblemDetail`

Extending `ResponseEntityExceptionHandler` gives Spring's own errors the same RFC 9457 shape: a missing `Idempotency-Key`, malformed JSON, a failed `@Valid`. Clients branch on the stable `type`, not on the message.

```java
@RestControllerAdvice
class ProblemAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SeatHoldException.class)
    ProblemDetail domain(SeatHoldException e) {
        return switch (e) {
            case WorkshopNotFound w -> problem(NOT_FOUND, "workshop-not-found", "No such workshop.");
            case HoldNotFound h -> problem(NOT_FOUND, "hold-not-found", "No such hold.");
            case InsufficientSeats s -> with(problem(CONFLICT, "insufficient-seats", "Not enough seats are available."),
                    "requested", s.requested, "available", s.available);
            case CustomerLimitExceeded c -> with(problem(CONFLICT, "customer-limit-exceeded", "This would exceed your seat limit for the workshop."),
                    "limit", c.limit, "alreadyHeld", c.alreadyHeld);
            case HoldNotActive n -> problem(CONFLICT, "hold-" + n.status.name().toLowerCase(),
                    "The hold is " + n.status.name().toLowerCase() + ".");
        };
    }

    @ExceptionHandler(IdempotencyKeyReused.class)
    ProblemDetail keyReused() {
        return problem(UNPROCESSABLE_ENTITY, "idempotency-key-reused",
                "This Idempotency-Key was already used with a different request.");
    }

    @ExceptionHandler(IdempotencyRequestInFlight.class)
    ResponseEntity<ProblemDetail> inFlight() {
        return ResponseEntity.status(CONFLICT).header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem(CONFLICT, "idempotency-request-in-flight", "A request with this key is still being processed."));
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("https://api.example.com/problems/" + type));
        return p;
    }

    private static ProblemDetail with(ProblemDetail p, Object... kv) {
        for (int i = 0; i < kv.length; i += 2) p.setProperty((String) kv[i], kv[i + 1]);
        return p;
    }
}
```

## Tests

### Unit: domain (JUnit 5, no Spring)

```java
class InMemorySeatHoldsTest {
    // assume MutableClock extends Clock, starts at 2026-11-09T09:00:00Z, and has advance(Duration)
    final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T09:00:00Z"));
    final InMemorySeatHolds holds = new InMemorySeatHolds(clock, 4);

    @BeforeEach void seed() { holds.addWorkshop("k8s-101", 10); }

    @Test void holdIsActiveUntilJustBeforeExpiresAtAndExpiredAtIt() {
        holds.hold("k8s-101", "bob", 4, Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(10).minusNanos(1));
        assertEquals(6, holds.available("k8s-101"));
        clock.advance(Duration.ofNanos(1));
        assertEquals(10, holds.available("k8s-101"));
    }

    @Test void confirmingAnExpiredHoldIsRejectedAndSeatsStayReturned() {
        Hold h = holds.hold("k8s-101", "bob", 4, Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(11));
        var e = assertThrows(HoldNotActive.class, () -> holds.confirm(h.id()));
        assertEquals(HoldStatus.EXPIRED, e.status);
        assertEquals(10, holds.available("k8s-101"));
    }

    @Test void confirmAfterAnEarlierHoldExpiredConfirmsTheRightHold() {   // the session's index bug
        holds.hold("k8s-101", "alice", 2, Duration.ofMinutes(5));
        Hold bob = holds.hold("k8s-101", "bob", 3, Duration.ofMinutes(30));
        clock.advance(Duration.ofMinutes(10));
        assertEquals(HoldStatus.CONFIRMED, holds.confirm(bob.id()).status());
        clock.advance(Duration.ofMinutes(30));
        assertEquals(7, holds.available("k8s-101"));
    }

    @ParameterizedTest
    @CsvSource({"4,0,true", "5,0,false", "2,3,false", "1,3,true"})   // seats, alreadyHeld, allowed
    void perCustomerLimit(int seats, int alreadyHeld, boolean allowed) {
        if (alreadyHeld > 0) holds.hold("k8s-101", "carol", alreadyHeld, Duration.ofMinutes(10));
        Executable call = () -> holds.hold("k8s-101", "carol", seats, Duration.ofMinutes(10));
        if (allowed) assertDoesNotThrow(call); else assertThrows(CustomerLimitExceeded.class, call);
    }

    @RepeatedTest(200)
    void concurrentHoldsNeverOversell() throws Exception {
        int threads = 100;
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(threads);
        var granted = new AtomicInteger();
        List<Future<?>> futures = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
            start.await();                                         // start gate: everyone hits hold() together
            try { holds.hold("k8s-101", "c" + i, 1, Duration.ofMinutes(10)); granted.incrementAndGet(); }
            catch (InsufficientSeats expected) { }
            return null;
        })).toList();
        start.countDown();
        for (var f : futures) f.get(5, SECONDS);
        assertEquals(10, granted.get());
        assertEquals(0, holds.available("k8s-101"));
    }
}
```

### Unit: application service (JUnit 5 + Mockito)

```java
@ExtendWith(MockitoExtension.class)
class HoldServiceTest {
    @Mock SeatHolds seatHolds;
    @Mock IdempotencyStore idempotency;
    HoldService service;

    @BeforeEach void setUp() {
        service = new HoldService(seatHolds, idempotency, new SeatHoldProperties(Duration.ofMinutes(10), 4, Duration.ofHours(24), Map.of()));
    }

    @Test void replayReturnsTheStoredResponseWithoutTakingSeats() {
        var stored = new HoldResponse("h-1", "k8s-101", 2, "HELD", Instant.EPOCH);
        when(idempotency.claim("alice", "key-1", "k8s-101:2")).thenReturn(new Claim.Replay(stored));
        var result = service.create("alice", "key-1", "k8s-101", new CreateHoldRequest(2));
        assertTrue(result.replayed());
        assertEquals(stored, result.response());
        verifyNoInteractions(seatHolds);
    }

    @Test void aRejectedHoldReleasesTheKeySoTheRetryReEvaluates() {
        when(idempotency.claim(any(), any(), any())).thenReturn(new Claim.Acquired());
        when(seatHolds.hold(any(), any(), anyInt(), any())).thenThrow(new InsufficientSeats(2, 1));
        assertThrows(InsufficientSeats.class, () -> service.create("alice", "key-1", "k8s-101", new CreateHoldRequest(2)));
        verify(idempotency).abandon("alice", "key-1");
    }

    @Test void anotherCustomersHoldIsNotFound() {
        when(seatHolds.find("h-9")).thenReturn(Optional.of(new Hold("h-9", "k8s-101", "bob", 1, Instant.EPOCH, HoldStatus.HELD)));
        assertThrows(HoldNotFound.class, () -> service.release("alice", "h-9"));
        verify(seatHolds, never()).release(any());
    }
}
```

### In-process web layer (`@WebMvcTest` + MockMvc)

```java
@WebMvcTest(HoldController.class)
@Import(ProblemAdvice.class)
class HoldControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HoldService holds;

    @Test void createReturns201WithLocation() throws Exception {
        when(holds.create(eq("alice"), eq("key-00000001"), eq("k8s-101"), any()))
                .thenReturn(new HoldResult(new HoldResponse("h-1", "k8s-101", 2, "HELD", Instant.EPOCH), false));
        mvc.perform(post("/v1/workshops/k8s-101/holds").with(jwt().jwt(j -> j.subject("alice")))
                        .header("Idempotency-Key", "key-00000001")
                        .contentType(APPLICATION_JSON).content("{\"seats\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/holds/h-1"))
                .andExpect(jsonPath("$.holdId").value("h-1"));
    }

    @Test void soldOutIs409ProblemDetail() throws Exception {
        when(holds.create(any(), any(), any(), any())).thenThrow(new InsufficientSeats(4, 3));
        mvc.perform(post("/v1/workshops/k8s-101/holds").with(jwt().jwt(j -> j.subject("alice")))
                        .header("Idempotency-Key", "key-00000002")
                        .contentType(APPLICATION_JSON).content("{\"seats\":4}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("/insufficient-seats")))
                .andExpect(jsonPath("$.available").value(3));
    }

    @Test void nonPositiveSeatsIs400ProblemDetail() throws Exception {
        mvc.perform(post("/v1/workshops/k8s-101/holds").with(jwt().jwt(j -> j.subject("alice")))
                        .header("Idempotency-Key", "key-00000003")
                        .contentType(APPLICATION_JSON).content("{\"seats\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(holds);
    }
}
```

### Live functional (REST Assured + JUnit 5, against `BASE_URL`)

This suite lives in its own `src/functionalTest` source set and runs as a pipeline stage after the deploy and rollout check. A failure blocks promotion.

```java
@Tag("functional")
class HoldJourneyFT {
    static final String BASE_URL = System.getenv("BASE_URL");
    static final String RUN = "ft-" + UUID.randomUUID();
    // assume Tokens.customer(name) gets a client-credentials token for a test customer from the secrets manager
    // assume Seed.workshop(id, capacity) uses the QA-only seeding path (there's no public create API), and Seed.cleanup(RUN) runs in @AfterAll
    // QA config sets seat-holds.hold-ttl=2s so the expiry journey runs in seconds

    RequestSpecification as(String customer) {
        return given().baseUri(BASE_URL).auth().oauth2(Tokens.customer(customer)).contentType(JSON);
    }

    @Test void retriedHoldReturnsTheSameHoldAndTakesSeatsOnce() {
        String workshop = Seed.workshop(RUN + "-retry", 3);
        String key = UUID.randomUUID().toString();
        String first = as("alice").header("Idempotency-Key", key).body(Map.of("seats", 2))
                .post("/v1/workshops/{w}/holds", workshop).then().statusCode(201).extract().path("holdId");
        String retry = as("alice").header("Idempotency-Key", key).body(Map.of("seats", 2))
                .post("/v1/workshops/{w}/holds", workshop).then().statusCode(201)
                .header("Idempotent-Replayed", "true").extract().path("holdId");
        assertEquals(first, retry);
        as("alice").get("/v1/workshops/{w}/availability", workshop).then().body("availableSeats", equalTo(1));
    }

    @Test void anotherCustomersHoldIs404() {
        String workshop = Seed.workshop(RUN + "-bola", 3);
        String bobs = as("bob").header("Idempotency-Key", UUID.randomUUID().toString()).body(Map.of("seats", 1))
                .post("/v1/workshops/{w}/holds", workshop).then().statusCode(201).extract().path("holdId");
        as("alice").delete("/v1/holds/{id}", bobs).then().statusCode(404)
                .contentType("application/problem+json");
    }

    @Test void unconfirmedHoldExpiresAndReturnsItsSeats() {
        String workshop = Seed.workshop(RUN + "-expiry", 2);
        String id = as("alice").header("Idempotency-Key", UUID.randomUUID().toString()).body(Map.of("seats", 2))
                .post("/v1/workshops/{w}/holds", workshop).then().statusCode(201).extract().path("holdId");
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                as("alice").get("/v1/holds/{id}", id).then().body("status", equalTo("EXPIRED")));
        as("alice").get("/v1/workshops/{w}/availability", workshop).then().body("availableSeats", equalTo(2));
        as("alice").post("/v1/holds/{id}/confirmation", id).then().statusCode(409)
                .body("type", endsWith("/hold-expired"));
    }
}
```

## What changes for production

**Persistence (Postgres):**
- Use two tables. `workshops` holds `id, capacity, version`. `holds` holds `id, workshop_id, customer_id, seats, status, expires_at, idem_client, idem_key, request_fingerprint, response, created_at`, with `UNIQUE (idem_client, idem_key)`.
- A hold runs in **one transaction**:
  1. `SELECT … FROM workshops WHERE id = ? FOR UPDATE`, which serializes holds for that workshop only.
  2. Sum the active held and confirmed seats, and the customer's active seats.
  3. Insert the hold.
  4. Commit.
- The idempotency key is a column on the hold, so a crash can't leave seats taken without a recorded key, and there's no separate cache that could fail. A duplicate insert hits the unique constraint, and the service replays the stored response.
- A denormalized `held`/`confirmed` counter with a conditional `UPDATE … WHERE capacity - held - confirmed >= :n` (checking the row count) also works, but then expiry has to decrement the counter.
- Expiry is computed at read time (`status = 'HELD' AND expires_at > now()`). A background sweeper flips stale rows to `EXPIRED` in batches with `FOR UPDATE SKIP LOCKED`, using ShedLock so one replica runs it at a time. Every transition goes into an append-only audit table.
- Terminal holds get a retention period.

**Scale-out:**
- Replicas are stateless behind the load balancer, with no sticky sessions.
- A popular workshop is a hot row. That's acceptable at seat volumes, and the lock is held for a few milliseconds.
- Hikari pool size is set against the database's connection limit, not just Tomcat's 200 threads.
- `server.shutdown=graceful`, with a preStop hook so readiness drops first.

**Security:**
- A Spring Security OAuth2 resource server (JWKS, issuer, audience), with `holds:write` scopes.
- Per-customer rate limiting on hold creation (429 with `Retry-After`), plus a cap on concurrent active holds per account against scalping bots.
- `server.error.include-stacktrace=never`, and unknown JSON fields are rejected.

**Observability:**
- Micrometer: `http.server.requests` with SLO buckets, `seat_holds_total{outcome=created|confirmed|released|expired}`, `seat_hold_rejections_total{reason}`, `idempotent_replays_total`, and a sweeper lag gauge. No `customerId` or `workshopId` tags.
- Page on 5xx and p99 burn rate for hold and confirm, never on 409s, which are expected during a launch.
- Structured JSON logs carry trace IDs through MDC and a hold ID at every decision point.
- Support answers "what happened to my hold" from the audit table.
