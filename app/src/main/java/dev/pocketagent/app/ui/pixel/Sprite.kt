package dev.pocketagent.app.ui.pixel

/** One palette entry of a sprite: the char used in the .pxl rows and its colour (ARGB, alpha included). */
class PaletteEntry(val char: Char, val argb: Int)

/** [indices] holds one palette index per pixel (row-major, width * height), -1 = transparent. */
class SpriteFrame(val indices: IntArray, val durationMs: Int)

/** A parsed sprite. Immutable apart from the bitmap cache the renderer keeps in [bitmaps]. */
class Sprite(
    val name: String,
    val width: Int,
    val height: Int,
    val frames: List<SpriteFrame>,
    val palette: List<PaletteEntry>,
) {
    /** Length of one animation loop. */
    val totalMs: Int = frames.sumOf { it.durationMs }.coerceAtLeast(1)

    /** Palette index of the tint-ramp chars '1'..'4' (-1 when the sprite does not use them). */
    internal val rampSlots: IntArray = IntArray(4) { k -> palette.indexOfFirst { it.char == '1' + k } }

    /** Renderer cache: the plain (no tint, no ramp) bitmap of each frame, filled lazily. */
    internal val bitmaps: Array<Any?> = arrayOfNulls(frames.size)

    /** Pure twin of [bitmaps]: the plain ARGB pixels of each frame (see SpriteRaster.plain). Never mutate them. */
    internal val rasters: Array<IntArray?> = arrayOfNulls(frames.size)

    /** Index of the frame that is showing [timeMs] after the animation started (loops). */
    fun frameAt(timeMs: Long): Int {
        if (frames.size == 1) return 0
        var t = (timeMs % totalMs).toInt()
        if (t < 0) t += totalMs
        for (i in frames.indices) {
            val d = frames[i].durationMs
            if (t < d) return i
            t -= d
        }
        return frames.size - 1
    }

    /** Milliseconds from [timeMs] until the next frame change (at least 1). */
    fun msUntilNextFrame(timeMs: Long): Int {
        if (frames.size == 1) return totalMs
        var t = (timeMs % totalMs).toInt()
        if (t < 0) t += totalMs
        for (i in frames.indices) {
            val d = frames[i].durationMs
            if (t < d) return (d - t).coerceAtLeast(1)
            t -= d
        }
        return 1
    }
}

/** Thrown for any problem in a .pxl file. The message always starts with "file:line: ". */
class PxlException(message: String) : IllegalArgumentException(message)

/**
 * Parser for the `.pxl` text format (docs/phase1-contract.md). Pure Kotlin, no Android types, so it is
 * unit-tested on the JVM. Strict on purpose: a wrong row length or an unknown palette char is an error,
 * never silently ignored.
 *
 *  - `#` in column 0 starts a comment line (so `#` cannot be a palette char). Blank lines are ignored
 *    between directives but not inside a frame (a frame is exactly `h` consecutive rows).
 *  - `palette` starts a new palette block; its lines are `<char> <#RRGGBB | #AARRGGBB | none>`.
 *    The block applies to every following sprite until another `palette` replaces it.
 *  - `.` is always transparent. A char defined as `none` is transparent too.
 *  - `sprite <name> <w> <h>`, then one or more `frame [ms]` + h rows of w chars.
 */
object PxlParser {
    private val WS = Regex("\\s+")
    private val NAME = Regex("[A-Za-z0-9_]+")
    const val DEFAULT_FRAME_MS = 120

    private class Pending(
        val name: String, val w: Int, val h: Int, val line: Int,
        val entries: List<PaletteEntry>, val index: Map<Char, Int>,
    ) {
        val frames = ArrayList<SpriteFrame>()
    }

