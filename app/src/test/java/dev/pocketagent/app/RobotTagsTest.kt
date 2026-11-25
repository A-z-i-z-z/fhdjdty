package dev.pocketagent.app

import dev.pocketagent.app.ui.RobotTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class RobotTagsTest {
    /** Every `const val` of the object: name to value (kotlin-reflect is not on the classpath, so plain Java reflection). */
    private fun constants(): Map<String, String> =
        RobotTags::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { f -> f.isAccessible = true; f.name to (f.get(null) as String) }

    @Test fun valuesAreUniqueAndWellFormed() {
        val all = constants()
        assertTrue("the registry is not empty", all.size >= 50)
        val shape = Regex("^[a-z][a-z0-9.-]*$")
        for ((name, value) in all) {
            assertTrue("$name = '$value' must match ${shape.pattern}", shape.matches(value))
            assertFalse("$name has a slash", value.contains('/'))
            assertFalse("$name ends with a dot", value.endsWith("."))
            assertFalse("$name has an empty part", value.contains(".."))
        }
        val dup = all.entries.groupBy({ it.value }, { it.key }).filter { it.value.size > 1 }
        assertTrue("duplicate values: $dup", dup.isEmpty())
    }

    @Test fun theTagsOfTheContractTableExist() {
        val values = constants().values.toSet()
        val wanted = listOf(
            "settings.runtime.state", "settings.runtime.start", "settings.runtime.stop", "settings.runtime.restart",
            "settings.runtime.practice", "settings.diag.export", "home.state", "home.start", "chats.new",
            "chat.input", "chat.send", "chat.stop", "chat.attach", "chat.mic",
            "chat.approval.once", "chat.approval.session", "chat.approval.always", "chat.approval.deny",
            "chat.forged.chat", "chat.list", "chat.thought", "chat.memory.open", "chat.menu", "chat.find",
            "hermes.tab.soul", "hermes.tab.memory", "hermes.tab.user", "hermes.tab.skills", "hermes.tab.config", "hermes.tab.logs",
            "hermes.doc.editor", "hermes.doc.save", "hermes.skill.new", "hermes.skill.name", "hermes.skill.create", "hermes.skill.delete",
            "terminal.input", "terminal.run", "terminal.output",
            "usage.today", "usage.budget.limit", "usage.budget.warn", "usage.budget.block", "behaviour.idle.minutes",
            "backup.save", "backup.restore", "backup.result", "bots.export", "bots.import", "practice.banner",
        )
        for (w in wanted) assertTrue("missing tag $w", w in values)
    }

    @Test fun confirmDialogsAreTaggedByTheDesignComponentNotHere() {
        // Scenarios use dialog.confirm: there is no tag for the confirm buttons of the skill delete or the backup restore.
        val names = constants().keys
        assertFalse("HERMES_SKILL_DELETE_CONFIRM" in names)
        assertFalse("BACKUP_CONFIRM" in names)
    }

    @Test fun constantsHaveTheDocumentedValues() {
        assertEquals("chat.attach", RobotTags.CHAT_ATTACH)
        assertEquals("chat.mic", RobotTags.CHAT_MIC)
        assertEquals("settings.runtime.practice", RobotTags.RUNTIME_PRACTICE)
        assertEquals("settings.diag.export", RobotTags.DIAG_EXPORT_LOGS)
        assertEquals("practice.banner", RobotTags.PRACTICE_BANNER)
        assertEquals("chat.forged.chat", RobotTags.CHAT_FORGED_CHAT)
    }

    @Test fun functionsFollowThePattern() {
        assertEquals("chats.row.0", RobotTags.chatsRow(0))
        assertEquals("chat.msg.0", RobotTags.chatMsg(0))
        assertEquals("chat.msg.12", RobotTags.chatMsg(12))
        assertEquals("chat.tool.3", RobotTags.chatTool(3))
        assertEquals("settings.section.runtime", RobotTags.settingsSection("runtime"))
        assertEquals("hermes.skill.row.practice-skill", RobotTags.hermesSkillRow("practice-skill"))
        assertEquals("bots.row.2", RobotTags.botsRow(2))
    }

    @Test fun functionResultsDoNotCollideWithConstants() {
        val values = constants().values.toSet()
        val made = listOf(RobotTags.chatsRow(0), RobotTags.chatMsg(0), RobotTags.chatTool(0), RobotTags.botsRow(0), RobotTags.settingsSection("x"))
        for (m in made) assertFalse("$m is also a constant", m in values)
    }
}
