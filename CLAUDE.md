# interview-prep

Mock-interview prep skills live in `.claude/skills/<skill-dir>/`. Each skill runs practice sessions and writes what they produce under `practice/`.

## practice/ conventions

Every prep skill follows these rules, so any skill's sessions can be found with one command.

1. **Stay in your own directory.** A skill writes and globs only under `practice/<skill-dir>/`, where `<skill-dir>` is the skill's directory name in `.claude/skills/`.
   - In SKILL.md, anchor every path as `${CLAUDE_SKILL_DIR}/../../../practice/<skill-dir>/…`, so it still works when Claude is started inside a session folder.
   - `${CLAUDE_SKILL_DIR}` is substituted only in SKILL.md, so reference files say `<session-dir>` instead.
2. **Session folders** are named `practice/<skill-dir>/<YYYY-MM-DD>-[<facets>-]<kebab-title>[-N]/`.
   - Facets are optional and skill-specific, and they use canonical IDs. For example, api-coding-challenge uses `<language>-<framework>`.
   - Add `-2`, `-3`, … when the name is already taken.
3. **Every session has a `session.yaml`**, its single machine-readable record.
   - It always has `skill`, `date`, `title` and `status` (`in-progress | complete | abandoned`).
   - When the session is complete it also has `verdict`, `topics` and one `score_<dim>` key per scored dimension. A dimension that wasn't assessed gets no key.
   - Scores run from 1 to 4. **Below bar** means 2 or lower.
   - Shared dimension names: `correctness`, `code_quality`, `communication`, `performance`, `api_design`, `testing`, `security`, `observability`, `resilience`, plus any the skill adds.
   - Write one `key: value` per line with no inline comments, so the `grep` lookups below match.
4. **File names:** the result file is `FEEDBACK.md` and the transcript is `TRANSCRIPT.md`.
5. **`practice/<skill-dir>/INDEX.md`** starts with the table header `| Date | Session | Verdict |`, followed by any columns the skill adds.
   - It uses ISO dates.
   - It is **regenerated** in date order from every `complete` session.yaml, never appended to, so it can't go stale or out of order.
6. **There is no root index.** `ls practice/` is the roll-up.
7. **Each skill defines its own transcript format** in its own references.

### One-command lookups

```bash
cat practice/ci-cd/INDEX.md                                        # all completed CI/CD sessions
ls -d practice/api-coding-challenge/????-??-??-go-*/ | tail -5     # last 5 Go sessions
ls -d practice/*/2026-11-0[1-7]-*/                                 # one week, all skills
grep -l '^status: in-progress' practice/*/*/session.yaml           # resumable sessions anywhere
grep -lE '^score_security: [12]$' practice/*/*/session.yaml        # below bar on Security, any skill
```
