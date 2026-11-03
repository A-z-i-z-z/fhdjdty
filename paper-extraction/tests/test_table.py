import unittest

from pipeline.table import kelvin_to_c


class KelvinToCelsius(unittest.TestCase):
    def test_plain_kelvin_is_converted(self):
        rec = {"measurement_temperature_as_reported": "300 K", "measurement_temperature_C": None}
        self.assertTrue(kelvin_to_c(rec))
        self.assertAlmostEqual(rec["measurement_temperature_C"], 26.85)

    def test_existing_value_and_ranges_are_left_alone(self):
        rec = {"measurement_temperature_as_reported": "423 K", "measurement_temperature_C": 150.0}
        self.assertFalse(kelvin_to_c(rec))
        for text in ("between 450 and 520 K", "room temperature", ""):
            rec = {"measurement_temperature_as_reported": text, "measurement_temperature_C": None}
            self.assertFalse(kelvin_to_c(rec))
            self.assertIsNone(rec["measurement_temperature_C"])


if __name__ == "__main__":
    unittest.main()
