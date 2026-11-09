# net/http

Framework layer for api-coding-challenge, read with one language layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` in the language file, then `### X` here. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and this file's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | net-http |
| aliases | stdlib, net/http |
| display | net/http |
| languages | go |
| part2_noun | net/http REST API |
| in_process_test | httptest |
| part2_min | 25–30 |

## Not needed

- `main`, `http.Server` setup or `ListenAndServe` (a `NewServer(deps) http.Handler` that registers the routes is enough)
- a third-party router (Go 1.22+ `http.ServeMux` patterns such as `"GET /items/{id}"` are enough)
- hand-written JSON decode and encode helpers (a `// assume decodeJSON exists` stub is fine if you say which limits it applies)

## Request dispatch

- `http.Server` runs every request on its own goroutine (one per connection for HTTP/1.1, one per stream for HTTP/2), with no pool and no limit on how many run at once.
- The handler built once by `NewServer(deps)`, and the `Server` struct behind it, is shared by every request goroutine. A map field on it gets concurrent writes, and concurrent map writes crash the whole process rather than one request (P2).
- There's no built-in concurrency cap: a semaphore middleware or `netutil.LimitListener` (`golang.org/x/net`) adds one. The server recovers a panic inside a handler and closes that connection, but a panic in a goroutine the handler started kills the process.

## Mechanisms

| Mechanism | Idiom | Note |
|---|---|---|
| error mapping | a small `writeProblem(w, status, type, detail)` helper writing `application/problem+json`, called from one error-to-status mapping (often a `func(w, r) error` handler adapter) | net/http has no problem-details support; `http.Error` writes `text/plain`, and the mux's own 404 and 405 replies are plain text unless routed through the helper |
| middleware | `func(http.Handler) http.Handler`, composed around the mux or around one route's handler | the outermost wrapper runs first; per-route middleware wraps the handler at registration |
| validation | `http.MaxBytesReader` + `json.Decoder.DisallowUnknownFields`, then a hand-written `Validate() error` on the request DTO (or `go-playground/validator` struct tags) | no body limit by default; `encoding/json` accepts unknown fields and trailing data unless told otherwise; a missing field decodes as the zero value, so required fields need a pointer or an explicit check |
| config binding | a typed config struct loaded at startup (env or file) and held in an `atomic.Pointer[Config]` that a reload swaps | there's no config binding; "changeable without a code change" means a reload on SIGHUP, a file watch or a poll, and each request reads the pointer once |
| health | a hand-written `/livez` and `/readyz` on the mux | readiness checks dependencies and returns 503 once shutdown starts; liveness never checks dependencies |
| shutdown | `srv.Shutdown(ctx)` on SIGTERM, caught with `signal.NotifyContext` | stops accepting and waits for in-flight requests until `ctx` expires; `ListenAndServe` returns `http.ErrServerClosed` at once, so `main` must wait for `Shutdown` to return; goroutines the handlers started need their own drain |

## Expected Part 1 adaptation

- The Part 1 type becomes a dependency of a `Server` struct built by an explicit `NewServer(deps) http.Handler`, which registers the routes. There's no DI container, so the wiring is plain constructor calls.
- The clock (`now func() time.Time`) and the in-memory store are constructor parameters, and the store sits behind a small interface defined where the handlers consume it.
- Handlers only decode the request DTO, call the Part 1 code and encode the response DTO.
- Part 1 state that was used from one goroutine must be guarded, with the mutex in the Part 1 type next to the state rather than in the handler, because every request runs on its own goroutine against the same instance.

## Built-ins to forbid

- **C1** (event sessionizer): none
- **C2** (two-feed reconciliation): none
- **C3** (top-N per category): none
- **C4** (duplicate-contact grouping): none
- **C5** (leaderboard): none
- **S1** (per-client rate limiter): rate-limit middleware for net/http such as `go-chi/httprate` or `tollbooth` (replaces the Part 1 limiter that Part 2 must reuse)
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

- **M1** Hook: polymorphic promotion JSON: `encoding/json` can't decode into an interface, so the handler reads a `type` discriminator first (`json.RawMessage` or a custom `UnmarshalJSON`) and decodes the concrete struct second, rejecting an unknown type with a 400

## Twist guards

- **async**: `r.Context()` is cancelled as soon as the handler returns, so a background job started with it dies at once, and `srv.Shutdown` doesn't wait for it → expect: the 202 handler hands the job to a worker that owns its context, and the candidate drains the workers on shutdown.
- **conditional-requests**: `http.ServeContent` answers `If-None-Match` and `If-Match` on reads once the handler sets an `ETag`, but never on writes → expect: the scorer looks for the hand-written `If-Match` check, the 412 and the 428 on the write path.

