# pass@8 retry on the 73 unsolved research-level statements, 2026-11-03

Same queue (`queue/research100.jsonl`) and prompt as run 2 of `../2026-11-03-research100/`.
Only the 73 statements still unsolved after runs 1-2, now with 8 Haiku 5.5 agents per statement
(different strategy nudges), up to 4 rounds each with Lean feedback.
`--agents-per-lemma 8 --rounds 4 --concurrency 16 --verify-workers 4`, about 4 h 15 min wall time.

| Tier | Unsolved before | Newly proved | Cumulative (of 100) | Calls | Cost (USD) |
|---|---|---|---|---|---|
| putnam_easy | 10 | 3 (2014 B1, 2021 B2, 2023 A1) | **23/30** | 278 | 3.78 |
| putnam_hard | 24 | 2 (2001 B6, 2006 B6) | **8/30** | 753 | 3.35 |
| fc_solved | 29 | 0 | **1/30** | 928 | 1.86 |
| fc_open | 10 | 0 | **0/10** | 320 | 0.46 |
| **total** | 73 | **5** | **32/100** | 2279 | **9.45** |

All 5 new proofs (108-333 lines) were re-compiled independently with `lake env lean`: 0 errors, statements
match the queue, no banned tokens, standard axioms only. They are in `../../proved-research/`.

Cost over the three research runs: $4.43 + $9.45 = $13.88.

Takeaway: 8x the attempts bought 5 more Putnam problems (about $1.90 per new proof) and nothing at research
level. Failures were mostly compile errors and `sorry`-containing partial proofs, so more brute force has a
low return here; decomposition into lemmas or a stronger planner is the next lever.

## Erdős 423 check

The one formal-conjectures "research solved" statement Haiku proved, `erdos_423.variants.nondecreasing`
("a(n) - n is nondecreasing"), is formalized correctly. It is trivial: the definition makes the sequence strictly
increasing, and any strictly increasing integer sequence has a(n) - n nondecreasing. erdosproblems.com/423 mentions
it as a side remark ("In fact, the sequence a_n - n is nondecreasing and unbounded"). The research content is the
unbounded part, a_n ≥ n + Ω(log log n) (Tang, arXiv:2603.09939). So the issue is a "research solved" label on an
easy half, not a wrong statement. Upstream already knows: open PRs google-deepmind/formal-conjectures#7668 (whose
description says the same thing) and #7622 add proofs. Nothing was reported or submitted.
