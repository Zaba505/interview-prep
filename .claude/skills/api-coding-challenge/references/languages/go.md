# Go

Language layer for api-coding-challenge, read with one framework layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` here, then `### X` in the framework file. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and the framework's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | go |
| aliases | go, golang |
| display | Go |
| fence | go |
| extensions | *.go |
| ignore | vendor/, bin/, *.test, *.prof |
| unit_test | go test (table-driven) + testify |
| live_test | go test -tags functional + testify |
| part1_min | 20–25 |

## Ground rules

- Write your Go in this folder, with any file names and layout you like. One package is fine.
- Pseudo-syntax is fine. You don't need imports, a package clause, `go.mod` or `main`; go straight to types and functions.
- In pure Part 1 logic you may leave out `if err != nil` checks or write a `// handle err` stub. `// assume X exists` stubs are fine too.
- Use the standard library only, including generics and the `slices`, `maps` and `cmp` packages. No third-party modules in Part 1.

## Tolerance

Tolerance covers ceremony, never decisions.

- **Free:** missing imports, package clause, `go.mod` or `main`; an omitted `if err != nil` in pure Part 1 logic; a `// handle err` stub; unused variables or imports the compiler would reject; `NewX` constructors and getters left out; not remembering an exact standard-library name (syntax questions are free).
- **Scored:** an omitted error check, or an error dropped with `_ =`, on an IO or HTTP path, which counts as a swallowed error; a map lookup or type assertion without the comma-ok form where the zero value is a valid result or the assertion can fail, which hides a missing key or panics; a `panic` used for an expected failure such as bad input or a missing record.

## Type mapping

| Concept | Idiom | Note |
|---|---|---|
| instant / duration | `time.Time`, `time.Duration`; compare instants with `t.Equal`, `Before`, `After` | `==` on `time.Time` also compares the location and the monotonic reading, so two equal instants can compare unequal; `t.Sub` gives a `Duration`, not raw nanoseconds |
| optional | a comma-ok `(T, bool)` return, or `(T, error)` with a sentinel such as `ErrNotFound` | a `*T` that may be nil works too, but an unchecked nil panics at the caller; the zero value isn't "absent" when zero is a valid result |
| money | `int64` minor units (cents), with the currency alongside | never `float64`; percentages and splits round explicitly in integer math, and the candidate says where the remainder cent goes |
| ordered map | a sorted slice searched with `sort.Search` or `slices.BinarySearchFunc` | the standard library has no tree map; `slices.Insert` into a sorted slice is O(n), which is fine if the candidate says so; a B-tree at scale |
| heap | `container/heap` over a slice type, or sort and take K | `container/heap` needs the five-method `heap.Interface`; a min-heap capped at K for top-K; its slice order isn't sorted |
| value type | a struct passed and returned by value | the copy is shallow, so a slice or map field still aliases the caller's; `slices.Clone` / `maps.Clone` before storing it |
| clock | an injected `now func() time.Time` (or a one-method `Clock` interface) | `time.Now()` called inside the logic can't be controlled in tests; a test passes a closure over a time it advances |
| per-key atomic update | a `sync.Mutex` guarding the map, held across the whole check-then-act | `sync.Map` has `LoadOrStore` and `CompareAndSwap` but no atomic read-modify-write for an arbitrary update; per-key or sharded locks under contention |
| extension point | a small interface, defined where it's consumed, or a function type | interfaces are satisfied implicitly, so one method is often enough; a `func(Cart) Discount` can be the whole strategy |
| stub idiom | `// assume X exists`, or a function whose body is `panic("TODO")` | `// handle err` stands in for a skipped error check |
| set | `map[K]struct{}` for membership; a sorted `[]K` when a set is returned (seed C4) | Go has no set type; `struct{}` values take no space, and a sorted slice keeps the output deterministic |
| local and zoned time | `time.LoadLocation` plus `time.Date(..., loc)`, and `t.In(loc)` for wall-clock checks (seeds S3, M4) | `time.Date` resolves a DST gap or overlap silently, so a strong candidate says which way; `LoadLocation` needs tzdata in the image (`import _ "time/tzdata"`) |
| sum type | a typed `const` block for plain states (Go has no enums); a sealed interface (an unexported marker method) with a type switch (seeds M2, M5) | a typed `string` const reads well on the wire, where an `iota` int encodes as a bare number; Go has no exhaustiveness check, so the type switch needs a `default` that fails loudly, or the `exhaustive` linter |