## Trivia example

"what's the method for reading a `{id}` path wildcard?"

## Dim-3 anchor

A `NewServer(deps)` constructor that registers routes on an `http.ServeMux` and returns an `http.Handler`, with handlers that only decode, call Part 1 and encode. Errors flow back as values to one `writeProblem` path; cross-cutting concerns are `func(http.Handler) http.Handler` middleware; bodies are bounded with `MaxBytesReader` and decoded with `DisallowUnknownFields`.

## Code hooks

- `err.Error()` written to the response → internals leak to clients (S2)
- a nil slice in a response → encodes as `null`, not `[]` (A1)
- a map or counter field on the `Server` struct mutated in a handler → every request goroutine shares it (P2)
- a missing `return` after writing an error response → the handler carries on and writes a second status and body (correctness)
- a header set after `w.WriteHeader` or after the first `Write` → silently dropped, so `Content-Type` or `Location` never reaches the client (A1)
- `http.Error` or a bare `w.WriteHeader(500)` in some handlers and problem JSON in others → two error formats (A1)
- `json.NewDecoder(r.Body).Decode` with no `MaxBytesReader` → an unbounded body read into memory (S2)
- `http.ListenAndServe(addr, h)` or a zero-value `http.Server` → no read-header, read, write or idle timeouts, so slow clients hold goroutines (S2)
- `http.DefaultClient` or `&http.Client{}` for a downstream call → no timeout, so a slow dependency holds the request goroutine indefinitely (R2)
- `r.Context()` handed to a goroutine that outlives the handler → cancelled the moment the response is written (correctness)

## Signals by thread ID

### P2: Concurrency inside the shared server struct

Opener: "Every request runs on its own goroutine against the same `<Server>`, which holds `<field>`. What happens with 200 concurrent requests?"
- `http.Server` gives each request its own goroutine with no pool or cap, so `<field>` has as many concurrent callers as there are requests in flight
- concurrent writes to a shared map crash the whole process (`fatal error: concurrent map writes`), and the server's per-handler panic recovery can't catch it
- a global mutex middleware that serializes every request isn't the fix; the lock belongs in the Part 1 type, next to the state

### P4

