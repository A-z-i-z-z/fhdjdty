#!/usr/bin/env python3
"""Run a swarm of Claude Haiku agents over the lemma queue.

Each agent gets one lemma, writes a proof, and the verifier compiles it.
On failure the agent gets the Lean error back and tries again, up to
--rounds times. Several agents can work the same lemma with different
strategy nudges (--agents-per-lemma); once one succeeds the rest stop.
Only proofs that pass verify.py are written to proved/.

Backends:
  claude-cli  runs `claude -p` headless (uses your Claude plan's usage)
  api         Anthropic API via the `anthropic` package (needs an API key)
  mock        canned answers from a JSON file, for testing the pipeline

Examples:
  python3 swarm/run_swarm.py --requires core --dry-run
  python3 swarm/run_swarm.py --backend claude-cli --agents-per-lemma 1 --rounds 4
  python3 swarm/run_swarm.py --backend api --tier exercise --concurrency 10
"""
from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import sys
import time
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify  # noqa: E402

ROOT = verify.ROOT
PROMPT_TEMPLATE = (ROOT / "prompts" / "agent.md").read_text()

# Haiku 5.5 rejects non-default sampling parameters, so diversity across
# agents on the same lemma comes from these nudges instead of temperature.
STRATEGIES = [
    "",
    "Strategy nudge: look for an existing library lemma first and try to close the goal with `exact` or `simpa using`.",
    "Strategy nudge: try induction (or strong induction) and keep each step small.",
    "Strategy nudge: try a case split (for example on a remainder mod n) followed by `omega`, `decide` or `simp`.",
    "Strategy nudge: for inequalities, clear denominators first, then use `nlinarith` with explicit square hints.",
    "Strategy nudge: prove one or two helper lemmas first, then combine them in the main theorem.",
]


def lean_version() -> str:
    try:
        return subprocess.run(["lean", "--version"], capture_output=True, text=True, cwd=ROOT).stdout.strip()
    except FileNotFoundError:
        return "Lean 4 (see lean-toolchain)"


def render_prompt(lemma: dict, lean_ver: str, feedback: str, strategy: str) -> str:
    header = verify.HEADERS[lemma["requires"]]
    pre = lemma.get("preamble", "").strip()
    library = ("Mathlib is imported (`import Mathlib`), and `Finset` is open, so `range` means `Finset.range`."
               if lemma["requires"] == "mathlib" else
               "No Mathlib: only Lean core is available. Mathlib tactics such as `ring`, `linarith`, `nlinarith`, `norm_num` and `positivity` do not exist here; use `omega`, `simp`, `decide`, `induction`, `grind`.")
    fb = ""
    if feedback:
        fb = ("\n## Your previous attempt failed\n\n" + feedback +
              "\n\nFix the problem and answer again with one full ```lean block.")
    if strategy:
        fb = "\n" + strategy + "\n" + fb
    return PROMPT_TEMPLATE.format(
        lean_version=lean_ver, library_line=library, header=header,
        preamble_display=("\n" + pre + "\n") if pre else "",
        name=lemma["name"], statement=lemma["statement"], id=lemma["id"],
        feedback=fb)


# ---------------------------------------------------------------- backends

class ClaudeCLI:
    """`claude -p` with every tool turned off: one prompt in, one answer out."""

    def __init__(self, model: str, timeout: int):
        self.model, self.timeout = model, timeout

    async def ask(self, prompt: str) -> tuple[str, dict]:
        cmd = ["claude", "-p", "--model", self.model, "--output-format", "json",
               "--tools", "", "--no-session-persistence", "--strict-mcp-config"]
        proc = await asyncio.create_subprocess_exec(
            *cmd, stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE, cwd="/tmp")
        try:
            out, err = await asyncio.wait_for(proc.communicate(prompt.encode()), self.timeout)
        except asyncio.TimeoutError:
            proc.kill()
            return "", {"error": "claude cli timeout"}
        try:
            data = json.loads(out.decode())
        except json.JSONDecodeError:
            return "", {"error": (err.decode() or out.decode())[:500]}
        meta = {"cost_usd": data.get("total_cost_usd"), "usage": data.get("usage")}
        if data.get("is_error"):
            meta["error"] = str(data.get("result"))[:500]
        return data.get("result") or "", meta