## Runtime & concurrency

- Goroutines are multiplexed by the runtime onto `GOMAXPROCS` OS threads, so any two goroutines can run in parallel.
- Under the Go memory model a data race has no defined result. Concurrent map writes are detected by the runtime and kill the whole process with `fatal error: concurrent map writes`, which `recover` can't catch. A race on any other value (a counter, a slice, a struct field) silently loses updates, and a slice or interface value can tear.
- Primitives: `sync.Mutex` / `RWMutex`, `sync/atomic` (`atomic.Int64`, and `atomic.Pointer[T]` for publishing immutable snapshots), `sync.Map` for append-mostly caches, `sync.Once`, and channels for handing ownership between goroutines. Beyond Part 1, which is standard-library only, `golang.org/x/sync` adds `errgroup`, `semaphore` and `singleflight`.
- Race tooling: the race detector (`go test -race`) reports only races that actually happen during the run, so a test has to drive the concurrent path; `go test -race -count=N` repeats it. It costs several times the CPU and memory, so it runs in tests and CI, not production.
- Container knobs: before Go 1.25, `GOMAXPROCS` defaulted to the host's CPU count rather than the container's CPU limit (`automaxprocs` was the fix); from Go 1.25 it honors the cgroup limit. `GOMEMLIMIT` sets a soft memory limit so the GC works harder before the container is OOM-killed, and `GOGC` trades memory for CPU.

## Seed calibration

- **C1** (event sessionizer): ok
- **C2** (two-feed reconciliation): ok
- **C3** (top-N per category): stub: a generic min-heap (`Heap[T]` with `Push`, `Pop`, `Len` and a `less` function), so the candidate skips the `container/heap` boilerplate.
- **C4** (duplicate-contact grouping): ok
- **C5** (leaderboard): ok
- **S1** (per-client rate limiter): forbid: `golang.org/x/time/rate`, whose `Limiter` is the Part 1 core.
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

- **C1** Planted edge: map iteration order is random and `sort.Slice` isn't stable, so users ranged out of a map, and events with equal timestamps, come out in a different order from run to run unless ties are broken explicitly.
- **C3** Planted edge: money as `int64` minor units vs `float64`
- **C5** Planted edge: map iteration order is random, so ties in the ranking must be broken explicitly.
- **S3** Planted edge: `time.Time` compared with `==` instead of `Equal`, and wall-clock times without a `time.Location`
- **S3** Hook: a sorted slice per room searched with `sort.Search` for the floor/ceiling lookup, because there's no tree map; `slices.Insert` is O(n) per booking
- **S4** Hook: compare-and-set under one `sync.Mutex` (the version check and the write in the same critical section)
- **M1** Planted edge: rounding a percentage discount in `int64` minor units, and where the remainder cent goes

## Twist guards

- **concurrency**: a racing map write crashes the process instead of losing an update, so one mutex around each map access looks like the whole fix → expect: the scorer looks for the check-then-act (the vote count, the lease-token check) inside one critical section or a version compare-and-set; two separately locked steps still lose a vote or accept a stale ack.
- **async**: `go func()` makes background work look free → expect: the scorer looks for bounded workers, a `recover` in each worker (an unrecovered panic in any goroutine kills the process), and a context made with `context.WithoutCancel(ctx)` rather than `context.Background()`, so the trace survives (O3).

## Dim-2 anchor

Plain structs and small interfaces defined by their consumers; errors returned as values and wrapped with `%w`, with sentinel or typed errors (`errors.Is` / `errors.As`) for the cases callers branch on, and no `panic` for expected failures. Pure logic separated from state, time and IO (an injected `now func() time.Time`), with the mutex kept beside the state it guards.

## Code hooks

- `float64` used for money → rounding errors (correctness)
- a map read and written from several goroutines without a lock → `fatal error: concurrent map writes` kills the whole process (P2)
- a lock released between the check and the write (look up under `mu`, unlock, then write under `mu` again) → the check-then-act race a mutex doesn't fix by itself (P2)
- a struct holding a `sync.Mutex` passed or returned by value → the copy has its own lock, so nothing is guarded (`go vet` copylocks) (P2)
- `==` on `time.Time` → equal instants compare unequal when the location or monotonic reading differs (correctness)
- a slice or map argument stored without a copy → the caller's later writes change the stored state (code_quality)
- `context.Background()` inside a goroutine or helper that was handed a `ctx` → the trace and the cancellation drop off (O3)
- `go func()` with no owner, bound or `recover` → an unrecovered panic kills the process, and the work is lost on shutdown (R4)