- N/A: net/http has no data layer; candidates bring `database/sql` with a driver such as `pgx`, or `pgxpool` directly (language signals)
- with no server-side cap on concurrent requests, the pool size (`SetMaxOpenConns`, or `pgxpool`'s `MaxConns`) is what bounds database load; requests queue for a connection, so queries take `r.Context()` to give up when the client does

### P5

- N/A: net/http has no cache abstraction; candidates bring a local LRU or a Redis client (language signals)
- `http.ServeContent` checks `If-None-Match` against an `ETag` the handler sets, but only after the body has been rendered; checking the version first and writing a 304 saves the work
- `ETag` and `Cache-Control` are set by hand on `w.Header()` before `WriteHeader`

### P6

- `net/http/pprof` serves the profiles over HTTP; importing it registers on `http.DefaultServeMux`, so it belongs on a separate internal listener, never the public mux

### A1

- one error contract: a `writeProblem` helper writing RFC 9457 `application/problem+json` with stable `type` URIs, reached from one place that maps domain errors to statuses with `errors.Is` / `errors.As`
- the mux's own 404 and 405 replies, `http.Error` and a `*http.MaxBytesError` are plain text or unmapped unless the candidate routes them through the same helper (a catch-all `/` pattern for 404, an `errors.As` check for 413)
- `w.Header().Set("Location", ...)` before `w.WriteHeader(http.StatusCreated)` for 201 + `Location`, because headers written after `WriteHeader` are dropped
- collections initialized with `make([]T, 0)`, because a nil slice encodes as `null`

### A5

- spec-first with `oapi-codegen`'s `std-http-server` output: handlers implement a generated `ServerInterface` on a plain `http.ServeMux`, so the spec stays the source of truth (`ogen` is the alternative)
- code-first with `swaggo/swag` comment annotations drifts, because nothing checks the comments against the handlers; request-validation middleware against the spec (`kin-openapi`) or a CI diff keeps it honest

### T2

Opener: "How do you test your handlers, validation and error responses? And the persistence layer?"
- `httptest.NewRecorder` with `handler.ServeHTTP(rec, req)` for one handler; `httptest.NewServer(NewServer(fakes))` to exercise the mux, the middleware and the real client path
- the domain faked by a hand-written struct satisfying the handler's interface, asserting the status, `Content-Type`, headers and problem body
- N/A: avoiding a full context boot per test: net/http has no container to boot.

### T3

Drill-down: "How is this different from your `httptest` tests and from contract tests? Why have all three?"
- **Complements:**
  - the same test cases can run in-process and live by pointing one client at `httptest.NewServer(...).URL` or at `BASE_URL`

### S1

- N/A: net/http has no JWT resource-server support; candidates bring `golang-jwt/jwt/v5` or `lestrrat-go/jwx` with a cached JWKS (language signals)
- coarse checks in auth middleware (`func(http.Handler) http.Handler`) that validates the token and stores the principal in the request context under an unexported key type; object-level checks in the handler or domain, using that principal
- scope checks per route by wrapping a handler at registration, e.g. `mux.Handle("DELETE /bookings/{id}", requireScope("bookings:write", h))`

### S2

- `http.MaxBytesReader` caps the body, which net/http doesn't do by default; a `*http.MaxBytesError` maps to 413
- `json.Decoder` with `DisallowUnknownFields`, plus a second `Decode` that must return `io.EOF`, so trailing data is rejected; missing fields decode as zero values, so required fields are checked explicitly
- echoing `err.Error()` to clients is the trap: decoder, `strconv` and driver errors leak field names, types and internals, so the response gets a fixed detail and the error goes to the log
- an `http.Server` with `ReadHeaderTimeout` and read, write and idle timeouts; the zero-value server and `http.ListenAndServe` have none, which leaves the service open to slowloris

### O1

- request logging as middleware that wraps the `ResponseWriter` to capture the status and bytes, and logs with `r.Context()` so the trace ID is on the line

### O2

- N/A: net/http emits no metrics; candidates bring Prometheus `client_golang` or the OpenTelemetry metrics SDK (language signals)
- per-endpoint RED metrics come from middleware (`promhttp.InstrumentHandlerDuration` or `otelhttp.NewHandler`), labeled by the matched route pattern (`r.Pattern`, Go 1.23+) rather than the raw path, so IDs in URLs don't explode cardinality

### O3

- `otelhttp.NewHandler` around the mux starts a server span per request from the incoming `traceparent`, and `otelhttp.NewTransport` on the outbound `http.Client` injects it, so the trace continues downstream
- the span lives in `r.Context()`, so it reaches Part 1 and downstream calls only if handlers pass `r.Context()` down

### O4

- a hand-written `/readyz` that checks dependencies and returns 503 once shutdown starts (an `atomic.Bool` flipped on SIGTERM), and a `/livez` that checks nothing external, so a dependency outage doesn't restart every pod
- `srv.Shutdown(ctx)` on SIGTERM stops accepting and waits for in-flight requests until `ctx` expires; it doesn't wait for hijacked connections or for goroutines the handlers started
- `ListenAndServe` returns `http.ErrServerClosed` as soon as `Shutdown` begins, so `main` must block until `Shutdown` returns, or the process exits mid-drain
- the readiness flip comes first, then a short pause for the load balancer to notice, then `Shutdown`; a preStop hook does the same job

### R1

- N/A: net/http has no data layer or optimistic locking; candidates write a conditional update through `database/sql`, or a conditional write in Redis or DynamoDB (language signals)
- the `sync.Mutex` from P2 guards one process only; with N replicas the invariant moves to the store, and a lost conditional write maps to 409 through the same `writeProblem` path, never a 500

### R2

- `http.Client` has no default timeout: `http.DefaultClient` and `&http.Client{}` wait indefinitely on a slow downstream, so a strong answer sets `Client.Timeout` or a per-request `context.WithTimeout`, plus the transport's dial and TLS timeouts
- `http.Transport`'s `MaxIdleConnsPerHost` defaults to 2, so a busy client churns connections to one downstream; a response body must be read to the end and closed, or its connection isn't reused

### R5

- the server has no concurrency limit, so overload grows goroutines and memory until the container is OOM-killed; a semaphore middleware that returns 503 with `Retry-After` when full sheds load instead

## Reference solution shape

- the domain logic, request and response DTOs, and a `Server` built by `NewServer(deps) http.Handler` that registers routes on an `http.ServeMux`
- a `writeProblem` helper and one error-to-status mapping
- `func(http.Handler) http.Handler` middleware where the seed needs it, and the twist
- one `httptest` test: `httptest.NewServer(NewServer(fakes))` asserting the status and problem body
