"""Deterministic cleaning that needs no model: normalisation, hard filters,
exact and near-duplicate removal. Runs before the swarm so no tokens are spent
on records a regex can already reject."""

from __future__ import annotations

import hashlib
import re
import unicodedata
from collections import defaultdict

TEXT_FIELDS = ("instruction", "context", "response")

CITATION_RE = re.compile(r"\[(?:\d+|citation needed|note \d+)\]", re.IGNORECASE)
MULTISPACE_RE = re.compile(r"[ \t ]+")
MULTINEWLINE_RE = re.compile(r"\n{3,}")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_RE = re.compile(r"(?<!\d)(?:\+?\d{1,2}[\s.-]?)?\(?\d{3}\)?[\s.-]\d{3}[\s.-]\d{4}(?!\d)")
WORD_RE = re.compile(r"[a-z0-9]+")


def normalise_text(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = CITATION_RE.sub("", text)
    text = MULTISPACE_RE.sub(" ", text)
    text = "\n".join(line.strip() for line in text.split("\n"))
    text = MULTINEWLINE_RE.sub("\n\n", text)
    return text.strip()


def normalise(record: dict) -> dict:
    out = dict(record)
    for field in TEXT_FIELDS:
        out[field] = normalise_text(record.get(field, ""))
    return out


def hard_filter_reasons(record: dict, max_chars: int) -> list[str]:
    reasons = []
    if not record["instruction"]:
        reasons.append("empty_instruction")
    if not record["response"]:
        reasons.append("empty_response")
    total = sum(len(record[f]) for f in TEXT_FIELDS)
    if total > max_chars:
        reasons.append(f"too_long>{max_chars}_chars")
    joined = "\n".join(record[f] for f in ("instruction", "response"))
    if EMAIL_RE.search(joined):
        reasons.append("pii_email")
    if PHONE_RE.search(joined):
        reasons.append("pii_phone")
    return reasons


def _words(text: str) -> list[str]:
    return WORD_RE.findall(text.lower())


def exact_key(record: dict) -> str:
    words = _words(record["instruction"]) + ["\x00"] + _words(record["response"])
    return hashlib.blake2b(" ".join(words).encode(), digest_size=16).hexdigest()


def _shingles(record: dict, n: int = 3) -> set[int]:
    words = _words(record["instruction"] + " " + record["response"])
    if len(words) < n:
        grams = [" ".join(words)] if words else []
    else:
        grams = [" ".join(words[i : i + n]) for i in range(len(words) - n + 1)]
    return {
        int.from_bytes(hashlib.blake2b(g.encode(), digest_size=8).digest(), "big")
        for g in grams
    }


def near_duplicate_pairs(
    records: list[dict], threshold: float = 0.7, sketch: int = 16, min_shared: int = 4,
    max_bucket: int = 50,
) -> list[tuple[int, int, float]]:
    """Find near-duplicate pairs with a bottom-k sketch and an inverted index.

    Each record keeps its `sketch` smallest shingle hashes. Records sharing at
    least `min_shared` sketch hashes become candidates, and every candidate is
    confirmed with the exact Jaccard similarity of the full shingle sets, so the
    sketch only decides what gets compared, never what gets removed.
    """
    shingles = [_shingles(r) for r in records]
    sketches = [sorted(s)[:sketch] for s in shingles]
    index: dict[int, list[int]] = defaultdict(list)
    for i, sk in enumerate(sketches):
        for h in sk:
            index[h].append(i)
    shared: dict[tuple[int, int], int] = defaultdict(int)
    for members in index.values():
        if len(members) < 2 or len(members) > max_bucket:
            continue
        for a_pos, a in enumerate(members):
            for b in members[a_pos + 1 :]:
                shared[(a, b)] += 1
    pairs = []
    for (a, b), count in shared.items():
        small = min(len(sketches[a]), len(sketches[b]))
        if count < min(min_shared, small):
            continue
        sa, sb = shingles[a], shingles[b]
        jac = len(sa & sb) / len(sa | sb) if sa or sb else 1.0
        if jac >= threshold:
            pairs.append((a, b, jac))
    return pairs


def run(records: list[dict], max_chars: int = 12000, near_dup_threshold: float = 0.7):
    """Return (kept, rejected). Rejected records carry stage and reasons."""
    kept, rejected = [], []
    seen: dict[str, str] = {}
    for rec in records:
        rec = normalise(rec)
        reasons = hard_filter_reasons(rec, max_chars)
        if reasons:
            rejected.append({**rec, "reject_stage": "rules", "reasons": reasons})
            continue
        key = exact_key(rec)
        if key in seen:
            rejected.append(
                {**rec, "reject_stage": "rules", "reasons": [f"exact_duplicate_of:{seen[key]}"]}
            )
            continue
        seen[key] = rec["id"]
        kept.append(rec)

    # Near duplicates: keep the first record of each cluster (union-find).
    parent = list(range(len(kept)))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b, _ in near_duplicate_pairs(kept, threshold=near_dup_threshold):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)

    survivors = []
    for i, rec in enumerate(kept):
        root = find(i)
        if root == i:
            survivors.append(rec)
        else:
            rejected.append(
                {**rec, "reject_stage": "rules",
                 "reasons": [f"near_duplicate_of:{kept[root]['id']}"]}
            )
    return survivors, rejected


def repeated_prompts(records: list[dict], min_instruction_overlap: float = 0.5) -> dict[str, str]:
    """After labeling: find records that repeat a prompt already in the set.

    Two kinds survive the near-duplicate check above because their full text
    differs: the same instruction and passage with a different answer (for
    example three separate "What is the English Premier League?" records), and
    the same answer under a reworded instruction. For each group the record with
    the highest swarm quality is kept. Returns {dropped id: kept id}.
    Records must carry `labels.quality`.
    """
    def norm(text: str) -> str:
        return " ".join(_words(text))

    ranked = sorted(records, key=lambda r: (-r["labels"]["quality"], r["id"]))
    dropped: dict[str, str] = {}
    by_prompt: dict[tuple[str, str], str] = {}
    by_response: dict[str, list[dict]] = defaultdict(list)
    for rec in ranked:
        prompt = (norm(rec["instruction"]), norm(rec["context"]))
        if prompt in by_prompt:
            dropped[rec["id"]] = by_prompt[prompt]
            continue
        by_prompt[prompt] = rec["id"]
        resp_words = _words(rec["response"])
        if len(resp_words) >= 5:
            instr = set(_words(rec["instruction"]))
            twin = next((k for k in by_response[" ".join(resp_words)]
                         if len(instr & k["w"]) / max(1, len(instr | k["w"])) >= min_instruction_overlap),
                        None)
            if twin:
                dropped[rec["id"]] = twin["id"]
                continue
            by_response[" ".join(resp_words)].append({"id": rec["id"], "w": instr})
    return dropped
