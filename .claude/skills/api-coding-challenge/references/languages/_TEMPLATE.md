# Language layer template

A language layer tells the api-coding-challenge skill what it needs to know about one programming language: how to phrase the problem in it, what to forgive, what to expect, and which follow-up signals only make sense in it. Each session reads one language layer and one framework layer (`../frameworks/<id>.md`) on top of the stack-neutral core, which is the challenge bank, the follow-up bank and the rubric.

You can fill this template in without opening SKILL.md or the core references:
- the [shared contract](#shared-contract) explains how the layers combine;
- [Header keys](#header-keys) and [Sections](#sections) say what to write;
- [Skeleton](#skeleton) is the file you ship.

## How to add a stack

1. **Copy the template.** Copy the block under [Skeleton](#skeleton) into `languages/<id>.md`, where `<id>` is the language's canonical ID. If the framework is new too, fill in `../frameworks/_TEMPLATE.md` after this one.
2. **Fill in the header.** Give every key in [Header keys](#header-keys) a value.
   - Add the `id` to the `languages` row of each framework file it pairs with.
   - Check the grep contract from `references/`: `grep -E '^\| (id|aliases|languages) \|' languages/<id>.md` must print exactly your `id` and `aliases` rows.
3. **Cover the primary threads.** Every thread whose primary layer is *language* in [Thread IDs](#thread-ids) needs at least 2 entries or an `N/A: <why>` line. Add to any other thread where the language has something to say.
4. **Calibrate all 15 seeds.** Give every seed in [Seed IDs](#seed-ids) a Seed calibration entry. `ok` is allowed, but only after you've checked.
5. **Dry-run one session.** Run `/api-coding-challenge <id>/<framework-id> <shape>` through to the debrief, answering as a candidate. Check that:
   - CHALLENGE.md reads natively in this language (signatures, ground rules, examples) and has no other language's terms;
   - the follow-ups and the debrief annotations use this language's tools;
   - Debrief notes doesn't report a missing layer section.
6. **If the core needed an edit, the contract has a bug: fix the template.** Make the core edit under the [move rule](#rules). Then change this template and `../frameworks/_TEMPLATE.md` so the next stack won't need that edit, and say so in the PR description. If you touched the shared contract, check that it's still identical in both templates (from `references/`, this prints nothing):

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

| Key | Value | The skill uses it for | Java example |
|---|---|---|---|
| `id` | The canonical ID: lowercase letters, digits and `-`, matching the file name `languages/<id>.md`. Don't change it once sessions use it, because session folders, session.yaml and CHALLENGE.md record it. | Stack resolution, the session folder name, `language:` in session.yaml, the Language row of CHALLENGE.md's header, INDEX.md, and the `languages` row of framework files | `java` |
| `aliases` | Other tokens that resolve to this file, lowercase. An alias can't contain a space, because the arguments are split on spaces, or a `/`, because a stack token is split on its first `/`. The space after each comma only separates the list. Each alias is unique across `languages/`. If there's no other alias, repeat the `id`. | Stack resolution | `java, jdk` |
| `display` | The language's name as written in prose | `{Language}` in CHALLENGE.md, the ground rules and Part 1; the Title and Stack lines of the role block | `Java` |
| `fence` | The info string for fenced code blocks | Interface sketches in CHALLENGE.md; REFERENCE-SOLUTION.md | `java` |
| `extensions` | Globs for the candidate's source files | Reading the candidate's code in Phase 3; file names in FEEDBACK.md's code review | `*.java` |
| `ignore` | Globs or directories that Phase 3 skips (build output, dependencies, caches), or `none` | Reading the candidate's code in Phase 3 | `target/, build/, .gradle/, *.class` |
| `unit_test` | The unit-test tools, named as you'd name them to a peer | The Stack line of the role block; the unit-test sketch in REFERENCE-SOLUTION.md | `JUnit 5 + Mockito` |
| `live_test` | The tools for the live functional suite against a deployed environment | The live-test sketch in REFERENCE-SOLUTION.md | `REST Assured + JUnit 5` |
| `part1_min` | The Part 1 time budget in minutes, as `<lo>–<hi>` with an en dash. Start from `20–25`, and widen it only for ceremony that Tolerance can't forgive. | `{part1_min}` in CHALLENGE.md; the Timeline target and Total in FEEDBACK.md | `20–25` |

**Values.** Each value is one line of raw text, with no backticks and no `|`, because the skill reads values literally. (A glob may render oddly on GitHub, and that's fine.) Separate list items with a comma and a space. No value may be empty.

**Grep contract.** SKILL.md finds stacks with:

```bash
grep -E '^\| (id|aliases|languages) \|' ${CLAUDE_SKILL_DIR}/references/{languages,frameworks}/[!_]*.md
```

- Write the `id` and `aliases` rows exactly as `| key | value |`, with one space on each side of every cell.
- No other line in the file may start with `| id |`, `| aliases |` or `| languages |`. That's why the keys in the table above are in backticks.
- Files whose names start with `_`, like this template, are never matched.

## Sections

Keep every heading in the skeleton, spelled exactly, because the skill looks sections up by heading. If a section has nothing for this language, write `none` under it rather than deleting it. Inside a table cell, write `|` as `\|`.

### Ground rules

- **Used:** copied into CHALLENGE.md's Ground rules when a session starts, alongside the core's generic rules (ask questions anytime, say "part 1 done", ask for hints). The reference solution follows them too.
- **Write:** 2–4 bullets addressed to the candidate. Say:
  - where the code goes;
  - which boilerplate they can skip;
  - which shortcuts are fine;
  - which ecosystem libraries Part 1 may use beyond the standard library. If you don't name any, none are allowed.
- **Java:**
  - Write your Java in this folder, with any file names and layout you like.
  - Pseudo-syntax is fine. You don't need imports, a package declaration, `main` or build files; go straight to classes and methods.
  - Minor syntax slips don't matter. Records, Lombok and `// assume X exists` stubs are all fine.

### Tolerance

- **Used:** when scoring. It extends the core's never-penalize list, which covers missing imports and build files, boilerplate, minor syntax slips, `// assume X exists` stubs, and forgotten API names.
- **Write:** first the line "Tolerance covers ceremony, never decisions." Then two labeled bullets, each a `;`-separated list:
  - **Free:** ceremony this language forces that is never penalized.
  - **Scored:** omissions that look like ceremony in this language but are really decisions, so they count.

  The test: if a reviewer would ask "what happens when this fails?", it's a decision. Framework ceremony (route registration, app wiring) belongs in the framework file's Not needed. A framework omission that is really a decision is one of the framework file's Code hooks.
- **Go:**
  - **Free:** an omitted `if err != nil` in pure Part 1 logic; a `// handle err` stub.
  - **Scored:** an omitted error or `_ =` on an IO or HTTP path, which counts as a swallowed error.

### Type mapping

- **Used:** to render the core's neutral seed signatures in this language, to judge Part 1 design choices, and to score Dim 2.
- **Write:** a table with columns `Concept | Idiom | Note`.
  - Keep all ten concepts below. The idiom is what a strong candidate reaches for, and the note gives the trap or the trade-off.
  - The core's neutral seed signatures use only these concepts plus lists, maps, sets, enums and plain scalars, so the ten rows are enough to render any seed.
  - Add a row where another idiom isn't obvious. The usual extras are local and zoned time (seeds S3, M4) and sum types (seeds M2, M5).
  - If an idiom depends on how the framework dispatches requests, give one per model and say so in the note. For example, the per-key atomic update differs between threads and an event loop.

| Concept | What it's for | Java idiom |
|---|---|---|
| instant / duration | timestamps and gaps (seeds C1, C3, S2, S3) | `Instant`, `Duration`; not `LocalDateTime` for instants |
| optional | absent results (seed S5's lease) | `Optional<T>` as a return type |
| money | prices and revenue (seeds C3, M1, M3) | `BigDecimal` with explicit rounding, never `double` |
| ordered map | floor/ceiling lookups (seed S3's intervals) | `TreeMap` / `NavigableMap` |
| heap | top-K (seed C3) | `PriorityQueue` |
| value type | immutable inputs and results | `record` |
| clock | time that tests can control | an injected `java.time.Clock` |
| per-key atomic update | check-then-act on a shared map (seeds S1, S2, S4) | `ConcurrentHashMap.compute` / `merge` |
| extension point | where new variants plug in (seeds M1, M3) | an `interface`, as a strategy |
| stub idiom | how the candidate skips a helper | `// assume X exists` |

### Runtime & concurrency

- **Used:** for the P2 and T4 follow-ups, and for scoring races. It tells the interviewer what a race means in this language.
- **Write:** bullets covering:
  - the execution model: OS threads, green threads, an event loop, async/await;
  - the memory model, and what an unsynchronized race does here: a silently lost update, a crash, or a compile error;
  - the primitives: locks, atomics, concurrent collections, channels;
  - race tooling: a detector, a stress harness;
  - runtime knobs that matter in a container, such as heap limits and GC.

  Request dispatch belongs to the framework file.
- **Java:** platform and virtual threads; a race silently loses updates; `synchronized`, `ReentrantLock`, atomics and `ConcurrentHashMap`; jcstress; `-XX:MaxRAMPercentage` and GC pauses.
- **Go:** concurrent map writes crash the program; `go test -race`.

### Seed calibration

- **Used:** in Phase 1, when a seed is mutated into the session's Part 1.
- **Write:** one entry per seed, all 15, in ID order, keeping the seed names from the skeleton.
  - Calibration is about fit. Write `ok` if the seed fits `part1_min` in this language at the same difficulty as in any other stack.
  - Otherwise write one or more tagged notes, as sub-bullets if there's more than one:
    - `stub:` a helper the candidate may assume, because building it in this language would eat the budget. CHALLENGE.md says so.
    - `trim:` a part of Part 1 to drop so it fits `part1_min`.
    - `forbid:` a stdlib or ecosystem feature that solves the Part 1 core outright. CHALLENGE.md tells the candidate not to use it.
  - A pitfall this language adds to a seed isn't a fit problem. It goes in Seed lines as a `Planted edge`.
  - Never change a seed's core idea, planted edges or twist. Those belong to the core.
- **Go:** `**C3** (top-N per category): stub: a generic min-heap, so the candidate skips the container/heap boilerplate.`

### Seed lines

- **Used:** wherever its seed is used: writing Part 1 and Part 2, and building the private rubric in Phase 3.
- **Write:** bullets of the form `- **<seed>** <field>: <line>`, where `<field>` is one of the [seed fields](#seed-ids). Each line adds to the core field it names, or restates it in this stack's terms; when writing CHALLENGE.md, prefer the restated wording. Use it for:
  - a pitfall this stack adds to a seed, as a `Planted edge`. It's scored like the core's planted edges and never hinted at.
  - the seed lines that the move rule takes out of the core.

  Write `none` if there are neither.
- **Go:** `- **C5** Planted edge: map iteration order is random, so ties in the ranking must be broken explicitly.`
- **Java:**
  - `` - **S3** Planted edge: `Instant` vs `LocalDateTime`/time zones ``
  - `` - **S4** Hook: compare-and-set (`ConcurrentHashMap.compute`) ``

### Twist guards

- **Used:** at "part 1 done", when the twist is written into Part 2.
- **Write:** one bullet for each [twist kind](#twist-kinds) this language changes, as `- **<kind>**: <what changes> → <guard>`, using the forbid, restate and expect guards listed there.
  - Write `none` if no twist needs a guard.
  - The framework file has its own Twist guards, for changes that come from request dispatch or from framework built-ins. Both files' guards apply.
- **Node:** `**concurrency**: there's one thread, so the lost update happens between an awaited read and the write → expect: the candidate finds that interleaving; a lock isn't the answer.`
- **Rust:** `**concurrency**: an unsynchronized shared map doesn't compile → restate: ask for throughput under contention, so lock granularity is the decision.`

### Dim-2 anchor

- **Used:** when scoring Code quality & modeling (`code_quality`).
- **Write:** 1–3 sentences on what a 3 (Principal) looks like in this language, naming its idioms. The core's neutral anchor already covers readable names and small methods, immutable values where they fit, pure logic kept apart from state, time and IO, and abstractions only where extension is likely. Don't repeat it.
- **Java:** Immutability and records where it fits. Pure logic separated from state, time and IO (injected `Clock`).

### Code hooks

- **Used:** in Phase 3, when building the private rubric. Each hook found in the candidate's code becomes a follow-up opener or a FEEDBACK.md issue, with `file:line`.
- **Write:** bullets of the form `<pattern> → <why it matters> (<thread ID or dimension key>)`. The core already covers the stack-neutral hooks (domain types on the wire, a 200 from a create, a swallowed exception), and the framework file adds its own.
- **Java:**
  - `` `double` used for money → rounding errors (correctness) ``
  - `` a `HashMap` field mutated on every request → lost updates (P2) ``

### Signals by thread ID

- **Used:** in the follow-up round, for openers and drill-downs, and at the debrief, for scoring and for the strong Principal answers written into TRANSCRIPT.md.
- **Write:** one `### <ID>` section per thread you have something for, in [Thread IDs](#thread-ids) order. Every language-primary thread must be present. Use only the frozen IDs in that table.

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
- **A `- N/A:` bullet** tells the scorer not to expect something the thread's topic normally covers (see the hint column of [Thread IDs](#thread-ids)). Rust example, under `### T4`: `- N/A: repeated runs to catch a data race: safe Rust rejects data races at compile time.`
- **`N/A: <why>` on its own**, as the section's only line, says the whole thread has nothing stack-specific here. It satisfies primary coverage.

## Skeleton

Copy everything inside this block into `languages/<id>.md`, then replace each `<…>` placeholder.

````markdown
# <display>

Language layer for api-coding-challenge, read with one framework layer on top of the stack-neutral core. For follow-up thread X, use the core thread, then `### X` here, then `### X` in the framework file. Bullets add to the core. An `Opener:` line or a heading title replaces the core's, and the framework's wins if both give one. `N/A:` marks something this stack doesn't have, so don't expect it. Seed lines add to their seed the same way, and the code hooks and twist guards of both layers all apply.

| Key | Value |
|---|---|
| id | <id> |
| aliases | <alias>, <alias> |
| display | <display> |
| fence | <fence> |
| extensions | <*.ext> |
| ignore | <dir/>, <glob> |
| unit_test | <tools> |
| live_test | <tools> |
| part1_min | <lo>–<hi> |

## Ground rules

- <bullet for CHALLENGE.md's Ground rules, addressed to the candidate>

## Tolerance

Tolerance covers ceremony, never decisions.

- **Free:** <ceremony that is never penalized>
- **Scored:** <an omission that is a decision in this language>

## Type mapping

| Concept | Idiom | Note |
|---|---|---|
| instant / duration | | |
| optional | | |
| money | | |
| ordered map | | |
| heap | | |
| value type | | |
| clock | | |
| per-key atomic update | | |
| extension point | | |
| stub idiom | | |

## Runtime & concurrency

- <execution model>
- <memory model, and what a race does here>
- <primitives>
- <race tooling>
- <container knobs>

## Seed calibration

- **C1** (event sessionizer): <ok | tag: note>
- **C2** (two-feed reconciliation): <ok | tag: note>
- **C3** (top-N per category): <ok | tag: note>
- **C4** (duplicate-contact grouping): <ok | tag: note>
- **C5** (leaderboard): <ok | tag: note>
- **S1** (per-client rate limiter): <ok | tag: note>
- **S2** (inventory holds): <ok | tag: note>
- **S3** (meeting-room booking): <ok | tag: note>
- **S4** (versioned key-value store): <ok | tag: note>
- **S5** (job queue with leases): <ok | tag: note>
- **M1** (promotion engine): <ok | tag: note>
- **M2** (approval workflow): <ok | tag: note>
- **M3** (parking garage): <ok | tag: note>
- **M4** (notification routing): <ok | tag: note>
- **M5** (access-policy evaluator): <ok | tag: note>

## Seed lines

<none | - **<seed>** <field>: <line>>

## Twist guards

<none | - **<kind>**: <what changes> → <guard>>

## Dim-2 anchor

<1–3 sentences>

## Code hooks

- <pattern> → <why it matters> (<thread ID or dimension key>)

## Signals by thread ID

### P1

- <signal>
- <signal>

### P3

- <signal>
- <signal>

### P6

- <signal>
- <signal>

### T1

- <signal>
- <signal>

### T3

- <signal>
- <signal>

### T4

- <signal>
- <signal>

### S4

- <signal>
- <signal>

### O1

- <signal>
- <signal>

### O3

- <signal>
- <signal>

### R2

- <signal>
- <signal>
````
