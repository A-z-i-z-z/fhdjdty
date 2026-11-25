package dev.pocketagent.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.Locale

class PlacesTest {
    // IANA zone.tab values (ISO 6709), converted by hand and checked with a script, written with two decimals
    // as the shipped asset has them: Oslo +5955+01045, Istanbul +4101+02858, New York +404251-0740023,
    // Sydney -3352+15113, Kolkata +2232+08822.
    private val zonesTsv = """
        # source test
        Europe/Oslo	NO	59.92	10.75
        Europe/Istanbul	TR	41.02	28.97
        America/New_York	US	40.71	-74.01
        Australia/Sydney	AU	-33.87	151.22
        Asia/Kolkata	IN	22.53	88.37
        Asia/Calcutta	IN	22.53	88.37
    """.trimIndent()

    private val citiesTsv = """
        # source test
        Istanbul	İstanbul	TR	41.01	28.96	15000
        Ankara		TR	39.93	32.86	5000
        Diyarbakir	Diyarbakır	TR	37.91	40.24	1700
        Zurich	Zürich	CH	47.37	8.54	430
        Lodz	Łódź	PL	51.76	19.46	670
        Reykjavik	Reykjavík	IS	64.15	-21.94	135
        Strasbourg		FR	48.58	7.75	290
        New York		US	40.71	-74.01	8800
        York		GB	53.96	-1.08	200
        Yorkton		CA	51.21	-102.46	16
        Port of Spain		TT	10.66	-61.51	37
        Lisbon		PT	38.72	-9.14	545
        Tonsberg		NO	59.27	10.41	1
    """.trimIndent()

    private fun table() = PlaceTable.parse(zonesTsv, citiesTsv)

    private fun assetDir(): File? =
        listOf("src/main/assets/places", "app/src/main/assets/places").map { File(it) }.firstOrNull { it.isDirectory }

    private fun near(expected: Double, actual: Double) = assertEquals(expected, actual, 0.05)

    @Test fun zoneLookupMatchesZoneTab() {
        val t = table()
        data class Ref(val id: String, val lat: Double, val lon: Double)
        val refs = listOf(
            Ref("Europe/Oslo", 59.917, 10.750), Ref("Europe/Istanbul", 41.017, 28.967),
            Ref("America/New_York", 40.714, -74.006), Ref("Australia/Sydney", -33.867, 151.217),
            Ref("Asia/Kolkata", 22.533, 88.367),
        )
        for (r in refs) {
            val p = t.forZone(r.id, 0)
            assertEquals(r.id, PlaceSource.ZONE, p.source)
            near(r.lat, p.latitude)
            near(r.lon, p.longitude)
        }
        val asset = assetDir()
        val z = if (asset == null) null else File(asset, "zones.tsv").takeIf { it.isFile }
        Assume.assumeTrue("the shipped zone table is not there yet", z != null)
        val big = PlaceTable.parse(z!!.readText(Charsets.UTF_8), "")
        for (r in refs) {
            val p = big.forZone(r.id, 0)
            assertEquals(r.id, PlaceSource.ZONE, p.source)
            near(r.lat, p.latitude)
            near(r.lon, p.longitude)
        }
    }

    @Test fun zoneRowCarriesCountryAndName() {
        val p = table().forZone("America/New_York", -18000)
        assertEquals("New York", p.name)
        assertEquals("US", p.countryCode)
        assertEquals(PlaceSource.ZONE, p.source)
    }

    @Test fun aliasResolves() {
        val t = table()
        val a = t.forZone("Asia/Calcutta", 19800)
        val b = t.forZone("Asia/Kolkata", 19800)
        assertEquals(PlaceSource.ZONE, a.source)
        assertEquals(b.latitude, a.latitude, 0.0)
        assertEquals(b.longitude, a.longitude, 0.0)
        assertEquals("Calcutta", a.name)
    }

    @Test fun unknownZoneFallsBackToOffset() {
        val p = table().forZone("Nowhere/Land_Of_Test", 3600)
        assertEquals(PlaceSource.OFFSET, p.source)
        assertEquals(3600 / 240.0, p.longitude, 1e-9)
        assertEquals(15.0, p.longitude, 1e-9)
        assertEquals(35.0, p.latitude, 0.0)
        assertEquals("Land Of Test", p.name)
        assertEquals("", p.countryCode)
        val west = table().forZone("X/Y", -18000)
        assertEquals(-75.0, west.longitude, 1e-9)
        val far = table().forZone("X/Z", 14 * 3600)
        assertTrue(far.longitude <= 180.0)
    }

    @Test fun emptyTableStillGivesAPlace() {
        val p = PlaceTable.EMPTY.forZone("Europe/Oslo", 3600)
        assertEquals(PlaceSource.OFFSET, p.source)
        assertEquals(15.0, p.longitude, 1e-9)
        assertEquals(35.0, p.latitude, 0.0)
        assertEquals("Oslo", p.name)
        assertTrue(PlaceTable.EMPTY.search("oslo").isEmpty())
        val u = PlaceTable.EMPTY.forZone("UTC", 0)
        assertEquals("UTC", u.name)
        assertEquals(0.0, u.longitude, 0.0)
    }

