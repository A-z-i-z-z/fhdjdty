package dev.pocketagent.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

/*
 * Places: where the sky is calculated for. Pure Kotlin (no Android import): the zone table, the offline city
 * list, the search over it and the order in which a place is chosen. The asset loading and the preference
 * live in PlaceStore (Android). Time zone, files and text come in as parameters.
 */

/** How a [Place] came about. */
enum class PlaceSource { ZONE, CITY, OFFSET, PINNED }

/** A point on the earth with a display name. Coordinates are what the sky and the weather use. */
data class Place(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val source: PlaceSource,
    val countryCode: String = "",
) {
    val isSouthern: Boolean get() = latitude < 0.0

    /** "59.9,10.7": both coordinates at one decimal, the key of the weather cache and of the request. */
    fun key(): String = formatCoord(latitude, 1) + "," + formatCoord(longitude, 1)
}

/** One row of the offline city list. [nativeName] is empty when it equals [name]. */
data class City(
    val name: String,
    val nativeName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val populationK: Int,
)

/** One row of the zone table: the typical point of a time zone id (links to another id are rows of their own). */
data class ZoneRow(val zoneId: String, val countryCode: String, val latitude: Double, val longitude: Double)

/** What the person chose: the guess from the time zone, or a city. */
sealed interface PlaceChoice {
    data object Auto : PlaceChoice
    data class Picked(val city: City) : PlaceChoice
}

/**
 * The zone table and the city list. [zones] and [cities] are used as given; [parse] reads the asset text.
 */
class PlaceTable(val zones: List<ZoneRow>, val cities: List<City>) {
    private val byId: HashMap<String, ZoneRow> = HashMap()
    private val byLowerId: HashMap<String, ZoneRow> = HashMap()
    private val normName: Array<String> = Array(cities.size) { normalize(cities[it].name) }
    private val normNative: Array<String> = Array(cities.size) {
        val n = cities[it].nativeName
        if (n.isEmpty()) "" else normalize(n)
    }

    init {
        for (z in zones) {
            if (!byId.containsKey(z.zoneId)) byId[z.zoneId] = z
            val low = z.zoneId.lowercase(Locale.ROOT)
            if (!byLowerId.containsKey(low)) byLowerId[low] = z
        }
    }

    /**
     * The place of a time zone: the table row (the exact id, links included as rows), else a guess from the
     * standard UTC offset: 15 degrees of longitude per hour, latitude 35, source OFFSET.
     */
    fun forZone(zoneId: String, standardOffsetSeconds: Int = 0): Place {
        val row = byId[zoneId] ?: byLowerId[zoneId.lowercase(Locale.ROOT)]
        if (row != null) return Place(row.latitude, row.longitude, zoneCityName(zoneId), PlaceSource.ZONE, row.countryCode)
        val lon = (standardOffsetSeconds / 240.0).coerceIn(-180.0, 180.0)
        return Place(35.0, lon, zoneCityName(zoneId), PlaceSource.OFFSET)
    }

    /**
     * Cities for the typed [query]: normalised; nothing below two characters; a prefix of the name or of one of
     * its words ranks before a substring; ties by population (large first), then name. The native name counts too.
     */
    fun search(query: String, limit: Int = 8): List<City> {
        val q = normalize(query).trim()
        if (q.length < 2) return emptyList()
        val max = limit.coerceIn(1, 20)
        val hits = ArrayList<Hit>()
        for (i in cities.indices) {
            val a = tier(normName[i], q)
            val b = if (normNative[i].isEmpty()) TIER_NONE else tier(normNative[i], q)
            val t = if (a < b) a else b
            if (t != TIER_NONE) hits.add(Hit(t, cities[i]))
        }
        hits.sortWith(
            compareBy<Hit> { it.tier }
                .thenByDescending { it.city.populationK }
                .thenBy { it.city.name },
        )
        val out = ArrayList<City>(minOf(max, hits.size))
        for (h in hits) {
            if (out.size >= max) break
            out.add(h.city)
        }
        return out
    }

    private class Hit(val tier: Int, val city: City)

    private fun tier(text: String, q: String): Int {
        if (text.startsWith(q)) return TIER_PREFIX
        var at = text.indexOf(q)
        if (at < 0) return TIER_NONE
        while (at >= 0) {
            if (at > 0 && !Character.isLetterOrDigit(text[at - 1])) return TIER_PREFIX
            at = text.indexOf(q, at + 1)
        }
        return TIER_SUBSTRING
    }

