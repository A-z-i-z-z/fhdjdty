package dev.pocketagent.app.ui.pixel.stage

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Small helpers shared by the sky tests: instants, a place, an input builder, the six theme accents. */
internal object SkyTestKit {
    const val OSLO_LAT = 59.9139
    const val OSLO_LON = 10.7522
    const val ISTANBUL_LAT = 41.0082
    const val ISTANBUL_LON = 28.9784
    const val SYDNEY_LAT = -33.8688
    const val SYDNEY_LON = 151.2093
    const val LONDON_LAT = 51.5074
    const val LONDON_LON = -0.1278
    const val TROMSO_LAT = 69.65
    const val TROMSO_LON = 18.96

    val UTC: ZoneId = ZoneOffset.UTC

    /** The accents of the six themes in their dark variants, then in their light variants (opaque ARGB). */
    val ACCENTS: IntArray = intArrayOf(
        0xFFF28A2E.toInt(), 0xFFF2B01E.toInt(), 0xFFF26A21.toInt(), 0xFF7CC85A.toInt(), 0xFFFF6A1A.toInt(), 0xFFE2503C.toInt(),
        0xFFE5701F.toInt(), 0xFFE9A10F.toInt(), 0xFFEE6A1F.toInt(), 0xFF5BAA3C.toInt(), 0xFFE85A10.toInt(), 0xFFC8321F.toInt(),
    )

    fun utc(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun inputs(
        epochMs: Long, lat: Double, lon: Double, mode: SkyMode = SkyMode.FOLLOW, zone: ZoneId = UTC,
        lists: Set<String> = setOf("sky", "app"), accent: Int = ACCENTS[0], blackNight: Boolean = false,
        night: ScenePalette = TestPalettes.DARK, userDays: List<UserDay> = emptyList(), firstLaunchEpochDay: Long? = null,
        seasons: SeasonSetting = SeasonSetting.AUTO,
    ): SkyInputs = SkyInputs(
        epochMs = epochMs, latDeg = lat, lonDeg = lon, zone = zone, mode = mode, night = night, accent = accent,
        variant = "NEWROZ", blackNight = blackNight, lists = lists, userDays = userDays,
        firstLaunchEpochDay = firstLaunchEpochDay, seasons = seasons,
    )

    fun frame(
        epochMs: Long, lat: Double, lon: Double, mode: SkyMode = SkyMode.FOLLOW, zone: ZoneId = UTC,
        lists: Set<String> = setOf("sky", "app"), accent: Int = ACCENTS[0], blackNight: Boolean = false,
        night: ScenePalette = TestPalettes.DARK, userDays: List<UserDay> = emptyList(), firstLaunchEpochDay: Long? = null,
        seasons: SeasonSetting = SeasonSetting.AUTO,
    ): SkyFrame = SkyFrames.build(
        inputs(epochMs, lat, lon, mode, zone, lists, accent, blackNight, night, userDays, firstLaunchEpochDay, seasons),
    )!!
}
