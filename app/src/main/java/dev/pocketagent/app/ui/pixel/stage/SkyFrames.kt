package dev.pocketagent.app.ui.pixel.stage

import java.time.Instant
import kotlin.math.floor

/**
 * Builds the sky frame: the real sun, moon and planets for an instant and a place, the colours of the moment, the
 * season and the special days. A pure function of its inputs (time, place, zone, palette, settings), so two builds
 * with equal inputs are equal field by field.
 */
object SkyFrames {
    /** The sun's altitude in the fixed day mode. */
    private const val DAY_MODE_SUN_ALT = 52.0

    private fun smoothstep(e0: Double, e1: Double, x: Double): Double {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun wrap180(x: Double): Double = x - 360.0 * floor((x + 180.0) / 360.0)

    private fun mix(a: IntArray, b: IntArray, e: Float): IntArray = IntArray(a.size) { Argb.lerp(a[it], b[it], e) }

    /** Null for [SkyMode.NIGHT] (the classic picture needs no frame). */
    fun build(i: SkyInputs): SkyFrame? {
        if (i.mode == SkyMode.NIGHT) return null
        val date = Instant.ofEpochMilli(i.epochMs).atZone(i.zone).toLocalDate()
        val facing = SkyProjection.facingAz(i.latDeg)
        val days = SceneCalendar.active(date, i.zone, i.lists, i.userDays, i.firstLaunchEpochDay)
        val season = Seasons.seasonFor(date, i.latDeg, i.seasons)
        val sunGold = days.strength.containsKey(DayEffect.SUN_GOLD)
        val warm = if (sunGold) SkyPalette.SUN_GOLD_WARM else 0f
        val meteorPeriod = if (days.strength.containsKey(DayEffect.METEORS)) 3500 else 14000
        val fires = days.strength[DayEffect.RIDGE_FIRES]
        val ridgeFires = if (fires == null) 0 else if (fires >= 0.75f) 2 else 1
        val lst = SkyClock.lstDeg(i.epochMs, i.lonDeg)

        if (i.mode == SkyMode.DAY) {
            val blend = SkyMoments.at(DAY_MODE_SUN_ALT, false)
            val c = SkyPalette.colors(i.night, blend, i.accent, i.blackNight, warm)
            val lift = SkyPalette.sixteenths(c.lift)
            return SkyFrame(
                mode = SkyMode.DAY, epochMs = i.epochMs, latDeg = i.latDeg, lonDeg = i.lonDeg, lstDeg = lst,
                sun = BodyPos(DAY_MODE_SUN_ALT.toFloat(), facing), moon = BodyPos(-90f, 0f),
                moonPhase16 = 0, moonWaxing = false, moonIllum = 0f, moonRed = 0f, moonLight = 0f,
                evening = false, a = blend.a, b = blend.b, blend = blend.t, moment = SkyMoment.DAY,
                daylight = 1f, depressionDeg = 0f,
                skyTop = c.skyTop, skyBottom = c.skyBottom, farRamp = c.farRamp, lift = lift,
                sunColor = SkyPalette.sunColor(DAY_MODE_SUN_ALT),
                season = season, facingAz = facing, meteorPeriodMs = meteorPeriod, ridgeFires = ridgeFires,
                fireworks = days.strength.containsKey(DayEffect.FIREWORKS), moonGlow = false, sunGold = sunGold,
                petHat = days.strength.containsKey(DayEffect.PAPER_HAT),
                iconsDark = SkyPalette.luma(c.skyTop) >= SkyPalette.ICONS_DARK_LUMA,
                dayIds = days.ids, planets = emptyList(),
            )
        }

        val sun = SkyClock.sun(i.epochMs)
        val sunAa = SkyClock.altAz(sun.raDeg, sun.decDeg, lst, i.latDeg)
        val moonP = SkyClock.moon(i.epochMs)
        val moonAa = SkyClock.altAz(moonP.raDeg, moonP.decDeg, lst, i.latDeg)
        val elong = ((moonP.lambdaDeg - sun.lambdaDeg) % 360.0 + 360.0) % 360.0

        // The sun's hour angle decides between a morning and an evening ladder. Both are built and mixed by the
        // hour angle, so the colours never jump at the meridian (high latitudes, polar seasons).
        val h = wrap180(lst - sun.raDeg)
        val eDouble: Double
        if (Math.abs(h) <= 165.0) {
            eDouble = smoothstep(-15.0, 15.0, h)
        } else {
            val h2 = if (h > 0.0) h else h + 360.0
            eDouble = 1.0 - smoothstep(165.0, 195.0, h2)
        }
        val e = eDouble.toFloat()
        val mornBlend = SkyMoments.at(sunAa.altDeg, false)
        val eveBlend = SkyMoments.at(sunAa.altDeg, true)
        val cm = SkyPalette.colors(i.night, mornBlend, i.accent, i.blackNight, warm)
        val ce = SkyPalette.colors(i.night, eveBlend, i.accent, i.blackNight, warm)
        val top = Argb.lerp(cm.skyTop, ce.skyTop, e)
        val bottom = Argb.lerp(cm.skyBottom, ce.skyBottom, e)
        val ramp = mix(cm.farRamp, ce.farRamp, e)
        val lift = SkyPalette.sixteenths(cm.lift + (ce.lift - cm.lift) * e)
        val evening = e >= 0.5f
        val dom = if (evening) eveBlend else mornBlend
        val moment = if (dom.t < 0.5f) dom.a else dom.b

        val daylight = smoothstep(-12.0, 6.0, sunAa.altDeg).toFloat()
        val depression = Math.max(0.0, -sunAa.altDeg).toFloat()
        val moonIllum = SkyClock.moonIllumination(elong)
        val moonAlt = moonAa.altDeg.toFloat()
        // Gated on the list, not on the eclipse's calendar day: the red window (plus or minus 40 or 60 minutes) can cross
        // local midnight, and a day rule would switch the red moon on or off in one frame there.
        val moonRed = if ("sky" in i.lists) Eclipses.redAt(i.epochMs) else 0f
        val moonLight = (moonIllum * smoothstep(0.0, 15.0, moonAa.altDeg).toFloat() * (1f - daylight)).coerceIn(0f, 1f)

        return SkyFrame(
            mode = SkyMode.FOLLOW, epochMs = i.epochMs, latDeg = i.latDeg, lonDeg = i.lonDeg, lstDeg = lst,
            sun = BodyPos(sunAa.altDeg.toFloat(), sunAa.azDeg.toFloat()), moon = BodyPos(moonAlt, moonAa.azDeg.toFloat()),
            moonPhase16 = SkyClock.moonPhase16(elong), moonWaxing = elong < 180.0, moonIllum = moonIllum,
            moonRed = moonRed, moonLight = moonLight,
            evening = evening, a = dom.a, b = dom.b, blend = dom.t, moment = moment,
            daylight = daylight, depressionDeg = depression,
            skyTop = top, skyBottom = bottom, farRamp = ramp, lift = lift, sunColor = SkyPalette.sunColor(sunAa.altDeg),
            season = season, facingAz = facing, meteorPeriodMs = meteorPeriod, ridgeFires = ridgeFires,
            fireworks = days.strength.containsKey(DayEffect.FIREWORKS), moonGlow = days.strength.containsKey(DayEffect.MOON_GLOW),
            sunGold = sunGold, petHat = days.strength.containsKey(DayEffect.PAPER_HAT),
            iconsDark = SkyPalette.luma(top) >= SkyPalette.ICONS_DARK_LUMA,
            dayIds = days.ids, planets = Planets.positions(i.epochMs, i.latDeg, i.lonDeg),
        )
    }
}
