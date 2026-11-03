"""Turning many cheap, noisy labels into verified ones.

Three independent checks:
1. Agreement: two differently-framed labelers (A, B) must agree on keep and
   category. Disagreements go to a third labeler (C) and the majority wins.
   Cohen's kappa between A and B is reported as a reliability score.
2. Canaries: known-bad records hidden in the stream. Recall on them estimates
   how many real bad records slip through. Below the threshold the run fails.
3. Gold labels: Dolly ships a human category per record. The swarm never sees
   it; its accuracy against it is reported, and confident disagreements are
   flagged as possible human mislabels.
"""

from __future__ import annotations

from collections import Counter

from .prompts import HARD_ISSUES


def cohen_kappa(a: list, b: list) -> float | None:
    n = len(a)
    if n == 0:
        return None
    observed = sum(x == y for x, y in zip(a, b)) / n
    ca, cb = Counter(a), Counter(b)
    expected = sum(ca[k] * cb.get(k, 0) for k in ca) / (n * n)
    if expected == 1:
        return 1.0 if observed == 1 else 0.0
    return (observed - expected) / (1 - expected)


def needs_tiebreak(a: dict | None, b: dict | None) -> bool:
    if a is None or b is None:
        return True
    return a["keep"] != b["keep"] or a["category"] != b["category"]


def decide(labels: list[dict], min_quality: float) -> dict:
    """Combine 2 or 3 labels for one record into a final verdict."""
    n = len(labels)
    majority = n // 2 + 1
    keep_votes = sum(l["keep"] for l in labels)
    cat_counts = Counter(l["category"] for l in labels).most_common()
    category, top = cat_counts[0]
    category_agreed = top >= majority
    issue_counts = Counter(i for l in labels for i in l["issues"])
    issues = sorted(i for i, c in issue_counts.items() if c >= majority)
    quality = sum(l["quality"] for l in labels) / n
    reasons = []
    if keep_votes < majority:
        reasons.append("labelers_voted_drop")
    if quality < min_quality:
        reasons.append(f"quality<{min_quality}")
    hard = [i for i in issues if i in HARD_ISSUES]
    reasons.extend(f"issue:{i}" for i in hard)
    return {
        "keep": not reasons,
        "reasons": reasons,
        "swarm_category": category if category_agreed else None,
        "category_agreed": category_agreed,
        "quality": round(quality, 2),
        "issues": issues,
        "n_labels": n,
        "unanimous": keep_votes in (0, n) and top == n,
    }


def canary_report(decisions: dict[str, dict], canaries: list[dict]) -> dict:
    by_kind: dict[str, list[bool]] = {}
    for c in canaries:
        d = decisions.get(c["id"])
        if d is None:
            continue
        by_kind.setdefault(c["canary"], []).append(not d["keep"])
    caught = sum(sum(v) for v in by_kind.values())
    total = sum(len(v) for v in by_kind.values())
    return {
        "total": total,
        "caught": caught,
        "recall": caught / total if total else None,
        "by_kind": {k: {"caught": sum(v), "total": len(v)} for k, v in sorted(by_kind.items())},
    }


def gold_report(records: list[dict], decisions: dict[str, dict]) -> dict:
    pairs, mislabels = [], []
    confusion: Counter = Counter()
    for r in records:
        d = decisions.get(r["id"])
        if not d or not r.get("category"):
            continue
        guess = d["swarm_category"]
        if guess is None:
            continue
        pairs.append(guess == r["category"])
        confusion[(r["category"], guess)] += 1
        if guess != r["category"] and d["unanimous"]:
            mislabels.append({"id": r["id"], "human": r["category"], "swarm": guess})
    return {
        "compared": len(pairs),
        "accuracy": sum(pairs) / len(pairs) if pairs else None,
        "possible_mislabels": mislabels,
        "top_confusions": [
            {"human": h, "swarm": s, "count": c}
            for (h, s), c in confusion.most_common() if h != s
        ][:10],
    }
