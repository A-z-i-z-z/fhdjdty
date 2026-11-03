"""Offline tests: no network, no model calls. Run from data-cleaning/:

    python -m unittest discover -s tests -v
"""

import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

from swarm_clean import canaries, fetch, pipeline, rules, verify
from swarm_clean.backends import ApiBackend, MockBackend, validate

FIXTURE = Path(__file__).parent / "fixtures" / "sample.jsonl"


def label(category="open_qa", quality=5, issues=(), keep=True):
    return {"category": category, "quality": quality, "issues": list(issues), "keep": keep, "note": ""}


class RulesTest(unittest.TestCase):
    def setUp(self):
        self.records = fetch.load(FIXTURE)
        self.kept, self.rejected = rules.run(self.records)
        self.reasons = {r["id"]: r["reasons"] for r in self.rejected}

    def test_exact_duplicate_removed(self):
        self.assertEqual(self.reasons["dolly-00020"], ["exact_duplicate_of:dolly-00004"])

    def test_near_duplicate_removed(self):
        self.assertEqual(self.reasons["dolly-00021"], ["near_duplicate_of:dolly-00005"])

    def test_pii_and_empty_rejected(self):
        self.assertIn("pii_email", self.reasons["dolly-00022"])
        self.assertIn("empty_response", self.reasons["dolly-00023"])

    def test_distinct_short_answers_are_not_merged(self):
        kept_ids = {r["id"] for r in self.kept}
        for rid in ("dolly-00005", "dolly-00006", "dolly-00016"):
            self.assertIn(rid, kept_ids)

    def test_citation_markers_stripped(self):
        lake = next(r for r in self.kept if r["id"] == "dolly-00000")
        self.assertNotIn("[3]", lake["context"])


class CanaryTest(unittest.TestCase):
    def test_canaries_are_broken_copies(self):
        kept, _ = rules.run(fetch.load(FIXTURE))
        made = canaries.make(kept, seed=1)
        self.assertGreaterEqual(len(made), 6)
        self.assertEqual(len({c["id"] for c in made}), len(made))
        by_id = {r["id"]: r for r in kept}
        for c in made:
            src = by_id[c["source_id"]]
            if c["canary"] == "swapped_response":
                self.assertNotEqual(c["response"], src["response"])
            if c["canary"] == "context_removed":
                self.assertEqual(c["context"], "")
                self.assertTrue(src["context"])


class VerifyTest(unittest.TestCase):
    def test_agreeing_pair_kept(self):
        d = verify.decide([label(), label()], min_quality=3)
        self.assertTrue(d["keep"])
        self.assertTrue(d["unanimous"])

    def test_majority_of_three(self):
        d = verify.decide([label(), label(keep=False, quality=2), label()], min_quality=3)
        self.assertTrue(d["keep"])
        self.assertFalse(d["unanimous"])

    def test_hard_issue_needs_majority_and_drops(self):
        one = verify.decide([label(issues=["needs_missing_context"]), label(), label()], 3)
        self.assertTrue(one["keep"])
        two = verify.decide([label(issues=["needs_missing_context"])] * 2 + [label()], 3)
        self.assertFalse(two["keep"])
        self.assertIn("issue:needs_missing_context", two["reasons"])

    def test_category_without_majority_is_none(self):
        d = verify.decide([label("open_qa"), label("general_qa"), label("closed_qa")], 3)
        self.assertIsNone(d["swarm_category"])

    def test_kappa(self):
        self.assertEqual(verify.cohen_kappa([1, 0, 1, 0], [1, 0, 1, 0]), 1.0)
        self.assertAlmostEqual(verify.cohen_kappa([1, 1, 0, 0], [1, 0, 1, 0]), 0.0)


