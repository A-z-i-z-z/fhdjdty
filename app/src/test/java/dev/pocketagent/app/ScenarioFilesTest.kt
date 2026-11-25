package dev.pocketagent.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * The scenario files of the phone robot (tools/scenarios) are data that no compiler checks. These tests pin their shape, the one
 * vocabulary the runner knows, the tags they may use (the tag table of the Wave 0 contract, section 3.12) and the rules that keep a
 * run on the owner's package harmless. They read repo files, so every test skips when the tree is not there (-Test runs from a copy).
 */
class ScenarioFilesTest {

    // The same list as `$script:PaOps` in tools/phone-lib.ps1; opVocabularyMatchesTheScript keeps the two equal.
    private val vocabulary = setOf(
        "link", "tap", "type", "wait", "expect", "expectNot", "shot", "kill", "forceStop", "notification", "noCrash",
        "key", "swipe", "wipe", "pref", "seed", "killGuest", "expectFile", "expectColor", "pick", "install", "adb",
        "scale", "a11y", "restore", "scanDump", "eachRoute", "measure",
    )

    /** Ops that change or read the app's private data, or system settings: never in a scenario marked safe. */
    private val notForSafe = setOf("wipe", "seed", "pref", "killGuest", "restore", "install", "adb", "pick", "scale", "expectFile")

    private val safeNames = setOf(
        "smoke-launch", "smoke-tabs-labels", "smoke-hermes-running", "smoke-terminal-version", "smoke-settings-walk",
        "design-gallery", "sweep-labels", "sweep-stretched-and-mirrored",
    )

    private val selectorFields = listOf("tag", "tagRe", "text", "desc", "textContains", "descContains", "re", "any")

    // The tag table of the contract (3.12). A tag is known when it equals one entry or matches one pattern.
    private val knownTags: Set<String> = setOf(
        "settings.runtime.state", "settings.runtime.start", "settings.runtime.stop", "settings.runtime.restart", "settings.runtime.practice",
        "settings.diag.export", "home.state", "home.start", "chats.new",
        "chat.input", "chat.send", "chat.stop", "chat.attach", "chat.mic",
        "chat.approval.once", "chat.approval.session", "chat.approval.always", "chat.approval.deny", "chat.forged.chat",
        "chat.list", "chat.thought", "chat.memory.open", "chat.menu", "chat.find",
        "chat.screen", "chat.controls", "chat.search", "chat.empty", "chat.msg.user", "chat.msg.bot", "chat.msg.tool", "chat.msg.error",
        "chat.msg.note", "chat.msg.bot.live", "chat.pending", "chat.jump", "chat.approval",
        "chat.tool.sheet", "chat.tool.output", "chat.tool.copy", "chat.tool.close",
        "hermes.tab.soul", "hermes.tab.memory", "hermes.tab.user", "hermes.tab.skills", "hermes.tab.config", "hermes.tab.logs",
        "hermes.doc.editor", "hermes.doc.save", "hermes.skill.new", "hermes.skill.name", "hermes.skill.create", "hermes.skill.delete",
        "terminal.input", "terminal.run", "terminal.output",
        "usage.today", "usage.budget.limit", "usage.budget.warn", "usage.budget.block", "behaviour.idle.minutes",
        "backup.save", "backup.restore", "backup.result", "bots.export", "bots.import",
        "nav.bar", "tab.home", "tab.chats", "tab.bots", "tab.hermes", "tab.settings", "share.dialog", "share.new", "share.item",
        "sheet", "sheet.scrim", "sheet.handle", "sheet.title", "dialog", "dialog.title", "dialog.confirm", "dialog.cancel",
        "petnote", "petnote.action", "skeleton", "ember", "top.back", "top.title",
        "gallery", "gallery.sheet", "gallery.dialog", "gallery.note", "gallery.note.error", "gallery.note.action",
        "stage.hearth", "diag.exitReasons", "diag.guestCode", "crash.offer", "crash.copy", "crash.dismiss", "privacy.webLookups", "practice.banner",
    )

    private val knownTagPatterns: List<Regex> = listOf(
        Regex("chats\\.row\\.\\d+"), Regex("chat\\.msg\\.\\d+"), Regex("chat\\.tool\\.\\d+"), Regex("bots\\.row\\.\\d+"),
        Regex("hermes\\.skill\\.row\\..+"), Regex("sheet\\.action\\.\\d+"),
        Regex("settings\\.section\\.(provider|usage|behaviour|appearance|voice|language|backup|runtime|diagnostics|about)"),
        Regex("screen\\.(home|chats|chat|bots|boteditor|hermes|terminal|settings|routines|routineeditor|starred|gallery)"),
    )

