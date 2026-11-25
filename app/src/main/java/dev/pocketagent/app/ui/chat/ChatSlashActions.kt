package dev.pocketagent.app.ui.chat

import dev.pocketagent.app.R
import dev.pocketagent.app.data.ChatMode
import dev.pocketagent.app.data.SlashCommands
import dev.pocketagent.app.hermes.HermesDoc
import dev.pocketagent.app.hermes.MemoryFormat
import dev.pocketagent.app.ui.theme.AppearanceStore
import dev.pocketagent.app.ui.theme.HapticCue
import dev.pocketagent.app.ui.theme.ThemeId
import dev.pocketagent.app.voice.SoundCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.pocketagent.app.ui.text.Texts
import dev.pocketagent.app.ui.settings.themeNameRes

/**
 * Runs one parsed slash command of the chat input (moved from the old chat screen without a change of behaviour). An
 * extension of the state holder, so the input area, the empty-chat shortcuts and the slash palette all call the same code.
 */
internal fun ChatScreenState.executeSlash(parsed: SlashCommands.ParsedSlash) {
    val ctx = context
    val arg = parsed.arg
    val mentionTargets = if (isGroup) mentionedBotIds(arg, memberBotsNow()) else emptyList()
    when (parsed.name) {
        "plan", "goal", "ultracode" -> {
            val targetMode = when (parsed.name) {
                "plan" -> ChatMode.PLAN
                "goal" -> ChatMode.GOAL
                else -> ChatMode.ULTRACODE
            }
            repo.setMode(convId, targetMode)
            if (arg.isNotBlank() || pendingAttachments.isNotEmpty()) {
                val sent = engine.send(convId, arg, mentionTargets, pendingAttachments).accepted()
                if (sent) { pendingAttachments = emptyList(); spark++; stick = true } else toastNotSent()
            } else {
                val modeLabel = Texts.get(ctx,
                    when (targetMode) {
                        ChatMode.PLAN -> R.string.ui_mode_banner_plan
                        ChatMode.GOAL -> R.string.ui_mode_banner_goal
                        else -> R.string.ui_mode_banner_ultracode
                    },
                )
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_mode_on_note, modeLabel))
            }
        }
        "loop" -> {
            repo.setMode(convId, ChatMode.LOOP)
            val (passes, body) = SlashCommands.parseLoopArgs(arg)
            if (body.isNotBlank() || pendingAttachments.isNotEmpty()) {
                val sent = engine.runLoop(convId, body, passes, pendingAttachments, mentionTargets).accepted()
                if (sent) { pendingAttachments = emptyList(); spark++; stick = true } else toastNotSent()
            } else {
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_mode_on_note, Texts.get(ctx, R.string.ui_mode_banner_loop)))
            }
        }
        "normal" -> {
            repo.setMode(convId, ChatMode.NORMAL)
            engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_mode_off_note))
        }
        "forge" -> {
            if (arg.isBlank()) {
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_forge_usage))
            } else {
                val sent = engine.send(convId, "/forge $arg", mentionTargets, pendingAttachments).accepted()
                if (sent) { pendingAttachments = emptyList(); spark++; stick = true } else toastNotSent()
            }
        }
        "skills" -> {
            skillPickerQuery = arg
        }
        "remember" -> {
            if (arg.isBlank()) {
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_remember_usage))
            } else {
                scope.launch {
                    val cleaned = arg.replace("§", "").trim()
                    val result = withContext(Dispatchers.IO) {
                        val cur = app.hermesHome.read(HermesDoc.USER).orEmpty()
                        val entries = MemoryFormat.entries(cur) + cleaned
                        val used = MemoryFormat.usedChars(entries)
                        if (used > MemoryFormat.USER_LIMIT) {
                            false to used
                        } else {
                            app.hermesHome.write(HermesDoc.USER, MemoryFormat.join(entries))
                            engine.notifyMemoryUpdated()
                            true to used
                        }
                    }
                    val note = if (result.first) {
                        Texts.get(ctx, R.string.ui_cmd_remember_saved, result.second, MemoryFormat.USER_LIMIT)
                    } else {
                        Texts.get(ctx, R.string.ui_cmd_remember_full, result.second, MemoryFormat.USER_LIMIT)
                    }
                    engine.addLocalNote(convId, note)
                }
            }
        }
        "memory" -> {
            scope.launch {
                val note = withContext(Dispatchers.IO) {
                    val memRaw = app.hermesHome.read(HermesDoc.MEMORY).orEmpty()
                    val usrRaw = app.hermesHome.read(HermesDoc.USER).orEmpty()
                    val memEntries = MemoryFormat.entries(memRaw)
                    val usrEntries = MemoryFormat.entries(usrRaw)
                    if (memEntries.isEmpty() && usrEntries.isEmpty()) {
                        Texts.get(ctx, R.string.ui_cmd_memory_empty)
                    } else {
                        buildString {
                            appendLine(Texts.get(ctx, R.string.ui_cmd_memory_header))
                            appendLine(Texts.get(ctx, R.string.eng_memory_md_used, MemoryFormat.usedChars(memEntries), MemoryFormat.MEMORY_LIMIT))
                            memEntries.takeLast(5).forEach { appendLine("  • ${it.take(120)}") }
                            appendLine(Texts.get(ctx, R.string.eng_user_md_used, MemoryFormat.usedChars(usrEntries), MemoryFormat.USER_LIMIT))
                            usrEntries.takeLast(5).forEach { appendLine("  • ${it.take(120)}") }
                        }.trim()
                    }
                }
                engine.addLocalNote(convId, note)
            }
        }
        "theme" -> {
            val pick = ThemeId.entries.firstOrNull { it.name.equals(arg.trim(), ignoreCase = true) }
            if (pick == null) {
                val names = ThemeId.entries.joinToString(", ") { it.name.lowercase() }
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_theme_usage, names))
            } else {
                AppearanceStore.get(ctx).update { it.copy(theme = pick) }
                engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_theme_switched, Texts.get(ctx, themeNameRes(pick))))
            }
        }
        "export" -> exportMenu = true
        "find" -> openSearch(arg)
        "clear" -> confirmClear = true
        "help" -> {
            val note = buildString {
                appendLine(Texts.get(ctx, R.string.ui_cmd_help_header))
                SlashCommands.visible.forEach { def -> appendLine("• ${def.usage}") }
            }.trim()
            engine.addLocalNote(convId, note)
        }
        "pet" -> {
            val idx = kotlin.random.Random.nextInt(3)
            val note = when (idx) {
                0 -> Texts.get(ctx, R.string.ui_cmd_pet_quip1, repo.bots.value.size)
                1 -> Texts.get(ctx, R.string.ui_cmd_pet_quip2)
                else -> Texts.get(ctx, R.string.ui_cmd_pet_quip3, messagesNow().size)
            }
            engine.addLocalNote(convId, note)
        }
        "newroz" -> {
            haptics.play(HapticCue.HOLD)
            app.sounds.play(SoundCue.FORGE)
            celebrateTrigger++
            engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_newroz_msg))
        }
        "agir", "fire" -> {
            haptics.play(HapticCue.HOLD)
            app.sounds.play(SoundCue.FORGE)
            celebrateTrigger++
            engine.addLocalNote(convId, Texts.get(ctx, R.string.ui_cmd_agir_msg))
        }
        "roll" -> {
            val spec = arg.lowercase()
            val note = if (spec == "coin") {
                Texts.get(ctx, if (kotlin.random.Random.nextBoolean()) R.string.ui_cmd_roll_coin_heads else R.string.ui_cmd_roll_coin_tails)
            } else {
                val sides = spec.removePrefix("d").toIntOrNull()?.coerceIn(2, 1000) ?: 20
                val rolled = kotlin.random.Random.nextInt(1, sides + 1)
                Texts.get(ctx, R.string.ui_cmd_roll_dice, sides, rolled)
            }
            engine.addLocalNote(convId, note)
        }
    }
}
