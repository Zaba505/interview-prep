# Framework layer template

A framework layer tells the api-coding-challenge skill what it needs to know about one web framework: how Part 2 is phrased in it, how it dispatches requests, which mechanisms the twist relies on, which built-ins to forbid, and which follow-up signals only make sense in it. Each session reads one language layer (`../languages/<id>.md`) and one framework layer on top of the stack-neutral core, which is the challenge bank, the follow-up bank and the rubric.

You can fill this template in without opening SKILL.md or the core references:
- the [shared contract](#shared-contract) explains how the layers combine;
- [Header keys](#header-keys) and [Sections](#sections) say what to write;
- [Skeleton](#skeleton) is the file you ship.

## How to add a stack

1. **Copy the template.** Copy the block under [Skeleton](#skeleton) into `frameworks/<id>.md`, where `<id>` is the framework's canonical ID. If the language is new too, fill in `../languages/_TEMPLATE.md` first.
2. **Fill in the header.** Give every key in [Header keys](#header-keys) a value.
   - `languages` may list only language IDs that have a file in `../languages/`.
   - Check the grep contract from `references/`: `grep -E '^\| (id|aliases|languages) \|' frameworks/<id>.md` must print exactly your `id`, `aliases` and `languages` rows.
3. **Cover the primary threads.** Every thread whose primary layer is *framework* in [Thread IDs](#thread-ids) needs at least 2 entries or an `N/A: <why>` line. Add to any other thread where the framework has something to say.
4. **Calibrate all 15 seeds.** Give every seed in [Seed IDs](#seed-ids) a Built-ins to forbid entry. `none` is allowed, but only after you've checked.
5. **Dry-run one session.** Run `/api-coding-challenge <language-id>/<id> <shape>` through to the debrief, answering as a candidate. Check that:
   - Part 2 in CHALLENGE.md reads natively in this framework and has no other framework's terms;
   - the follow-ups, FEEDBACK.md and the debrief annotations use this framework's tools;
   - Debrief notes doesn't report a missing layer section.
6. **If the core needed an edit, the contract has a bug: fix the template.** Make the core edit under the [move rule](#rules). Then change this template and `../languages/_TEMPLATE.md` so the next stack won't need that edit, and say so in the PR description. If you touched the shared contract, check that it's still identical in both templates (from `references/`, this prints nothing):

   ```bash
   diff <(sed -n '/^## Shared contract/,/^## Header keys/p' languages/_TEMPLATE.md) <(sed -n '/^## Shared contract/,/^## Header keys/p' frameworks/_TEMPLATE.md)
   ```

Filled-in files in this directory are worked examples.

## Shared contract

This section is word for word the same in both templates. Change both together; step 6 of the checklist has the check.

### How a session runs

1. **Part 1** (`part1_min` minutes): a problem mutated from a seed, written in the language and its standard library, plus any ecosystem libraries the language file's Ground rules allow. There's no framework.
2. **Part 2** (`part2_min` minutes): revealed at "part 1 done". The candidate exposes their Part 1 code as a `part2_noun`.
   - Part 2 always has the same **baseline requirements**: request and response DTOs kept separate from domain types, input validation, one consistent error format, appropriate status codes, and the Part 1 logic reused rather than rewritten.
   - It also adds exactly one twist.
3. **Follow-ups:** about two threads per category (three for Testing, always including T3), each tied to the candidate's code.
4. **Debrief:** scores the ten [dimensions](#dimensions) from 1 to 4, writes FEEDBACK.md, and adds a strong Principal answer under every follow-up in TRANSCRIPT.md.

### Rules

**Move rule.** No stack-coupled line is ever summarized or dropped.
- When a core line names one stack's terms, the core gets a neutral version.
- The original line moves **verbatim** into exactly one layer: the placement rule picks the file, and [Where moved lines go](#where-moved-lines-go) picks the section.
- So a session on an existing stack still renders exactly the content it did before the edit.

**Merge rule.** For follow-up thread X, the skill uses, in order:
1. the core thread,
2. the language file's `### X` section,
3. the framework file's `### X` section.

Layers **add** bullets. They never remove or reword a core line. Only two things replace the core's version, and when both layers give one, the framework's wins because it comes last:
- an `Opener:` line replaces the core opener;
- a title after the ID (`### P2: <title>`) replaces the core thread title.

Everything else merges the same way:
- The Dim-2 anchor comes from the language file and the Dim-3 anchor from the framework file. Each is read after the core's neutral anchor for that dimension.
- Code hooks are the union of the core's and both layers'. Twist guards are the union of both layers'.
- Seed lines add to their seed.

**Placement rule.** Each stack-specific line goes in the layer that owns its subject.
- **Framework file:** framework terms; libraries the framework auto-configures or ships as defaults; and plugins that exist only to integrate with the framework. Spring Boot examples: Tomcat, Boot properties, `@SpringBootTest`, Actuator, Hikari, JPA, Jackson, Micrometer, Spring Cache.
- **Language file:** the runtime, the standard library, test tools and other ecosystem libraries. Java examples: `ConcurrentHashMap`, JFR, JUnit/Mockito, REST Assured, Awaitility, Caffeine, Resilience4j, jqwik.
- **The line decides, not the library.** A general-purpose library stays in the language file even when the framework depends on it or bundles it. Only a line about how the framework configures or uses that library goes in the framework file. For example, "FastAPI validates request bodies with Pydantic" is a framework line, while "a Pydantic model as a Part 1 value type" is a language line.
- **A standard-library framework is still the framework.** When the framework ships with the language, as Go's `net/http` does, a line about how the service serves requests or calls downstreams with it (the server, the mux, the client, `httptest`, the JSON codec its handlers use) goes in the framework file. The rest of the standard library (`sync`, `context`, `time`, `testing`) stays in the language file.
- **Concurrency is split.** The language file owns the memory model, primitives and race tooling. The framework file owns request dispatch: thread-per-request, event loop or workers.
- **A line naming both** language and framework terms goes in the framework file, e.g. "plain JUnit 5 with no Spring context".
- **A line naming neither** (a cloud service, Kubernetes, HTTP itself, a team practice) stays in the core.
- **A framework file serves every language in its `languages` row**, so its lines must hold for all of them. A line that's true for only one language/framework pair has no home yet. A pair layer is deferred until a stack needs one.

**Primary coverage.** 21 of the 29 follow-up threads are stack-coupled. Each one has a primary layer, which must carry at least 2 entries, or an `N/A: <why>` line if the thread genuinely has nothing stack-specific in this stack.
- **Language primary:** P1 P3 P6 T1 **T3** T4 O1 O3 R2 S4
- **Framework primary:** P2 P4 P5 A1 A5 T2 S1 S2 O2 O4 R1
- **Core-only** (no primary layer): A2 A3 A4 S3 O5 R3 R4 R5

Details:
- Any layer may add to any thread, including the core-only ones.
- An **entry** is a leaf strong-answer bullet, including a `- N/A:` bullet. `Opener:` and `Drill-down:` lines don't count.
- T3 is the mandatory live-functional-test thread. It's language-primary because its tools (the test runner, the HTTP client, a polling helper, the report format) come from the ecosystem rather than the framework.
- A minimal framework may lack what a framework-primary thread is about: a data layer, a cache, metrics, health endpoints, JWT validation. Then its section says so, as `N/A: <framework> has no <thing>; candidates bring <library>`, and that library's signals go wherever the placement rule sends them.

### Where moved lines go

When the move rule takes a line out of the core, the placement rule picks the file and this table picks the section.

| A core line about… | Goes to |
|---|---|
| a follow-up thread: its title, opener, a drill-down or a strong-answer signal | Signals by thread ID, under `### <ID>` |
| a seed: its sketch or signature, a planted edge, its Part 2 sketch, its twist or a hook | Seed lines |
| the candidate's ground rules in CHALLENGE.md | Ground rules (language file) |
| what Part 2 doesn't need | Not needed (framework file) |
| what scoring never penalizes | Tolerance (language file) |
| code patterns to look for, or red flags | Code hooks |
| the code quality anchor | Dim-2 anchor (language file) |
| the framework implementation anchor | Dim-3 anchor (framework file) |
| the trivia example in the interviewer's conduct rules | Trivia example (framework file) |
| what REFERENCE-SOLUTION.md contains | Reference solution shape (framework file) |
| the Part 1 or Part 2 timing | `part1_min` or `part2_min` |
| the language or framework name in running text | nothing moves: the core gets a slot filled from `display` or `part2_noun` |

### Dimensions

The debrief scores ten dimensions from 1 to 4, where 3 is the Principal bar and 2 or lower is below it. Code hooks name a dimension by its key.

| # | Dimension | Key | Scored from |
|---|---|---|---|
| 1 | Problem solving & correctness | `correctness` | the Part 1 code and the clarifying questions |
| 2 | Code quality & modeling | `code_quality` | the candidate's code, against the core anchor plus the language file's Dim-2 anchor |
| 3 | `{Framework}` implementation (Part 2 code) | `framework_impl` | the Part 2 code, against the core anchor plus the framework file's Dim-3 anchor |
| 4 | Communication | `communication` | the whole session |
| 5 | Performance | `performance` | the P threads |
| 6 | API design & versioning | `api_design` | the A threads |
| 7 | Testing (unit + functional) | `testing` | the T threads; T3 carries the most weight |
| 8 | Security | `security` | the S threads |
| 9 | Observability | `observability` | the O threads |
| 10 | Resilience & scale-out | `resilience` | the R threads |

### Thread IDs

The 29 follow-up threads have frozen IDs. Never renumber or invent one.
- The round asks them in category order: Performance, API design & versioning, Testing, Security, Observability, Resilience & scale-out.
- In a layer file, a thread ID appears as a `### <ID>` heading under Signals by thread ID, or bare in parentheses at the end of a code hook.
- Only T3 groups its signals, under these bold labels: Structure, Config, Auth, Test data, Determinism, Pipeline, Scope, Complements, Platform specifics. Use the same labels to add to a group.

| ID | Category | Topic | Primary | A layer usually adds |
|---|---|---|---|---|
| P1 | Performance | Complexity and data structures | language | the standard structures for each access pattern (ordered map, heap, hash grouping) and their costs |
| P2 | Performance | Shared mutable state under concurrent requests | framework | how requests reach shared state; the language may add its atomic and locking primitives |
| P3 | Performance | Unbounded growth | language | bounded or evicting caches, container memory limits, GC pressure |
| P4 | Performance | Data access at scale | framework | the data-access library and connection pool the framework wires in: N+1 fixes, pool size vs request concurrency |
| P5 | Performance | Caching | framework | the framework's cache abstraction and HTTP caching support; the language may add a local cache library |
| P6 | Performance | Finding a bottleneck | language | profilers, thread dumps, GC logs, load-test tools; the framework may add its saturation metrics |
| A1 | API design & versioning | Defend the API they built | framework | the central error mapping and its problem-details support |
| A2 | API design & versioning | Idempotency and retries | core-only | usually nothing |
| A3 | API design & versioning | Pagination | core-only | usually nothing |
| A4 | API design & versioning | Evolving the contract | core-only | usually nothing |
| A5 | API design & versioning | Contract-first | framework | code-first spec generation vs codegen for this framework |
| T1 | Testing | Unit testing the Part 1 logic | language | the test runner, table or parameterized tests, the mocking library, property-based testing, a concurrency-test idiom |
| T2 | Testing | Testing the web layer and integration | framework | the in-process HTTP test, faking the domain, DB or Redis integration tests, test start-up cost |
| T3 | Testing | Live functional tests against a deployed lower environment (mandatory) | language | where the suite lives, the HTTP client and runner, a polling helper instead of sleeps, the CI report format |
| T4 | Testing | Proving a race is fixed | language | releasing N actors at once, the race detector or stress harness, repeat counts |
| S1 | Security | Authentication and authorization | framework | JWT resource-server support; where coarse and object-level checks live |
| S2 | Security | Hostile input | framework | the validation mechanism, body and collection limits, unknown-field rejection, keeping internals out of errors |
| S3 | Security | Sensitive data | core-only | usually nothing |
| S4 | Security | Staying secure over time | language | the ecosystem's dependency and SCA scanners, minimal runtime images |
| O1 | Observability | Logs | language | the structured-logging library; how the trace ID reaches every line |
| O2 | Observability | Metrics and alerts | framework | the per-endpoint metrics the framework emits; how to add business metrics |
| O3 | Observability | Tracing | language | the OpenTelemetry SDK and auto-instrumentation; carrying context across async boundaries |
| O4 | Observability | Health and operability | framework | liveness and readiness endpoints, graceful shutdown |
| O5 | Observability | An incident | core-only | usually nothing |
| R1 | Resilience & scale-out | N replicas | framework | optimistic locking or conditional writes in the framework's data layer |
| R2 | Resilience & scale-out | A slow downstream | language | HTTP-client timeout defaults, retry and circuit-breaker libraries |
| R3 | Resilience & scale-out | Reliable events | core-only | usually nothing |
| R4 | Resilience & scale-out | Deploys and failure mid-request | core-only | usually nothing |
| R5 | Resilience & scale-out | Overload | core-only | usually nothing |

### Seed IDs

The 15 seeds are problem templates that every session mutates. Every seed has five fields, which Seed lines refer to by name:
- **Sketch:** the Part 1 statement, plus a signature for the collections and stateful shapes.
- **Planted edge:** an edge case the statement deliberately leaves open. A strong candidate asks about it, and nobody hints at it.
- **Part 2 sketch:** the reference API used for assessment. It's never shown to the candidate.
- **Twist:** the one Part 2 requirement that forces an API-design decision.
- **Hook:** a pressure point to probe in the follow-ups.

Seed IDs S1–S5 (stateful seeds) are unrelated to thread IDs S1–S4 (Security). In a layer file, a seed ID is always bold and followed by the seed's name or a field, as in `**S1** (per-client rate limiter)` or `**S1** Twist:`.

| Seed | Shape | Part 1 core | Twist | Kind | Stack-sensitive spots |
|---|---|---|---|---|---|
| **C1** | collections | Event sessionizer: split each user's events into sessions on a gap over 30 minutes, and summarize each session | events arrive late and out of order across batches, so sessions are recomputed or merged | late-data | sorting, duration math, equal timestamps, map iteration order |
| **C2** | collections | Two-feed reconciliation: orders against shipments, giving matched, missing, unexpected and quantity mismatches | feeds hold millions of rows, so the work is async (202 + poll) | async | grouping, streaming or sort-merge, background jobs |
| **C3** | collections | Top-N products by revenue per category within `[from, to)`, ties broken by productId | results may be 60 seconds stale, so the candidate designs caching | caching | heap/top-K, the money type, half-open intervals |
| **C4** | collections | Duplicate-contact groups: contacts sharing a normalized email **or** phone, transitively | some rows in a batch are invalid: reject the batch, or accept the rest with per-row errors | partial-failure | string normalization, union-find, null or blank fields |
| **C5** | collections | Leaderboard: rating is the sum of a player's best 3 scores, with at least 3 matches to qualify | submission is idempotent by matchId; a different payload with the same ID gets 409 | idempotency | dedupe, tie ranking, map iteration order |
| **S1** | stateful | Per-client rate limiter with per-tier limits and an injected clock | per-tier limits come from typed config and change without a code change | runtime-config | the clock, thread safety, per-key atomic update, memory for idle clients |
| **S2** | stateful | Inventory holds with a TTL: hold, confirm, release, available | an `Idempotency-Key` on hold creation, so retries never double-hold | idempotency | the clock, lazy vs eager expiry, check-then-act races |
| **S3** | stateful | Meeting-room booking: reject overlaps, cancel, find available rooms | only the organizer can cancel, and the 409 helps without leaking other meetings | authz | ordered-map interval lookup, instants vs local times |
| **S4** | stateful | Versioned key-value store: optimistic concurrency, history, delete | conditional requests: `ETag`, `If-Match`, 412, 428 | conditional-requests | compare-and-set, value types, history bounds |
| **S5** | stateful | Job queue with leases, a visibility timeout and a dead-letter queue | a stale worker's ack is rejected with 409, because the lease token is fenced | concurrency | the clock, optional results, lease expiry under concurrency |
| **M1** | modeling | Promotion engine: price a cart under stackable, prioritized, sometimes-exclusive promotions | quotes expire and pin the version of the rules they used | versioned-rules | the extension point, money rounding, polymorphic JSON |
| **M2** | modeling | Approval workflow state machine: two distinct approvers above a threshold, with an audit trail | an illegal transition gets a 409 listing the allowed ones, and concurrent approvals never lose a vote | concurrency | enums or sum types, immutability, optimistic locking |
| **M3** | modeling | Parking garage: spot sizes, tickets, and fees with a free period and a daily cap | pricing changes without a redeploy, and a ticket keeps the rules from when it entered | versioned-rules | the extension point, duration rounding, concurrent spot assignment |
| **M4** | modeling | Notification routing: channels per event type, quiet hours in the user's time zone, a CRITICAL override | providers are slow and flaky, so delivery is async with retries | async | time zones and DST, background work, retries |
| **M5** | modeling | Access-policy evaluator: role allows plus attribute conditions; deny overrides and the default is deny | p99 under 5 ms for every caller, and an explain mode mustn't leak policy internals | latency-budget | sum types, indexing, caching decisions |

### Twist kinds

Twists are mutated and swapped between seeds, so the Twist guards in both layers are keyed by these kinds rather than by seed:

- **idempotency:** retries and resubmissions must not apply twice (C5, S2).
- **concurrency:** concurrent writers must not lose updates, and stale actors are fenced (S5, M2).
- **async:** long work runs in the background, as 202 + poll or as retries (C2, M4).
- **late-data:** input arrives late or out of order, so results are recomputed or merged (C1).
- **caching:** results may be stale for a bounded time (C3).
- **partial-failure:** some items in a batch fail (C4).
- **authz:** only some callers may act, and errors mustn't leak (S3).
- **conditional-requests:** ETags and preconditions (S4).
- **runtime-config:** behavior changes without a code change (S1).
- **versioned-rules:** a result pins the rule version it was computed under (M1, M3).
- **latency-budget:** a hard latency target on a hot path (M5).
- **pagination:** large result sets need stable cursors. No seed's twist uses it, but a mutated twist may.

A guard is one of:
- **forbid:** name the feature that would hand over the twist; Part 2 asks the candidate to build it instead.
- **restate:** reword the twist so it still forces a decision in this stack.
- **expect:** the twist still bites, but somewhere else; tell the scorer where.

## Header keys

The header is a two-column markdown table at the top of the file. Keys are lowercase and spelled exactly as below.

| Key | Value | The skill uses it for | Spring Boot example |
|---|---|---|---|
| `id` | The canonical ID: lowercase letters, digits and `-`, matching the file name `frameworks/<id>.md`. Don't change it once sessions use it, because session folders, session.yaml and CHALLENGE.md record it. | Stack resolution, the session folder name, `framework:` in session.yaml, the Framework row of CHALLENGE.md's header, INDEX.md | `spring-boot` |
| `aliases` | Other tokens that resolve to this file, lowercase. An alias can't contain a space, because the arguments are split on spaces; the space after each comma only separates the list. An alias may contain `/` (e.g. `net/http`), because a stack token is split on its first `/` only. Each alias is unique across `frameworks/`. If there's no other alias, repeat the `id`. | Stack resolution | `spring, springboot` |
| `display` | The framework's name as written in prose | `{Framework}` in CHALLENGE.md and Part 2, and in the Dim-3 name ("`{Framework}` implementation") in FEEDBACK.md; the Title and Stack lines of the role block | `Spring Boot` |
| `languages` | The language **IDs** (not aliases) this framework pairs with. A pair is supported only if both files exist and this row names the language. | Stack resolution; listing the supported pairs | `java` |
| `part2_noun` | What Part 2 builds, as a noun phrase that reads naturally after "Expose it as a" | The Part 2 heading and intro in CHALLENGE.md | `Spring Boot REST API` |
| `in_process_test` | The in-process HTTP test: its tool and harness | The in-process test sketch in REFERENCE-SOLUTION.md; T2 | `@WebMvcTest + MockMvc` |
| `part2_min` | The Part 2 time budget in minutes, as `<lo>–<hi>` with an en dash. Start from `20–25`. A framework that makes the candidate hand-wire routing, errors and validation needs more, e.g. `25–30`. | `{part2_min}` in CHALLENGE.md; the Timeline target and Total in FEEDBACK.md | `20–25` |

**Values.** Each value is one line of raw text, with no backticks and no `|`, because the skill reads values literally. (A value like `@WebMvcTest` may render oddly on GitHub, and that's fine.) Separate list items with a comma and a space. No value may be empty.

**Grep contract.** SKILL.md finds stacks with:

```bash
grep -E '^\| (id|aliases|languages) \|' ${CLAUDE_SKILL_DIR}/references/{languages,frameworks}/[!_]*.md
```

- Write the `id`, `aliases` and `languages` rows exactly as `| key | value |`, with one space on each side of every cell.
- No other line in the file may start with `| id |`, `| aliases |` or `| languages |`. That's why the keys in the table above are in backticks.
- Files whose names start with `_`, like this template, are never matched.

## Sections

Keep every heading in the skeleton, spelled exactly, because the skill looks sections up by heading. If a section has nothing for this framework, write `none` under it rather than deleting it. Inside a table cell, write `|` as `\|`.

### Not needed

- **Used:** in the "Not needed" paragraph of CHALLENGE.md's Part 2, after the core's items (build files, and a real database, because an in-memory store behind a repository interface is fine).
- **Write:** 1–3 bullets naming the framework-specific setup and ceremony the candidate can skip, such as config files, app wiring or route registration.
- **Spring Boot:**
  - `application.yml`
  - full Spring Security configuration

### Request dispatch

- **Used:** for the P2 and R1 follow-ups, and for scoring shared state in the Part 2 code.
- **Write:** bullets covering:
  - how a request reaches handler code: a thread-per-request pool and its default size, an event loop, a goroutine per request, or worker processes;
  - what's shared across requests: singletons, the server struct, module state;
  - the opt-in alternatives, with their caveats.

  What a race does at the memory level belongs to the language file.
- **Spring Boot:** Tomcat runs thread-per-request, with 200 threads by default. Controllers and services are singleton beans, so a mutable field is shared by every request thread. `spring.threads.virtual.enabled=true` switches to virtual threads, with pinning caveats.

### Mechanisms

- **Used:** to turn the core's neutral Part 2 wording ("cross-cutting middleware", "typed config changeable without a code change") into this framework's terms, and as the Dim-3 baseline.
- **Write:** a table with columns `Mechanism | Idiom | Note`. Keep all six mechanisms below. If the framework has no built-in mechanism for one, name what candidates hand-write or bring instead, e.g. a small `writeProblem` helper for error mapping.

| Mechanism | What it's for | Spring Boot idiom |
|---|---|---|
| error mapping | one central place that turns errors into RFC 9457 problem details | `@RestControllerAdvice` returning `ProblemDetail` |
| middleware | cross-cutting request handling: the rate limiter seed, auth, logging | `HandlerInterceptor` or a servlet `Filter` |
| validation | rejecting bad input at the edge | Bean Validation: `@Valid` plus constraint annotations |
| config binding | typed config that changes without a code change (seeds S1, M3) | `@ConfigurationProperties` |
| health | liveness and readiness for the orchestrator | Actuator's `/actuator/health/liveness` and `/readiness` groups |
| shutdown | draining in-flight requests | `server.shutdown=graceful` |

### Expected Part 1 adaptation

- **Used:** at "part 1 done", when Part 2 is written, and when scoring Dim 3, which rewards reusing Part 1 rather than rewriting it.
- **Write:** 2–5 bullets on what wrapping the Part 1 code should look like here:
  - how the Part 1 types are wired in: a DI container, constructors, or an explicit `NewServer(deps)`;
  - how the clock and the in-memory store are injected;
  - what must change now that requests arrive concurrently.

  Anything beyond this counts as rewriting Part 1, which is penalized.
- **Spring Boot:**
  - The Part 1 class becomes, or is wrapped by, a `@Service` with constructor injection.
  - The `Clock` is a `@Bean`, and the in-memory store sits behind a repository interface.
  - Part 1 state that was single-threaded must become thread-safe, because the bean is a singleton.

### Built-ins to forbid

- **Used:** at "part 1 done". Part 2's requirements tell the candidate to build these things themselves.
- **Write:** one entry per seed, all 15, in ID order, keeping the seed names from the skeleton. Write `none`, or the built-ins and what each would hand over.
  - Forbid a built-in only if it would make the seed's Part 2 work or a [baseline requirement](#how-a-session-runs) disappear. A built-in that just carries out a decision the candidate made is fine, e.g. a cache abstraction once they've chosen the key and the TTL.
  - A built-in that hands over a twist goes in Twist guards instead, as a `forbid`, so it still applies when a session swaps twists between seeds.
- **Spring Boot:** `` **S1** (per-client rate limiter): Spring Cloud Gateway's `RequestRateLimiter` filter (moves the enforcement Part 2 asks for out of the service). ``

### Seed lines

- **Used:** wherever its seed is used: writing Part 1 and Part 2, and building the private rubric in Phase 3.
- **Write:** bullets of the form `- **<seed>** <field>: <line>`, where `<field>` is one of the [seed fields](#seed-ids). Each line adds to the core field it names, or restates it in this stack's terms; when writing CHALLENGE.md, prefer the restated wording. Use it for:
  - a pitfall this stack adds to a seed, as a `Planted edge`. It's scored like the core's planted edges and never hinted at.
  - the seed lines that the move rule takes out of the core.

  Write `none` if there are neither.
- **Spring Boot:**
  - `` - **S1** Twist: per-tier limits come from `@ConfigurationProperties` and must be changeable without a code change. ``
  - `` - **M2** Hook: optimistic locking with `@Version` ``

### Twist guards

- **Used:** at "part 1 done", when the twist is written into Part 2.
- **Write:** one bullet for each [twist kind](#twist-kinds) this framework changes, as `- **<kind>**: <what changes> → <guard>`, using the forbid, restate and expect guards listed there.
  - Use it for twists that request dispatch changes, and for framework built-ins that would hand over a twist.
  - Write `none` if no twist needs a guard.
  - The language file has its own Twist guards. Both files' guards apply.
- **Spring Boot:** `` **conditional-requests**: `ShallowEtagHeaderFilter` derives ETags from the response body, not from the version → forbid. ``
- **FastAPI:** `` **concurrency**: `async def` handlers share one event loop, so the race moves to `await` points, while `def` handlers run on a threadpool → expect: the candidate says which kind they wrote and why. ``

### Trivia example

- **Used:** in the interviewer's conduct rules, as the example of syntax or API trivia that the interviewer answers directly, because the interview doesn't test recall.
- **Write:** one question in quotes, of the kind a candidate asks in the middle of Part 2.
- **Spring Boot:** "what's the annotation for request-body validation?"

### Dim-3 anchor

- **Used:** when scoring "`{Framework}` implementation (Part 2 code)" (`framework_impl`).
- **Write:** 1–3 sentences on what a 3 (Principal) looks like in this framework, naming its idioms. The core's neutral anchor already covers:
  - a thin HTTP layer that reuses Part 1;
  - DTOs and validation at the edge;
  - one central mapping to problem details;
  - correct status codes;
  - explicit dependency wiring;
  - shared state that's safe under concurrent requests;
  - a deliberate handling of the twist.

  Don't repeat it.
- **Spring Boot:** Clean controller → service → domain layering. DTOs plus Bean Validation. `@RestControllerAdvice` producing a consistent `ProblemDetail`. Constructor injection. Thread-safe singleton state.

### Code hooks

- **Used:** in Phase 3, when building the private rubric. Each hook found in the candidate's code becomes a follow-up opener or a FEEDBACK.md issue, with `file:line`.
- **Write:** bullets of the form `<pattern> → <why it matters> (<thread ID or dimension key>)`.
  - The core already covers the stack-neutral hooks (domain types on the wire, a 200 from a create, a swallowed exception), and the language file adds its own.
  - This is also where a framework omission that looks like ceremony but is really a decision goes.
- **Spring Boot:**
  - `` a missing `@Valid` on a request body → validation silently skipped (S2) ``
  - `` field injection with `@Autowired` → hidden dependencies, harder tests (framework_impl) ``
- **net/http:**
  - `` `err.Error()` written to the response → internals leak to clients (S2) ``
  - `` a nil slice in a response → encodes as `null`, not `[]` (A1) ``

### Signals by thread ID

- **Used:** in the follow-up round, for openers and drill-downs, and at the debrief, for scoring and for the strong Principal answers written into TRANSCRIPT.md.
- **Write:** one `### <ID>` section per thread you have something for, in [Thread IDs](#thread-ids) order. Every framework-primary thread must be present. Use only the frozen IDs in that table.

```markdown
### P2: <optional title; replaces the core thread title>

Opener: "<replaces the core opener; keep the <placeholders> the interviewer fills in from the candidate's code>"
Drill-down: "<added to the core's drill-downs>"
- <strong-answer signal, added to the core's>
- **<T3 group label>:**
  - <signal read with that group>
- N/A: <something the thread's topic normally expects>: <why this stack has no analogue>.
```

- **Write what a strong answer says in this stack.** You can't see the core's signals, and you don't need to. A signal that repeats the core's costs one line; a missing one costs the candidate credit they earned.
- **Signals are only for scoring and the debrief**, and they're never read out, so write them as checkable facts.
- **A `- N/A:` bullet** tells the scorer not to expect something the thread's topic normally covers (see the hint column of [Thread IDs](#thread-ids)). net/http example, under `### T2`: `- N/A: avoiding a full context boot per test: net/http has no container to boot.`
- **`N/A: <why>` on its own**, as the section's only line, says the whole thread has nothing stack-specific here. It satisfies primary coverage.
- **Spring Boot**, under `### P2`: `Opener: "<Service> is a Spring singleton holding <field>. What happens with 200 concurrent requests?"`

### Reference solution shape

- **Used:** for REFERENCE-SOLUTION.md, which is written only if the candidate asks for it at the end.
- **Write:** bullets naming, in this framework's terms:
  - the code it contains: domain logic, DTOs, the HTTP layer, the error mapping and the twist;
  - the in-process test sketch (`in_process_test`).

  The core adds the unit-test sketch (the language's `unit_test`), the live functional test sketch (the language's `live_test`, pointed at a `BASE_URL` env var) and the production notes.
- **Spring Boot:**
  - the domain logic, DTOs, and a controller and service
  - `@RestControllerAdvice` returning `ProblemDetail`
  - the twist
  - one `@WebMvcTest`

## Skeleton

Copy everything inside this block into `frameworks/<id>.md`, then replace each `<…>` placeholder.

````markdown
# <display>

Framework layer for api-coding-challenge, read with one language layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` in the language file, then `### X` here. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and this file's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | <id> |
| aliases | <alias>, <alias> |
| display | <display> |
| languages | <language-id>, <language-id> |
| part2_noun | <noun phrase> |
| in_process_test | <tool + harness> |
| part2_min | <lo>–<hi> |

## Not needed

- <framework setup or ceremony the candidate can skip in Part 2>

## Request dispatch

- <how a request reaches handler code>
- <what's shared across requests>
- <opt-in alternatives and their caveats>

## Mechanisms

| Mechanism | Idiom | Note |
|---|---|---|
| error mapping | | |
| middleware | | |
| validation | | |
| config binding | | |
| health | | |
| shutdown | | |

## Expected Part 1 adaptation

- <how the Part 1 types are wired in>
- <how the clock and the store are injected>
- <what changes now that requests arrive concurrently>

## Built-ins to forbid

- **C1** (event sessionizer): <none | built-in (what it would hand over)>
- **C2** (two-feed reconciliation): <none | built-in (what it would hand over)>
- **C3** (top-N per category): <none | built-in (what it would hand over)>
- **C4** (duplicate-contact grouping): <none | built-in (what it would hand over)>
- **C5** (leaderboard): <none | built-in (what it would hand over)>
- **S1** (per-client rate limiter): <none | built-in (what it would hand over)>
- **S2** (inventory holds): <none | built-in (what it would hand over)>
- **S3** (meeting-room booking): <none | built-in (what it would hand over)>
- **S4** (versioned key-value store): <none | built-in (what it would hand over)>
- **S5** (job queue with leases): <none | built-in (what it would hand over)>
- **M1** (promotion engine): <none | built-in (what it would hand over)>
- **M2** (approval workflow): <none | built-in (what it would hand over)>
- **M3** (parking garage): <none | built-in (what it would hand over)>
- **M4** (notification routing): <none | built-in (what it would hand over)>
- **M5** (access-policy evaluator): <none | built-in (what it would hand over)>

## Seed lines

<none | - **<seed>** <field>: <line>>

## Twist guards

<none | - **<kind>**: <what changes> → <guard>>

## Trivia example

"<a syntax or API question the interviewer just answers>"

## Dim-3 anchor

<1–3 sentences>

## Code hooks

- <pattern> → <why it matters> (<thread ID or dimension key>)

## Signals by thread ID

### P2

- <signal>
- <signal>

### P4

- <signal>
- <signal>

### P5

- <signal>
- <signal>

### A1

- <signal>
- <signal>

### A5

- <signal>
- <signal>

### T2

- <signal>
- <signal>

### S1

- <signal>
- <signal>

### S2

- <signal>
- <signal>

### O2

- <signal>
- <signal>

### O4

- <signal>
- <signal>

### R1

- <signal>
- <signal>

## Reference solution shape

- <code components, in this framework's terms>
- <the in-process test sketch>
````
