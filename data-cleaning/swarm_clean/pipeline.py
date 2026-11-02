"""End-to-end pilot: fetch -> rules -> canaries -> swarm labels (A, B) ->
tie-breaks (C) -> verified verdicts -> clean dataset + report.

    python -m swarm_clean.pipeline --backend mock --limit 300
    python -m swarm_clean.pipeline --backend api --limit 500 --workers 64
    python -m swarm_clean.pipeline --backend claude-cli --limit 200 --workers 8
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import sys
import time
from pathlib import Path

from . import canaries as canary_mod
from . import fetch, rules, verify
from .backends import Job, make_backend
from .prompts import PROMPT_VERSION, SYSTEMS, user_message

LICENSE_NOTE = (
    "Derived from databricks-dolly-15k (c) Databricks, Inc., licensed CC BY-SA 3.0 "
    "(https://creativecommons.org/licenses/by-sa/3.0/). This cleaned subset and its "
    "labels are shared under the same license."
)

# Haiku 5.5 list prices per million tokens (prompts up to 100K tokens).
PRICE_IN, PRICE_OUT = 0.10, 0.50


class LabelCache:
    """Append-only JSONL cache so an interrupted run resumes without paying twice.

    The key hashes the model, prompt and record text, so editing a prompt or a
    cleaning rule invalidates exactly the affected entries. Errors are not
    cached: they are retried on the next run.
    """

    def __init__(self, path: Path):
        self.path = path
        self.entries: dict[str, dict] = {}
        if path.exists():
            for line in path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    row = json.loads(line)
                    self.entries[row["key"]] = row
        path.parent.mkdir(parents=True, exist_ok=True)
        self.fh = open(path, "a", encoding="utf-8")

    @staticmethod
    def key(model_tag: str, job: Job) -> str:
        blob = "\x1f".join([model_tag, PROMPT_VERSION, job.framing, SYSTEMS[job.framing],
                            user_message(job.record, job.framing)])
        return hashlib.blake2b(blob.encode(), digest_size=16).hexdigest()

    def put(self, key: str, job: Job, result: dict) -> None:
        if "label" not in result:
            return
        row = {"key": key, "id": job.record["id"], "framing": job.framing, **result}
        self.entries[key] = row
        self.fh.write(json.dumps(row) + "\n")
        self.fh.flush()

    def close(self) -> None:
        self.fh.close()


def run_pass(backend, cache: LabelCache, model_tag: str, framing: str, records: list[dict],
             workers: int) -> tuple[dict[str, dict], dict]:
    """Label every record with one framing. Returns (labels by record id, stats)."""
    labels: dict[str, dict] = {}
    todo: list[tuple[str, Job]] = []
    for rec in records:
        job = Job(id=f"{framing}-{rec['id']}", framing=framing, record=rec)
        key = cache.key(model_tag, job)
        hit = cache.entries.get(key)
        if hit:
            labels[rec["id"]] = hit["label"]
        else:
            todo.append((key, job))
    stats = {"framing": framing, "cached": len(labels), "called": len(todo), "errors": 0,
             "input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0}
    if not todo:
        return labels, stats
    keys = {job.id: key for key, job in todo}
    done = [0]
    started = time.time()

    def on_result(job: Job, res: dict) -> None:
        cache.put(keys[job.id], job, res)
        done[0] += 1
        if done[0] % 50 == 0 or done[0] == len(todo):
            rate = done[0] / max(1e-6, time.time() - started)
            print(f"  pass {framing}: {done[0]}/{len(todo)} ({rate:.1f}/s)", flush=True)

    results = backend.label([job for _, job in todo], workers=workers, on_result=on_result)
    errors: dict[str, int] = {}
    for _, job in todo:
        res = results.get(job.id, {"error": "missing result"})
        usage = res.get("usage") or {}
        stats["input_tokens"] += usage.get("input_tokens", 0)
        stats["output_tokens"] += usage.get("output_tokens", 0)
        stats["cost_usd"] += usage.get("cost_usd", 0.0)
        if "label" in res:
            labels[job.record["id"]] = res["label"]
        else:
            stats["errors"] += 1
            kind = res["error"].split(":")[0]
            errors[kind] = errors.get(kind, 0) + 1
    stats["error_kinds"] = errors
    return labels, stats


def write_jsonl(path: Path, rows: list[dict]) -> None:
    with open(path, "w", encoding="utf-8") as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--backend", choices=["mock", "api", "claude-cli"], default="mock")
    ap.add_argument("--model", help="api: model id (default claude-haiku-5-5); claude-cli: alias (default haiku)")
    ap.add_argument("--effort", default="low", choices=["low", "medium", "high"])
    ap.add_argument("--batch", action="store_true", help="api only: use the Message Batches API (half price, async)")
    ap.add_argument("--workers", type=int, default=32, help="concurrent labelers")
    ap.add_argument("--input", type=Path, help="local Dolly-format JSONL instead of downloading")
    ap.add_argument("--limit", type=int, default=300, help="sample size; 0 = whole dataset")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--canary-fraction", type=float, default=0.05)
    ap.add_argument("--min-quality", type=float, default=3.0)
    ap.add_argument("--min-canary-recall", type=float, default=0.8,
                    help="fail the run if the swarm catches fewer canaries than this")
    ap.add_argument("--out", type=Path, default=Path("out"))
    ap.add_argument("--plan", action="store_true", help="show how many calls a run would make, then stop")
    args = ap.parse_args(argv)

    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    # 1. Load
    src = args.input or fetch.download(out / "raw" / "databricks-dolly-15k.jsonl")
    records = fetch.load(src, limit=args.limit or None, seed=args.seed)
    print(f"loaded {len(records)} records from {src}")

    # 2. Deterministic rules
    kept, rejected = rules.run(records)
    print(f"rules: kept {len(kept)}, rejected {len(rejected)}")

    # 3. Canaries, shuffled in among the real records
    canaries = canary_mod.make(kept, fraction=args.canary_fraction, seed=args.seed)
    stream = kept + canaries
    random.Random(args.seed).shuffle(stream)
    print(f"canaries: {len(canaries)} hidden among {len(stream)} records")

    if args.plan:
        calls = 2 * len(stream)
        est = calls * (900 * PRICE_IN + 250 * PRICE_OUT) / 1e6
        print(f"plan: {calls} labeling calls plus roughly 10-25% tie-breaks; "
              f"rough API cost at Haiku 5.5 list price: ${est:.2f} (half with --batch)")
        return 0

    backend = make_backend(args.backend, args.model, args.effort, args.batch)
    model_tag = f"{backend.name}:{args.model or 'default'}:{args.effort}"
    cache = LabelCache(out / "cache" / "labels.jsonl")

    # 4. Two independent passes
    try:
        labels_a, stats_a = run_pass(backend, cache, model_tag, "A", stream, args.workers)
        labels_b, stats_b = run_pass(backend, cache, model_tag, "B", stream, args.workers)

        # 5. Tie-breaks where A and B disagree or one failed
        contested = [r for r in stream
                     if verify.needs_tiebreak(labels_a.get(r["id"]), labels_b.get(r["id"]))]
        labels_c, stats_c = run_pass(backend, cache, model_tag, "C", contested, args.workers)
    finally:
        cache.close()
    print(f"tie-breaks: {len(contested)}")

    # 6. Verdicts
    decisions: dict[str, dict] = {}
    for rec in stream:
        rid = rec["id"]
        votes = [labels_a.get(rid), labels_b.get(rid)]
        if rid in labels_c or verify.needs_tiebreak(*votes):
            votes.append(labels_c.get(rid))
        votes = [v for v in votes if v is not None]
        if len(votes) < 2:
            decisions[rid] = {"keep": False, "reasons": ["labeling_failed"], "swarm_category": None,
                              "category_agreed": False, "quality": None, "issues": [],
                              "n_labels": len(votes), "unanimous": False}
        else:
            decisions[rid] = verify.decide(votes, args.min_quality)

    canary_stats = verify.canary_report(decisions, canaries)
    gold = verify.gold_report(kept, decisions)
    mislabel_ids = {m["id"] for m in gold["possible_mislabels"]}
    real_ids = [r["id"] for r in kept]
    both = [rid for rid in real_ids if rid in labels_a and rid in labels_b]
    kappa_keep = verify.cohen_kappa([labels_a[i]["keep"] for i in both], [labels_b[i]["keep"] for i in both])
    kappa_cat = verify.cohen_kappa([labels_a[i]["category"] for i in both],
                                   [labels_b[i]["category"] for i in both])

    # 7. Outputs (canaries never reach clean.jsonl)
    clean, swarm_rejected = [], []
    for rec in kept:
        d = decisions[rec["id"]]
        labels = {
            "swarm_category": d["swarm_category"], "quality": d["quality"], "issues": d["issues"],
            "unanimous": d["unanimous"], "possible_mislabel": rec["id"] in mislabel_ids,
        }
        if d["keep"]:
            clean.append({**rec, "labels": labels})
        else:
            swarm_rejected.append({**rec, "reject_stage": "swarm", "reasons": d["reasons"], "labels": labels})
    clean.sort(key=lambda r: r["id"])
    write_jsonl(out / "clean.jsonl", clean)
    write_jsonl(out / "rejected.jsonl", rejected + swarm_rejected)
    write_jsonl(out / "canaries.jsonl", [{**c, "verdict": decisions[c["id"]]} for c in canaries])
    (out / "LICENSE.txt").write_text(LICENSE_NOTE + "\n", encoding="utf-8")

    recall = canary_stats["recall"]
    passed = recall is not None and recall >= args.min_canary_recall
    tokens_in = sum(s["input_tokens"] for s in (stats_a, stats_b, stats_c))
    tokens_out = sum(s["output_tokens"] for s in (stats_a, stats_b, stats_c))
    cost_reported = sum(s.get("cost_usd", 0.0) for s in (stats_a, stats_b, stats_c))
    report = {
        "status": "PASS" if passed else "FAIL",
        "backend": backend.name, "model_tag": model_tag, "prompt_version": PROMPT_VERSION,
        "source": str(src), "seed": args.seed,
        "counts": {
            "loaded": len(records), "rules_rejected": len(rejected), "labeled_real": len(kept),
            "canaries": len(canaries), "swarm_rejected": len(swarm_rejected), "clean": len(clean),
            "tiebreaks": len(contested),
        },
        "passes": [stats_a, stats_b, stats_c],
        "agreement": {"kappa_keep": kappa_keep, "kappa_category": kappa_cat},
        "canaries": canary_stats,
        "gold_category": {k: v for k, v in gold.items() if k != "possible_mislabels"}
        | {"possible_mislabels": len(gold["possible_mislabels"])},
        "tokens": {"input": tokens_in, "output": tokens_out,
                   "est_api_cost_usd": round((tokens_in * PRICE_IN + tokens_out * PRICE_OUT) / 1e6, 4),
                   "backend_reported_usd": round(cost_reported, 4)},
        "reject_reasons": _reason_counts(rejected + swarm_rejected),
    }
    (out / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    (out / "report.md").write_text(_markdown(report, gold), encoding="utf-8")
    print(f"\n{report['status']}: {len(clean)} clean records -> {out / 'clean.jsonl'}")
    print(f"canary recall {_pct(recall)}, gold category accuracy {_pct(gold['accuracy'])}, "
          f"kappa(keep) {_num(kappa_keep)}")
    if not passed:
        print(f"canary recall below {args.min_canary_recall:.0%}: labels are not trustworthy", file=sys.stderr)
        return 2
    return 0


def _reason_counts(rows: list[dict]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for row in rows:
        for reason in row["reasons"]:
            key = reason.split(":")[0] if reason.startswith(("exact_dup", "near_dup")) else reason
            counts[key] = counts.get(key, 0) + 1
    return dict(sorted(counts.items(), key=lambda kv: -kv[1]))


def _pct(x):
    return "n/a" if x is None else f"{x:.1%}"


def _num(x):
    return "n/a" if x is None else f"{x:.2f}"


def _markdown(r: dict, gold: dict) -> str:
    c = r["counts"]
    lines = [
        f"# Cleaning run report: {r['status']}",
        "",
        f"Backend `{r['model_tag']}`, prompts `{r['prompt_version']}`, seed {r['seed']}.",
        "",
        "## Counts",
        "",
        "| Stage | Records |", "|---|---|",
        f"| Loaded | {c['loaded']} |",
        f"| Rejected by rules (filters, duplicates) | {c['rules_rejected']} |",
        f"| Labeled by the swarm | {c['labeled_real']} (+{c['canaries']} canaries) |",
        f"| Needed a tie-break | {c['tiebreaks']} |",
        f"| Rejected by the swarm | {c['swarm_rejected']} |",
        f"| **Clean** | **{c['clean']}** |",
        "",
        "## Verification",
        "",
        f"- Canary recall: **{_pct(r['canaries']['recall'])}** "
        f"({r['canaries']['caught']}/{r['canaries']['total']} known-bad records rejected)",
    ]
    for kind, v in r["canaries"]["by_kind"].items():
        lines.append(f"  - {kind}: {v['caught']}/{v['total']}")
    lines += [
        f"- Agreement between labelers A and B (Cohen's kappa): keep {_num(r['agreement']['kappa_keep'])}, "
        f"category {_num(r['agreement']['kappa_category'])}",
        f"- Category accuracy against Dolly's human labels: **{_pct(gold['accuracy'])}** "
        f"over {gold['compared']} records",
        f"- Possible human mislabels (all labelers agree on a different category): "
        f"{len(gold['possible_mislabels'])}",
        "",
        "## Reject reasons",
        "",
        "| Reason | Count |", "|---|---|",
    ]
    lines += [f"| {k} | {v} |" for k, v in r["reject_reasons"].items()]
    lines += [
        "",
        "## Cost",
        "",
        f"{r['tokens']['input']:,} input and {r['tokens']['output']:,} output tokens, "
        f"about ${r['tokens']['est_api_cost_usd']} at Haiku 5.5 API list price"
        + (f" (the backend itself reported ${r['tokens']['backend_reported_usd']}, which"
           " prices prompt-cache reads correctly)" if r["tokens"].get("backend_reported_usd") else "")
        + ". Cached labels from earlier runs are free and not counted.",
        "",
        "## Labeler calls",
        "",
        "| Pass | From cache | Called | Errors |", "|---|---|---|---|",
    ]
    lines += [f"| {p['framing']} | {p['cached']} | {p['called']} | {p['errors']} |" for p in r["passes"]]
    lines += ["", LICENSE_NOTE, ""]
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main())