    @Test fun normalizeTable() {
        assertEquals("istanbul", PlaceTable.normalize("İstanbul"))
        assertEquals("diyarbakir", PlaceTable.normalize("Diyarbakır"))
        assertEquals("zurich", PlaceTable.normalize("Zürich"))
        assertEquals("lodz", PlaceTable.normalize("Łódź"))
        assertEquals("reykjavik", PlaceTable.normalize("Reykjavík"))
        assertEquals("strasse", PlaceTable.normalize("Straße"))
        assertEquals("kobenhavn", PlaceTable.normalize("København"))
        assertEquals("aeroskobing", PlaceTable.normalize("Ærøskøbing"))
        assertEquals("dakovo", PlaceTable.normalize("Đakovo"))
        assertEquals("", PlaceTable.normalize(""))
    }

    @Test fun normalizeIgnoresTheDefaultLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("istanbul", PlaceTable.normalize("ISTANBUL"))
            assertEquals("diyarbakir", PlaceTable.normalize("DIYARBAKIR"))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test fun searchRanking() {
        val t = table()
        // "york": New York has it as a word prefix, York and Yorkton as name prefixes: one tier, population decides
        assertEquals(listOf("New York", "York", "Yorkton"), t.search("york").map { it.name })
        // "ton": Tonsberg starts with it (population 1), Yorkton only contains it (population 16): prefix first
        assertEquals(listOf("Tonsberg", "Yorkton"), t.search("ton").map { it.name })
        // a word prefix counts as a prefix
        assertEquals(listOf("Port of Spain"), t.search("spain").map { it.name })
        // a plain substring is found
        assertEquals(listOf("Lisbon"), t.search("bon").map { it.name })
        assertTrue(t.search("qqqq").isEmpty())
        // limit honoured and coerced to 1..20
        assertEquals(2, t.search("or", 2).size)
        assertEquals(4, t.search("or", 99).size)
        assertEquals(1, t.search("york", 0).size)
        assertEquals(1, t.search("york", -5).size)
    }

    @Test fun searchTiesBreakByPopulationThenName() {
        val tsv = "# x\nBeta\t\tXX\t1\t1\t100\nBear\t\tXX\t1\t1\t100\nBig\t\tXX\t1\t1\t900\nBe\t\tXX\t1\t1\t5\n"
        val t = PlaceTable.parse("", tsv)
        assertEquals(listOf("Bear", "Beta", "Be"), t.search("be").map { it.name })
        assertEquals(listOf("Big"), t.search("bi").map { it.name })
    }

    @Test fun searchBelowTwoCharsIsEmpty() {
        val t = table()
        assertTrue(t.search("").isEmpty())
        assertTrue(t.search("i").isEmpty())
        assertTrue(t.search(" i ").isEmpty())
        assertFalse(t.search("is").isEmpty())
    }

    @Test fun searchFindsNativeName() {
        val t = table()
        assertEquals("Istanbul", t.search("İstanbul").first().name)
        assertEquals("Istanbul", t.search("ist").first().name)
        assertEquals("Diyarbakir", t.search("diyarbakır").first().name)
        assertEquals("Lodz", t.search("łód").first().name)
        assertEquals("Zurich", t.search("zürich").first().name)
        assertEquals("Reykjavik", t.search("REYKJAVÍK").first().name)
    }

    @Test fun parseSkipsBadLines() {
        val zones = "# c\n\nA/B\tXX\t10\t20\nshort\tXX\t1\nC/D\tXX\tnorth\t20\nE/F\tXX\t91\t20\nG/H\tXX\t10\t181\nI/J\tXX\t-90\t-180\n\tXX\t1\t1\n"
        val cities = "# c\n\nOk\t\tXX\t10\t20\t5\nshort\tXX\t1\t1\nNo\t\tXX\tabc\t20\t5\nBad\t\tXX\t95\t20\t5\nNopop\t\tXX\t1\t1\tmany\nNeg\t\tXX\t1\t1\t-3\n\t\tXX\t1\t1\t5\nEdge\tEdgy\tYY\t90\t180\t0\r\n"
        val t = PlaceTable.parse(zones, cities)
        assertEquals(listOf("A/B", "I/J"), t.zones.map { it.zoneId })
        assertEquals(listOf("Ok", "Edge"), t.cities.map { it.name })
        assertEquals("Edgy", t.cities[1].nativeName)
        assertEquals(0, t.cities[1].populationK)
    }

    @Test fun parseNeverThrows() {
        val t = PlaceTable.parse("\u0000\t\t\n\t\t\t\n", "\t\t\t\t\t\n###\n")
        assertTrue(t.zones.isEmpty())
        assertTrue(t.cities.isEmpty())
    }

