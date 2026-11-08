# Assessment rubric

## Building the private rubric (at the start of Phase 3)

Work from CHALLENGE.md, the seed it was mutated from (if any) in `challenge-bank.md`, and the candidate's code. Keep the result in your working context only. It reaches disk only through the debrief (FEEDBACK.md and the TRANSCRIPT.md annotations).

Cover these points:
- **Expected Part 1 approach:** the approach and complexity of each core operation.
- **Planted edge cases:** for each one, record whether the candidate *asked* (check the transcript), *handled it in code*, or missed it.
- **Reference API shape:** from the seed's Part 2 sketch, or your own design for a novel problem. Compare resources, verbs, status codes and the error contract against it.
- **The twist:** what handling it well looks like.
- **Code-specific hooks:** concrete issues and choices in *their* code, with `file:line`. These are your follow-up openers. Examples:
  - a non-thread-safe field in a singleton
  - a domain entity returned from a controller
  - a 200 returned from a create
  - a missing `@Valid`
  - a swallowed exception
  - `double` used for money

## Scoring ground rules

- **Never penalize:**
  - missing imports, package, `main` or build files
  - getters, setters or boilerplate
  - minor syntax slips
  - Lombok or records
  - `// assume X exists` stubs
  - not remembering an exact annotation name (syntax questions are free)
- **Do penalize, in proportion to severity:**
  - logic errors, and planted edges that were both unhandled and not asked about
  - races in shared state
  - domain or persistence types leaking through the API
  - wrong status codes
  - swallowed or over-broad exception handling
  - inconsistent error shapes
  - rewriting Part 1 instead of reusing it
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

| # | Dimension | What a 3 (Principal) looks like |
|---|---|---|
| 1 | Problem solving & correctness | Clarifies before coding. Correct on all planted edges, whether asked about or handled. States complexity. Picks the right data structures. |
| 2 | Code quality & modeling | Readable names and small methods. Immutability and records where it fits. Pure logic separated from state, time and IO (injected `Clock`). Abstractions only where extension is likely. |
| 3 | Spring implementation (Part 2 code) | Clean controller → service → domain layering that reuses Part 1. DTOs plus Bean Validation. `@RestControllerAdvice` producing a consistent `ProblemDetail`. Correct status codes. Constructor injection. Thread-safe singleton state. The twist handled deliberately. |
| 4 | Communication | Asks design-shaping questions and states assumptions. Gives a crisp walkthrough. Answers are structured (conclusion first, then mechanics), and the candidate takes pushback well. |
| 5 | Performance | See `follow-ups.md` § Performance signals. |
| 6 | API design & versioning | See § API design & versioning. |
| 7 | Testing (unit + functional) | See § Testing. Live functional testing in a lower environment carries the most weight. |
| 8 | Security | See § Security. Object-level authz (BOLA) is the key discriminator. |
| 9 | Observability | See § Observability. |
| 10 | Resilience & scale-out | See § Resilience & scale-out. |

## Principal-bar signals (cross-cutting)

**Positive:**
- Asks questions that change the design before coding: volumes, consistency needs, consumers.
- States assumptions explicitly.
- Designs for testability, with pure functions and an injected `Clock`.
- Raises trade-offs unprompted, picks one, and says why.
- Zooms out to consumers, contracts, operability, rollout and team practice.
- Ties choices to failure modes ("if Redis is down, then…").
- Right-sizes the design and knows when *not* to over-engineer.

**Red flags:**
- Jumps straight to code.
- Can't state complexity.
- Ignores concurrency in a singleton.
- Exposes entities.
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
| Part 1 | <m> min | 20–25 |
| Part 2 | <m> min | 20–25 |
| Follow-ups | ~<m> min | 15–20 |
| **Total** | <m> min | 55–70 |

Hints used: <n> (<level and topic for each, or "none">)

## Scorecard

| Dimension | Score | Gist |
|---|---|---|
| Problem solving & correctness | <1–4> | <one line> |
| Code quality & modeling | | |
| Spring implementation | | |
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
| High | `<File>.java:<line>` | <what's wrong and its impact> | <concrete fix> |

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

## practice/INDEX.md

Create the file with this header if it's missing, then append one row per completed session:

```markdown
# Practice index

| Date | Challenge | Shape | Verdict | Weakest areas | Topics |
|---|---|---|---|---|---|
```

Example row:

```markdown
| 2026-11-08 | [Room booking](2026-11-08-room-booking/) | Stateful | Lean Hire | Observability, Resilience | interval overlap, 409 vs 422, BOLA on cancel, live functional tests |
```

- **Weakest areas:** the two lowest-scoring dimensions. Break ties toward the follow-up categories.
- **Topics:** 3–6 keywords a future you would search for.
