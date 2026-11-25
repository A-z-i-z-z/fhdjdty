package dev.pocketagent.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The phone tools run against the owner's real app. The only thing between a wrongly flagged scenario and the owner's data is the guard
 * `Assert-PaTwinTarget` as the first statement of every function that wipes, writes, seeds, kills the guest, installs or runs a free adb
 * call, and `Set-PaSystemScales` as the only place that changes a system setting (Wave 0 contract, 1.3 and 5.2). These tests read the three
 * scripts as text and check exactly that. Comments are ignored; function bodies end at a closing brace in column 0.
 */
class PhoneToolsSafetyTest {

    private val scripts = listOf("tools/phone-lib.ps1", "tools/phone-test.ps1", "tools/phone-perf.ps1")

    /** The twin-only ops and the function that implements each. */
    private val guardedFunctions = listOf(
        "Invoke-PaOpWipe", "Invoke-PaOpPref", "Invoke-PaOpSeed", "Invoke-PaOpKillGuest", "Invoke-PaOpExpectFile",
        "Invoke-PaOpRestore", "Invoke-PaOpInstall", "Invoke-PaOpPick", "Invoke-PaOpAdb",
    )

    private class Line(val file: String, val no: Int, val text: String, val fn: String, val firstStatement: String)

    private fun repoRoot(): File? {
        var d: File? = File("").absoluteFile
        var i = 0
        while (d != null && i < 8) {
            if (File(d, "tools/phone-lib.ps1").isFile) return d
            d = d.parentFile
            i++
        }
        return null
    }

    /** Code lines of a script (comment lines and block comments dropped) with the function each belongs to and that function's first statement. */
    private fun parse(rel: String, text: String): List<Line> {
        val raw = text.lines()
        val fnOf = arrayOfNulls<String>(raw.size)
        val firstOf = arrayOfNulls<String>(raw.size)
        val keep = BooleanArray(raw.size)
        var inBlock = false
        var curName: String? = null
        var curStart = -1
        var curFirst: String? = null
        val header = Regex("^function\\s+([A-Za-z0-9_-]+)")
        for (i in raw.indices) {
            val line = raw[i]
            val t = line.trim()
            if (inBlock) {
                if (t.contains("#>")) inBlock = false
                continue
            }
            if (t.startsWith("<#")) {
                if (!t.contains("#>")) inBlock = true
                continue
            }
            if (t.startsWith("#")) continue
            keep[i] = true
            val m = header.find(line)
            if (m != null) {
                curName = m.groupValues[1]
                curStart = i
                curFirst = null
                // a one-line function ends on its own line
                val opens = line.count { it == '{' }
                val closes = line.count { it == '}' }
                if (opens > 0 && opens == closes) {
                    fnOf[i] = curName
                    firstOf[i] = line.substringAfter('{').trim()
                    curName = null
                    continue
                }
            }
            if (curName != null) {
                fnOf[i] = curName
                if (curFirst == null && i > curStart && t.isNotEmpty() && t != "{") curFirst = t
                firstOf[i] = curFirst
                if (line == "}") {
                    // the first statement is known once the body began; make it visible on the header line too
                    curName = null
                }
            }
        }
        val out = ArrayList<Line>()
        for (i in raw.indices) {
            if (!keep[i]) continue
            // a function's first statement is only known after its first body line: resolve it for lines of the same function
            out.add(Line(rel, i + 1, raw[i], fnOf[i] ?: "<script>", firstOf[i] ?: ""))
        }
        // lines of a function seen before its first statement (the header itself): fill from the function's first known statement
        val firstByFn = HashMap<String, String>()
        for (l in out) if (l.fn != "<script>" && l.firstStatement.isNotEmpty() && l.fn !in firstByFn) firstByFn[l.fn] = l.firstStatement
        return out.map { if (it.fn != "<script>" && it.firstStatement.isEmpty()) Line(it.file, it.no, it.text, it.fn, firstByFn[it.fn] ?: "") else it }
    }

    private fun load(): List<Line> {
        val root = repoRoot()
        Assume.assumeTrue("repo files are not reachable from here", root != null)
        val all = ArrayList<Line>()
        for (rel in scripts) {
            val f = File(root, rel)
            Assume.assumeTrue("$rel is not there", f.isFile)
            all.addAll(parse(rel, f.readText(Charsets.UTF_8)))
        }
        return all
    }

    private fun guarded(l: Line): Boolean = l.firstStatement.startsWith("Assert-PaTwinTarget")

    @Test
    fun everyPmClearSitsInAFunctionThatChecksTheTwinFirst() {
        val hits = load().filter { it.text.contains("pm clear") }
        assertTrue("the wipe op is expected to be the one place", hits.isNotEmpty())
        for (l in hits) assertTrue("${l.file}:${l.no} 'pm clear' in ${l.fn} whose first statement is '${l.firstStatement}'", guarded(l))
    }

