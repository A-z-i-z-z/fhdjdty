# Research-level queue, 100 agents, 2026-11-03

Queue: `queue/research100.jsonl`, built by `queue/make_research.py` from two Apache-2.0 sources:

- [PutnamBench](https://github.com/trishullab/PutnamBench) (commit a635813), Lean 4 statements of Putnam problems 2000-2025.
  For "find the answer" problems the official answer (stored by PutnamBench next to the `sorry`) is filled in.
- [formal-conjectures](https://github.com/google-deepmind/formal-conjectures) (commit 7bdc4a4), statements tagged
  `research solved` (proved in the literature, not formalized) or `research open` (open problems).

500 candidates were extracted; the 311 that elaborate against plain Mathlib v4.35.0 (no formal-conjectures helper
library) were kept, and 100 sampled with a fixed seed:

| Tier | Count | What it is |
|---|---|---|
| putnam_easy | 30 | Putnam A1/A2/B1/B2 |
| putnam_hard | 30 | Putnam A5/A6/B5/B6 |
| fc_solved | 30 | Erdős problems, OEIS, Green's list, ... solved in papers |
| fc_open | 10 | open problems. Canaries: a "proof" here would mean a verifier bug or a misformalization |

Setup: 1 Haiku 5.5 agent per lemma (`claude -p`, tools off), up to 4 rounds with Lean feedback, Lean 4.35.0 + Mathlib v4.35.0.

## Results

Run 1 used the original prompt. Haiku often **declined to answer** on hard statements ("I can't verify this, so I'm
not returning a code block"), which made 261 of 360 attempts parse failures. Run 1 also hit a verifier bug: theorems
declared inside a namespace were rejected at the `#print axioms` step (fixed in `swarm/verify.py`, test added). Run 2
re-ran the 77 unsolved statements with a prompt line saying a checker compiles the answer and a best attempt is
always wanted.

| Tier | Run 1 | Run 2 (unsolved only) | Total | Cost run 1 + run 2 (USD) |
|---|---|---|---|---|
| putnam_easy | 19/30 | +1 | **20/30** | 1.42 + 0.51 |
| putnam_hard | 4/30 | +2 | **6/30** | 1.06 + 0.82 |
| fc_solved | 0/30 | +1 | **1/30** | 0.22 + 0.27 |
| fc_open | 0/10 | 0 | **0/10** | 0.05 + 0.07 |
| **total** | 23 | +4 | **27/100** | **4.43** (658 calls) |

Proved hard Putnam problems: 2000 A6, 2005 A5, 2007 B5, 2008 B5, 2010 B6, 2021 A5.

All 27 proofs in `../../proved-research/` were re-compiled independently with `lake env lean`: 0 errors, no `sorry`,
axioms only from {propext, Classical.choice, Quot.sound}.

## Notes for a human reader

- The one formal-conjectures "solved" statement that fell, `Erdos423 erdos_423.variants.nondecreasing`
  ("a(n) - n is nondecreasing", credited to Bolan and Tang), was proved in ~30 lines using only that the sequence is
  strictly increasing, which is part of the definition. As formalized the statement looks much weaker than the
  research result it is attributed to. Possibly a misformalization; worth a human look before telling anyone.
  Nothing was reported or submitted upstream.
- No open problem was "proved", so the canaries found no verifier hole.
- In run 2 most failures moved from "no answer" to `sorry` in the answer (policy) or compile errors: Haiku now tries,
  but cannot finish multi-page arguments in 4 rounds.

## Where Haiku breaks down

Roughly: textbook and olympiad exercises ≈ 99%, easy Putnam ≈ 67%, hard Putnam ≈ 20%, research-level ≈ 0-3%.
More agents per lemma or more rounds would mainly help the Putnam tiers; research statements need a different
approach (decomposition into lemmas, a stronger model for planning, library search tools).
