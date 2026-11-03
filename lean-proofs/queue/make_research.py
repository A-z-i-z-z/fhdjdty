"""Build queue/research.jsonl from PutnamBench and formal-conjectures checkouts.

Usage: python3 -I queue/make_research.py PUTNAMBENCH_DIR FORMAL_CONJECTURES_DIR OUT.jsonl

Writes candidates only; statements that do not elaborate against this
project's Mathlib are filtered out afterwards (see runs/*research*/README.md).
Tiers:
  putnam_easy   PutnamBench A1/A2/B1/B2, 2000 onwards
  putnam_hard   PutnamBench A5/A6/B5/B6, 2000 onwards
  fc_solved     formal-conjectures, @[category research solved]
  fc_open       formal-conjectures, @[category research open]  (canaries: a proof means a bug)
Answer-style Putnam problems get the official answer filled in from the
comment PutnamBench stores next to the `sorry`.
"""
import json, random, re, sys
from pathlib import Path

pb, fc, out = map(Path, sys.argv[1:4])
rng = random.Random(20261103)
items = []

def stmt_ok(s):
    return ":=" not in s and "sorry" not in s and len(s) < 1500

# ---------- PutnamBench ----------
def putnam(path):
    t = path.read_text()
    name = path.stem
    m = re.search(rf"^theorem {name}\b(.*?):=\s*(by\s+)?sorry\s*$", t, flags=re.S | re.M)
    if not m:
        return None
    stmt = m.group(1).strip()
    pre_lines = []
    head = t[: m.start()]
    head = re.sub(r"/--.*?-/", "", head, flags=re.S)
    lines = head.splitlines()
    i = 0
    while i < len(lines):
        l = lines[i]
        if l.startswith("import"):
            i += 1; continue
        if "_solution" in l and l.rstrip().endswith("sorry"):
            ans = lines[i + 1].strip() if i + 1 < len(lines) else ""
            if not ans.startswith("--"):
                return None
            pre_lines.append(l.rstrip()[: -len("sorry")] + ans[2:].strip())
            i += 2; continue
        if l.strip():
            pre_lines.append(l)
        i += 1
    pre = "\n".join(pre_lines).strip()
    if "sorry" in pre or not stmt_ok(stmt):
        return None
    return dict(id=name, name=name, requires="mathlib", preamble=pre, statement=stmt,
                notes="PutnamBench (answer filled in where the problem asks for one)", source="PutnamBench")

pfiles = sorted((pb / "lean4" / "src").glob("putnam_*.lean"))
def pick(parts, tier, k):
    c = [p for p in pfiles if int(p.stem.split("_")[1]) >= 2000 and p.stem.split("_")[2] in parts]
    rng.shuffle(c)
    got = []
    for p in c:
        it = putnam(p)
        if it:
            it["tier"] = tier; got.append(it)
        if len(got) == k:
            break
    return got
items += pick({"a1", "a2", "b1", "b2"}, "putnam_easy", 40)
items += pick({"a5", "a6", "b5", "b6"}, "putnam_hard", 40)

# ---------- formal-conjectures ----------
CHUNK_START = re.compile(r"^(@\[|theorem|lemma|def|noncomputable|abbrev|namespace|end\b|open|section|variable|set_option|instance|structure|inductive|private|protected|scoped|local|attribute|notation|macro|syntax|universe|example|public|module|irreducible_def|class)")
PREFIX = re.compile(r"^(@\[.*\]\s*|set_option .* in\s*|open .* in\s*)$")
def chunks(text):
    text = re.sub(r"/-.*?-/", "", text, flags=re.S)       # docstrings and block comments
    text = re.sub(r"^--.*$", "", text, flags=re.M)
    out, cur = [], []
    for line in text.splitlines():
        if line and not line[0].isspace() and CHUNK_START.match(line):
            if cur and not all(PREFIX.match(l) or not l.strip() for l in cur):
                out.append("\n".join(cur)); cur = []
        cur.append(line)
    if cur:
        out.append("\n".join(cur))
    return out

def clean(c):
    c = re.sub(r"/--.*?-/\s*", "", c, flags=re.S)
    c = re.sub(r"@\[[^\]]*\]\s*", "", c)
    c = re.sub(r"^set_option [^\n]* in\n", "", c, flags=re.M)
    c = re.sub(r"^public ", "", c, flags=re.M)
    return c.strip()

fc_items = {"fc_solved": [], "fc_open": []}
ffiles = sorted((fc / "FormalConjectures").rglob("*.lean"))
rng.shuffle(ffiles)
for f in ffiles:
    t = f.read_text()
    if "variable" in t or "answer(" in t:
        pass
    t = re.sub(r"/-[^-].*?-/", "", t, count=1, flags=re.S)  # licence header
    cs = chunks(t)
    keep = []
    for c in cs:
        raw = c
        cl = clean(c)
        if not cl or cl.startswith(("module", "import", "/-!", "@[expose]")) or cl == "public section" or cl.startswith("public import"):
            continue
        opens = re.findall(r"^(open .*) in\s*$", cl, flags=re.M)
        cl = re.sub(r"^open .* in\s*\n", "", cl, flags=re.M).strip()
        m = re.match(r"(theorem|lemma)\s+(\S+)(.*?):=\s*(by\s+)?sorry\s*$", cl, flags=re.S)
        cat = re.search(r"category research (solved|open)", raw)
        if m and cat and "answer(" not in cl and "variable" not in t:
            tier = "fc_" + cat.group(1)
            stmt = m.group(3).strip()
            if stmt_ok(stmt) and "sorry" not in "\n".join(keep):
                rel = f.relative_to(fc).as_posix()
                nm = m.group(2)
                fc_items[tier].append(dict(
                    id=("fc_" + re.sub(r"\W", "_", rel[len("FormalConjectures/"):-5]) + "_" + re.sub(r"\W", "_", nm))[:90],
                    name=nm, requires="mathlib", tier=tier, preamble="\n\n".join(keep + opens), statement=stmt,
                    notes=f"formal-conjectures {rel}", source="formal-conjectures"))
        if re.match(r"(theorem|lemma|example)\b", cl):
            continue  # never put other theorems (often sorried) in the preamble
        keep.append(cl)
for tier, k in (("fc_solved", 300), ("fc_open", 120)):
    items += fc_items[tier][:k]

seen = set(); final = []
for it in items:
    if it["id"] in seen:
        continue
    seen.add(it["id"]); final.append(it)
out.write_text("".join(json.dumps(x, ensure_ascii=False) + "\n" for x in final))
from collections import Counter
print(len(final), Counter(x["tier"] for x in final), {k: len(v) for k, v in fc_items.items()})
