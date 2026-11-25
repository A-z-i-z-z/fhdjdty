package dev.pocketagent.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.app.PocketApp
import dev.pocketagent.app.R
import dev.pocketagent.app.hermes.SkillInfo
import dev.pocketagent.app.ui.components.ButtonKind
import dev.pocketagent.app.ui.components.PixelButton
import dev.pocketagent.app.ui.components.PixelIcon
import dev.pocketagent.app.ui.components.PixelIconButton
import dev.pocketagent.app.ui.components.PixelIconView
import dev.pocketagent.app.ui.components.PixelPanel
import dev.pocketagent.app.ui.components.PocketChip
import dev.pocketagent.app.ui.components.PocketSheet
import dev.pocketagent.app.ui.components.PocketTextField
import dev.pocketagent.app.ui.theme.Corner
import dev.pocketagent.app.ui.theme.Pocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.pocketagent.app.ui.text.t

/**
 * Visual, searchable Skill Picker bottom sheet backed by `HermesHome.listSkills()`.
 * Supports category filtering, instant preview of SKILL.md, and 1-tap prompt insertion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillPickerSheet(
    initialQuery: String = "",
    onUseSkill: (SkillInfo) -> Unit,
    onOpenInspector: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val hermes = remember { PocketApp.from(ctx).hermesHome }
    val c = Pocket.colors
    var skills by remember { mutableStateOf<List<SkillInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(initialQuery) }
    var selectedCat by remember { mutableStateOf("") }
    var expandedPath by remember { mutableStateOf<String?>(null) }
    var previewBody by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        skills = withContext(Dispatchers.IO) { runCatching { hermes.listSkills() }.getOrDefault(emptyList()) }
        loaded = true
    }
    LaunchedEffect(expandedPath) {
        val p = expandedPath
        previewBody = if (p == null) "" else withContext(Dispatchers.IO) {
            runCatching { hermes.readSkill(p).take(900) }.getOrDefault("")
        }
    }

    val categories = remember(skills) {
        skills.map { it.category.ifBlank { "general" } }.distinct().sorted()
    }
    val filtered = remember(skills, query, selectedCat) {
        val q = query.trim()
        skills.filter { s ->
            val cat = s.category.ifBlank { "general" }
            val catOk = selectedCat.isEmpty() || cat.equals(selectedCat, ignoreCase = true)
            val qOk = q.isEmpty() ||
                s.name.contains(q, ignoreCase = true) ||
                s.description.contains(q, ignoreCase = true) ||
                cat.contains(q, ignoreCase = true)
            catOk && qOk
        }
    }

    PocketSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 580.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixelIconView(PixelIcon.SKILL, tint = c.accent, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    t(R.string.ui_skills_picker_title, filtered.size),
                    color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Text(
                    t(R.string.ui_skills_picker_manage),
                    color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(role = Role.Button) { onDismiss(); onOpenInspector() }.padding(6.dp),
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PocketTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f),
                    placeholder = t(R.string.ui_skills_picker_search), singleLine = true,
                )
                if (query.isNotEmpty()) {
                    PixelIconButton(PixelIcon.CLOSE, onClick = { query = "" }, contentDescription = t(R.string.ui_dlg_close))
                }
            }

            if (categories.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PocketChip(t(R.string.ui_filter_all), selectedCat.isEmpty(), { selectedCat = "" })
                    categories.forEach { cat ->
                        PocketChip(cat, selectedCat == cat, { selectedCat = if (selectedCat == cat) "" else cat })
                    }
                }
            }

            when {
                !loaded -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(t(R.string.ui_skills_picker_loading), color = c.textMuted, fontSize = 14.sp)
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(t(R.string.ui_cmd_skills_none), color = c.textMuted, fontSize = 14.sp)
                }
                else -> LazyColumn(
                    Modifier.weight(1f, fill = false).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.path }) { skill ->
                        val expanded = expandedPath == skill.path
                        PixelPanel(Modifier.fillMaxWidth(), color = c.bgDeep, border = if (expanded) c.accent else c.outline, corner = Corner.chip) {
                            Column(
                                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    expandedPath = if (expanded) null else skill.path
                                }.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        skill.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        skill.category.ifBlank { "general" }, color = c.accent, fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.accent.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                if (skill.description.isNotBlank()) {
                                    Text(
                                        skill.description, color = c.textMuted, fontSize = 12.5.sp,
                                        maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                AnimatedVisibility(expanded && previewBody.isNotBlank()) {
                                    Box(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(c.codeBg).padding(8.dp),
                                    ) {
                                        Text(
                                            previewBody, color = c.text.copy(alpha = 0.85f), fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace, maxLines = 10, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    PixelButton(
                                        t(R.string.ui_skills_picker_use),
                                        onClick = { onUseSkill(skill); onDismiss() },
                                        icon = PixelIcon.PLAY,
                                        kind = ButtonKind.PRIMARY,
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}
