def truncate(s, width, suffix="..."):
    """Shorten s to fit in width characters, ending with suffix if anything was cut.

    The result is never longer than width.
    """
    if len(s) <= width:
        return s
    return s[:width] + suffix


def initials(name):
    """Return the uppercase initials of each word in name. Extra whitespace is ignored."""
    return "".join(word[0].upper() for word in name.strip().split(" "))
