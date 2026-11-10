# Supplier Deduplication

| | |
|---|---|
| Skill | api-coding-challenge |
| Language | java |
| Framework | spring-boot |
| Shape | Collection processing |
| Started | 2026-11-09 23:00 EST |
| Start epoch | 1794283238 |

## Ground rules

- Write your Java in this folder, with any file names and layout you like.
- Pseudo-syntax is fine. You don't need imports, a package declaration, `main` or build files; go straight to classes and methods.
- Minor syntax slips don't matter. Records, Lombok and `// assume X exists` stubs are all fine.
- Ask clarifying questions at any time; asking them is part of the exercise.
- Say **"part 1 done"** when you're ready to move on. Ask for a hint if you're stuck; hints are noted in the debrief, as they would be on a real panel.

## Part 1: Duplicate-supplier grouping (~20–25 min)

Procurement pulls supplier records from several regional ERP systems, and the same real-world supplier often shows up more than once with slightly different details. Before anything gets merged, we need to find the groups of records that refer to the same supplier.

Given a list of `Supplier` records (id, name, email, website), group the suppliers that are duplicates. Two suppliers match when they share:

- a normalized **email**: trimmed and compared case-insensitively; **or**
- a normalized **website**: lowercased, with the scheme (`http://` or `https://`), a leading `www.`, and everything from the first `/` onward removed.

Matching is transitive: if A matches B by email and B matches C by website, then A, B and C are all one group. `name` is informational only and isn't used for matching.

Return the groups as sets of supplier ids.

### Example

| id | name | email | website |
|---|---|---|---|
| S1 | Acme Corp | `Billing@Acme.com ` | `https://www.acme.com/` |
| S2 | ACME Corporation | `billing@acme.com` | `null` |
| S3 | Acme Ltd | `ap@acme-ltd.co.uk` | `acme.com/contact` |
| S4 | Globex | `sales@globex.io` | `http://globex.io` |
| S5 | Globex Inc. | `SALES@GLOBEX.IO` | `https://globex.io/about` |

→ two groups: `{S1, S2, S3}` (S1–S2 share an email, S1–S3 share the website `acme.com`) and `{S4, S5}`.

### Interface sketch

```java
record Supplier(String id, String name, String email, String website) {}

List<Set<String>> duplicateGroups(List<Supplier> suppliers);
```

## Part 2: Expose it as a Spring Boot REST API (~20–25 min)

_Revealed at 23:34._

Wrap your Part 1 code in a Spring Boot REST API. Keep the Part 1 logic reusable: the API should be a layer around it, not a rewrite.

### Clients need to be able to

- Upload a batch of supplier records exported from one regional ERP system.
- Check what happened to an upload they made earlier: how many rows were accepted, and which were rejected.
- Retrieve the duplicate groups found across all supplier records uploaded so far, from every ERP.

### Requirements

- Request and response DTOs, so domain types aren't exposed directly
- Input validation with meaningful 4xx responses
- A consistent error response format
- Appropriate status codes
- **Some rows in an upload will be invalid. Decide how an upload with some bad rows is handled, and make sure an ERP integration can tell, programmatically, exactly which rows failed and why.**

### Not needed

Build files, a real database (an in-memory store behind a repository interface is fine), `application.yml`, or full Spring Security configuration.
