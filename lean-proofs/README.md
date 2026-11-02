# Lean proof search swarm (pilot)

Many cheap Claude Haiku 5.5 agents each try to prove one Lean 4 theorem. Lean checks every attempt, so a wrong proof simply fails to compile and only correct proofs survive. This folder is a 20-lemma pilot to measure how often that works before spending real usage on it.

Nothing here is submitted to Mathlib.

```
lean-proofs/
  queue/lemmas.jsonl      the 20 target statements (the lemma queue)
  prompts/agent.md        the prompt each agent gets
  swarm/verify.py         compile-check verifier (the only judge of success)
  swarm/run_swarm.py      runs the agents, retries with Lean errors, writes results
  tests/test_verify.py    verifier tests (core Lean, no Mathlib needed)
  lakefile.toml           pins Mathlib v4.35.0 so the verifier has a Mathlib environment
```

## How it works

1. `run_swarm.py` takes each lemma from the queue and starts `--agents-per-lemma` agents on it (default 1, so 20 agents for the pilot).
2. Each agent gets `prompts/agent.md` filled in with the statement and answers with one ```lean block.
3. `verify.py` checks the answer. If it fails, the agent gets the Lean error message and tries again, up to `--rounds` times (default 4).
4. The first proof that passes is written to `results/<run>/proved/<id>.lean`; other agents on that lemma stop. Every attempt is logged in `results/<run>/attempts.jsonl` and totals go to `summary.json`.

Agents on the same lemma get different strategy nudges (library lemma first, induction, case split, nlinarith, helper lemmas), because Haiku 5.5 does not accept a temperature setting.

## What the verifier accepts

A proof counts only if all of these hold:

- The main theorem's statement is exactly the queued one. The verifier rebuilds the file from its own copy of the statement and only takes the proof text from the agent.
- Anything the agent puts before the main theorem is a `theorem`/`lemma` helper. No `def`, `instance`, `open`, notation or macros, because those could change what the statement means.
- No `sorry`, `admit`, `axiom`, `native_decide`, `#exit`, `run_cmd`, debug options, or extra top-level commands hidden after the proof.
- Lean compiles the file with zero errors and no `sorry` warnings.
- `#print axioms` lists only `propext`, `Classical.choice` and `Quot.sound`.

`tests/test_verify.py` checks each of these rejection paths plus real compiles of good and bad proofs.

## How the 20 lemmas were chosen

The goal of a pilot is a trustworthy success rate, not new mathematics, so the list is built to make the results easy to read:

- **Known answer.** Every statement is true and has a known proof, so a failure means the agents (or the prompt) are weak, never that the target was impossible.
- **Checkable without people.** Each is a single self-contained statement over ℕ, ℤ, ℚ or ℝ with no new definitions beyond two tiny recursive ones in the core set.
- **Three difficulty tiers**, so the run shows where Haiku stops being useful:
  - `calibration` (6): already in Lean core or Mathlib (for example `Nat.mul_mod`, `Nat.succ_dvd_centralBinom`, Nicomachus' sum of cubes). This measures whether agents can find and use library lemmas.
  - `exercise` (10): textbook facts that need a short real proof: induction, case splits mod n, two-step inequalities (`n² < 2ⁿ` for n ≥ 5, odd squares are 1 mod 8, AM-GM for three cubes).
  - `stretch` (4): multi-step proofs where cheap agents are expected to struggle: `30 ∣ n⁵ - n`, a telescoping product, partial Basel sums below 2, and Nesbitt's inequality.
- **Two environments.** 6 lemmas need only Lean core, so the whole loop runs on any machine with Lean installed. 14 need Mathlib, which is where real contributions would live.
- **Unambiguous statements.** Each was written so the obvious reading is the formal one (explicit casts, explicit hypotheses like `0 < n`), so an agent cannot "win" through a loophole in the statement.

None of these are Mathlib contribution material. `--library-check` asks `exact?` whether a proved statement already exists in the library, which is the first filter any future contribution would need.

## Running it

Requirements: Python 3.10+, and Lean via [elan](https://github.com/leanprover/elan). For the Mathlib lemmas, fetch the prebuilt Mathlib once (about 5 GB):

```bash
cd lean-proofs
lake exe cache get
```

Then:

```bash
python3 -m unittest discover -s tests -v                  # verifier tests
python3 swarm/verify.py --check-statements                # every statement elaborates
python3 swarm/run_swarm.py --dry-run                      # show a prompt and the call budget

# real runs
python3 swarm/run_swarm.py --requires core                # 6 core lemmas, no Mathlib needed
python3 swarm/run_swarm.py                                # all 20, 1 agent each, 4 rounds
python3 swarm/run_swarm.py --agents-per-lemma 3 --library-check
```

Backends:

- `--backend claude-cli` (default) runs `claude -p --model claude-haiku-5-5` with all tools off, so it draws on your Claude plan's usage rather than API billing.
- `--backend api` uses the Anthropic API (`pip install anthropic`, set `ANTHROPIC_API_KEY`). At Haiku 5.5's $0.10 / $0.50 per million tokens, the full pilot (at most 80 calls) should cost well under $1.
- `--backend mock --mock-answers file.json` replays canned answers to test the pipeline.

`--verify-workers` limits how many Lean processes run at once. Each Mathlib check needs a few GB of RAM, so this, not the model, is usually the bottleneck when scaling up.

## What was and wasn't tested

- Verified in development: all verifier tests pass against Lean 4.35.0 core, the 6 core statements elaborate, and a mock run exercised the full retry and logging loop.
- Not yet run: the 14 Mathlib statements were not compiled locally (the Mathlib cache download was blocked in the build environment). The `lean-proofs` GitHub Actions workflow fetches Mathlib and checks every statement on each pull request.
- No real agent run yet. The first one is a cheap way to get the baseline numbers.

## Scaling up later

If the pilot's exercise and stretch hit rates look good, the next step is swapping the queue for real targets: open statements marked as solved-but-unformalized in collections such as Google DeepMind's formal-conjectures repository, or small lemmas from Mathlib's TODO lists. Those need a human read of every result before anything goes upstream, and contributions should follow Mathlib's own review process.
