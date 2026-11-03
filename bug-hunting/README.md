# Bug-hunting swarm (pilot)

Many cheap Haiku agents each read one small slice of an open-source Python project and try to
prove a bug in it. A claim only counts if it comes with a pytest test that a machine can run
and watch fail. Everything else is thrown away automatically.

```
target repo (pinned commit)
   │  split into units: groups of top-level functions/classes, ≤300 lines each
   ▼
hunter agents (one per unit, N in parallel)  ──►  claims: test_code + optional fix_patch
   ▼
verifier (per claim, fresh copy of the target each time)
   1. static gate     parses, imports the package, names the symbol, no banned modules,
                      no `assert False` / pytest.fail / self-raised AssertionError
   2. reproduce       test must FAIL twice with the same signature, and the failure must be
                      an assertion in the test or an exception raised inside the package.
                      NameError/TypeError/import errors in the test itself are rejected.
   3. fix check       if a patch is given: it may touch only package files, the claim's test
                      must pass after it, and no test that passed before may now fail
   ▼
dedupe (file, symbol, signature) ──► runs/<run-id>/report.md + findings/<id>/
```

Statuses: `confirmed_fixed` (reproduced and the agent's patch fixes it without breaking the
suite), `confirmed` (reproduced, no working patch), `rejected` (with the reason).

Nothing is filed, posted or opened anywhere. Findings stay in `runs/`, which is git-ignored.

## Pilot target: boltons 25.0.0

[boltons](https://github.com/mahmoud/boltons) is a pure-Python utility library: about 40
independent modules (`strutils`, `iterutils`, `cacheutils`, `urlutils`, ...), each with clear
docstrings and its own pytest suite, and no compiled code or services to stand up. That makes
it a good fit for "one agent, one slice, one test". The config pins tag `25.0.0` at commit
`c23dbdad` so every run sees the same code. Swap in another target by writing a config like
`configs/boltons.json`.

## Running it

```bash
cd bug-hunting
pip install -r requirements.txt

# 1. Smoke test, no model calls: replays canned claims against a toy package with 3 planted bugs
python -m pytest -q tests

# 2. Pilot on the subscription (Claude Code CLI, `claude -p`): 20 agents, 5 at a time
python -m swarm.orchestrator --config configs/boltons.json --backend cli --model haiku \
    --max-units 20 --concurrency 5

# 3. Same pilot on the API (needs ANTHROPIC_API_KEY), with a hard $1 cap
python -m swarm.orchestrator --config configs/boltons.json --backend api \
    --max-units 20 --concurrency 10 --max-cost-usd 1
```

Useful flags: `--only strutils.py` (focus on one module), `--effort low|medium|high`,
`--max-claims 3` (per agent), `--repeats 2`, `--no-fix-check` (skip patch + full-suite run),
`--isolate-network` (run tests under `unshare -rn`).

Outputs in `runs/<run-id>/`: `report.md`, `findings/<id>/{test_repro.py,fix.patch,finding.json}`,
`claims.jsonl` (every claim with its verdict), `agents.jsonl` (per-agent tokens, cost, errors),
`baseline.json`.

## What has been tested

- `tests/test_pipeline.py` replays 10 canned claims against `examples/toy_target` and checks
  each verdict: 3 real bugs confirmed (one an in-library crash), 1 duplicate merged, a claim
  that passes on current code, a test with a typo (NameError), a wrong call signature
  (TypeError), `assert False`, a `subprocess` import, and a fix that breaks the existing suite
  are all handled as expected.
- A live run with `--backend cli --model haiku` against the toy target: 2 agents found all 3
  planted bugs, each with a patch that passed the fix check, for about $0.006 in total.
- First real run on boltons 25.0.0 (2026-11-02): 20 Haiku agents via `--backend cli`, 5 at a
  time, $0.158 and 155 s in total. 29 claims, 28 reproduce on the pinned commit after the two
  verifier fixes below (all 29 claims were re-verified with `--backend replay`, no new model
  calls). 3 of the 28 come with a patch that fixes the test without breaking any of the 423
  suite tests. The 28 still need a human read before anything goes upstream.
- 100-agent run on boltons (2026-11-02): `--max-unit-lines 150` splits boltons into 96 slices,
  one Haiku agent each, 10 at a time. $0.784 and 340 s in total, 0 agent errors. 102 claims,
  101 reproduce, 94 unique after dedupe, 13 with a patch that passes all 423 suite tests. It
  re-found the first pilot's bugs and many new ones. A 99% reproduction rate says the tests
  fail, not that every expectation is right, so precision is unknown until someone triages.
- 100-agent run on more-itertools `master` (2026-11-03): 99 slices of 80 lines, $1.45, 272 s.
  22 claims, 18 reproduce (2 of them only after the gate fix below). Manual triage: 4 worth
  filing, 5 minor, 5 already reported upstream (4 closed as not planned), 3 debatable, 1 wrong.
  The static gate now also accepts tests that import the package root, because more-itertools
  re-exports everything from `__init__`.
- That run exposed two verifier problems, now fixed: the "test must name the symbol" rule
  rejected 12 claims whose tests reach a dunder or private helper through its class or a public
  caller (it now accepts any part of the dotted symbol, or an import of the claimed module), and
  pytest 9 cannot load boltons' `conftest.py`, so the baseline suite silently ran 0 tests and
  every fix check passed vacuously (pytest is now pinned below 9, and a fix check refuses to
  report `ok` without a baseline).

## Scaling past the pilot

- **Subscription vs API.** The `cli` backend runs on the logged-in Claude subscription and is
  fine for tens of agents, but hundreds or thousands in parallel will hit the subscription's
  rate and usage limits. Use `--backend api` for a real swarm. At Haiku 5.5's list price
  ($0.10 / $0.50 per million input/output tokens) one agent on a 300-line slice costs roughly
  $0.001–0.003, so 1,000 agents is in the low single-digit dollars. `--max-cost-usd` stops
  launching new agents once the cap is reached. The Message Batches API would halve that again
  if latency doesn't matter (not wired up yet).
- **More agents per slice.** Agents are independent, so running the same unit 3–5 times with
  different `--effort` or temperature-free reruns raises recall; dedupe already merges repeats.
- **Isolation.** The verifier runs model-written code. It strips API keys from the environment,
  bans obvious dangerous imports, and can cut network access, but it is not a sandbox. Run big
  swarms in a disposable container or VM.
- **Human review stays.** The verifier proves the test fails, not that the test's expectation
  is what maintainers intend. Each finding carries the docstring or doc line the agent relied
  on (`expected_behavior_source`) so a person can check it quickly before anything goes
  upstream, and many projects ask that bug reports and bounty submissions be human-reviewed.

## Layout

```
configs/            target configs (boltons.json, toy.json)
prompts/hunter.md   the agent prompt
swarm/target.py     pinned checkout + splitting files into units
swarm/agents.py     api / cli / replay backends, claim schema, cost tracking
swarm/verify.py     static gate, reproduction, fix check
swarm/orchestrator.py  parallel run, dedupe, report
examples/           toy package with planted bugs + canned claims
tests/              end-to-end pipeline test
```
