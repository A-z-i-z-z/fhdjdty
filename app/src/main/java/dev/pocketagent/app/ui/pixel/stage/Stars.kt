package dev.pocketagent.app.ui.pixel.stage

import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/** Look knob: stars fainter than this are not drawn (3.0 to 4.5). */
const val STAR_MAG_LIMIT: Float = 4.5f

/** Look knob: the alpha floor of the faintest stars (0.2 to 0.5). */
const val STAR_DIM_BASE: Float = 0.35f

/**
 * The bright stars in a table of parallel arrays, brightest first. Positions are J2000 right ascension and
 * declination in degrees; [tint] is 0 blue-white, 1 white, 2 warm; [nameId] is 0 or a 1-based index into [names].
 *
 * The file `assets/sky/stars.bin` (little endian): header of 12 bytes, the text AGST, a version byte (1), the
 * magnitude limit times ten (one byte), the star count N (u16), the name count M (u16) and two reserved bytes; then
 * N records of 8 bytes: right ascension u16 (`round(raDeg / 360 * 65536)`), declination i16 (`round(decDeg / 90 *
 * 32767)`), magnitude times ten i8, tint u8, name id u16; then M names, each a length byte (1 to 24) and that many
 * UTF-8 bytes.
 */
class StarTable(
    val raDeg: FloatArray, val decDeg: FloatArray, val mag: FloatArray,
    val tint: ByteArray, val nameId: ShortArray, val names: List<String>,
) {
    val size: Int get() = raDeg.size

    /** The proper name of star [i], or null when it has none. */
    fun nameOf(i: Int): String? {
        val id = nameId[i].toInt() and 0xFFFF
        return if (id >= 1 && id <= names.size) names[id - 1] else null
    }

    companion object {
        val EMPTY: StarTable = StarTable(FloatArray(0), FloatArray(0), FloatArray(0), ByteArray(0), ShortArray(0), emptyList())

        private const val HEADER = 12
        private const val RECORD = 8

        /** Reads a table. Never throws: bad magic, version, count, truncation or a value out of range gives [EMPTY]. */
        fun parse(bytes: ByteArray): StarTable {
            try {
                return parseOrThrow(bytes)
            } catch (e: RuntimeException) {
                return EMPTY
            }
        }

        private fun parseOrThrow(b: ByteArray): StarTable {
            if (b.size < HEADER) return EMPTY
            fun u8(i: Int): Int = b[i].toInt() and 0xFF
            fun u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
            if (b[0] != 'A'.code.toByte() || b[1] != 'G'.code.toByte() || b[2] != 'S'.code.toByte() || b[3] != 'T'.code.toByte()) return EMPTY
            if (u8(4) != 1) return EMPTY
            val n = u16(6)
            val m = u16(8)
            if (n == 0) return EMPTY
            if (HEADER.toLong() + n.toLong() * RECORD > b.size.toLong()) return EMPTY
            val ra = FloatArray(n)
            val dec = FloatArray(n)
            val mag = FloatArray(n)
            val tint = ByteArray(n)
            val nameId = ShortArray(n)
            for (i in 0 until n) {
                val p = HEADER + i * RECORD
                val decRaw = u16(p + 2).toShort().toInt()
                if (decRaw < -32767) return EMPTY
                val t = u8(p + 5)
                val id = u16(p + 6)
                if (t > 2 || id > m) return EMPTY
                ra[i] = (u16(p) / 65536.0 * 360.0).toFloat()
                dec[i] = (decRaw / 32767.0 * 90.0).toFloat()
                mag[i] = b[p + 4].toInt() / 10f
                tint[i] = t.toByte()
                nameId[i] = id.toShort()
            }
            val names = ArrayList<String>(m)
            var q = HEADER + n * RECORD
            for (k in 0 until m) {
                if (q >= b.size) return EMPTY
                val len = u8(q)
                if (len < 1 || len > 24 || q + 1 + len > b.size) return EMPTY
                names.add(String(b, q + 1, len, StandardCharsets.UTF_8))
                q += 1 + len
            }
            return StarTable(ra, dec, mag, tint, nameId, names)
        }
    }
}

