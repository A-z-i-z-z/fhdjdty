"""Stage 4: merge checks and verdicts into one table, plus a rejects table and a report."""
from __future__ import annotations

import csv
import json
import re
from collections import Counter

from .common import Workdir, safe_id, squash, squash_variants

COLUMNS = [
    "record_id", "paper_id", "title", "year", "doi", "source_url",
    "material_as_written", "material_class", "mobile_ion", "dopants", "data_origin",
    "synthesis_method", "sintering_temperature_C", "sintering_time_h", "relative_density_pct",
    "conductivity_value_as_reported", "conductivity_unit_as_reported", "conductivity_S_per_cm", "log10_conductivity", "conductivity_kind",
    "measurement_temperature_as_reported", "measurement_temperature_C", "activation_energy_eV",
    "measurement_method", "evidence_quote", "unit_evidence_quote", "evidence_location",
    "status", "corrections", "warnings", "notes",
]

# Fields the verifier may correct. Core fields (material, conductivity, temperature, origin)
# are never patched: if those are wrong the record is rejected instead.
CORRECTABLE = {
    "synthesis_method": str, "sintering_temperature_C": float, "sintering_time_h": float,
    "relative_density_pct": float, "activation_energy_eV": float, "measurement_method": str,
    "conductivity_kind": str, "dopants": list, "material_class": str,
}


def _coerce(kind, v):
    if v is None or v == "" or str(v).lower() == "null":
        return None
    if kind is float:
        return float(v)
    if kind is list:
        return v if isinstance(v, list) else [x.strip() for x in str(v).split(",") if x.strip()]
    return str(v)


_KELVIN = re.compile(r"^\s*(\d+(?:\.\d+)?)\s*K\s*$")


def kelvin_to_c(rec: dict) -> bool:
    """Fill measurement_temperature_C from a plain 'NNN K' string. The model copies, code converts."""
    if rec.get("measurement_temperature_C") is not None:
        return False
    m = _KELVIN.match(rec.get("measurement_temperature_as_reported") or "")
    if not m:
        return False
    rec["measurement_temperature_C"] = round(float(m.group(1)) - 273.15, 2)
    return True


def _load(path):
    return json.loads(path.read_text()) if path.exists() else None


def build(wd: Workdir) -> None:
    kept, rejected = [], []
    usage = Counter()
    n_papers = n_in_scope = 0
    for pid in wd.paper_ids():
        ex = _load(wd.extractions / f"{safe_id(pid)}.json")
        checked = _load(wd.checked / f"{safe_id(pid)}.json")
        ver = _load(wd.verifications / f"{safe_id(pid)}.json")
        if ex is None or checked is None:
            continue
        n_papers += 1
        n_in_scope += bool(ex["output"].get("paper_in_scope"))
        for stage in (ex, ver):
            model_usage = ((stage or {}).get("usage") or {}).get("model_usage") or {}
            for m in model_usage.values():
                usage["input"] += m.get("inputTokens", 0) + m.get("cacheReadInputTokens", 0) + m.get("cacheCreationInputTokens", 0)
                usage["output"] += m.get("outputTokens", 0)
                usage["notional_usd"] += m.get("costUSD", 0) or 0
        meta = wd.meta(pid)
        variants = squash_variants(wd.text(pid))
        verdicts = {v["record_id"]: v for v in (ver or {}).get("output", {}).get("verdicts", [])}

        for c in checked:
            rec = dict(c["record"])
            row = {
                "record_id": c["record_id"], "paper_id": pid, "title": meta.get("title"),
                "year": (meta.get("published") or "")[:4] or None, "doi": meta.get("doi"),
                "source_url": meta.get("abs_url"),
                "conductivity_S_per_cm": c["conductivity_S_per_cm"],
                "log10_conductivity": c["log10_conductivity"],
                "warnings": "; ".join(c["warnings"]),
            }
            if not c["passed"]:
                failed = [k for k, ok in c["hard_checks"].items() if not ok]
                rejected.append({**row, **rec, "status": "rejected_by_checker", "reason": ",".join(failed)})
                continue
            v = verdicts.get(c["record_id"])
            if v is None:
                rejected.append({**row, **rec, "status": "not_verified", "reason": "no verdict"})
                continue
            if v["verdict"] == "unsupported":
                rejected.append({**row, **rec, "status": "rejected_by_verifier", "reason": v["explanation"]})
                continue
            applied = []
            for fe in v.get("field_errors", []):
                f = fe.get("field")
                ev = squash(fe.get("evidence") or "")
                # Accept a correction only if its own evidence is a verbatim span of the paper.
                if f in CORRECTABLE and len(ev) >= 8 and any(ev in t for t in variants):
                    try:
                        rec[f] = _coerce(CORRECTABLE[f], fe.get("correct_value"))
                        applied.append(f"{f}={rec[f]}")
                    except (TypeError, ValueError):
                        pass
            kelvin_to_c(rec)
            row["status"] = "verified_corrected" if applied else "verified"
            row["corrections"] = "; ".join(applied)
            kept.append({**row, **rec})

    for r in kept + rejected:
        if isinstance(r.get("dopants"), list):
            r["dopants"] = ";".join(r["dopants"])

    with open(wd.root / "records.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS, extrasaction="ignore")
        w.writeheader()
        w.writerows(kept)
    with open(wd.root / "rejected.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLUMNS + ["reason"], extrasaction="ignore")
        w.writeheader()
        w.writerows(rejected)

    status = Counter(r["status"] for r in kept + rejected)
    origin = Counter(r.get("data_origin") for r in kept)
    total = len(kept) + len(rejected)
    lines = [
        "# Pilot run report", "",
        f"- Papers processed: {n_papers} ({n_in_scope} in scope)",
        f"- Records extracted: {total}",
        f"- Kept: {len(kept)} ({len(kept) / total:.0%})" if total else "- Kept: 0",
        "", "| status | records |", "|---|---|",
        *[f"| {k} | {v} |" for k, v in sorted(status.items())],
        "", "Kept records by origin: " + ", ".join(f"{k}: {v}" for k, v in origin.items()),
        "", f"Tokens: {usage['input']:,} in, {usage['output']:,} out "
            f"(notional API-price equivalent ${usage["notional_usd"]:.4f}).",
    ]
    (wd.root / "report.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
