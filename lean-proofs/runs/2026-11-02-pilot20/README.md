# First real pilot run, 2026-11-02

- 20 lemmas, 1 Haiku 5.5 agent each (`claude -p`, all tools off), up to 4 rounds.
- Lean 4.35.0 + Mathlib v4.35.0 (prebuilt cache), cloud container, 4 CPUs.
- Command: `python3 swarm/run_swarm.py --agents-per-lemma 1 --rounds 4 --concurrency 6 --verify-workers 3 --library-check`

| Tier | Proved | First-try |
|---|---|---|
| calibration | 6/6 | 5/6 (core_mul_mod needed a 2nd round) |
| exercise | 10/10 | 10/10 |
| stretch | 4/4 | 4/4 |
| **total** | **20/20** | **19/20** |

- 21 model calls in total; reported cost $0.0369 (about $0.0018 per lemma), drawn from plan usage.
- Wall time about 2 minutes, dominated by Mathlib compile checks (~8-10 s each).
- Every proof in `../../proved/` was re-checked independently with `lake env lean`: 0 errors, axioms only from {propext, Classical.choice, Quot.sound}.
- `exact?` found 5 statements already in the library (core_mul_mod, core_replicate_sum, ml_fib_sum, ml_succ_dvd_central_binom, ml_central_binom_even).

Takeaway: this queue is too easy to separate tiers. The next pilot needs harder targets.
