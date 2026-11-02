#!/usr/bin/env python3
"""Compile-check verifier for the Lean proof swarm.

An agent's answer is accepted only if ALL of these hold:

1. It contains a ```lean block with `theorem <name>` whose statement is
   exactly the one from the queue (whitespace-insensitive).
2. Anything before the main theorem is only `theorem`/`lemma` helpers,
   so the agent cannot add `open`, notation, instances or definitions
   that would change what the statement means.
3. No banned tokens: sorry, admit, axiom, native_decide, #exit, macros,
   set_option other than heartbeats/recursion depth, and so on.
4. We rebuild the file ourselves (our header + preamble + helpers +
   OUR copy of the statement + their proof) and Lean compiles it with
   zero errors and no `sorry` warnings.
5. `#print axioms` shows only the three standard axioms
   (propext, Classical.choice, Quot.sound).

Usage:
  verify.py --lemma ml_six_dvd --answer answer.md       # check one answer
  verify.py --check-statements [--requires mathlib]     # every statement
                                                        # elaborates with sorry
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent          # lean-proofs/
DEFAULT_QUEUE = ROOT / "queue" / "lemmas.jsonl"
ALLOWED_AXIOMS = {"propext", "Classical.choice", "Quot.sound"}

HEADERS = {
    "core": "",
    "mathlib": "import Mathlib\n\nopen Finset\n",
}

# Tokens that are never allowed anywhere in agent-written text.
BANNED = [
    (r"\bsorry\b", "sorry"),
    (r"\badmit\b", "admit"),
    (r"\baxiom\b", "axiom"),
    (r"\bnative_decide\b", "native_decide"),
    (r"\bimplemented_by\b", "implemented_by"),
    (r"\bextern\b", "extern"),
    (r"\bunsafe\b", "unsafe"),
    (r"\bopaque\b", "opaque"),
    (r"\bimport\b", "import"),
    (r"\b(macro|macro_rules|syntax|elab|elab_rules|notation|infix|infixl|infixr|prefix|postfix)\b", "syntax extension"),
    (r"\b(run_cmd|run_tac|run_elab)\b", "run_cmd"),
    (r"#(exit|eval|reduce)\b", "# command"),
    (r"\bdebug\.", "debug option"),
    (r"\bTrustLevel\b|\bLean\.ofReduceBool\b", "kernel bypass"),
]
SET_OPTION_OK = {"maxHeartbeats", "maxRecDepth", "synthInstance.maxHeartbeats"}
HELPER_START = re.compile(r"^(private\s+)?(theorem|lemma)\s|^--|^/-|^$")


@dataclass
class Result:
    lemma_id: str
    ok: bool
    stage: str                      # parse | policy | compile | axioms | timeout | ok
    errors: list[str] = field(default_factory=list)
    axioms: list[str] | None = None
    lean_file: str = ""             # the exact file that was compiled
    seconds: float = 0.0

    def feedback(self, limit: int = 3000) -> str:
        """Short error text to hand back to the agent for its next round."""
        text = f"[{self.stage}] " + "\n\n".join(self.errors)
        return text[:limit]


def load_queue(path: Path = DEFAULT_QUEUE) -> dict[str, dict]:
    lemmas = {}
    for line in path.read_text().splitlines():
        if line.strip():
            item = json.loads(line)
            lemmas[item["id"]] = item
    return lemmas


def _norm(s: str) -> str:
    return " ".join(s.split())


def extract_lean_block(answer: str) -> str | None:
    blocks = re.findall(r"```lean4?\s*\n(.*?)```", answer, flags=re.S)
    return blocks[-1] if blocks else None


def split_answer(lemma: dict, code: str) -> tuple[str, str] | str:
    """Return (helpers, proof) or an error string."""
    name = lemma["name"]
    m = re.search(rf"^theorem\s+{re.escape(name)}\b", code, flags=re.M)
    if not m:
        return f"could not find `theorem {name}` at the start of a line"
    helpers, rest = code[: m.start()], code[m.end():]
    if ":=" not in rest:
        return "main theorem has no `:=`"
    stmt, proof = rest.split(":=", 1)
    if _norm(stmt) != _norm(lemma["statement"]):
        return ("statement was changed. It must be exactly:\n"
                f"theorem {name} {lemma['statement']} :=")
    return helpers, proof


def clean_helpers(lemma: dict, helpers: str) -> str:
    """Drop things the agent may echo back from the prompt: the header
    lines (import Mathlib, open Finset) and the lemma's own preamble."""
    pre = lemma.get("preamble", "").strip()
    if pre:
        helpers = helpers.replace(pre, "")
    header = {l.strip() for l in HEADERS[lemma["requires"]].splitlines() if l.strip()}
    return "\n".join(l for l in helpers.splitlines() if l.strip() not in header)