    fun parse(fileName: String, text: String): List<Sprite> {
        val lines = text.split('\n')
        val out = ArrayList<Sprite>()
        val seen = HashSet<String>()
        var pal = LinkedHashMap<Char, Int?>()   // null = "none"
        var cur: Pending? = null
        var i = 0

        fun fail(line: Int, msg: String): Nothing = throw PxlException("$fileName:$line: $msg")

        fun finish(p: Pending) {
            if (p.frames.isEmpty()) fail(p.line, "sprite '${p.name}' has no frames")
            out.add(Sprite(p.name, p.w, p.h, p.frames.toList(), p.entries))
        }

        while (i < lines.size) {
            val raw = lines[i].removeSuffix("\r")
            i++
            val lineNo = i
            if (raw.isBlank() || raw.startsWith("#")) continue
            val tok = raw.trim().split(WS)
            when {
                tok[0] == "palette" && tok.size == 1 -> {
                    cur?.let { finish(it) }
                    cur = null
                    pal = LinkedHashMap()
                }
                tok[0] == "sprite" && tok.size == 4 -> {
                    cur?.let { finish(it) }
                    val name = tok[1]
                    if (!NAME.matches(name)) fail(lineNo, "bad sprite name '$name' (letters, digits, underscore)")
                    if (!seen.add(name)) fail(lineNo, "duplicate sprite name '$name' in this file")
                    val w = tok[2].toIntOrNull() ?: fail(lineNo, "bad sprite width '${tok[2]}'")
                    val h = tok[3].toIntOrNull() ?: fail(lineNo, "bad sprite height '${tok[3]}'")
                    if (w <= 0 || h <= 0 || w > 4096 || h > 4096) fail(lineNo, "bad sprite size ${w}x$h")
                    val entries = ArrayList<PaletteEntry>()
                    val index = HashMap<Char, Int>()
                    for ((ch, argb) in pal) {
                        if (argb == null) {
                            index[ch] = -1
                        } else {
                            index[ch] = entries.size
                            entries.add(PaletteEntry(ch, argb))
                        }
                    }
                    cur = Pending(name, w, h, lineNo, entries, index)
                }
                tok[0] == "frame" && tok.size <= 2 -> {
                    val p = cur ?: fail(lineNo, "'frame' before any 'sprite'")
                    var ms = DEFAULT_FRAME_MS
                    if (tok.size == 2) {
                        ms = tok[1].toIntOrNull() ?: fail(lineNo, "bad frame duration '${tok[1]}'")
                        if (ms <= 0) fail(lineNo, "frame duration must be positive, got $ms")
                    }
                    val idx = IntArray(p.w * p.h)
                    for (r in 0 until p.h) {
                        if (i >= lines.size) {
                            fail(lines.size, "sprite '${p.name}' frame ${p.frames.size} ends after $r of ${p.h} rows")
                        }
                        val row = lines[i].removeSuffix("\r")
                        i++
                        if (row.length != p.w) {
                            fail(i, "row has ${row.length} chars, expected ${p.w} (sprite '${p.name}', frame ${p.frames.size}, row $r)")
                        }
                        for (x in 0 until p.w) {
                            val ch = row[x]
                            idx[r * p.w + x] = if (ch == '.') -1 else (p.index[ch]
                                ?: fail(i, "char '$ch' at column $x is not in the palette (sprite '${p.name}')"))
                        }
                    }
                    p.frames.add(SpriteFrame(idx, ms))
                }
                tok.size == 2 && tok[0].length == 1 -> {
                    val ch = tok[0][0]
                    if (ch == '#') fail(lineNo, "'#' cannot be a palette char")
                    if (ch == '.' && tok[1] != "none") fail(lineNo, "'.' is always transparent")
                    pal[ch] = parseColor(tok[1]) { msg -> fail(lineNo, msg) }
                }
                else -> fail(lineNo, "cannot parse line '${raw.trim()}'")
            }
        }
        cur?.let { finish(it) }
        return out
    }

    /** `none` -> null; `#RRGGBB` -> opaque; `#AARRGGBB`. */
    private inline fun parseColor(text: String, fail: (String) -> Nothing): Int? {
        if (text == "none") return null
        if (text.length != 7 && text.length != 9 || text[0] != '#') fail("bad colour '$text' (use #RRGGBB, #AARRGGBB or none)")
        val v = text.substring(1).toLongOrNull(16) ?: fail("bad colour '$text' (not hexadecimal)")
        return if (text.length == 7) (0xFF000000L or v).toInt() else v.toInt()
    }
}
