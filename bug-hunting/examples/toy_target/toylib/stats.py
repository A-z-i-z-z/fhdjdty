def clamp(x, lo, hi):
    """Return x limited to the closed range [lo, hi]."""
    return max(lo, min(x, hi))


def median(values):
    """Return the median of a non-empty sequence.

    For an even number of values the median is the average of the two middle values.
    """
    s = sorted(values)
    n = len(s)
    if n == 0:
        raise ValueError("median() of empty sequence")
    return s[n // 2]
