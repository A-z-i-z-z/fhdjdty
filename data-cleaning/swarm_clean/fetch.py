"""Download Databricks Dolly 15k and take a reproducible sample."""

from __future__ import annotations

import json
import random
import urllib.request
from pathlib import Path

DOLLY_URL = (
    "https://huggingface.co/datasets/databricks/databricks-dolly-15k/"
    "resolve/main/databricks-dolly-15k.jsonl"
)


def download(dest: Path, url: str = DOLLY_URL) -> Path:
    """Download the dataset once; later runs reuse the local copy."""
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    with urllib.request.urlopen(url, timeout=120) as resp, open(tmp, "wb") as fh:
        while chunk := resp.read(1 << 20):
            fh.write(chunk)
    tmp.rename(dest)
    return dest


def load(path: Path, limit: int | None = None, seed: int = 0) -> list[dict]:
    """Load records, giving each a stable id from its line number.

    With a limit, a seeded random sample is taken so pilots are reproducible
    and not biased toward the start of the file.
    """
    records = []
    with open(path, encoding="utf-8") as fh:
        for i, line in enumerate(fh):
            line = line.strip()
            if not line:
                continue
            row = json.loads(line)
            records.append(
                {
                    "id": row.get("id") or f"dolly-{i:05d}",
                    "instruction": row.get("instruction", "") or "",
                    "context": row.get("context", "") or "",
                    "response": row.get("response", "") or "",
                    "category": row.get("category", "") or "",
                }
            )
    if limit is not None and limit < len(records):
        rng = random.Random(seed)
        records = rng.sample(records, limit)
        records.sort(key=lambda r: r["id"])
    return records
