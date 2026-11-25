package dev.pocketagent.app

import android.content.Intent
import dev.pocketagent.app.ui.Route
import dev.pocketagent.app.ui.decodeRoute
import dev.pocketagent.app.ui.nav.NavFlags
import dev.pocketagent.app.ui.nav.parseNavFlags
import dev.pocketagent.app.util.AppLog
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.ZoneId

/** One extra of a debug link: left alone, cleared back to the normal value, or set. */
sealed interface Setting<out T> {
    data object Keep : Setting<Nothing>
    data object Clear : Setting<Nothing>
    data class Set<T>(val value: T) : Setting<T>
}

/**
 * What a debug link asks for. [rejected] lists the names of the extras that could not be read (they are ignored and
 * logged). [seed] and [nav] are the raw texts of those extras (already known to be acceptable when they are not null).
 */
data class DebugRequest(
    val reset: Boolean,
    val route: Route?,
    val clock: Setting<Long>,
    val language: Setting<String>,
    val rtl: Setting<Boolean>,
    val seed: String?,
    val nav: String?,
    val rejected: List<String>,
)

/**
 * The debug link of a build with test hooks: one intent action ([AppLinks.ACTION_DEBUG]) addressed to MainActivity by
 * name (there is no manifest filter for it) that the phone robot uses to open a screen, pin the clock, force a
 * language or a layout direction, switch navigation flags or seed test data:
 *
 * adb shell am start -a dev.pocketagent.app.DEBUG -n PACKAGE/dev.pocketagent.app.MainActivity
 * --es route chat:abc --es clock 2027-03-21T20:30 --es lang tr --es rtl 1 --es reset 1 --es seed chats:120 --es nav back=0,shared=0
 *
 * All extras are strings and all are optional (a missing one means: keep), except [EXTRA_TOKEN], which is required.
 * In a build without test hooks [parse] returns null whatever the extras, so the intent is ignored.
 *
 * THE TOKEN (who may send a link). MainActivity is exported (share target), so any installed app could address it
 * with this action. The link is therefore inert unless it carries `--es token <T>`, where T is the content of the
 * file `files/debug-link.token` in the app's private directory. [ensureToken] creates that file (32 hex characters
 * from SecureRandom) at the first start of a build with test hooks and never changes it afterwards (it is removed only
 * by `pm clear` or an uninstall). Another app cannot read the file; the shell can, on a debuggable build, through
 * run-as. The protocol for the phone robot, for every link:
 *
 * 1. T = `adb shell run-as PACKAGE cat files/debug-link.token`, trimmed (PACKAGE is the package under test: the owner's
 *    debug build or the twin).
 * 2. If the file is missing (a fresh install or after `pm clear`), start the app once with its normal launcher intent
 *    (`adb shell am start -n PACKAGE/dev.pocketagent.app.MainActivity`), wait until it runs and read the file again.
 * 3. Add `--es token T` to the command line above. A link without the extra, or with a wrong value, is ignored (one
 *    warning line in app.log: "debug link refused") and changes nothing.
 */
object DebugLinks {
    const val EXTRA_TOKEN = "token"
    const val TOKEN_FILE = "debug-link.token"
    private const val TOKEN_CHARS = 32
    private const val TOKEN_MIN = 16

    const val EXTRA_ROUTE = "route"
    const val EXTRA_CLOCK = "clock"
    const val EXTRA_LANG = "lang"
    const val EXTRA_RTL = "rtl"
    const val EXTRA_RESET = "reset"
    const val EXTRA_SEED = "seed"
    const val EXTRA_NAV = "nav"

