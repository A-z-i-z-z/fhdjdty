"""End-to-end check of the pipeline on the toy target with canned (replayed) claims.

Every canned claim carries an `_expect` note; this test makes sure the verifier reaches
exactly that verdict, so a change that lets a bogus claim through fails loudly.
"""

import json
from pathlib import Path

from swarm.orchestrator import main

HERE = Path(__file__).resolve().parent.parent
REPLAY = HERE / "examples" / "replay" / "toy_claims.jsonl"


def expected_status(note: str) -> tuple[str, str | None]:
    if note.startswith("confirmed_fixed"):
        return "confirmed_fixed", "ok"
    if note.startswith("confirmed (fix breaks"):
        return "confirmed", "breaks_suite"
    if note.startswith("confirmed") or note.startswith("deduped"):
        return "confirmed", None
    return "rejected", None


def test_replay_verdicts(tmp_path):
    main([
        "--config", str(HERE / "configs" / "toy.json"), "--backend", "replay",
        "--replay-file", str(REPLAY), "--runs-dir", str(tmp_path), "--run-id", "t",
        "--max-claims", "20",
    ])
    out = tmp_path / "t"
    canned = {json.loads(l)["test_code"]: json.loads(l)["_expect"] for l in REPLAY.open()}
    records = [json.loads(l) for l in (out / "claims.jsonl").open()]
    assert len(records) == len(canned)
    for r in records:
        note = canned[r["claim"]["test_code"]]
        status, fix = expected_status(note)
        assert r["verdict"]["status"] == status, (note, r["verdict"])
        if fix:
            assert r["verdict"]["fix"] == fix, (note, r["verdict"])

    findings = sorted(p.name for p in (out / "findings").iterdir())
    # three planted bugs -> three unique findings after dedupe
    assert len(findings) == 3
    assert "Unique confirmed findings: **3**" in (out / "report.md").read_text()