class AnthropicAPI:
    def __init__(self, model: str, timeout: int, effort: str):
        import anthropic  # pip install anthropic
        self.anthropic = anthropic
        self.client = anthropic.AsyncAnthropic(timeout=timeout, max_retries=4)
        self.model, self.effort = model, effort

    async def ask(self, prompt: str) -> tuple[str, dict]:
        try:
            resp = await self.client.messages.create(
                model=self.model, max_tokens=16000,
                output_config={"effort": self.effort},
                messages=[{"role": "user", "content": prompt}])
        except self.anthropic.RateLimitError as e:
            return "", {"error": f"rate limited: {e}"}
        except self.anthropic.APIStatusError as e:
            return "", {"error": f"api error {e.status_code}: {e.message}"[:500]}
        except self.anthropic.APIConnectionError as e:
            return "", {"error": f"connection error: {e}"}
        if resp.stop_reason == "refusal":
            return "", {"error": "refusal"}
        text = "".join(b.text for b in resp.content if b.type == "text")
        u = resp.usage
        return text, {"usage": {"input_tokens": u.input_tokens, "output_tokens": u.output_tokens}}


class Mock:
    """answers.json: {"lemma_id": ["answer for round 1", "answer for round 2", ...]}"""

    def __init__(self, path: Path):
        self.answers = json.loads(path.read_text())
        self.calls: dict[str, int] = {}

    async def ask(self, prompt: str) -> tuple[str, dict]:
        lid = next(k for k in self.answers if f"Theorem id: `{k}`" in prompt)
        i = self.calls.get(lid, 0)
        self.calls[lid] = i + 1
        seq = self.answers[lid]
        return seq[min(i, len(seq) - 1)], {"mock": True}


# ---------------------------------------------------------------- swarm

class Swarm:
    def __init__(self, args, lemmas: list[dict], backend, out: Path):
        self.args, self.lemmas, self.backend, self.out = args, lemmas, backend, out
        self.agent_sem = asyncio.Semaphore(args.concurrency)
        self.verify_sem = asyncio.Semaphore(args.verify_workers)
        self.solved: dict[str, dict] = {}
        self.lean_ver = lean_version()
        self.log = (out / "attempts.jsonl").open("a")

    def record(self, row: dict):
        self.log.write(json.dumps(row, ensure_ascii=False) + "\n")
        self.log.flush()

    async def agent(self, lemma: dict, agent_no: int):
        strategy = STRATEGIES[agent_no % len(STRATEGIES)]
        feedback = ""
        async with self.agent_sem:
            for rnd in range(1, self.args.rounds + 1):
                if lemma["id"] in self.solved:
                    return
                prompt = render_prompt(lemma, self.lean_ver, feedback, strategy)
                t0 = time.time()
                answer, meta = await self.backend.ask(prompt)
                gen_s = time.time() - t0
                if meta.get("error"):
                    self.record(dict(lemma=lemma["id"], agent=agent_no, round=rnd, stage="backend",
                                     ok=False, errors=[meta["error"]], meta=meta))
                    await asyncio.sleep(5)
                    continue
                async with self.verify_sem:
                    res = await asyncio.to_thread(verify.verify_answer, lemma, answer, self.args.project, self.args.timeout)
                self.record(dict(lemma=lemma["id"], agent=agent_no, round=rnd, stage=res.stage, ok=res.ok,
                                 errors=res.errors[:5], gen_seconds=round(gen_s, 1),
                                 verify_seconds=round(res.seconds, 1), meta=meta, answer=answer))
                status = "PROVED" if res.ok else f"fail:{res.stage}"
                print(f"  {lemma['id']:<28} agent {agent_no} round {rnd}: {status}", flush=True)
                if res.ok and lemma["id"] not in self.solved:
                    self.solved[lemma["id"]] = dict(agent=agent_no, round=rnd)
                    (self.out / "proved").mkdir(exist_ok=True)
                    (self.out / "proved" / f"{lemma['id']}.lean").write_text(res.lean_file)
                    return
                feedback = res.feedback()

    async def run(self):
        tasks = [self.agent(lem, k) for lem in self.lemmas for k in range(self.args.agents_per_lemma)]
        await asyncio.gather(*tasks)
        self.log.close()