    @Test fun resolveOrder() {
        val t = table()
        val city = City("Ankara", "", "TR", 39.93, 32.86, 5000)
        val pinned = Place(1.0, 2.0, "Pinned", PlaceSource.PINNED)
        assertEquals(pinned, resolvePlace(PlaceChoice.Picked(city), pinned, "Europe/Oslo", 3600, t))
        assertEquals(pinned, resolvePlace(PlaceChoice.Auto, pinned, "Europe/Oslo", 3600, t))
        val picked = resolvePlace(PlaceChoice.Picked(city), null, "Europe/Oslo", 3600, t)
        assertEquals(PlaceSource.CITY, picked.source)
        assertEquals("Ankara", picked.name)
        assertEquals(39.93, picked.latitude, 0.0)
        assertEquals("TR", picked.countryCode)
        val auto = resolvePlace(PlaceChoice.Auto, null, "Europe/Oslo", 3600, t)
        assertEquals(PlaceSource.ZONE, auto.source)
        assertEquals("Oslo", auto.name)
    }

    @Test fun southernFlag() {
        assertTrue(Place(-33.9, 151.2, "Sydney", PlaceSource.ZONE).isSouthern)
        assertFalse(Place(59.9, 10.7, "Oslo", PlaceSource.ZONE).isSouthern)
        assertFalse(Place(0.0, 10.7, "Equator", PlaceSource.ZONE).isSouthern)
    }

    @Test fun placeKey() {
        assertEquals("59.9,10.7", Place(59.9496, 10.7461, "Oslo", PlaceSource.ZONE).key())
        assertEquals("-33.9,151.2", Place(-33.87, 151.22, "Sydney", PlaceSource.ZONE).key())
        assertEquals("0.0,0.0", Place(-0.04, -0.02, "Null", PlaceSource.PINNED).key())
    }

    @Test fun assetsParse() {
        val dir = assetDir()
        val z = if (dir == null) null else File(dir, "zones.tsv").takeIf { it.isFile }
        val c = if (dir == null) null else File(dir, "cities.tsv").takeIf { it.isFile }
        Assume.assumeTrue("the shipped place tables are not there yet", z != null && c != null)
        val zt = z!!.readText(Charsets.UTF_8)
        val ct = c!!.readText(Charsets.UTF_8)
        val t = PlaceTable.parse(zt, ct)
        assertTrue("zones: ${t.zones.size}", t.zones.size >= 400)
        assertTrue("cities: ${t.cities.size}", t.cities.size in 800..1500)
        assertEquals("duplicate zone id", t.zones.size, t.zones.map { it.zoneId }.toSet().size)
        for (r in t.zones) {
            assertTrue(r.zoneId, r.latitude in -90.0..90.0 && r.longitude in -180.0..180.0)
        }
        for (r in t.cities) {
            assertTrue(r.name, r.latitude in -90.0..90.0 && r.longitude in -180.0..180.0)
        }
        assertEquals("every line must be read", zt.lines().count { it.isNotBlank() && !it.startsWith("#") }, t.zones.size)
        assertNotNull(t.forZone("Europe/Oslo", 0))
    }

    @Test fun roundCoordHalfUp() {
        assertEquals(59.9, roundCoord(59.9496), 0.0)
        assertEquals(10.8, roundCoord(10.75), 0.0)
        assertEquals(-10.8, roundCoord(-10.75), 0.0)
        assertEquals(0.0, roundCoord(-0.04), 0.0)
        assertEquals(1.0 / 0.0, 1.0 / roundCoord(-0.04), 0.0) // positive zero, not negative zero
        assertEquals(59.95, roundCoord(59.9496, 2), 0.0)
        assertEquals(0.0, roundCoord(Double.NaN), 0.0)
    }

    @Test fun formatCoordIgnoresLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("10.5", formatCoord(10.5))
            assertEquals("-10.8", formatCoord(-10.75))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("59.9", formatCoord(59.9496))
            assertEquals("1234.6", formatCoord(1234.55))
        } finally {
            Locale.setDefault(old)
        }
        assertEquals("0.0", formatCoord(-0.04))
        assertEquals("0.0", formatCoord(-0.0))
        assertEquals("12.35", formatCoord(12.345, 2))
        assertEquals("7", formatCoord(7.4, 0))
        assertEquals("0.0", formatCoord(Double.POSITIVE_INFINITY))
    }

    @Test fun zoneCityNameCases() {
        assertEquals("Port of Spain", zoneCityName("America/Port_of_Spain"))
        assertEquals("Buenos Aires", zoneCityName("America/Argentina/Buenos_Aires"))
        assertEquals("UTC", zoneCityName("UTC"))
        assertEquals("Etc/", zoneCityName("Etc/"))
        assertEquals("", zoneCityName(""))
    }
}
