# Assessment rubric

## Building the private rubric (at the start of Phase 3)

This rubric is stack-neutral. The `{…}` slots are filled from the layer headers, as SKILL.md § Files and paths describes, and the layer sections named below add the stack's terms.

Work from CHALLENGE.md, the seed it was mutated from (if any) in `challenge-bank.md` together with both layers' Seed lines for it, and the candidate's code. Keep the result in your working context only. It reaches disk only through the debrief (FEEDBACK.md, session.yaml and the TRANSCRIPT.md annotations).

Cover these points:
- **Expected Part 1 approach:** the approach and complexity of each core operation.
- **Planted edge cases:** for each one, record whether the candidate *asked* (check the transcript), *handled it in code*, or missed it. Include any planted edges the layers' Seed lines add.
- **Reference API shape:** from the seed's Part 2 sketch, or your own design for a novel problem. Compare resources, verbs, status codes and the error contract against it.
- **The twist:** what handling it well looks like. Both layers' Twist guards for its kind say where it bites in this stack.
- **Expected adaptation:** how the Part 1 code should be wrapped, from the framework file's Expected Part 1 adaptation. Going beyond it counts as rewriting Part 1.
- **Code-specific hooks:** concrete issues and choices in *their* code, with `file:line`. These are your follow-up openers. Examples:
  - every Code hook in both layer files
  - shared mutable state that isn't safe under concurrent requests
  - domain or persistence types on the wire
  - a 200 returned from a create
  - request input that's never validated
  - a swallowed exception
  - floating point used for money

## Scoring ground rules

- **Never penalize:**
  - missing imports or build files
  - boilerplate
  - minor syntax slips
  - `// assume X exists` stubs
  - not remembering an exact API name (syntax questions are free)
  - anything on the **Free** list of the language file's Tolerance
- **Do penalize, in proportion to severity:**
  - logic errors, and planted edges that were both unhandled and not asked about
  - races in shared state
  - domain or persistence types leaking through the API
  - wrong status codes
  - swallowed or over-broad exception handling
  - inconsistent error shapes
  - rewriting Part 1 instead of reusing it
  - anything on the **Scored** list of the language file's Tolerance, even where it looks like ceremony
- **Hints:**
  - A level-1 nudge is minor.
  - Level 2 caps that dimension at 3.
  - Level 3 caps it at 2.
- **Skipped questions or categories:** mark them **Not assessed**. Don't score them, and don't include them in the verdict, but list them under risks, because a real panel would note the missing signal.

## Dimensions

Score each one from 1 to 4.

**General anchors:**
- **1, gap:** missing, incorrect, or only reached with heavy prompting.
- **2, Senior:** correct and reasonable but reactive. Covers *what* but not *why*, and reaches trade-offs only when prompted.
- **3, Principal:** complete and proactive. Names trade-offs and failure modes unprompted and connects them to consumers and operations.
- **4, exceptional:** everything in 3, plus teaching. Offers alternatives with crisp selection criteria, anticipates the next question, and would raise the team's bar.

The **Key** column is the `score_<key>` name used in session.yaml.

| # | Dimension | Key | What a 3 (Principal) looks like |
|---|---|---|---|
| 1 | Problem solving & correctness | `correctness` | Clarifies before coding. Correct on all planted edges, whether asked about or handled. States complexity. Picks the right data structures. |
| 2 | Code quality & modeling | `code_quality` | Readable names and small methods. Immutable values where they fit. Pure logic separated from state, time and IO (an injected clock). Abstractions only where extension is likely. Then the language file's Dim-2 anchor. |
| 3 | {Framework} implementation (Part 2 code) | `framework_impl` | A thin HTTP layer that reuses Part 1. DTOs and validation at the edge. One central mapping to problem details. Correct status codes. Explicit dependency wiring. Shared state that's safe under concurrent requests. The twist handled deliberately. Then the framework file's Dim-3 anchor. |
| 4 | Communication | `communication` | Asks design-shaping questions and states assumptions. Gives a crisp walkthrough. Answers are structured (conclusion first, then mechanics), and the candidate takes pushback well. |
| 5 | Performance | `performance` | See `follow-ups.md` § Performance signals. |
| 6 | API design & versioning | `api_design` | See § API design & versioning. |
| 7 | Testing (unit + functional) | `testing` | See § Testing. Live functional testing in a lower environment carries the most weight. |
| 8 | Security | `security` | See § Security. Object-level authz (BOLA) is the key discriminator. |
| 9 | Observability | `observability` | See § Observability. |
| 10 | Resilience & scale-out | `resilience` | See § Resilience & scale-out. |

## Principal-bar signals (cross-cutting)

**Positive:**
- Asks questions that change the design before coding: volumes, consistency needs, consumers.
- States assumptions explicitly.
- Designs for testability, with pure functions and an injected clock.
- Raises trade-offs unprompted, picks one, and says why.
- Zooms out to consumers, contracts, operability, rollout and team practice.
- Ties choices to failure modes ("if Redis is down, then…").
- Right-sizes the design and knows when *not* to over-engineer.

**Red flags:**
- Jumps straight to code.
- Can't state complexity.
- Ignores concurrent access to shared state.
- Puts domain or persistence types on the wire.
- Gives mechanism-free buzzword answers ("just add Kafka").
- Gets defensive when probed.
- Over-engineers a 25-minute problem.

## Verdict (at the Principal bar)

| Verdict | Typical profile |
|---|---|
| **Strong Hire** | Nothing below 3; several 4s; clearly raises the bar. |
| **Hire** | Average ≥ 3; at most one 2 (not in correctness); no 1s. |
| **Lean Hire** | Mostly 3s with a couple of 2s; no 1s in the core dimensions (1, 3, 7). |
| **Lean No Hire** | Several 2s, or a 1 outside the core dimensions; would be a solid Senior. |
| **No Hire** | A 1 in correctness, two or more 1s, or mostly 2s. |