    @Test
    fun settingsPutExistsOnlyInSetPaSystemScales() {
        val hits = load().filter { it.text.contains("settings put") }
        assertTrue("Set-PaSystemScales is expected to hold them", hits.isNotEmpty())
        for (l in hits) assertEquals("${l.file}:${l.no} 'settings put' outside Set-PaSystemScales", "Set-PaSystemScales", l.fn)
    }

    @Test
    fun writesThroughRunAsAreGuarded() {
        val writes = Regex("run-as.*(\\brm\\s|\\bcp\\s|cat\\s*>)")
        for (l in load()) {
            if (writes.containsMatchIn(l.text)) assertTrue("${l.file}:${l.no} run-as with rm, cp or cat > in ${l.fn} is not guarded", guarded(l))
        }
    }

    /**
     * Commands sent through the generic helper `Invoke-PaRunAs` never contain the text "run-as" on their own line, so the test above cannot see
     * them. Two checks: a write or kill spelled in the call itself needs the guard, and every caller of the helper is either one of the guarded
     * functions or one of the three read-only readers named here.
     */
    @Test
    fun theRunAsHelperIsOnlyUsedByGuardedFunctionsOrReaders() {
        val writes = Regex("Invoke-PaRunAs[^\\n]*(\\brm\\s|\\bcp\\s|\\bmv\\s|\\bkill\\b|cat\\s*>|>\\s*files/)")
        val readers = setOf("Get-PaAppLog", "Get-PaLatestChat", "Invoke-PaOpExpectChat")
        for (l in load()) {
            if (writes.containsMatchIn(l.text)) assertTrue("${l.file}:${l.no} Invoke-PaRunAs with a write or kill in ${l.fn} is not guarded", guarded(l))
            if (l.text.contains("Invoke-PaRunAs") && !l.text.trimStart().startsWith("function ")) {
                assertTrue("${l.file}:${l.no} Invoke-PaRunAs is called from ${l.fn}, which is neither guarded nor a read-only reader", guarded(l) || l.fn in readers)
            }
        }
    }

    @Test
    fun adbInstallIsGuarded() {
        val install = Regex("(?i)adb[^\\n]*\\binstall\\b")
        for (l in load()) {
            if (install.containsMatchIn(l.text)) assertTrue("${l.file}:${l.no} an adb install in ${l.fn} is not guarded", guarded(l))
        }
    }

    @Test
    fun everyTwinOnlyOpStartsWithTheGuard() {
        val lines = load()
        for (name in guardedFunctions) {
            val body = lines.filter { it.fn == name }
            assertTrue("function $name exists in the scripts", body.isNotEmpty())
            assertTrue("$name must start with Assert-PaTwinTarget, starts with '${body.first().firstStatement}'", body.first().firstStatement.startsWith("Assert-PaTwinTarget"))
        }
    }

    @Test
    fun theGuardRefusesAnythingButTheTwin() {
        val body = load().filter { it.fn == "Assert-PaTwinTarget" }.joinToString("\n") { it.text }
        assertTrue("it compares with the twin package", body.contains("\$PaTwinPackage"))
        assertTrue("it reads the variant of the running build", body.contains("variant"))
        assertTrue("it throws", body.contains("throw"))
    }

    @Test
    fun noForbiddenSwitchesAnywhere() {
        // Each of these would let a test run get around the phone's own protection (Wave 0 contract, 1.1 and 1.3).
        val forbidden = listOf("uninstall", "bypass-low-target-sdk", "package_verifier", "verifier")
        for (l in load()) {
            val low = l.text.lowercase()
            for (w in forbidden) assertTrue("${l.file}:${l.no} contains '$w'", !low.contains(w))
        }
    }

    @Test
    fun theOwnerPackageIsNeverSpelledOutInTheScripts() {
        // package names come from tools/build-ids.ps1 only
        val literal = Regex("dev\\.pocketagent\\.app(\\.debug)?(?![\\w.])")
        for (l in load()) {
            // the names of the app's classes and actions ("dev.pocketagent.app.DEBUG") are not package ids; a bare id is
            val hit = literal.find(l.text)
            assertTrue("${l.file}:${l.no} spells a package id: ${hit?.value}", hit == null)
        }
    }

    @Test
    fun noSerialsAccountsOrPathsOfThePc() {
        val mail = Regex("[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[a-z]{2,}")
        for (l in load()) {
            assertTrue("${l.file}:${l.no} has an e-mail address", !mail.containsMatchIn(l.text))
            assertTrue("${l.file}:${l.no} has a user path", !l.text.contains("C:\\Users\\"))
        }
    }
}
