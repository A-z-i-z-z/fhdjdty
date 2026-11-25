package dev.pocketagent.app.ui.hermes

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.hermes.SkillInfo

/**
 * Holds the editor buffers of the Hermes inspector (Soul, Memory, User and every skill that was opened) so that
 * unsaved edits survive leaving the screen (bottom-bar taps) and rotation. Activity-scoped, like MainViewModel.
 */
internal class HermesEditorsViewModel(app: Application) : AndroidViewModel(app) {
    val home = PocketApp.from(app).hermesHome

    val soul = DocState({ home.read(HermesDoc.SOUL) }, { home.write(HermesDoc.SOUL, it) })
    val memory = DocState({ home.read(HermesDoc.MEMORY) }, { home.write(HermesDoc.MEMORY, it) })
    val user = DocState({ home.read(HermesDoc.USER) }, { home.write(HermesDoc.USER, it) })

    // A plain map on purpose: a new buffer starts clean, so its creation (during composition) changes nothing visible.
    private val skillStates = HashMap<String, DocState>()

    /** The skill whose editor is showing in the Skills tab (null = the list). */
    var openSkill by mutableStateOf<SkillInfo?>(null)

    /** The buffer of a skill; created on first use and kept, so edits stay when the viewer is closed. */
    fun skillState(skill: SkillInfo): DocState =
        skillStates.getOrPut(skill.path) { DocState({ home.readSkill(skill.path) }, { home.writeSkill(skill.path, it) }) }

    val anyDirty: Boolean
        get() = soul.dirty || memory.dirty || user.dirty || skillStates.values.any { it.dirty }

    val anySkillDirty: Boolean get() = skillStates.values.any { it.dirty }

    /** Drops the editor buffer of a skill that was deleted (its unsaved edits go with it). */
    fun forgetSkill(path: String) {
        skillStates.remove(path)
        if (openSkill?.path == path) openSkill = null
    }

    /** True when this skill has unsaved edits. */
    fun skillDirty(path: String): Boolean = skillStates[path]?.dirty == true

    /** Throws away every unsaved edit. */
    fun discardAll() {
        soul.revert(); memory.revert(); user.revert()
        skillStates.values.forEach { it.revert() }
    }
}