def summarize(out: Path, lemmas: list[dict], solved: dict, args) -> dict:
    rows = [json.loads(l) for l in (out / "attempts.jsonl").read_text().splitlines() if l.strip()]
    by_tier: dict[str, list[int]] = {}
    for lem in lemmas:
        t = by_tier.setdefault(lem["tier"], [0, 0])
        t[0] += lem["id"] in solved
        t[1] += 1
    stages: dict[str, int] = {}
    for r in rows:
        stages[r["stage"]] = stages.get(r["stage"], 0) + 1
    cost = sum((r.get("meta") or {}).get("cost_usd") or 0 for r in rows)
    summary = dict(
        backend=args.backend, model=args.model, agents_per_lemma=args.agents_per_lemma, rounds=args.rounds,
        lemmas=len(lemmas), proved=len(solved), attempts=len(rows),
        by_tier={k: f"{v[0]}/{v[1]}" for k, v in by_tier.items()},
        attempt_outcomes=stages, reported_cost_usd=round(cost, 4),
        solved={k: v for k, v in sorted(solved.items())},
        unsolved=sorted(l["id"] for l in lemmas if l["id"] not in solved),
    )
    if args.library_check:
        lib = {}
        for lem in lemmas:
            if lem["id"] in solved:
                lib[lem["id"]] = verify.library_check(lem, args.project)
        summary["already_in_library"] = {k: v for k, v in lib.items() if v}
    (out / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False))
    return summary


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--queue", type=Path, default=verify.DEFAULT_QUEUE)
    ap.add_argument("--project", type=Path, default=ROOT)
    ap.add_argument("--backend", choices=["claude-cli", "api", "mock"], default="claude-cli")
    ap.add_argument("--model", default="claude-haiku-5-5")
    ap.add_argument("--effort", default="medium", help="api backend only")
    ap.add_argument("--mock-answers", type=Path)
    ap.add_argument("--only", nargs="*", help="lemma ids")
    ap.add_argument("--tier", choices=["calibration", "exercise", "stretch"])
    ap.add_argument("--requires", choices=["core", "mathlib"])
    ap.add_argument("--agents-per-lemma", type=int, default=1)
    ap.add_argument("--rounds", type=int, default=4, help="attempts per agent (each retry sees the last Lean error)")
    ap.add_argument("--concurrency", type=int, default=10, help="agents running at once")
    ap.add_argument("--verify-workers", type=int, default=2, help="Lean processes at once (Mathlib needs ~2-4 GB RAM each)")
    ap.add_argument("--timeout", type=int, default=300, help="seconds per Lean check")
    ap.add_argument("--agent-timeout", type=int, default=600)
    ap.add_argument("--library-check", action="store_true", help="after the run, ask `exact?` if proved lemmas already exist")
    ap.add_argument("--out", type=Path, default=ROOT / "results")
    ap.add_argument("--dry-run", action="store_true", help="print the first prompt and exit")
    args = ap.parse_args()

    lemmas = list(verify.load_queue(args.queue).values())
    if args.only:
        lemmas = [l for l in lemmas if l["id"] in args.only]
    if args.tier:
        lemmas = [l for l in lemmas if l["tier"] == args.tier]
    if args.requires:
        lemmas = [l for l in lemmas if l["requires"] == args.requires]
    if not lemmas:
        ap.error("no lemmas selected")

    if args.dry_run:
        print(render_prompt(lemmas[0], lean_version(), "", ""))
        print(f"\n[dry run] {len(lemmas)} lemmas x {args.agents_per_lemma} agents x up to {args.rounds} rounds "
              f"= at most {len(lemmas) * args.agents_per_lemma * args.rounds} model calls")
        return 0

    if args.backend == "claude-cli":
        backend = ClaudeCLI(args.model, args.agent_timeout)
    elif args.backend == "api":
        backend = AnthropicAPI(args.model, args.agent_timeout, args.effort)
    else:
        if not args.mock_answers:
            ap.error("--backend mock needs --mock-answers")
        backend = Mock(args.mock_answers)

    run_id = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    out = args.out / run_id
    out.mkdir(parents=True)
    print(f"run {run_id}: {len(lemmas)} lemmas, {args.agents_per_lemma} agent(s) each, "
          f"{args.rounds} rounds, backend={args.backend} model={args.model}")
    swarm = Swarm(args, lemmas, backend, out)
    asyncio.run(swarm.run())
    s = summarize(out, lemmas, swarm.solved, args)
    print(json.dumps({k: s[k] for k in ("proved", "lemmas", "by_tier", "attempt_outcomes", "reported_cost_usd")}, indent=2))
    print(f"results in {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