    private class Scenario(val file: File, val json: JSONObject) {
        val name: String get() = json.optString("name")
        val isPerf: Boolean get() = file.parentFile?.name == "perf"
    }

    private fun repoRoot(): File? {
        var d: File? = File("").absoluteFile
        var i = 0
        while (d != null && i < 8) {
            if (File(d, "tools/scenarios").isDirectory) return d
            d = d.parentFile
            i++
        }
        return null
    }

    private fun scenarioDir(): File {
        val root = repoRoot()
        Assume.assumeTrue("repo files are not reachable from here", root != null)
        return File(root, "tools/scenarios")
    }

    /** Every scenario file: tools/scenarios and tools/scenarios/perf (a file counts when it has a name and either steps or a measure block). */
    private fun load(): List<Scenario> {
        val dir = scenarioDir()
        val all = ArrayList<Scenario>()
        val folders = listOf(dir, File(dir, "perf"))
        for (f in folders) {
            val files = f.listFiles { x -> x.isFile && x.name.endsWith(".json") } ?: continue
            for (x in files.sortedBy { it.name }) {
                val j = JSONObject(x.readText(Charsets.UTF_8))
                if (j.has("name") && (j.has("steps") || j.has("measure"))) all.add(Scenario(x, j))
            }
        }
        return all
    }