Judgment overrides arithmetic, but say why when it does. Write it the way a candid panel debrief would sound: specific, evidence-based, and not padded with praise.

## FEEDBACK.md template

```markdown
# Feedback: <Title>

**Verdict (Principal bar): <Strong Hire | Hire | Lean Hire | Lean No Hire | No Hire>**

<Three sentences: overall signal, strongest area, biggest risk.>

## Timeline

| Phase | Actual | Target |
|---|---|---|
| Part 1 | <m> min | {part1_min} |
| Part 2 | <m> min | {part2_min} |
| Follow-ups | ~<m> min | 15–20 |
| **Total** | <m> min | <{part1_min} + {part2_min} + 15–20> |

Hints used: <n> (<level and topic for each, or "none">)

## Scorecard

| Dimension | Score | Gist |
|---|---|---|
| Problem solving & correctness | <1–4> | <one line> |
| Code quality & modeling | | |
| {Framework} implementation | | |
| Communication | | |
| Performance | | |
| API design & versioning | | |
| Testing (unit + functional) | | |
| Security | | |
| Observability | | |
| Resilience & scale-out | | |

Not assessed: <categories, or "none">

## Code review

### Strengths
- <specific, with file:line where useful>

### Issues

| Severity | Location | Issue | Better approach |
|---|---|---|---|
| High | `<File>.<ext>:<line>` | <what's wrong and its impact> | <concrete fix> |

Severity levels:
- **High:** wrong results, data loss, or a security hole.
- **Medium:** production risk under load or over time.
- **Low:** clarity or style.

## Follow-ups

Each question, your verbatim answer, and a strong Principal answer are in [TRANSCRIPT.md](TRANSCRIPT.md#follow-ups). Clarifying questions worth asking are in [Debrief notes](TRANSCRIPT.md#debrief-notes).

## Study before the real loop

1. **<Topic>**: why it matters given this session, and one concrete thing to practice or read.
2. **<Topic>**: …
3. **<Topic>**: …
```

**Timeline targets:** `{part1_min}` and `{part2_min}` come from the layer headers, and the follow-ups take 15–20. The Total target is `part1_min + part2_min + 15–20`, adding the low ends and the high ends separately: `20–25` and `20–25` give `55–70`, and `20–25` and `25–30` give `60–75`.

## session.yaml

`<session-dir>/session.yaml` is the session's single machine-readable record. A completed one looks like this:

```yaml
skill: api-coding-challenge
date: 2026-11-08
title: "Hot-desk reservations"
status: complete
language: java
framework: spring-boot
shape: stateful
seed: S3
verdict: Lean Hire
topics: "interval overlap, 409 vs 422, BOLA on cancel, live functional tests"
score_correctness: 3
score_code_quality: 3
score_framework_impl: 3
score_communication: 3
score_performance: 3
score_api_design: 3
score_testing: 3
score_security: 2
score_observability: 2
```

Here Resilience was Not assessed, so `score_resilience` is absent.

**Format.** These rules keep the one-command lookups in the root `CLAUDE.md` working, e.g. `grep -lE '^score_security: [12]$'`.
- Write one `key: value` per line, in the order above, with no comments.
- Double-quote `title` and `topics`, and nothing else.
- **`status`:** `in-progress`, `complete` or `abandoned`.
- **`shape`:** `stateful`, `collections` or `modeling`.
- **`seed`:** the ID of the seed the problem was mutated from (`C1`–`C5`, `S1`–`S5`, `M1`–`M5`), or `none` for a fresh design.
- **`verdict`:** one of the five verdicts above, spelled exactly as in the table.
- **`topics`:** 3–6 comma-separated keywords a future you would search for.
- **`score_<key>`:** an integer from 1 to 4, using the Key column of the Dimensions table. A Not assessed dimension gets no key.

**Who writes what:**

| When | Writes |
|---|---|
| Phase 1 | `skill`, `date`, `title`, `status: in-progress`, `language`, `framework`, `shape` |
| Debrief | `seed`, `verdict`, `topics`, every `score_<key>`, then `status: complete` |
| `review <folder> abandon` | `seed`, then `status: abandoned` |

Never write the seed in Phase 1 or in CHALLENGE.md, because it points the candidate at the planted edge cases.

## INDEX.md

`<session-dir>/../INDEX.md` is this skill's study index, with one row per `complete` session. **Regenerate the whole file** at every debrief; never append to it. That way it can't go stale or out of order.

To regenerate:
1. List the completed sessions with `grep -l '^status: complete$' <session-dir>/../*/session.yaml`. Folder names start with the ISO date, so the glob order is date order.
2. Read each listed session.yaml and build one row from it, in that order.
3. Write the file: the header below, then the rows. In-progress and abandoned sessions get no row.

```markdown
| Date | Session | Verdict | Language | Framework | Shape | Seed | Below bar | Topics |
|---|---|---|---|---|---|---|---|---|
| 2026-11-08 | [Hot-desk reservations](2026-11-08-java-spring-boot-hot-desk-reservations/) | Lean Hire | java | spring-boot | stateful | S3 | Security (2), Observability (2) | interval overlap, 409 vs 422, BOLA on cancel, live functional tests |
```

- **Session:** the `title`, linked to its folder.
- **Date, Verdict, Language, Framework, Shape, Seed, Topics:** copied from session.yaml.
- **Below bar:** every dimension scored 2 or lower, in Dimensions-table order, each with its score. Write `—` if there are none. List all of them, because a "two lowest" rule would hide a 2 whenever two other dimensions scored 1.
