"""Stage 2: deterministic checks. No model involved; every rule here is mechanical.

Hard checks (a failure rejects the record before the verifier sees it):
  quote_in_text      the evidence quote is a verbatim span of the paper
  value_in_quote     conductivity_value_as_reported is a span of the quote
  unit_shown         the unit is in the quote, or in unit_evidence_quote which is itself a span
  value_parses       the reported string parses to a number and a conductivity unit
  value_in_range     1e-14 <= sigma <= 1e-1 S/cm (no known solid electrolyte is outside this)

Soft checks (recorded as warnings, passed to the verifier):
  each non-null numeric field (sintering T, density, Ea, measurement T) appears as a
  number somewhere in the paper text.
"""
from __future__ import annotations

import math
import re

from .common import norm_chars, squash, squash_variants

SIGMA_MIN, SIGMA_MAX = 1e-14, 1e-1

_UNIT_RE = re.compile(
    r"(?P<prefix>[munμµ]?)\s*S\s*(?:/\s*(?P<per>cm|m)\b|(?:[x.·•⋅]\s*)?(?P<per2>cm|m)\s*\^?\s*-\s*1)",
)
_NUM = r"(?P<mant>\d+(?:\.\d+)?)"
_SCI_RE = re.compile(_NUM + r"\s*(?:x|\*)\s*10\s*\^?\s*\(?\s*(?P<exp>[-+]?\s*\d+)\s*\)?")
_E_RE = re.compile(_NUM + r"\s*[eE]\s*(?P<exp>[-+]?\d+)")
_POW_RE = re.compile(r"(?<![\d.])10\s*\^?\s*\(?\s*(?P<exp>-\s*\d+)\s*\)?")
_PLAIN_RE = re.compile(r"(?<![\d.])" + _NUM)

PREFIX = {"": 1.0, "m": 1e-3, "u": 1e-6, "μ": 1e-6, "µ": 1e-6, "n": 1e-9}


def parse_conductivity(s: str) -> float | None:
    """Parse strings like '9.95 x 10-4 S/cm', '1.2 mS cm-1', '3e-4 S cm−1' to S/cm.

    Returns None if the string has no recognisable number or unit, so a vague
    or mangled value is rejected rather than guessed at.
    """
    t = norm_chars(s).replace("{", "").replace("}", "")
    u = _UNIT_RE.search(t)
    if not u:
        return None
    head = t[: u.start()]
    m = _SCI_RE.search(head) or _E_RE.search(head)
    if m:
        value = float(m.group("mant")) * 10 ** int(m.group("exp").replace(" ", ""))
    else:
        m = _POW_RE.search(head)
        if m:
            value = 10 ** int(m.group("exp").replace(" ", ""))
        else:
            nums = list(_PLAIN_RE.finditer(head))
            if not nums:
                return None
            value = float(nums[-1].group("mant"))
    value *= PREFIX.get(u.group("prefix"), 1.0)
    if (u.group("per") or u.group("per2")) == "m":
        value /= 100.0  # S/m -> S/cm
    return value


_ANY_NUM = re.compile(r"(?<![\w.])-?\d+(?:\.\d+)?")


def numbers_in(text: str) -> set[float]:
    return {float(x) for x in _ANY_NUM.findall(norm_chars(text))}


def _near(x: float, pool: set[float], rel: float = 0.005) -> bool:
    return any(abs(x - p) <= max(rel * abs(x), 1e-9) for p in pool)


def check_record(rec: dict, text_variants: list[str], text_numbers: set[float]) -> dict:
    hard: dict[str, bool] = {}
    warnings: list[str] = []

    q = squash(rec.get("evidence_quote", ""))
    hard["quote_in_text"] = len(q) >= 8 and any(q in v for v in text_variants)

    value = rec.get("conductivity_value_as_reported", "")
    unit = rec.get("conductivity_unit_as_reported", "")
    hard["value_in_quote"] = bool(squash(value)) and squash(value) in q

    # The unit must be shown verbatim somewhere: in the quote, or in a second quote
    # (a table header) that is itself a span of the paper.
    u, ue = squash(unit), squash(rec.get("unit_evidence_quote") or "")
    unit_shown = bool(u) and (u in q or (u in ue and any(ue in v for v in text_variants)))
    hard["unit_shown"] = unit_shown

    sigma = parse_conductivity(f"{value} {unit}")
    hard["value_parses"] = sigma is not None
    hard["value_in_range"] = sigma is not None and SIGMA_MIN <= sigma <= SIGMA_MAX
    if sigma is not None:
        sigma = float(f"{sigma:.6g}")

    if len(q) <= len(squash(value)) + len(u) + 2:
        warnings.append("quote is only the value; material comes from context")

    for field in ("sintering_temperature_C", "sintering_time_h", "relative_density_pct",
                  "activation_energy_eV", "measurement_temperature_C"):
        x = rec.get(field)
        if x is None:
            continue
        candidates = {float(x)}
        if field == "measurement_temperature_C":
            candidates |= {round(float(x) + 273.15, 2), round(float(x) + 273, 2)}  # stated in K
        if not any(_near(c, text_numbers) for c in candidates):
            warnings.append(f"{field}={x} not found as a number in the paper")

    if rec.get("measurement_temperature_C") is None and not rec.get("measurement_temperature_as_reported"):
        warnings.append("no measurement temperature")

    ea = rec.get("activation_energy_eV")
    if ea is not None and not (0.05 <= ea <= 2.0):
        warnings.append(f"activation_energy_eV={ea} outside 0.05-2 eV")

    return {
        "hard_checks": hard,
        "passed": all(hard.values()),
        "warnings": warnings,
        "conductivity_S_per_cm": sigma,
        "log10_conductivity": round(math.log10(sigma), 3) if sigma else None,
    }


def check_paper(paper_id: str, text: str, extraction: dict) -> list[dict]:
    variants = squash_variants(text)
    nums = numbers_in(text)
    out = []
    for i, rec in enumerate(extraction.get("records", [])):
        rid = f"{paper_id}#{i + 1:03d}"
        out.append({"record_id": rid, "paper_id": paper_id, "record": rec,
                    **check_record(rec, variants, nums)})
    return out
