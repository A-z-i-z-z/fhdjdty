"""Paper extraction swarm: one Haiku agent per paper, a mechanical check, then a second Haiku wave.

Run from paper-extraction/:
    python3 -m pipeline.cli fetch --seeds seeds.txt --n 15
    python3 -m pipeline.cli extract --concurrency 8
    python3 -m pipeline.cli check
    python3 -m pipeline.cli verify --concurrency 8
    python3 -m pipeline.cli table
or all four model/check stages at once:
    python3 -m pipeline.cli run --concurrency 8

Every stage is resumable: a paper whose output file exists is skipped.
"""
from __future__ import annotations

import argparse
import json
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from . import check as check_mod
from . import fetch as fetch_mod
from . import table as table_mod
from .common import ROOT, Workdir, call_haiku, fill, load_prompt, load_schema, safe_id

DEFAULT_MODEL = "haiku"
MAX_TEXT_CHARS = 400_000  # ~100k tokens; anything longer is almost certainly a thesis


def _parallel(fn, items, concurrency: int, label: str) -> None:
    if not items:
        print(f"{label}: nothing to do")
        return
    print(f"{label}: {len(items)} papers, {concurrency} at a time")
    failures = 0
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futs = {pool.submit(fn, it): it for it in items}
        for f in as_completed(futs):
            pid = futs[f]
            try:
                print(f"  ok   {pid}  {f.result()}")
            except Exception as e:  # keep the swarm going; the paper is retried on the next run
                failures += 1
                print(f"  FAIL {pid}  {type(e).__name__}: {str(e)[:200]}", file=sys.stderr)
    print(f"{label}: done, {failures} failed")


# ---------------------------------------------------------------------------

def do_extract(wd: Workdir, model: str, concurrency: int, only: list[str] | None) -> None:
    schema = load_schema("extraction.schema.json")
    template = load_prompt("extract.md")
    todo = [p for p in (only or wd.paper_ids())
            if not (wd.extractions / f"{safe_id(p)}.json").exists()]

    def one(pid: str) -> str:
        text = wd.text(pid)[:MAX_TEXT_CHARS]
        meta = wd.meta(pid)
        prompt = fill(template, paper_id=pid, title=meta.get("title", ""), text=text)
        res = call_haiku(prompt, schema, model)
        res["paper_id"], res["model"] = pid, model
        (wd.extractions / f"{safe_id(pid)}.json").write_text(json.dumps(res, indent=2))
        return f"{len(res['output'].get('records', []))} records"

    _parallel(one, todo, concurrency, "extract")


def do_check(wd: Workdir) -> None:
    totals = {"records": 0, "passed": 0}
    for pid in wd.paper_ids():
        ex = wd.extractions / f"{safe_id(pid)}.json"
        if not ex.exists():
            continue
        out = check_mod.check_paper(pid, wd.text(pid), json.loads(ex.read_text())["output"])
        (wd.checked / f"{safe_id(pid)}.json").write_text(json.dumps(out, indent=2))
        totals["records"] += len(out)
        totals["passed"] += sum(r["passed"] for r in out)
    print(f"check: {totals['passed']}/{totals['records']} records passed the mechanical checks")


def do_verify(wd: Workdir, model: str, concurrency: int, only: list[str] | None) -> None:
    schema = load_schema("verification.schema.json")
    template = load_prompt("verify.md")
    todo = []
    for pid in only or wd.paper_ids():
        c = wd.checked / f"{safe_id(pid)}.json"
        if c.exists() and not (wd.verifications / f"{safe_id(pid)}.json").exists():
            todo.append(pid)

    def one(pid: str) -> str:
        checked = json.loads((wd.checked / f"{safe_id(pid)}.json").read_text())
        passing = [c for c in checked if c["passed"]]
        if not passing:
            res = {"output": {"verdicts": []}, "usage": None}
        else:
            # The verifier sees the record and the checker's warnings, not the extractor's prompt.
            records = [{"record_id": c["record_id"], **c["record"], "checker_warnings": c["warnings"]}
                       for c in passing]
            prompt = fill(template, paper_id=pid, title=wd.meta(pid).get("title", ""),
                          text=wd.text(pid)[:MAX_TEXT_CHARS],
                          records=json.dumps(records, indent=1, ensure_ascii=False))
            res = call_haiku(prompt, schema, model)
        res["paper_id"], res["model"] = pid, model
        (wd.verifications / f"{safe_id(pid)}.json").write_text(json.dumps(res, indent=2))
        v = res["output"]["verdicts"]
        return f"{sum(x['verdict'] != 'unsupported' for x in v)}/{len(passing)} kept"

    _parallel(one, todo, concurrency, "verify")


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--workdir", default=str(ROOT / "data" / "pilot"))
    sub = ap.add_subparsers(dest="cmd", required=True)

    f = sub.add_parser("fetch", help="download papers from arXiv")
    f.add_argument("--seeds", default=str(ROOT / "seeds.txt"), help="file of arXiv ids, one per line")
    f.add_argument("--query", default=None,
                   help="arXiv query to top up to --n (default: the widening list in fetch.DEFAULT_QUERIES; '' for none)")
    f.add_argument("--n", type=int, default=15)

    a = sub.add_parser("add", help="register a local PDF or .txt as a paper")
    a.add_argument("path")
    a.add_argument("--id", required=True)
    a.add_argument("--title", required=True)
    a.add_argument("--url")

    for name in ("extract", "verify", "run"):
        p = sub.add_parser(name)
        p.add_argument("--model", default=DEFAULT_MODEL)
        p.add_argument("--concurrency", type=int, default=8)
        p.add_argument("--only", nargs="*", help="paper ids to limit to")
    sub.add_parser("check")
    sub.add_parser("table")

    args = ap.parse_args(argv)
    wd = Workdir(Path(args.workdir))
    wd.ensure()

    if args.cmd == "fetch":
        seeds = []
        sp = Path(args.seeds)
        if sp.exists():
            seeds = [l.split("#")[0].strip() for l in sp.read_text().splitlines()]
            seeds = [s for s in seeds if s]
        query = fetch_mod.DEFAULT_QUERIES if args.query is None else (args.query or None)
        ids = fetch_mod.fetch(wd, seeds, query, args.n)
        print(f"fetch: {len(ids)} papers in {wd.papers}")
    elif args.cmd == "add":
        print(fetch_mod.add_local(wd, args.id, args.title, args.path, args.url))
    elif args.cmd == "extract":
        do_extract(wd, args.model, args.concurrency, args.only)
    elif args.cmd == "check":
        do_check(wd)
    elif args.cmd == "verify":
        do_verify(wd, args.model, args.concurrency, args.only)
    elif args.cmd == "table":
        table_mod.build(wd)
    elif args.cmd == "run":
        do_extract(wd, args.model, args.concurrency, args.only)
        do_check(wd)
        do_verify(wd, args.model, args.concurrency, args.only)
        table_mod.build(wd)


if __name__ == "__main__":
    main()