/**
 * The stars of one scene size on the stage: their screen places, recomputed only when the minute, the place
 * (rounded to 0.1 degree), the stage width or the horizon row changes; the precession from J2000 is done once per UTC
 * day. Arrays are sized at construction, so nothing is allocated per frame. Drawing (the plus-shaped bright stars,
 * the twinkle) belongs to the renderer part and reads [screenX], [screenY] and [alphaFor].
 */
class StarLayer(val table: StarTable) {
    /** How many stars are on the strip after the last [update]. */
    var visibleCount: Int = 0
        private set

    /** How often the screen places were recomputed (a cache that holds shows no change). */
    var projections: Int = 0
        private set

    /** Slot k of the visible stars: the index into [table], and the stage pixel. Valid below [visibleCount]. */
    val starIndex: IntArray = IntArray(table.size)
    val screenX: IntArray = IntArray(table.size)
    val screenY: IntArray = IntArray(table.size)

    private val raNow = DoubleArray(table.size)
    private val decNow = DoubleArray(table.size)
    private val scratch = DoubleArray(2)
    private var precessDay = Long.MIN_VALUE
    private var keyMinute = Long.MIN_VALUE
    private var keyLat = Int.MIN_VALUE
    private var keyLon = Int.MIN_VALUE
    private var keyWidth = -1
    private var keyHorizon = Int.MIN_VALUE

    fun update(frame: SkyFrame, width: Int, horizonRow: Int) {
        val minute = Math.floorDiv(frame.epochMs, 60_000L)
        val latKey = (frame.latDeg * 10.0).roundToInt()
        val lonKey = (frame.lonDeg * 10.0).roundToInt()
        if (minute == keyMinute && latKey == keyLat && lonKey == keyLon && width == keyWidth && horizonRow == keyHorizon) return
        keyMinute = minute
        keyLat = latKey
        keyLon = lonKey
        keyWidth = width
        keyHorizon = horizonRow
        projections++
        val day = Math.floorDiv(frame.epochMs, 86_400_000L)
        if (day != precessDay) { // the precession depends on the date only, so once per UTC day
            precessDay = day
            for (i in 0 until table.size) {
                SkyClock.precess(table.raDeg[i].toDouble(), table.decDeg[i].toDouble(), frame.epochMs, scratch)
                raNow[i] = scratch[0]
                decNow[i] = scratch[1]
            }
        }
        var count = 0
        for (i in 0 until table.size) {
            if (table.mag[i] > STAR_MAG_LIMIT) continue
            val aa = SkyClock.altAz(raNow[i], decNow[i], frame.lstDeg, frame.latDeg)
            val packed = SkyProjection.project(aa.azDeg, aa.altDeg, frame.facingAz, width, horizonRow)
            if (packed == SkyProjection.NONE) continue
            starIndex[count] = i
            screenX[count] = packed and 0xFFFF
            screenY[count] = packed ushr 16
            count++
        }
        visibleCount = count
    }

    companion object {
        /** Tint colours (opaque ARGB) of tint 0 blue-white, 1 white, 2 warm. */
        val TINT_ARGB: IntArray = intArrayOf(0xFFBCD2FF.toInt(), 0xFFEEEBFF.toInt(), 0xFFFFD9A8.toInt())

        /**
         * How visible a star of magnitude [mag] is when the sun is [depressionDeg] below the horizon: 0 in daylight,
         * brightest stars first at dusk (`threshold = 1 + 1.5 * (mag + 1.5)`), full three degrees after its
         * threshold; times the weather's [visibility].
         */
        fun alphaFor(mag: Float, depressionDeg: Float, visibility: Float): Float {
            val thr = 1f + 1.5f * (mag + 1.5f)
            return ((depressionDeg - thr) / 3f).coerceIn(0f, 1f) * visibility
        }
    }
}
