package dev.pocketagent.app

import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.hermes.HermesHome
import dev.pocketagent.app.hermes.LogKind
import dev.pocketagent.app.hermes.MemoryFormat
import dev.pocketagent.app.hermes.SkillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HermesHomeTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun home(): Pair<HermesHome, File> {
        val h = File(tmp.root, "hermes").also { it.mkdirs() }
        return HermesHome(h, File(tmp.root, "logs/app.log"), File(tmp.root, "logs/runtime.log")) to h
    }

    // ---- skill frontmatter ----------------------------------------------------------------------------------------------

    @Test fun parsesTheFrontmatterOfARealSkill() {
        val fm = SkillParser.parse(
            "---\nname: arxiv\ndescription: \"Search arXiv papers by keyword, author, category, or ID.\"\nversion: 1.0.0\n" +
                "author: Hermes Agent\nmetadata:\n  hermes:\n    tags: [Research]\n---\n\n# arXiv Research\n"
        )
        assertEquals("arxiv", fm.name)
        assertEquals("Search arXiv papers by keyword, author, category, or ID.", fm.description)
    }

    @Test fun parsesQuotedPlainAndBlockScalars() {
        assertEquals("it's fine", SkillParser.parse("---\nname: x\ndescription: 'it''s fine'\n---").description)
        assertEquals("plain text", SkillParser.parse("---\nname: x\ndescription: plain text # comment\n---").description)
        assertEquals("one two three", SkillParser.parse("---\nname: x\ndescription: >\n  one two\n  three\nother: 1\n---").description)
        assertEquals("line1\nline2", SkillParser.parse("---\nname: x\ndescription: |\n  line1\n  line2\n---").description)
        assertEquals("say \"hi\"", SkillParser.parse("---\nname: x\ndescription: \"say \\\"hi\\\"\"\n---").description)
    }

    @Test fun nestedKeysAndMissingFrontmatterAreIgnored() {
        val nested = SkillParser.parse("---\nmetadata:\n  name: inner\n  description: inner too\nname: outer\n---\n")
        assertEquals("outer", nested.name)
        assertNull(nested.description)
        val none = SkillParser.parse("# just markdown\nname: not frontmatter\n")
        assertNull(none.name); assertNull(none.description)
        assertNull(SkillParser.parse("").name)
        assertEquals("bom", SkillParser.parse("﻿---\nname: bom\n---").name)
    }

    @Test fun listsSkillsWithCategoryAndRelativePath() {
        val (hh, home) = home()
        fun skill(rel: String, text: String) = File(home, "skills/$rel").also { it.parentFile.mkdirs() }.writeText(text)
        skill("research/arxiv/SKILL.md", "---\nname: arxiv\ndescription: Papers\n---\nbody")
        skill("research/arxiv/scripts/helper.py", "print()")
        skill("software-development/spike/SKILL.md", "---\nname: spike\ndescription: \"Try things\"\n---\n")
        skill("web/blocked-page-recovery/SKILL.md", "no frontmatter at all")
        skill("loose/SKILL.md", "---\nname: loose-skill\n---\n")
        skill(".hidden/x/SKILL.md", "---\nname: hidden\n---\n")
        val list = hh.listSkills()
        assertEquals(listOf("loose-skill", "arxiv", "spike", "blocked-page-recovery"), list.map { it.name })
        val arxiv = list.first { it.name == "arxiv" }
        assertEquals("research", arxiv.category)
        assertEquals("research/arxiv/SKILL.md", arxiv.path)
        assertEquals("Papers", arxiv.description)
        assertEquals("", list.first { it.name == "loose-skill" }.category)
        val broken = list.first { it.name == "blocked-page-recovery" }
        assertEquals("", broken.description)
        assertEquals("web", broken.category)
        assertTrue(hh.readSkill(arxiv.path).endsWith("body"))
    }

    @Test fun skillPathsCannotEscapeTheSkillsDirectory() {
        val (hh, home) = home()
        File(home, "skills/a/b").mkdirs()
        File(home, "secret.md").writeText("top secret")
        for (bad in listOf("../secret.md", "a/../../secret.md", "/etc/passwd.md", "a/b/SKILL.txt", "", "a//SKILL.md", "./a/SKILL.md")) {
            try { hh.readSkill(bad); fail("read of '$bad' should have been refused") } catch (_: IllegalArgumentException) { }
            try { hh.writeSkill(bad, "x"); fail("write of '$bad' should have been refused") } catch (_: IllegalArgumentException) { }
        }
        assertEquals("top secret", File(home, "secret.md").readText())
    }

    @Test fun writeSkillCreatesAndKeepsABackup() {
        val (hh, home) = home()
        hh.writeSkill("mine/new-skill/SKILL.md", "---\nname: new-skill\ndescription: v1\n---\n")
        assertEquals("v1", hh.listSkills().single().description)
        hh.writeSkill("mine/new-skill/SKILL.md", "---\nname: new-skill\ndescription: v2\n---\n")
        assertEquals("v2", hh.listSkills().single().description)
        assertTrue(File(home, "skills/mine/new-skill/SKILL.md.bak").readText().contains("v1"))
        assertEquals("the .bak is not listed as a skill", 1, hh.listSkills().size)
    }

    // ---- creating and deleting skills ----------------------------------------------------------------------------------------

    @Test fun createSkillWritesTheTemplateAndListsIt() {
        val (hh, home) = home()
        val info = hh.createSkill("custom", "my-recipe_2", "Cook \"quick\" dinners\nfast")
        assertEquals("custom/my-recipe_2/SKILL.md", info.path)
        assertEquals("custom", info.category)
        val text = File(home, "skills/custom/my-recipe_2/SKILL.md").readText()
        val fm = SkillParser.parse(text)
        assertEquals("my-recipe_2", fm.name)
        assertEquals("Cook \"quick\" dinners fast", fm.description)
        assertTrue(text.contains("version: 1.0.0"))
        assertTrue(text.contains("metadata:\n  hermes:"))
        assertTrue(text.contains("# My recipe 2"))
        assertEquals(listOf("my-recipe_2"), hh.listSkills().map { it.name })
        // without a category the skill sits directly below skills/
        assertEquals("solo/SKILL.md", hh.createSkill("", "solo", "").path)
        assertTrue(SkillParser.parse(File(home, "skills/solo/SKILL.md").readText()).description!!.startsWith("Use when"))
    }

    @Test fun createSkillRefusesBadNamesAndDuplicates() {
        val (hh, home) = home()
        for (bad in listOf("", "Upper", "has space", "../x", "a/b", "-lead", "x".repeat(65), "references", "né")) {
            try { hh.createSkill("custom", bad, "d"); fail("name '$bad' should have been refused") } catch (_: IllegalArgumentException) { }
        }
        for (bad in listOf("Cat", "a/b", "..", "scripts")) {
            try { hh.createSkill(bad, "ok", "d"); fail("category '$bad' should have been refused") } catch (_: IllegalArgumentException) { }
        }
        assertFalse(File(home, "skills").exists() && File(home, "skills").walkTopDown().any { it.name == "SKILL.md" })
        hh.createSkill("custom", "once", "d")
        try { hh.createSkill("custom", "once", "again"); fail("duplicate") } catch (_: java.io.IOException) { }
        File(home, "skills/isskill").mkdirs(); File(home, "skills/isskill/SKILL.md").writeText("---\nname: isskill\n---\n")
        try { hh.createSkill("isskill", "inner", "d"); fail("a skill is not a category") } catch (_: java.io.IOException) { }
    }

    @Test fun deleteSkillRemovesItsFolderAndAnEmptyCategoryOnly() {
        val (hh, home) = home()
        hh.createSkill("custom", "one", "d"); hh.createSkill("custom", "two", "d")
        File(home, "skills/custom/one/references").mkdirs(); File(home, "skills/custom/one/references/a.md").writeText("x")
        hh.deleteSkill("custom/one/SKILL.md")
        assertFalse(File(home, "skills/custom/one").exists())
        assertTrue(File(home, "skills/custom/two/SKILL.md").isFile)
        assertTrue("category stays while it holds another skill", File(home, "skills/custom").isDirectory)
        hh.deleteSkill("custom/two/SKILL.md")
        assertFalse("an empty category folder is removed too", File(home, "skills/custom").exists())
        assertTrue(File(home, "skills").isDirectory)
    }

    @Test fun deleteSkillRefusesAnythingElse() {
        val (hh, home) = home()
        File(home, "outside").mkdirs(); File(home, "outside/SKILL.md").writeText("x")
        hh.createSkill("cat", "keep", "d")
        File(home, "skills/cat/nested").mkdirs()
        for (bad in listOf("../outside/SKILL.md", "SKILL.md", "cat/keep/other.md", "/abs/SKILL.md", "", "cat/../../outside/SKILL.md")) {
            try { hh.deleteSkill(bad); fail("delete of '$bad' should have been refused") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(File(home, "outside/SKILL.md").isFile)
        assertTrue(File(home, "skills/cat/keep/SKILL.md").isFile)
        // a folder that holds another skill is not deleted
        File(home, "skills/cat/keep/inner").mkdirs(); File(home, "skills/cat/keep/inner/SKILL.md").writeText("---\nname: inner\n---\n")
        try { hh.deleteSkill("cat/keep/SKILL.md"); fail("nested skill") } catch (_: java.io.IOException) { }
        assertTrue(File(home, "skills/cat/keep/inner/SKILL.md").isFile)
        try { hh.deleteSkill("cat/missing/SKILL.md"); fail("missing") } catch (_: java.io.IOException) { }
    }

    // ---- documents --------------------------------------------------------------------------------------------------------

    @Test fun docsReadWriteWithBackupAndConfigIsReadOnly() {
        val (hh, home) = home()
        assertNull(hh.read(HermesDoc.SOUL))
        assertTrue(hh.docInfo(HermesDoc.SOUL).startsWith("/root/.hermes/SOUL.md"))
        assertTrue(hh.docInfo(HermesDoc.SOUL).contains("not created yet"))
        hh.write(HermesDoc.SOUL, "first soul")
        hh.write(HermesDoc.SOUL, "second soul")
        assertEquals("second soul", hh.read(HermesDoc.SOUL))
        assertEquals("first soul", File(home, "SOUL.md.bak").readText())
        assertTrue(hh.docInfo(HermesDoc.SOUL).contains("11 B"))

        hh.write(HermesDoc.MEMORY, "one\n§\ntwo")
        hh.write(HermesDoc.USER, "likes tea")
        assertEquals("one\n§\ntwo", File(home, "memories/MEMORY.md").readText())
        assertEquals("likes tea", File(home, "memories/USER.md").readText())
        assertTrue(hh.docInfo(HermesDoc.USER).startsWith("/root/.hermes/memories/USER.md"))

        File(home, "config.yaml").writeText("model:\n  default: x\n")
        assertEquals("model:\n  default: x\n", hh.read(HermesDoc.CONFIG))
        try { hh.write(HermesDoc.CONFIG, "nope"); fail("config must be read-only") } catch (_: UnsupportedOperationException) { }
        assertEquals("model:\n  default: x\n", hh.read(HermesDoc.CONFIG))
        // Nothing is left behind by the atomic writer.
        assertTrue(home.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun memoryEntriesFollowHermesDelimiterRules() {
        assertEquals("\n§\n", MemoryFormat.ENTRY_DELIMITER)
        assertEquals(listOf("a", "b", "c"), MemoryFormat.entries("a\n§\n  b \n§\n\n§\nc\n"))
        assertTrue(MemoryFormat.entries("").isEmpty())
        assertEquals("a\n§\nb", MemoryFormat.join(listOf("a", "b")))
        assertEquals(5, MemoryFormat.usedChars(listOf("a", "b")))   // the delimiter counts, like Hermes's budget
        assertEquals(2200, MemoryFormat.limitOf(HermesDoc.MEMORY))
        assertEquals(1375, MemoryFormat.limitOf(HermesDoc.USER))
    }

    // ---- logs -------------------------------------------------------------------------------------------------------------

    @Test fun tailLogKeepsTheEndDropsAPartialLineAndRedacts() {
        val (hh, _) = home()
        val log = File(tmp.root, "logs/runtime.log").also { it.parentFile.mkdirs() }
        log.writeText((1..2000).joinToString("\n") { "line number $it" } + "\n")
        val tail = hh.tailLog(LogKind.RUNTIME, 500)
        assertTrue(tail.length <= 500)
        assertTrue(tail.trimEnd().endsWith("line number 2000"))
        assertTrue("starts on a line boundary", tail.startsWith("line number "))
        assertEquals("", hh.tailLog(LogKind.APP))   // no app.log yet

        dev.pocketagent.app.diag.Diag.redactor.addSecret("t", "SUPERSECRETKEYVALUE")
        try {
            File(tmp.root, "logs/app.log").writeText("started with SUPERSECRETKEYVALUE ok\n")
            val app = hh.tailLog(LogKind.APP)
            assertFalse(app.contains("SUPERSECRETKEYVALUE"))
            assertTrue(app.contains("started with"))
        } finally { dev.pocketagent.app.diag.Diag.redactor.clear() }
    }

    @Test fun appLogIncludesThePreviousFileWhenThereIsRoom() {
        val (hh, _) = home()
        File(tmp.root, "logs").mkdirs()
        File(tmp.root, "logs/app.log.1").writeText("old line\n")
        File(tmp.root, "logs/app.log").writeText("new line\n")
        assertEquals("old line\nnew line\n", hh.tailLog(LogKind.APP))
    }

    @Test fun apiLogShowsRecentCalls() {
        val (hh, _) = home()
        dev.pocketagent.app.diag.ApiLog.add("GET /health -> 200 3ms")
        assertTrue(hh.tailLog(LogKind.API).contains("GET /health -> 200 3ms"))
    }
}
