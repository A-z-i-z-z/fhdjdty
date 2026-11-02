"""Swarm orchestrator: split the target into units, run one hunter agent per unit in parallel,
verify every claim automatically, deduplicate, and write a report.

Examples (run from the bug-hunting/ folder):

    # Pipeline smoke test, no model calls:
    python -m swarm.orchestrator --config configs/toy.json --backend replay \
        --replay-file examples/replay/toy_claims.jsonl

    # Pilot: 20 Haiku agents, 10 at a time, on the API, capped at $1:
    python -m swarm.orchestrator --config configs/boltons.json --backend api \
        --max-units 20 --concurrency 10 --max-cost-usd 1

Nothing here files issues, opens PRs or contacts anyone. Findings stay in runs/<run-id>/.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import asdict
from pathlib import Path

from .agents import AgentResult, build_prompt, make_backend
from .target import BUG_HUNTING_DIR, TargetConfig, make_units, prepare_checkout
from .verify import Verdict, Verifier, claim_id

STATUS_RANK = {"confirmed_fixed": 2, "confirmed": 1, "rejected": 0}


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", required=True, help="target config JSON")
    ap.add_argument("--backend", choices=["api", "cli", "replay"], default="api")
    ap.add_argument("--model", default="claude-haiku-5-5")
    ap.add_argument("--effort", default="medium", choices=["low", "medium", "high", "xhigh", "max"])
    ap.add_argument("--replay-file")
    ap.add_argument("--max-units", type=int, default=20, help="number of agents to launch")
    ap.add_argument("--only", default="", help="substring filter on unit ids, e.g. strutils.py")
    ap.add_argument("--concurrency", type=int, default=10, help="agents running at once")
    ap.add_argument("--verify-workers", type=int, default=4)
    ap.add_argument("--max-claims", type=int, default=3, help="max claims per agent")
    ap.add_argument("--max-cost-usd", type=float, default=2.0, help="stop launching agents past this")
    ap.add_argument("--repeats", type=int, default=2, help="times a test must fail identically")
    ap.add_argument("--test-timeout", type=int, default=60)
    ap.add_argument("--no-fix-check", action="store_true", help="skip patch + full-suite check")
    ap.add_argument("--isolate-network", action="store_true", help="run tests under unshare -rn")
    ap.add_argument("--runs-dir", default=str(BUG_HUNTING_DIR / "runs"))
    ap.add_argument("--run-id", default=time.strftime("%Y%m%d-%H%M%S"))
    return ap.parse_args(argv)


async def run(args) -> Path:
    cfg = TargetConfig.load(args.config)
    runs = Path(args.runs_dir)
    out = runs / args.run_id
    out.mkdir(parents=True, exist_ok=True)
    root = prepare_checkout(cfg, runs / "_targets")

    units = [u for u in make_units(cfg, root) if args.only in u.unit_id][: args.max_units]
    print(f"[swarm] target {cfg.name} at {root}; {len(units)} units, concurrency {args.concurrency}")

    verifier = Verifier(root, cfg.package, cfg.test_paths, repeats=args.repeats,
                        timeout_s=args.test_timeout, isolate_network=args.isolate_network,
                        check_fixes=not args.no_fix_check)
    if not args.no_fix_check:
        baseline = verifier.compute_baseline()
        print(f"[swarm] baseline suite: {baseline}")
        (out / "baseline.json").write_text(json.dumps(baseline, indent=2))

    backend = make_backend(args.backend, args.model, args.effort, args.replay_file)
    sem = asyncio.Semaphore(args.concurrency)
    pool = ThreadPoolExecutor(args.verify_workers)
    loop = asyncio.get_running_loop()
    spent = 0.0
    skipped_for_budget = 0
    agent_results: list[AgentResult] = []
    records: list[dict] = []  # one per claim: claim + verdict

    async def verify_claim(unit_id: str, claim: dict) -> None:
        claim = dict(claim, id=claim_id(claim), unit=unit_id)
        verdict: Verdict = await loop.run_in_executor(pool, verifier.verify, claim)
        records.append({"claim": claim, "verdict": verdict.to_dict()})
        print(f"[verify] {claim['id']} {verdict.status:<15} {claim['symbol']}: {verdict.reason}")

    async def one_agent(unit) -> None:
        nonlocal spent, skipped_for_budget
        async with sem:
            if spent >= args.max_cost_usd:
                skipped_for_budget += 1
                return
            prompt = build_prompt(cfg, root, unit, args.max_claims)
            res = await backend.hunt(unit, prompt)
        spent += res.cost_usd
        agent_results.append(res)
        tag = f"error: {res.error}" if res.error else f"{len(res.claims)} claim(s)"
        print(f"[agent] {unit.unit_id}: {tag} (${res.cost_usd:.4f})")
        await asyncio.gather(*(verify_claim(unit.unit_id, c) for c in res.claims[: args.max_claims]))

    started = time.time()
    await asyncio.gather(*(one_agent(u) for u in units))
    pool.shutdown(wait=True)

    findings = dedupe(records)
    write_outputs(out, cfg, args, units, agent_results, records, findings,
                  spent, skipped_for_budget, time.time() - started)
    print(f"[swarm] done: {len(findings)} unique confirmed finding(s); report at {out / 'report.md'}")
    return out


def dedupe(records: list[dict]) -> list[dict]:
    """Keep one record per (file, symbol, failure signature), preferring ones with a working fix.

    Assertion failures all share the signature "assert", so several wrong-value claims about the
    same symbol collapse into one finding. Crashes are keyed by exception type and the target
    function that raised it.
    """
    best: dict[tuple, dict] = {}
    for r in records:
        v = r["verdict"]
        if v["status"] == "rejected":
            continue
        key = (r["claim"]["file"], r["claim"]["symbol"], v["signature"])
        if key not in best or STATUS_RANK[v["status"]] > STATUS_RANK[best[key]["verdict"]["status"]]:
            best[key] = r
    return sorted(best.values(), key=lambda r: -STATUS_RANK[r["verdict"]["status"]])


def write_outputs(out, cfg, args, units, agent_results, records, findings, spent, skipped, secs):
    with open(out / "agents.jsonl", "w") as f:
        for r in agent_results:
            f.write(json.dumps(asdict(r)) + "\n")
    with open(out / "claims.jsonl", "w") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")

    for r in findings:
        d = out / "findings" / r["claim"]["id"]
        d.mkdir(parents=True, exist_ok=True)
        (d / "test_repro.py").write_text(r["claim"]["test_code"])
        if r["claim"].get("fix_patch"):
            (d / "fix.patch").write_text(r["claim"]["fix_patch"])
        (d / "finding.json").write_text(json.dumps(r, indent=2))

    reasons: dict[str, int] = {}
    for r in records:
        if r["verdict"]["status"] == "rejected":
            key = r["verdict"]["reason"][:70]
            reasons[key] = reasons.get(key, 0) + 1
    n_claims = len(records)
    n_conf = sum(r["verdict"]["status"] != "rejected" for r in records)
    lines = [
        f"# Bug-hunt run {args.run_id}",
        "",
        f"- Target: **{cfg.name}** ({cfg.repo or cfg.local_path} @ {cfg.ref or 'local'})",
        f"- Backend: `{args.backend}`, model `{args.model}`, effort `{args.effort}`",
        f"- Agents run: {len(agent_results)} of {len(units)} units"
        + (f" ({skipped} skipped by the ${args.max_cost_usd} budget)" if skipped else ""),
        f"- Agent errors: {sum(1 for r in agent_results if r.error)}",
        f"- Cost: ${spent:.4f}, wall time {secs:.0f}s",
        f"- Claims: {n_claims}; reproduced: {n_conf}; rejected: {n_claims - n_conf}",
        f"- Unique confirmed findings: **{len(findings)}** "
        f"({sum(r['verdict']['status'] == 'confirmed_fixed' for r in findings)} with a verified fix)",
        "",
        "Nothing from this run has been filed anywhere. A person should read each finding "
        "before it goes upstream: the verifier proves the test fails, not that the test's "
        "expectation is what the maintainers intend.",
        "",
        "## Findings",
        "",
    ]
    if not findings:
        lines.append("_None._")
    for r in findings:
        c, v = r["claim"], r["verdict"]
        lines += [
            f"### {c['title']}",
            "",
            f"- `{c['file']}` · `{c['symbol']}` · status **{v['status']}** · fix: {v['fix']}",
            f"- Signature: `{v['signature']}`",
            f"- Why: {c['explanation']}",
            f"- Contract: {c['expected_behavior_source']}",
            f"- Files: `findings/{c['id']}/`",
            "",
        ]
    lines += ["## Rejection reasons", ""]
    lines += [f"- {k}: {n}" for k, n in sorted(reasons.items(), key=lambda kv: -kv[1])] or ["_None._"]
    (out / "report.md").write_text("\n".join(lines) + "\n")


def main(argv=None) -> None:
    asyncio.run(run(parse_args(argv)))


if __name__ == "__main__":
    main()