def strip_comments(s: str) -> str:
    s = re.sub(r"/-.*?-/", "", s, flags=re.S)
    return re.sub(r"--[^\n]*", "", s)


def policy_check(helpers: str, proof: str, lemma: dict) -> list[str]:
    errs = []
    helpers, proof = strip_comments(helpers), strip_comments(proof)
    text = helpers + "\n" + proof
    for pat, label in BANNED:
        if re.search(pat, text):
            errs.append(f"banned construct: {label}")
    for opt in re.findall(r"set_option\s+([\w.]+)", text):
        if opt not in SET_OPTION_OK:
            errs.append(f"set_option {opt} is not allowed")
    for line in helpers.splitlines():
        if line and not line[0].isspace() and not HELPER_START.match(line):
            errs.append(f"only theorem/lemma helpers are allowed before the main theorem (no def, instance, open, notation), got: {line[:80]}")
    for line in proof.splitlines()[1:]:
        if line.strip() and not line[0].isspace():
            errs.append(f"proof lines must be indented (no new top-level commands), got: {line[:80]}")
            break
    if re.search(rf"^(private\s+)?(theorem|lemma)\s+{re.escape(lemma['name'])}\b", helpers, flags=re.M):
        errs.append("helpers may not reuse the main theorem's name")
    return errs


def build_file(lemma: dict, helpers: str, proof: str) -> str:
    parts = [HEADERS[lemma["requires"]]]
    if lemma.get("preamble"):
        parts.append(lemma["preamble"].strip() + "\n")
    if helpers.strip():
        parts.append(helpers.strip() + "\n")
    parts.append(f"theorem {lemma['name']} {lemma['statement']} :={proof.rstrip()}\n")
    parts.append(f"#print axioms {lemma['name']}\n")
    return "\n".join(parts)


def lean_cmd(requires: str, project: Path) -> tuple[list[str], Path]:
    """Command prefix and cwd. Mathlib files run inside the lake project."""
    if requires == "mathlib":
        return ["lake", "env", "lean", "--json"], project
    return ["lean", "--json"], project


