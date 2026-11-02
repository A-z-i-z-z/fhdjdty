"""Canaries: known-bad records built from the real data and hidden among the
real ones. Their correct verdict is known by construction (they must be
rejected), so the share the swarm catches is an automatic, free measure of
whether the labels can be trusted. Canaries never reach the output."""

from __future__ import annotations

import random

CONTEXT_CATEGORIES = {"closed_qa", "information_extraction", "summarization"}


def _swapped(rng: random.Random, records: list[dict], idx: int) -> dict:
    """Instruction from one record, response from an unrelated one."""
    base = records[idx]
    others = [r for r in records if r["category"] != base["category"]] or records
    other = rng.choice([r for r in others if r["id"] != base["id"]] or others)
    return {
        **base,
        "context": base["context"],
        "response": other["response"],
        "canary": "swapped_response",
    }


def _context_removed(rng: random.Random, records: list[dict], idx: int) -> dict | None:
    """A task that depends on a passage, with the passage deleted."""
    pool = [r for r in records if r["category"] in CONTEXT_CATEGORIES and len(r["context"]) > 200]
    if not pool:
        return None
    base = rng.choice(pool)
    return {**base, "context": "", "canary": "context_removed"}


def _truncated(rng: random.Random, records: list[dict], idx: int) -> dict | None:
    """A long response cut off mid-sentence."""
    pool = [r for r in records if len(r["response"].split()) >= 60]
    if not pool:
        return None
    base = rng.choice(pool)
    words = base["response"].split()
    cut = max(8, len(words) // 4)
    return {**base, "response": " ".join(words[:cut]), "canary": "truncated_response"}


MAKERS = [_swapped, _context_removed, _truncated]


def make(records: list[dict], fraction: float = 0.05, seed: int = 0, minimum: int = 6) -> list[dict]:
    """Create about `fraction` * len(records) canaries, cycling the kinds."""
    if len(records) < 2:
        return []
    rng = random.Random(seed + 7919)
    want = max(minimum, round(len(records) * fraction))
    out: list[dict] = []
    attempts = 0
    while len(out) < want and attempts < want * 5:
        maker = MAKERS[attempts % len(MAKERS)]
        attempts += 1
        canary = maker(rng, records, rng.randrange(len(records)))
        if canary is None:
            continue
        canary["source_id"] = canary["id"]
        canary["id"] = f"canary-{len(out):04d}"
        out.append(canary)
    return out
