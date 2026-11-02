import math
import unittest

from pipeline.check import check_record, numbers_in, parse_conductivity
from pipeline.common import squash_variants


class ParseConductivity(unittest.TestCase):
    cases = {
        "9.95 x 10-4 S/cm": 9.95e-4,
        "9.95 × 10−4 S cm−1": 9.95e-4,
        "1.2 mS cm-1": 1.2e-3,
        "1.2 mS/cm": 1.2e-3,
        "3.4e-4 S cm-1": 3.4e-4,
        "10-4 S/cm": 1e-4,
        "5.72 × 10^{-5} S·cm−1": 5.72e-5,
        "0.5 μS cm-1": 5e-7,
        "0.12 S m-1": 1.2e-3,
        "2.5 × 10⁻³ S cm⁻¹": 2.5e-3,
        # Separators seen in the 100-paper run, which the first parser rejected.
        "2.0 × 10-4 S • cm-1": 2.0e-4,
        "10-4 S.cm-1": 1e-4,
        "15.7 mS.cm−1": 1.57e-2,
    }

    def test_cases(self):
        for s, want in self.cases.items():
            with self.subTest(s=s):
                got = parse_conductivity(s)
                self.assertIsNotNone(got)
                self.assertTrue(math.isclose(got, want, rel_tol=1e-9), (s, got))

    def test_rejects_vague(self):
        for s in ("high conductivity", "about 1e-4", "1.2 mA cm-2"):
            self.assertIsNone(parse_conductivity(s), s)


TEXT = """The highest conductivity of 9.95 x 10-4 S/cm was obtained for a 0.05 Ca ce-
ramic sample sintered at 1050 °C, with an activation energy of 0.23 eV."""


class CheckRecord(unittest.TestCase):
    def rec(self, **kw):
        base = {
            "evidence_quote": "The highest conductivity of 9.95 x 10-4 S/cm was obtained for a 0.05 Ca ceramic sample",
            "conductivity_value_as_reported": "9.95 x 10-4",
            "conductivity_unit_as_reported": "S/cm",
            "unit_evidence_quote": None,
            "sintering_temperature_C": 1050, "activation_energy_eV": 0.23,
            "measurement_temperature_C": None, "measurement_temperature_as_reported": None,
        }
        base.update(kw)
        return check_record(base, squash_variants(TEXT), numbers_in(TEXT))

    def test_good_record_passes_across_hyphenated_line_break(self):
        r = self.rec()
        self.assertTrue(r["passed"], r)
        self.assertAlmostEqual(r["conductivity_S_per_cm"], 9.95e-4)

    def test_paraphrased_quote_rejected(self):
        r = self.rec(evidence_quote="A conductivity of 9.95 x 10-4 S/cm was reached by the 0.05 Ca sample")
        self.assertFalse(r["hard_checks"]["quote_in_text"])

    def test_value_not_in_quote_rejected(self):
        r = self.rec(conductivity_value_as_reported="1.2 x 10-3")
        self.assertFalse(r["hard_checks"]["value_in_quote"])

    def test_invented_secondary_number_warns(self):
        r = self.rec(sintering_temperature_C=1200)
        self.assertTrue(r["passed"])
        self.assertTrue(any("sintering_temperature_C" in w for w in r["warnings"]))

    def test_out_of_range_rejected(self):
        text = "we measured 5 x 10 2 S/cm"  # superscript minus lost in PDF extraction
        r = check_record({"evidence_quote": text, "conductivity_value_as_reported": "5 x 10 2",
                          "conductivity_unit_as_reported": "S/cm"},
                         squash_variants(text), numbers_in(text))
        self.assertFalse(r["passed"])

    def test_table_row_with_unit_in_header(self):
        text = ("Table 1. Conductivity at 25 °C.\ny      σtotal (S cm−1)\n"
                "0.05   4.8 × 10−4\n")
        base = {"evidence_quote": "0.05   4.8 × 10−4", "conductivity_value_as_reported": "4.8 × 10−4",
                "conductivity_unit_as_reported": "S cm−1"}
        v, n = squash_variants(text), numbers_in(text)
        self.assertFalse(check_record({**base, "unit_evidence_quote": None}, v, n)["passed"])
        ok = check_record({**base, "unit_evidence_quote": "σtotal (S cm−1)"}, v, n)
        self.assertTrue(ok["passed"], ok)
        self.assertAlmostEqual(ok["conductivity_S_per_cm"], 4.8e-4)
        bad = check_record({**base, "unit_evidence_quote": "σtotal (mS cm−1)"}, v, n)
        self.assertFalse(bad["passed"])


if __name__ == "__main__":
    unittest.main()