def run_lean(src: str, requires: str, project: Path, timeout: int):
    import time
    cmd, cwd = lean_cmd(requires, project)
    with tempfile.NamedTemporaryFile("w", suffix=".lean", delete=False, dir=None) as f:
        f.write(src)
        path = f.name
    t0 = time.time()
    try:
        p = subprocess.run(cmd + [path], cwd=cwd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return None, time.time() - t0
    finally:
        os.unlink(path)
    msgs = []
    for line in p.stdout.splitlines():
        try:
            msgs.append(json.loads(line))
        except json.JSONDecodeError:
            msgs.append({"severity": "error", "data": line, "pos": {"line": 0}})
    if p.returncode != 0 and not any(m.get("severity") == "error" for m in msgs):
        msgs.append({"severity": "error", "data": (p.stderr or "lean exited non-zero")[:2000], "pos": {"line": 0}})
    return msgs, time.time() - t0


def parse_axioms(msgs: list[dict], name: str) -> list[str] | None:
    for m in msgs:
        d = m.get("data", "")
        if f"'{name}' does not depend on any axioms" in d:
            return []
        hit = re.search(rf"'{re.escape(name)}' depends on axioms: \[(.*?)\]", d, flags=re.S)
        if hit:
            return [a.strip() for a in hit.group(1).split(",") if a.strip()]
    return None


def verify_answer(lemma: dict, answer: str, project: Path = ROOT, timeout: int = 300) -> Result:
    rid = lemma["id"]
    code = extract_lean_block(answer) if "```" in answer else answer
    if code is None:
        return Result(rid, False, "parse", ["no ```lean code block found"])
    parts = split_answer(lemma, code)
    if isinstance(parts, str):
        return Result(rid, False, "parse", [parts])
    helpers, proof = parts
    helpers = clean_helpers(lemma, helpers)
    errs = policy_check(helpers, proof, lemma)
    if errs:
        return Result(rid, False, "policy", errs)
    src = build_file(lemma, helpers, proof)
    msgs, secs = run_lean(src, lemma["requires"], project, timeout)
    if msgs is None:
        return Result(rid, False, "timeout", [f"Lean did not finish within {timeout}s"], lean_file=src, seconds=secs)
    bad = [m for m in msgs if m.get("severity") == "error"
           or (m.get("severity") == "warning" and "sorry" in m.get("data", ""))]
    if bad:
        errs = [f"line {m.get('pos', {}).get('line', '?')}: {m.get('data', '')}" for m in bad]
        return Result(rid, False, "compile", errs, lean_file=src, seconds=secs)
    axioms = parse_axioms(msgs, lemma["name"])
    if axioms is None:
        return Result(rid, False, "axioms", ["could not read #print axioms output"], lean_file=src, seconds=secs)
    extra = sorted(set(axioms) - ALLOWED_AXIOMS)
    if extra:
        return Result(rid, False, "axioms", [f"uses non-standard axioms: {extra}"], axioms, src, secs)
    return Result(rid, True, "ok", [], axioms, src, secs)


def check_statement(lemma: dict, project: Path = ROOT, timeout: int = 600) -> Result:
    """The statement itself must elaborate (only a sorry warning allowed)."""
    src = build_file(lemma, "", " by\n  sorry")
    msgs, secs = run_lean(src, lemma["requires"], project, timeout)
    if msgs is None:
        return Result(lemma["id"], False, "timeout", [], lean_file=src, seconds=secs)
    errs = [m.get("data", "") for m in msgs if m.get("severity") == "error"]
    return Result(lemma["id"], not errs, "ok" if not errs else "compile", errs, lean_file=src, seconds=secs)


def library_check(lemma: dict, project: Path = ROOT, timeout: int = 180) -> str | None:
    """Ask `exact?` whether the library already proves the statement in one step.
    Returns the suggestion text, or None if nothing was found."""
    src = build_file(lemma, "", " by\n  exact?").replace(f"#print axioms {lemma['name']}\n", "")
    msgs, _ = run_lean(src, lemma["requires"], project, timeout)
    if not msgs or any(m.get("severity") == "error" for m in msgs):
        return None
    for m in msgs:
        if "Try this" in m.get("data", ""):
            return m["data"].strip()
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--queue", type=Path, default=DEFAULT_QUEUE)
    ap.add_argument("--project", type=Path, default=ROOT, help="lake project with Mathlib (default: lean-proofs/)")
    ap.add_argument("--lemma")
    ap.add_argument("--answer", type=Path, help="file with the agent's answer (markdown with a ```lean block)")
    ap.add_argument("--check-statements", action="store_true")
    ap.add_argument("--requires", choices=["core", "mathlib"], help="only lemmas needing this")
    ap.add_argument("--timeout", type=int, default=300)
    args = ap.parse_args()
    queue = load_queue(args.queue)

    if args.check_statements:
        failed = 0
        for lem in queue.values():
            if args.requires and lem["requires"] != args.requires:
                continue
            r = check_statement(lem, args.project, max(args.timeout, 600))
            print(f"{'OK  ' if r.ok else 'FAIL'} {lem['id']} ({r.seconds:.1f}s)")
            for e in r.errors:
                print("     " + e.replace("\n", "\n     "))
            failed += not r.ok
        return 1 if failed else 0

    if not (args.lemma and args.answer):
        ap.error("give --lemma and --answer, or --check-statements")
    r = verify_answer(queue[args.lemma], args.answer.read_text(), args.project, args.timeout)
    print(json.dumps(asdict(r), indent=2, ensure_ascii=False))
    return 0 if r.ok else 1


if __name__ == "__main__":
    sys.exit(main())
