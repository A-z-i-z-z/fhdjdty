from toylib.stats import clamp, median
from toylib.text import initials, truncate


def test_clamp():
    assert clamp(5, 0, 3) == 3
    assert clamp(-1, 0, 3) == 0
    assert clamp(2, 0, 3) == 2


def test_median_odd():
    assert median([3, 1, 2]) == 2


def test_truncate_short_string_unchanged():
    assert truncate("hi", 10) == "hi"


def test_initials_simple():
    assert initials("ada lovelace") == "AL"
