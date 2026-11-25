package dev.pocketagent.app.ui

/**
 * The one registry of test tags for the controls the phone robot (tools/phone-test.ps1) has to find. Owners write
 * `Modifier.testTag(RobotTags.X)`; the robot reads them as `resource-id` (the shell root turns that on, `robotRoot()` in `ui.nav`).
 *
 * Not in this file, on purpose: the tags of the shell (`nav.bar`, `tab.*`, `screen.*`, `share.*`: NavTags), of the design
 * components (`sheet`, `dialog`, `petnote`, `gallery`, ...: DESIGN) and the finer chat tags that only the chat screen writes
 * (`chat.screen`, `chat.msg.user`, `chat.tool.sheet`, ...: STREAM).
 *
 * A tag may sit on a container: the robot taps the centre of its bounds and reads `checked` from the first checkable descendant.
 * One tag per node: never put two on the same modifier chain (the inner one is lost).
 *
 * Every constant is a plain lower-case, dot-separated string. `RobotTagsTest` keeps them unique and well formed.
 */
object RobotTags {
    // Settings > Runtime and Diagnostics
    const val RUNTIME_STATE = "settings.runtime.state"
    const val RUNTIME_START = "settings.runtime.start"
    const val RUNTIME_STOP = "settings.runtime.stop"
    const val RUNTIME_RESTART = "settings.runtime.restart"
    const val RUNTIME_PRACTICE = "settings.runtime.practice"
    const val DIAG_EXPORT_LOGS = "settings.diag.export"

    // The bar that says the practice model is on (ui/settings/PracticeBanner.kt)
    const val PRACTICE_BANNER = "practice.banner"

    // Home and the chat list
    const val HOME_STATE = "home.state"
    const val HOME_START = "home.start"
    const val CHATS_NEW = "chats.new"

    // Chat
    const val CHAT_INPUT = "chat.input"
    const val CHAT_SEND = "chat.send"
    const val CHAT_STOP = "chat.stop"
    const val CHAT_ATTACH = "chat.attach"
    const val CHAT_MIC = "chat.mic"
    const val CHAT_LIST = "chat.list"
    const val CHAT_THOUGHT = "chat.thought"
    const val CHAT_APPROVAL_ONCE = "chat.approval.once"
    const val CHAT_APPROVAL_SESSION = "chat.approval.session"
    const val CHAT_APPROVAL_ALWAYS = "chat.approval.always"
    const val CHAT_APPROVAL_DENY = "chat.approval.deny"
    const val CHAT_MEMORY_OPEN = "chat.memory.open"
    const val CHAT_FORGED_CHAT = "chat.forged.chat"
    const val CHAT_MENU = "chat.menu"
    const val CHAT_FIND = "chat.find"

    // Hermes screen and terminal
    const val HERMES_TAB_SOUL = "hermes.tab.soul"
    const val HERMES_TAB_MEMORY = "hermes.tab.memory"
    const val HERMES_TAB_USER = "hermes.tab.user"
    const val HERMES_TAB_SKILLS = "hermes.tab.skills"
    const val HERMES_TAB_CONFIG = "hermes.tab.config"
    const val HERMES_TAB_LOGS = "hermes.tab.logs"
    const val HERMES_DOC_EDITOR = "hermes.doc.editor"
    const val HERMES_DOC_SAVE = "hermes.doc.save"
    const val HERMES_SKILL_NEW = "hermes.skill.new"
    const val HERMES_SKILL_NAME = "hermes.skill.name"
    const val HERMES_SKILL_CREATE = "hermes.skill.create"
    const val HERMES_SKILL_DELETE = "hermes.skill.delete"
    const val TERMINAL_INPUT = "terminal.input"
    const val TERMINAL_RUN = "terminal.run"
    const val TERMINAL_OUTPUT = "terminal.output"

    // Settings sections
    const val USAGE_TODAY = "usage.today"
    const val USAGE_BUDGET_LIMIT = "usage.budget.limit"
    const val USAGE_BUDGET_WARN = "usage.budget.warn"
    const val USAGE_BUDGET_BLOCK = "usage.budget.block"
    const val BEHAVIOUR_IDLE_MINUTES = "behaviour.idle.minutes"
    const val BACKUP_SAVE = "backup.save"
    const val BACKUP_RESTORE = "backup.restore"
    const val BACKUP_RESULT = "backup.result"

    // Bots
    const val BOTS_EXPORT = "bots.export"
    const val BOTS_IMPORT = "bots.import"

    /** Row [i] of the chat list; 0 is the top row. */
    fun chatsRow(i: Int): String = "chats.row.$i"

    /** Message [i] of an open chat; 0 is the NEWEST message (stable while the list is virtualised). */
    fun chatMsg(i: Int): String = "chat.msg.$i"

    /** Tool card [i] of an open chat, counted among tool cards only; 0 is the newest. */
    fun chatTool(i: Int): String = "chat.tool.$i"

    /** Header of a settings section: `provider usage behaviour appearance voice language backup runtime diagnostics about`. */
    fun settingsSection(key: String): String = "settings.section.$key"

    fun hermesSkillRow(name: String): String = "hermes.skill.row.$name"

    /** Card [i] of the bots list. */
    fun botsRow(i: Int): String = "bots.row.$i"
}