    /** The steps of a scenario with the steps nested in them (eachRoute) and its cleanup steps. */
    private fun flat(arr: JSONArray?, out: MutableList<JSONObject> = ArrayList()): List<JSONObject> {
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            out.add(s)
            flat(s.optJSONArray("steps"), out)
        }
        return out
    }

    private fun allSteps(s: Scenario): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        flat(s.json.optJSONArray("steps"), out)
        flat(s.json.optJSONArray("cleanup"), out)
        return out
    }

    private fun tagsOf(sel: JSONObject, out: MutableSet<String>) {
        val t = sel.optString("tag", "")
        if (t.isNotEmpty()) out.add(t)
        val any = sel.optJSONArray("any")
        if (any != null) for (i in 0 until any.length()) tagsOf(any.getJSONObject(i), out)
    }

    private fun hasSelector(sel: JSONObject): Boolean = selectorFields.any { sel.has(it) }

    @Test
    fun everyFileParsesAndUsesTheVocabulary() {
        val all = load()
        assertTrue("expected at least 14 scenario files (9 scenarios and 5 perf), found ${all.size}", all.size >= 14)
        for (s in all) {
            assertEquals("name must equal the file name of ${s.file.name}", s.file.name.removeSuffix(".json"), s.name)
            for (st in allSteps(s)) {
                val op = st.optString("op")
                assertTrue("${s.name}: unknown op '$op'", op in vocabulary)
                if (op == "tap" || op == "expect" || op == "expectNot") {
                    assertTrue(
                        "${s.name}: op $op needs a selector",
                        hasSelector(st) || (op != "tap" && (st.has("log") || st.has("navIs") || st.has("chatHas"))),
                    )
                }
                if (op == "wait") {
                    assertTrue("${s.name}: wait needs ms, a state, a log pattern or a selector", st.has("ms") || st.has("state") || st.has("log") || hasSelector(st))
                }
                if (st.has("timeout")) assertTrue("${s.name}: timeout must be a number", st.get("timeout") is Number)
                if (st.has("ms")) assertTrue("${s.name}: ms must be a number", st.get("ms") is Number)
                if (op == "eachRoute") assertTrue("${s.name}: eachRoute needs steps", (st.optJSONArray("steps")?.length() ?: 0) > 0)
            }
            if (s.json.has("timeoutSec")) assertTrue("${s.name}: timeoutSec must be a number", s.json.get("timeoutSec") is Number)
        }
    }

    @Test
    fun everyTagIsKnown() {
        for (s in load()) {
            for (st in allSteps(s)) {
                val tags = LinkedHashSet<String>()
                tagsOf(st, tags)
                for (t in tags) {
                    if (t.contains("{{")) continue
                    val ok = t in knownTags || knownTagPatterns.any { it.matches(t) }
                    assertTrue("${s.name}: tag '$t' is not in the tag table of the contract", ok)
                }
            }
        }
    }

    @Test
    fun theEightSafeScenariosExistAndNothingElseIsSafe() {
        val all = load().filter { !it.isPerf }
        val safe = all.filter { it.json.optBoolean("safe", false) }.map { it.name }.toSet()
        assertEquals(safeNames, safe)
        for (n in safeNames) {
            val s = all.first { it.name == n }
            val flags = s.json.optJSONArray("flags")
            assertTrue("$n must carry a flag", flags != null && flags.length() > 0)
        }
    }

    @Test
    fun safeScenariosOnlyReadNavigateAndTakePictures() {
        for (s in load().filter { it.json.optBoolean("safe", false) }) {
            assertTrue("${s.name}: a safe scenario is not flagged practice", !s.json.optBoolean("practice", false))
            for (st in allSteps(s)) {
                val op = st.optString("op")
                assertTrue("${s.name}: a safe scenario must not use op '$op'", op !in notForSafe)
                if (op == "type") {
                    assertEquals("${s.name}: type is allowed only into the terminal", "terminal.input", st.optString("tag"))
                    assertEquals("${s.name}: only this text may be typed", "hermes --version", st.optString("text"))
                }
                if (op == "link") {
                    val dbg = st.optJSONObject("debug")
                    if (dbg != null) {
                        assertTrue("${s.name}: a safe link never sends seed", !dbg.has("seed"))
                        assertTrue("${s.name}: a safe link never sends nav", !dbg.has("nav"))
                    }
                }
            }
        }
    }

    @Test
    fun smokeScenariosNeverTypeIntoAChat() {
        val smoke = load().filter { s ->
            val f = s.json.optJSONArray("flags")
            f != null && (0 until f.length()).any { f.getString(it) == "smoke" }
        }
        assertTrue("at least the five smoke scenarios", smoke.size >= 5)
        for (s in smoke) {
            assertTrue("${s.name}: smoke scenarios are safe", s.json.optBoolean("safe", false))
            assertTrue("${s.name}: no practice", !s.json.optBoolean("practice", false))
            for (st in allSteps(s)) {
                val op = st.optString("op")
                assertTrue("${s.name}: no $op in a smoke scenario", op !in setOf("seed", "pref", "wipe", "killGuest"))
                if (op == "type") assertTrue("${s.name}: nothing is typed into a chat", st.optString("tag") != "chat.input")
            }
        }
    }

    @Test
    fun coreSuiteHasTheFirstSet() {
        val core = load().filter { s ->
            val f = s.json.optJSONArray("flags")
            !s.isPerf && f != null && (0 until f.length()).any { f.getString(it) == "core" } && (0 until f.length()).none { f.getString(it) == "slow" }
        }
        // the first set of stage 1: the 8 safe scenarios and nav-state-restore. The twin-only files are written after the first compile.
        assertTrue("core suite has at least 9 scenarios, found ${core.size}", core.size >= 9)
    }

    @Test
    fun routesFileListsTheRoutesTheSweepsVisit() {
        val dir = scenarioDir()
        val j = JSONObject(File(dir, "routes.json").readText(Charsets.UTF_8))
        val routes = j.getJSONArray("routes")
        assertTrue("at least 20 routes", routes.length() >= 20)
        val screens = setOf("screen.home", "screen.chats", "screen.chat", "screen.bots", "screen.boteditor", "screen.hermes", "screen.terminal",
            "screen.settings", "screen.routines", "screen.routineeditor", "screen.starred", "screen.gallery")
        val seen = HashSet<String>()
        for (i in 0 until routes.length()) {
            val r = routes.getJSONObject(i)
            val route = r.getString("route")
            assertTrue("duplicate route $route", seen.add(route))
            assertTrue("route $route names an unknown screen tag", r.getString("screen") in screens)
        }
        for (key in listOf("home", "chats", "bots", "hermes", "terminal", "settings", "routines", "starred", "gallery")) {
            assertTrue("route $key is listed", key in seen)
        }
        for (sec in listOf("provider", "usage", "behaviour", "appearance", "voice", "language", "backup", "runtime", "diagnostics", "about")) {
            assertTrue("settings:$sec is listed", "settings:$sec" in seen)
        }
    }

    @Test
    fun perfFilesCarryAMeasureBlock() {
        val perf = load().filter { it.isPerf }
        val ids = perf.map { it.name.substringBefore('-') }.toSet()
        for (id in listOf("P1", "P4", "P5", "P6", "P7")) assertTrue("perf scenario $id exists", id in ids)
        for (s in perf) {
            val m = s.json.optJSONObject("measure")
            assertTrue("${s.name}: perf scenarios have a measure block", m != null)
            assertTrue("${s.name}: measure.kind is frames, coldstart or idle", m!!.optString("kind") in setOf("frames", "coldstart", "idle"))
            assertTrue("${s.name}: measure.budget is an object", m.optJSONObject("budget") != null)
            if (s.name.startsWith("P4")) assertTrue("P4 runs on the practice model", s.json.optBoolean("practice", false))
        }
    }

    /** A scenario that deletes something on the owner package must prove first where it is, and stop at its first failed cleanup step. */
    @Test
    fun theOnlyDeletingCleanupIsGuarded() {
        val p4 = load().first { it.name.startsWith("P4") }
        assertTrue("P4 stops its cleanup at the first failed step", p4.json.optBoolean("cleanupChain", false))
        val cleanup = p4.json.getJSONArray("cleanup")
        val first = cleanup.getJSONObject(0)
        assertEquals("expect", first.optString("op"))
        assertTrue("the first cleanup step proves the route", first.optString("navIs").startsWith("chat:{{var:"))
        assertTrue("the first cleanup step proves the chat text", first.optString("chatHas").isNotEmpty())
        val steps = p4.json.getJSONArray("steps")
        var captured = false
        for (i in 0 until steps.length()) if (steps.getJSONObject(i).has("captureChat")) captured = true
        assertTrue("the chat id is captured right after the new chat opens", captured)
        // nothing may look for a word of the interface language to delete anything
        for (i in 0 until cleanup.length()) {
            val c = cleanup.getJSONObject(i)
            assertTrue("no text selector in P4's cleanup", !c.has("text") && !c.has("textContains") && !c.has("any"))
        }
    }

    /** The stretched/mirrored sweep changes the owner's app language; it must always put it back. */
    @Test
    fun sweepsThatSwitchLanguageResetInCleanup() {
        for (n in listOf("sweep-stretched-and-mirrored")) {
            val s = load().first { it.name == n }
            val cleanup = s.json.optJSONArray("cleanup")
            assertTrue("$n has a cleanup", cleanup != null && cleanup.length() > 0)
            val dbg = cleanup!!.getJSONObject(0).optJSONObject("debug")
            assertTrue("$n resets language and layout direction in its cleanup", dbg != null && dbg.optString("reset") == "1" && dbg.has("lang") && dbg.has("rtl"))
        }
    }

    @Test
    fun opVocabularyMatchesTheScript() {
        val root = repoRoot()
        Assume.assumeTrue("repo files are not reachable from here", root != null)
        val lib = File(root, "tools/phone-lib.ps1")
        Assume.assumeTrue("tools/phone-lib.ps1 is not there", lib.exists())
        val text = lib.readText(Charsets.UTF_8)
        val m = Regex("PaOps\\s*=\\s*@\\(([^)]*)\\)").find(text)
        assertTrue("PaOps list not found in phone-lib.ps1", m != null)
        val words = Regex("'([A-Za-z]+)'").findAll(m!!.groupValues[1]).map { it.groupValues[1] }.toSet()
        assertEquals("tools/phone-lib.ps1 and ScenarioFilesTest must list the same ops", vocabulary, words)
        val twin = Regex("PaTwinOps\\s*=\\s*@\\(([^)]*)\\)").find(text)
        assertTrue("PaTwinOps list not found", twin != null)
        val twinWords = Regex("'([A-Za-z]+)'").findAll(twin!!.groupValues[1]).map { it.groupValues[1] }.toSet()
        assertTrue("every twin-only op is in the vocabulary", vocabulary.containsAll(twinWords))
        for (op in listOf("wipe", "pref", "seed", "killGuest", "restore", "install", "adb", "pick")) assertTrue("$op is twin only", op in twinWords)
    }

    @Test
    fun theFilesAreFreeOfNamesAndSecrets() {
        val dir = scenarioDir()
        val files = dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".txt") || it.name.endsWith(".md") || it.name.endsWith(".xml")) }.toList()
        assertTrue(files.isNotEmpty())
        val serialLike = Regex("\\b(?=[A-Z0-9]*[0-9])(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10,}\\b")
        val mail = Regex("[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[a-z]{2,}")
        for (f in files) {
            val t = f.readText(Charsets.UTF_8)
            if (f.name == "gfxinfo-sample.txt" || f.name == "dump-sample.xml") continue
            assertTrue("${f.name}: no e-mail address", !mail.containsMatchIn(t))
            assertTrue("${f.name}: nothing that looks like a device serial", !serialLike.containsMatchIn(t))
        }
    }

    @Test
    fun aFailingFileWouldBeCaught() {
        // guards the guard: the vocabulary check really rejects an unknown op
        val bad = JSONObject("{\"name\":\"x\",\"steps\":[{\"op\":\"teleport\"}]}")
        val st = bad.getJSONArray("steps").getJSONObject(0)
        if (st.optString("op") in vocabulary) fail("teleport must not be in the vocabulary")
    }
}