    companion object {
        private const val TIER_PREFIX = 0
        private const val TIER_SUBSTRING = 1
        private const val TIER_NONE = 2

        val EMPTY: PlaceTable = PlaceTable(emptyList(), emptyList())

        /**
         * Reads the two asset texts (tab separated, UTF-8): zones `zoneId, CC, lat, lon`, cities
         * `name, nativeName, CC, lat, lon, populationK`. Lines starting with # and bad lines are skipped;
         * latitude -90..90, longitude -180..180. Never throws.
         */
        fun parse(zonesTsv: String, citiesTsv: String): PlaceTable {
            val zones = ArrayList<ZoneRow>()
            val cities = ArrayList<City>()
            try {
                for (raw in zonesTsv.lineSequence()) {
                    val line = raw.trimEnd('\r')
                    if (line.isBlank() || line.startsWith("#")) continue
                    val f = line.split('\t')
                    if (f.size < 4) continue
                    val id = f[0].trim()
                    if (id.isEmpty()) continue
                    val lat = coord(f[2], 90.0) ?: continue
                    val lon = coord(f[3], 180.0) ?: continue
                    zones.add(ZoneRow(id, f[1].trim(), lat, lon))
                }
            } catch (_: Exception) {
            }
            try {
                for (raw in citiesTsv.lineSequence()) {
                    val line = raw.trimEnd('\r')
                    if (line.isBlank() || line.startsWith("#")) continue
                    val f = line.split('\t')
                    if (f.size < 6) continue
                    val name = f[0].trim()
                    if (name.isEmpty()) continue
                    val lat = coord(f[3], 90.0) ?: continue
                    val lon = coord(f[4], 180.0) ?: continue
                    val pop = f[5].trim().toIntOrNull() ?: continue
                    if (pop < 0) continue
                    cities.add(City(name, f[1].trim(), f[2].trim(), lat, lon, pop))
                }
            } catch (_: Exception) {
            }
            return PlaceTable(zones, cities)
        }

        private fun coord(text: String, limit: Double): Double? {
            val v = text.trim().toDoubleOrNull() ?: return null
            if (!v.isFinite() || v < -limit || v > limit) return null
            return v
        }

        /**
         * Search form of a text: NFD, marks dropped, lower case (Locale.ROOT), then dotless i to i, sharp s to ss,
         * o with stroke to o, ae ligature to ae, d with bar to d, l with stroke to l.
         */
        fun normalize(s: String): String {
            val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            val sb = StringBuilder(decomposed.length)
            for (ch in decomposed) {
                if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                sb.append(ch)
            }
            val lower = sb.toString().lowercase(Locale.ROOT)
            val out = StringBuilder(lower.length + 4)
            for (ch in lower) {
                when (ch) {
                    'ı' -> out.append('i')
                    'ß' -> out.append("ss")
                    'ø' -> out.append('o')
                    'æ' -> out.append("ae")
                    'đ' -> out.append('d')
                    'ł' -> out.append('l')
                    else -> out.append(ch)
                }
            }
            return out.toString()
        }
    }
}

/** The place in force: [pinned] (a test), else the picked city (source CITY), else the guess from the zone. */
fun resolvePlace(choice: PlaceChoice, pinned: Place?, zoneId: String, standardOffsetSeconds: Int, table: PlaceTable): Place {
    if (pinned != null) return pinned
    if (choice is PlaceChoice.Picked) {
        val c = choice.city
        return Place(c.latitude, c.longitude, c.name, PlaceSource.CITY, c.countryCode)
    }
    return table.forZone(zoneId, standardOffsetSeconds)
}

/** [value] rounded half up (away from zero) to [decimals]; never negative zero. */
fun roundCoord(value: Double, decimals: Int = 1): Double {
    if (!value.isFinite()) return 0.0
    val scaled = BigDecimal(value.toString()).setScale(decimals.coerceIn(0, 8), RoundingMode.HALF_UP)
    val d = scaled.toDouble()
    return if (d == 0.0) 0.0 else d
}

/** [value] rounded like [roundCoord] and written with a dot whatever the phone's language is; "-0.0" is "0.0". */
fun formatCoord(value: Double, decimals: Int = 1): String {
    val places = decimals.coerceIn(0, 8)
    if (!value.isFinite()) return BigDecimal.ZERO.setScale(places).toPlainString()
    val scaled = BigDecimal(value.toString()).setScale(places, RoundingMode.HALF_UP)
    val text = scaled.toPlainString()
    return if (scaled.signum() == 0) BigDecimal.ZERO.setScale(places).toPlainString() else text
}

/** "America/Port_of_Spain" gives "Port of Spain"; an id without a slash is returned as it is. */
fun zoneCityName(zoneId: String): String {
    val slash = zoneId.lastIndexOf('/')
    if (slash < 0) return zoneId
    val tail = zoneId.substring(slash + 1)
    if (tail.isEmpty()) return zoneId
    return tail.replace('_', ' ')
}