## Signals by thread ID

### P1

- the right structure for the access pattern: a map for grouping, a sorted slice with `sort.Search` for floor/ceiling (there's no tree map), `container/heap` for top-K
- a heap capped at K is O(n log K), where `slices.SortFunc` over everything is O(n log n); each insert into a sorted slice is O(n), so a write-heavy ordered index calls for a B-tree (`google/btree`)
- `make([]T, 0, n)` and `make(map[K]V, n)` preallocate instead of growing repeatedly; a `map[string]struct{}` set stores no values
- a `[]T` of structs is contiguous, while `[]*T` adds a heap object and a pointer per element, and more GC scan work

### P2

Drill-down: "Is a `sync.Map` enough? Walk me through `<check-then-act op>`."
- the whole check-then-act runs inside one `mu.Lock()` / `defer mu.Unlock()`; `sync.Map`'s `LoadOrStore` and `CompareAndSwap` cover insert-if-absent and swap, not an arbitrary read-modify-write
- per-key locks or a sharded map (N maps, each with its own mutex, chosen by key hash) when one mutex is the bottleneck; `atomic.Int64` for plain counters
- `RWMutex` pays off only when reads dominate and the critical section is long; for short sections a plain `Mutex` is often faster

### P3

- eviction or TTL: an LRU (`hashicorp/golang-lru/v2`, or `container/list` plus a map) or a sweep goroutine on a `time.Ticker` that stops when its `ctx` is done; idle per-client entries are the usual leak
- `GOMEMLIMIT` set a little below the container limit, so the GC works harder before the OOM killer acts; with only the default `GOGC=100`, the heap can reach twice the live set
- a map never shrinks: deleting keys frees the values but not the buckets, so a map that once held a million keys keeps that memory until it's rebuilt
- re-slicing (`q = q[1:]` as a queue, or keeping a small sub-slice) keeps the whole backing array alive
- leaked goroutines, blocked forever on a channel or missing a cancel, hold their stacks and everything they reference; the goroutine count shows them

### P4

- `database/sql` defaults: `SetMaxOpenConns` is unlimited and `SetMaxIdleConns` is 2, so both are set deliberately, along with `SetConnMaxLifetime`
- N+1 fixed with one query (`WHERE id = ANY($1)` with `pgx`, or a join); `rows.Close()` deferred and `rows.Err()` checked, or the connection leaks
- `QueryContext` with the caller's `ctx`, so a cancelled request gives its connection back

### P5

- local (`hashicorp/golang-lru/v2`'s expirable LRU, `ristretto`, `otter`) vs distributed (Redis through `go-redis`)
- `golang.org/x/sync/singleflight` collapses concurrent misses for one key into a single downstream call, so an expiry doesn't cause a stampede

### P6

- `pprof` CPU, heap, goroutine, mutex and block profiles read with `go tool pprof`; the mutex and block profiles are off until `runtime.SetMutexProfileFraction` / `SetBlockProfileRate` turn them on
- the goroutine profile (`debug=2`) dumps every goroutine's stack: thousands parked in `sync.(*Mutex).Lock`, or waiting for a `database/sql` connection, point at lock contention or pool starvation
- `go tool trace` shows scheduler delay, GC pauses and blocking; `GODEBUG=gctrace=1` logs every GC
- `testing.B` benchmarks compared with `benchstat`, and load tests with `vegeta`, `k6` or `hey`

### T1

- table-driven tests: a slice of cases ranged over with `t.Run(tc.name, ...)`, so a failure names its case
- an injected `now func() time.Time` (a closure over a time the test advances) for time logic; `testing/synctest` (Go 1.25) for timers and goroutines under a fake clock, as a bonus
- hand-written fakes of small consumer-side interfaces; `gomock` or `testify/mock` only at true boundaries
- native fuzzing (`func FuzzX(f *testing.F)`) or `rapid` for invariants as a bonus
- a concurrency test that starts N goroutines against a stateful component and runs under `-race`

### T2

- `testcontainers-go` for a real database or Redis in integration tests

### T3

- **Structure:**
  - its own package (`functional/` or `e2e/`) behind a `//go:build functional` constraint, so `go test ./...` skips it and CI runs `go test -tags functional ./functional/...`; or a separate repo
  - black-box over HTTP only, with no imports of the service's internal packages
- **Config:**
  - `BASE_URL` and credentials from env vars, read in `TestMain`, which fails fast when they're missing instead of skipping silently
- **Auth:**
  - `golang.org/x/oauth2/clientcredentials` gives an HTTP client that fetches and refreshes the token
- **Test data:**
  - each test creates its own data under a unique prefix (`t.Name()` plus a random suffix) and removes it in `t.Cleanup`
- **Determinism:**
  - no `time.Sleep`; poll with testify's `require.Eventually` (or `EventuallyWithT`) for async or eventually consistent behavior
  - `-count=1`, because `go test` caches passing results and doesn't know a deployed environment changed
  - a client timeout plus `go test -timeout`, so a hung environment fails fast instead of at the 10-minute default
- **Pipeline:**
  - `gotestsum --junitfile report.xml` turns `go test -json` output into JUnit XML for the CI test report and its trends

### T4

- N goroutines released together on a closed channel (each waits on `<-start`, then `close(start)`), joined with a `sync.WaitGroup`, then the invariant asserted
- `go test -race -count=N`: the race detector reports only races that happen during the run, so repeats shake out rare interleavings, and the test is still probabilistic
- `-race` finds data races, not logic races: a check-then-act split across two locked sections is clean to the detector and still double-books, so the invariant assertion is what proves the fix

### S1

- `golang-jwt/jwt/v5` with `jwt.WithValidMethods` pinning the algorithm, plus `WithIssuer` and `WithAudience`; expiry is checked by default
- the JWKS fetched and cached with refresh (`MicahParks/keyfunc` or `lestrrat-go/jwx`'s cache), not fetched per request

### S4

- `govulncheck` reports only the vulnerabilities whose affected functions the code actually reaches, alongside Dependabot or Renovate on `go.mod`
- a static binary (`CGO_ENABLED=0`) on a `distroless/static` or `scratch` base, run as a non-root user; `scratch` also needs CA certificates and `time/tzdata`
- `go.sum` checked against the checksum database (`GOSUMDB`), with `-mod=readonly` in CI so a build can't quietly change dependencies

### O1

- `log/slog` with a `slog.JSONHandler` for structured output, with the logger passed in or held on a struct rather than the global `log` package
- Go has no thread-locals, so nothing attaches the trace ID implicitly: it reaches a line only through an explicit `ctx` (`logger.InfoContext(ctx, ...)` with a handler that reads the span from it, such as the `otelslog` bridge) or `logger.With(...)` attributes
- a goroutine started with `context.Background()` logs without the trace ID, which is the trap

### O2

- Prometheus `client_golang` registers Go runtime metrics (goroutines, GC pauses, heap) by default; business metrics are `CounterVec` / `Histogram` with label values from a bounded set, never from user input

### O3

- the OpenTelemetry Go SDK, with spans started explicitly (`ctx, span := tracer.Start(ctx, ...)`, then `defer span.End()`); instrumentation comes from wrapped libraries (`otelsql`, `otelgrpc`), and eBPF auto-instrumentation exists but isn't the norm
- context travels only as the explicit first `ctx context.Context` parameter, so a function that drops it, or a goroutine started from `context.Background()`, starts a new trace (the trap); `context.WithoutCancel(ctx)` keeps the span for background work without inheriting the cancellation

### R1

- a conditional `UPDATE ... SET version = version + 1 WHERE id = $1 AND version = $2` through `database/sql`, with `RowsAffected() == 0` as the conflict signal
- a serialization failure (SQLSTATE `40001`) from `pgx` is retried a bounded number of times, never surfaced as a 500

### R2

- every outbound call gets a deadline from `context.WithTimeout`, derived from the caller's `ctx` so the remaining budget propagates
- retries with capped exponential backoff and jitter (`cenkalti/backoff`) only for idempotent calls; circuit breakers with `sony/gobreaker`, or `failsafe-go` for retry, breaker and bulkhead together
- a bulkhead per dependency: a buffered channel or `golang.org/x/sync/semaphore` caps in-flight calls, so a slow downstream can't pile up goroutines and memory
