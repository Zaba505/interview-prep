# Transcript: Supplier Deduplication

[Challenge](CHALLENGE.md) · [Feedback](FEEDBACK.md)
Started 2026-11-09 23:00 EST

## Part 1: Implementation

**[23:05] Q (you):** Should ids be compared or only email and website? Also what do you mean by trimmed for email?
**A (interviewer):** Only email and website decide whether two suppliers match. The id just identifies the record, and it's what you return in the groups. It plays no part in matching. "Trimmed" means leading and trailing whitespace is removed, the way `String.strip()` does it. Whitespace inside the address isn't touched. So `"Billing@Acme.com "` in S1 becomes `"Billing@Acme.com"`, and the case-insensitive comparison then matches it to S2's `"billing@acme.com"`.

**[23:28] Q (you):** I believe I'm done with part 1 but it relied heavily on my assumption that map.get and map.put use `equals` instead of `hash` for key comparison
**A (interviewer):** Good that you flagged it. Rather than me answering it, let's test that assumption against your code.

**[23:34] Q (interviewer, Part 1 check):** Take just S1 and S2 from the example. Walk me through what `duplicateGroups` returns for those two, step by step. In particular, what happens inside `acc.get(key)` when S2 arrives after S1 is already in the map?
**A (you):** 1. For S1, the acc map is empty so ids should be null (or get throws and exceptions which a small try/catch could handle), a new array list is instantiated, S1 id is added to it and that list is added to the acc map keyed on the email and website from S1
2. For S2, assuming my memory is correct, acc.get() will use `.equals()` for finding a matching key in the map. When `equals` runs between S1 and S2 SupplierHashKeys the normalizedEmail comparisons using equalsIgnoreCase would return true, which would cause the map to returned the same list that S1 put in the map.
Note if my memory is incorrect, its simple to refactor by inversion where 2 maps would be constructed. One keyed by normalizedEmail and the other by normalizedWebsite. The values in both maps would be lists of Supplier and then both maps would need to be reconciled.

---
**[23:34] Part 1 complete** · epoch 1794285291 · 34 min

## Part 2: Implementation

**[23:37] Q (you):** What does it mean for a supplier to be invalid?
**A (interviewer):** Defining that is part of the design. Think about what a record has to have for the rest of the system to use it, given that these records feed the duplicate matching. What would you treat as invalid? Go with that, and make sure the rules you pick show up clearly in what the client gets back.

**[23:38] Q (you):** In regards to status, how much detail for accepted/rejected is needed? Would supplier ids be sufficient enough for now?
**A (interviewer):** For accepted rows, a count plus the ids is plenty. For rejected rows, ids alone don't meet the requirement. The ERP integration has to be able to tell, programmatically, which row failed and why, so it can act on it without a human reading a message. How you identify a row and how you express the reason is your call.