    private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,8}([-_][A-Za-z0-9]{1,8})*$")

    /**
     * Makes the token file when it is missing or unreadable (a build with test hooks only; never throws). Called once from
     * PocketApp.onCreate. The token is never rotated, so a link sent while the app starts cold still matches.
     */
    fun ensureToken(filesDir: File, debug: Boolean = BuildConfig.TEST_HOOKS) {
        if (!debug) return
        try {
            val file = File(filesDir, TOKEN_FILE)
            if (readToken(file) != null) return
            val bytes = ByteArray(TOKEN_CHARS / 2)
            SecureRandom().nextBytes(bytes)
            val hex = "0123456789abcdef"
            file.writeText(bytes.joinToString("") { "${hex[(it.toInt() shr 4) and 0xf]}${hex[it.toInt() and 0xf]}" } + "\n")
        } catch (e: Exception) {
            AppLog.w("Debug", "debug link token could not be written", e)
        }
    }

    /** The token in [file] (trimmed), or null when the file is missing, unreadable or shorter than a token made here. */
    internal fun readToken(file: File): String? =
        try { file.readText().trim().takeIf { it.length >= TOKEN_MIN } } catch (_: Exception) { null }

    /** True when [given] is exactly [expected] (both trimmed, compared in constant time); false when either is missing. */
    internal fun tokenAccepted(given: String?, expected: String?): Boolean {
        val g = given?.trim() ?: return false
        val e = expected?.trim() ?: return false
        if (e.length < TOKEN_MIN) return false
        return MessageDigest.isEqual(g.toByteArray(Charsets.UTF_8), e.toByteArray(Charsets.UTF_8))
    }

    /**
     * The request an incoming intent makes, or null (a release build, another action, an intent replayed from Recents, a
     * missing or wrong token). [tokenFile] is `File(filesDir, TOKEN_FILE)`; without it nothing is accepted.
     */
    fun parse(
        intent: Intent?,
        tokenFile: File? = null,
        debug: Boolean = BuildConfig.TEST_HOOKS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DebugRequest? {
        if (intent == null || !debug) return null
        if (intent.action != AppLinks.ACTION_DEBUG) return null
        if (!tokenAccepted(intent.getStringExtra(EXTRA_TOKEN), tokenFile?.let { readToken(it) })) {
            if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
                AppLog.w("Debug", "debug link refused: the token is missing or wrong")
            }
            return null
        }
        val extras = HashMap<String, String?>()
        for (name in listOf(EXTRA_ROUTE, EXTRA_CLOCK, EXTRA_LANG, EXTRA_RTL, EXTRA_RESET, EXTRA_SEED, EXTRA_NAV)) {
            extras[name] = intent.getStringExtra(name)
        }
        return parseParts(intent.action, extras, intent.flags, debug, zone)
    }

    /** The pure part of [parse]: no Android objects besides constants, so it runs in a JVM unit test. */
    internal fun parseParts(
        action: String?,
        extras: Map<String, String?>,
        flags: Int,
        debug: Boolean,
        zone: ZoneId,
        isTwin: Boolean = BuildConfig.IS_TWIN,
    ): DebugRequest? {
        if (!debug) return null
        if (action != AppLinks.ACTION_DEBUG) return null
        if ((flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null

        val rejected = ArrayList<String>()
        fun text(name: String): String? = extras[name]?.trim()?.takeIf { it.isNotEmpty() }

        var reset = false
        val resetText = text(EXTRA_RESET)
        if (resetText != null) {
            when (resetText.lowercase()) {
                "1", "true", "on" -> reset = true
                "0", "false", "off" -> reset = false
                else -> rejected.add(EXTRA_RESET)
            }
        }

        var route: Route? = null
        val routeText = text(EXTRA_ROUTE)
        if (routeText != null) {
            route = decodeRoute(routeText)
            if (route == null) rejected.add(EXTRA_ROUTE)
        }

        var clock: Setting<Long> = Setting.Keep
        val clockText = text(EXTRA_CLOCK)
        if (clockText != null) {
            if (clockText.equals("off", ignoreCase = true)) {
                clock = Setting.Clear
            } else {
                val ms = AppClock.parse(clockText, zone)
                if (ms == null) rejected.add(EXTRA_CLOCK) else clock = Setting.Set(ms)
            }
        }

        var language: Setting<String> = Setting.Keep
        val langText = text(EXTRA_LANG)
        if (langText == null && extras[EXTRA_LANG] != null) {
            // Present but blank: a mistake in the command line, not a request to keep.
            rejected.add(EXTRA_LANG)
        } else if (langText != null) {
            if (langText.equals("system", ignoreCase = true)) {
                language = Setting.Clear
            } else if (LANGUAGE_TAG.matches(langText)) {
                language = Setting.Set(langText.replace('_', '-'))
            } else {
                rejected.add(EXTRA_LANG)
            }
        }

        var rtl: Setting<Boolean> = Setting.Keep
        val rtlText = text(EXTRA_RTL)
        if (rtlText != null) {
            when (rtlText.lowercase()) {
                "1", "true", "on" -> rtl = Setting.Set(true)
                "0", "false", "off" -> rtl = Setting.Set(false)
                "auto" -> rtl = Setting.Clear
                else -> rejected.add(EXTRA_RTL)
            }
        }

        // Seeding writes test data in bulk: only the twin may do it.
        var seed: String? = null
        val seedText = text(EXTRA_SEED)
        if (seedText != null) {
            if (isTwin) seed = seedText else rejected.add(EXTRA_SEED)
        }

        var nav: String? = null
        val navText = text(EXTRA_NAV)
        if (navText != null) {
            if (parseNavFlags(navText) != null) nav = navText else rejected.add(EXTRA_NAV)
        }

        return DebugRequest(reset, route, clock, language, rtl, seed, nav, rejected)
    }

    /**
     * Carries out [req] in this order: reset, clock, language, direction, navigation flags, seed. Returns the route to
     * open (the caller opens it with [stackFor] once the chats are loaded), or null when the screen stays. Blocking:
     * the seed may write many files, so call it off the main thread.
     */
    fun apply(app: PocketApp, req: DebugRequest): Route? {
        if (!BuildConfig.TEST_HOOKS) return null
        for (name in req.rejected) AppLog.w("Debug", "debug link: cannot use the extra \"$name\"")
        if (req.reset) {
            AppClock.unpin()
            DebugOverrides.clear()
            NavFlags.reset()
        }
        when (val c = req.clock) {
            Setting.Keep -> {}
            Setting.Clear -> AppClock.unpin()
            is Setting.Set -> AppClock.pin(c.value)
        }
        when (val l = req.language) {
            Setting.Keep -> {}
            Setting.Clear -> DebugOverrides.setLanguage(null)
            is Setting.Set -> DebugOverrides.setLanguage(l.value)
        }
        when (val r = req.rtl) {
            Setting.Keep -> {}
            Setting.Clear -> DebugOverrides.setForceRtl(null)
            is Setting.Set -> DebugOverrides.setForceRtl(r.value)
        }
        req.nav?.let { text -> parseNavFlags(text)?.let { NavFlags.apply(it) } }
        req.seed?.let { text -> runSeed(app, text) }
        return req.route
    }

    private fun runSeed(app: PocketApp, text: String) {
        if (!DebugSeeder.isTwinPackage(app)) {
            AppLog.w("Debug", "debug link: seed ignored, only the twin build seeds test data")
            return
        }
        val spec = DebugSeeder.parse(text)
        if (spec == null) {
            AppLog.w("Debug", "debug link: cannot read the seed \"$text\"")
            return
        }
        try {
            AppLog.i("Seed", DebugSeeder.run(app, spec))
        } catch (e: Exception) {
            AppLog.w("Debug", "debug link: seeding failed", e)
        }
    }

    /** The stack a route is opened with: a tab root alone, a detail screen above the tab it belongs to. */
    fun stackFor(route: Route): List<Route> = when (route) {
        Route.Home, Route.Chats, Route.Bots, Route.Hermes -> listOf(route)
        is Route.Settings -> if (route.section == null) listOf(route) else listOf(Route.Settings(null), route)
        is Route.Chat -> listOf(Route.Chats, route)
        Route.Starred -> listOf(Route.Chats, route)
        is Route.BotEditor -> listOf(Route.Bots, route)
        Route.Terminal -> listOf(Route.Hermes, route)
        Route.Routines -> listOf(Route.Home, route)
        is Route.RoutineEditor -> listOf(Route.Home, Route.Routines, route)
        Route.Gallery -> listOf(Route.Home, route)
    }
}