class ValidateTest(unittest.TestCase):
    def test_rejects_bad_labels(self):
        for bad in [
            {**label(), "category": "poetry"},
            {**label(), "quality": 7},
            {**label(), "quality": True},
            {**label(), "issues": ["made_up"]},
            {**label(), "extra": 1},
            {k: v for k, v in label().items() if k != "keep"},
        ]:
            with self.assertRaises(ValueError):
                validate(bad)

    def test_api_parse(self):
        usage = SimpleNamespace(input_tokens=10, output_tokens=5)
        good = SimpleNamespace(stop_reason="end_turn", usage=usage,
                               content=[SimpleNamespace(type="text", text=json.dumps(label()))])
        self.assertEqual(ApiBackend._parse(good)["label"]["category"], "open_qa")
        refused = SimpleNamespace(stop_reason="refusal", usage=usage, content=[])
        self.assertEqual(ApiBackend._parse(refused), {"error": "refusal"})


class DisagreeingBackend(MockBackend):
    """Framing B flips the category on every third record to force tie-breaks."""

    def __init__(self):
        self.calls = {"A": 0, "B": 0, "C": 0}

    def label_one(self, job):
        self.calls[job.framing] += 1
        res = super().label_one(job)
        if job.framing == "B" and int(job.record["id"][-1], 16) % 3 == 0:
            res["label"]["category"] = "brainstorming" if res["label"]["category"] != "brainstorming" else "open_qa"
        return res


class PipelineTest(unittest.TestCase):
    def run_pipeline(self, out, backend=None):
        argv = ["--input", str(FIXTURE), "--limit", "0", "--out", str(out), "--workers", "4"]
        if backend is None:
            return pipeline.main(argv)
        with mock.patch.object(pipeline, "make_backend", return_value=backend):
            return pipeline.main(argv)

    def test_end_to_end_mock(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            self.assertEqual(self.run_pipeline(out), 0)
            report = json.loads((out / "report.json").read_text())
            self.assertEqual(report["status"], "PASS")
            clean = [json.loads(l) for l in (out / "clean.jsonl").read_text().splitlines()]
            ids = {r["id"] for r in clean}
            self.assertFalse(any(i.startswith("canary") for i in ids))
            for bad in ("dolly-00020", "dolly-00022", "dolly-00024", "dolly-00025"):
                self.assertNotIn(bad, ids)
            self.assertTrue(all("labels" in r for r in clean))
            lenient = [json.loads(l) for l in (out / "clean_lenient.jsonl").read_text().splitlines()]
            self.assertTrue(ids <= {r["id"] for r in lenient})
            self.assertTrue(all(r["labels"]["quality"] >= 3.5 for r in clean))
            self.assertEqual(report["counts"]["clean_lenient"], len(lenient))
            self.assertTrue((out / "LICENSE.txt").exists())

    def test_resume_uses_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            self.run_pipeline(out)
            self.run_pipeline(out, backend=MockBackend())
            report = json.loads((out / "report.json").read_text())
            self.assertTrue(all(p["called"] == 0 for p in report["passes"]))

    def test_disagreement_triggers_tiebreak(self):
        with tempfile.TemporaryDirectory() as tmp:
            backend = DisagreeingBackend()
            self.run_pipeline(Path(tmp), backend=backend)
            report = json.loads((Path(tmp) / "report.json").read_text())
            self.assertGreater(backend.calls["C"], 0)
            self.assertEqual(report["counts"]["tiebreaks"], backend.calls["C"])
            self.assertLess(report["agreement"]["kappa_category"], 1.0)

    def test_weak_labelers_fail_the_run(self):
        class KeepEverything(MockBackend):
            def label_one(self, job):
                return {"label": label(keep=True)}

        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(self.run_pipeline(Path(tmp), backend=KeepEverything()), 2)
            report = json.loads((Path(tmp) / "report.json").read_text())
            self.assertEqual(report["status"], "FAIL")
            self.assertEqual(report["canaries"]["recall"], 0.0)


if __name__ == "__main__":
    unittest.main()
