# Phase 1+2 build contract (shared by all agents working in parallel)

Written 2026-11-23 by the lead agent. Five agents build the new app at the same time, each owning
its own files. This file is the contract between them: **implement exactly the names and
signatures below** so the parts compile together. If you must deviate, keep the listed API working
(add, do not rename) and say so in your final report.

Ground rules for every agent:
- Kotlin + Jetpack Compose, packages under `dev.pocketagent.app`. Versions: see
  `gradle/libs.versions.toml` (Compose BOM 2026.11.01, Kotlin 2.4, minSdk 26, targetSdk 28,
  runs on Android 17). **No new dependencies** (no navigation-compose, no icons library, no Room,
  no image libraries). Available: compose ui/foundation/material3, activity-compose,
  lifecycle-runtime-compose, lifecycle-viewmodel-compose, coroutines, org.json (Android built-in).
- **Do not run Gradle.** Other agents are editing the tree at the same time; the lead agent
  compiles and sends errors back. Write code carefully (imports, exact types).
- Touch only the files you own. Need something from another part? Use the API below.
- No secrets in logs or UI. Public repo: no account names, project ids, e-mails, serials.
- Strings shown to the user go in string resources (English in `values/`, plus Turkish in
  `values-tr/` and French in `values-fr/`), each agent in its own file name (listed below) so
  nobody edits the same XML file. Escape apostrophes (`\'`).

## Identity and art direction (everyone)

The owner is Kurdish and loved the "Hearthlight" fire direction, but wants it far richer: real
pixel art with human-art quality, not generic AI-looking UI. Direction:

- **The hearth is Newroz fire.** The home screen is a living pixel scene: night sky over the
  Zagros mountains, a stone ring with a hand-animated fire, drifting embers, and the mascot.
- **Mascot: "Pêt"** (Kurmanji for "spark/flame"), a Kurdistan newt / salamander (Neurergus:
  near-black body with yellow spots, orange-red belly; salamanders are the fire spirits of
  folklore). Friendly, expressive, small. Moods: idle (breathing, blinking, tail sway), thinking
  (watching the fire, eyes up), talking, happy (hop), sleeping (curled, Zz, fire out), error
  (soot cloud, worried), waving.
- Pixel art rules: hand-placed pixels, limited palette per sprite, coloured (not pure black)
  outlines, one consistent light source (the fire, warm rim light), dithering for glow, hand-keyed
  frame animation (fire 6+ frames), no blurry scaling (nearest-neighbour, integer scale).
- Themes (ids fixed, see `ThemeId`): `NEWROZ` (default; night plum-black, fire orange/gold,
  cream text), `ZAGROS` (cold mountain night: slate blue, snow, saffron accent), `SEMENDER`
  (newt: ink black, yellow spots, belly orange), `NERGIZ` (light, spring narcissus: cream, leaf
  green, narcissus yellow), `KAWA` (blacksmith's forge: charcoal, iron, molten orange), `ROJ`
  (light, sun: warm sand, red, green, sun-gold).
- Body text uses the system sans font at readable sizes; titles and labels can use the bitmap
  pixel font (`PixelText`). Code/tool output uses monospace.
- Motion everywhere but purposeful; respect `Appearance.animations` (FULL / REDUCED / OFF).

## Shared pixel format `.pxl` (owner of the engine: agent ART)

Sprite sources are text files in `app/src/main/assets/pixel/*.pxl`, parsed at runtime and by the
PC preview tool `tools/pixel-preview.ps1` (renders PNGs you can look at with the Read tool).

```
# comment lines start with '#' in column 0
palette
. none
k #2A1A24
o #E8661C
y #F2C230
x #FFFFFF
sprite newt_idle 24 20
frame 160
<20 rows of exactly 24 chars>
frame 160
<20 rows ...>
sprite icon_send 12 12
...
```
- `palette` starts a palette block: lines `<char> <#RRGGBB | #AARRGGBB | none>` until the next
  directive. It applies to every following sprite in the same file until another `palette` block
  replaces it. `.` is always transparent.
- `sprite <name> <w> <h>` then one or more `frame [ms]` blocks (default 120 ms), each followed
  by exactly `h` rows of exactly `w` chars. Sprite names are unique across all files.
- **Recolour convention:** palette chars `1`..`4` are the "tint ramp" (1 = darkest .. 4 =
  lightest). Avatars use them for the user-chosen colour. Monochrome icons use only `x`
  (and `.`), and are tinted to one colour at draw time.

## Package `dev.pocketagent.app.ui.pixel` (owner: agent ART)

```kotlin
class Sprite(val name: String, val width: Int, val height: Int, val frames: List<SpriteFrame>, val palette: List<PaletteEntry>)
class SpriteFrame(val indices: IntArray /* palette index per pixel, -1 = transparent */, val durationMs: Int)
class PaletteEntry(val char: Char, val argb: Int)
object SpriteBank {
    fun init(context: android.content.Context)          // parses every assets/pixel/*.pxl once (call from PocketApp.onCreate is done by agent ENGINE; also safe to call twice)
    fun get(name: String): Sprite                       // throws IllegalArgumentException with the name if missing
    fun find(name: String): Sprite?
}
/** Crisp nearest-neighbour sprite. Animates through frames when frame == null and animations are on. */
@Composable fun PixelImage(sprite: Sprite, modifier: Modifier = Modifier, frame: Int? = null,
    tint: Color? = null /* monochrome icons: every opaque pixel becomes this colour */,
    ramp: List<Color>? = null /* replaces palette chars '1'..'4' in order */, playing: Boolean = true)
@Composable fun PixelImage(name: String, modifier: Modifier = Modifier, frame: Int? = null, tint: Color? = null, ramp: List<Color>? = null, playing: Boolean = true)
enum class MascotMood { IDLE, THINKING, TALKING, HAPPY, SLEEPING, ERROR, WAVING }
@Composable fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 96.dp)
enum class HearthState { COLD, KINDLING, BLAZING, SMOKE }
/** Full-width animated home scene: sky, stars, mountains, fire ring, embers, mascot. progress 0..1 is used while KINDLING. */
@Composable fun HearthScene(state: HearthState, modifier: Modifier = Modifier, progress: Float? = null, mood: MascotMood? = null /* null = derived from state */)
@Composable fun Fire(modifier: Modifier = Modifier, intensity: Float = 1f)
@Composable fun Embers(modifier: Modifier = Modifier, density: Float = 1f)
enum class AvatarId { NEWT, WOLF, FOX, OWL, EAGLE, GOAT, PARTRIDGE, BEE, LYNX, CAT, ROBOT, SUN, MOUNTAIN, HORSE, BEAR, DRAGON }
@Composable fun Avatar(id: AvatarId, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, animated: Boolean = false)
fun avatarIdOf(name: String?): AvatarId   // safe parse, falls back to NEWT
```
Also: the launcher icon (adaptive, with monochrome layer) and notification icon, generated from
`.pxl` by `tools/pxl-to-vector.ps1` into `app/src/main/res/drawable/`.

## Package `dev.pocketagent.app.ui.theme` and `ui.components` (owner: agent DESIGN)

```kotlin
enum class ThemeId { NEWROZ, ZAGROS, SEMENDER, NERGIZ, KAWA, ROJ }
enum class AccentId { THEME, FLAME, SAFFRON, MEADOW, SKY, ROSE, NEWT, SNOW }   // THEME = the theme's own accent
enum class DarkMode { SYSTEM, DARK, LIGHT }        // themes have a dark and a light variant
enum class AnimLevel { FULL, REDUCED, OFF }
enum class BubbleStyle { PIXEL, SOFT }
data class Appearance(
    val theme: ThemeId = ThemeId.NEWROZ, val accent: AccentId = AccentId.THEME,
    val darkMode: DarkMode = DarkMode.SYSTEM, val textScale: Float = 1f /* 0.85..1.3 */,
    val animations: AnimLevel = AnimLevel.FULL, val showMascot: Boolean = true,
    val bubbleStyle: BubbleStyle = BubbleStyle.PIXEL, val language: String = "" /* "", "en", "tr", "fr" */,
    val pixelFontTitles: Boolean = true,
)
class AppearanceStore private constructor(context: Context) {
    val appearance: StateFlow<Appearance>
    fun update(change: (Appearance) -> Appearance)
    companion object { fun get(context: Context): AppearanceStore }
}
data class PocketColors(
    val isDark: Boolean,
    val bg: Color, val bgDeep: Color, val surface: Color, val surfaceHigh: Color, val outline: Color,
    val text: Color, val textMuted: Color, val accent: Color, val accentSoft: Color, val onAccent: Color,
    val fireLow: Color, val fireMid: Color, val fireHigh: Color, val ember: Color,
    val userBubble: Color, val onUserBubble: Color, val botBubble: Color, val onBotBubble: Color,
    val toolBubble: Color, val onToolBubble: Color, val codeBg: Color, val error: Color, val success: Color, val warning: Color,
)
fun paletteFor(theme: ThemeId, dark: Boolean, accent: AccentId): PocketColors
/** Wraps content in MaterialTheme (colour scheme mapped from PocketColors) + the locals below. */
@Composable fun PocketTheme(appearance: Appearance, content: @Composable () -> Unit)
object Pocket { val colors: PocketColors @Composable get; val appearance: Appearance @Composable get; val anim: AnimLevel @Composable get }
```
Components (`dev.pocketagent.app.ui.components`), all themed by `Pocket.colors`:
```kotlin
enum class PixelIcon { HEARTH, CHATS, BOTS, SCROLL, TERMINAL, GEAR, SEND, STOP, PLUS, BACK, MORE, COPY, EDIT, TRASH, PIN, GROUP, TOOL, MEMORY, SKILL, SOUL, SEARCH, REFRESH, PLAY, CHECK, CLOSE, PALETTE, GLOBE, BUG, DOWN, UP, SPARK, SHARE, USER, KEY, INFO, MIC }
@Composable fun PixelIconView(icon: PixelIcon, modifier: Modifier = Modifier, tint: Color = Pocket.colors.text, size: Dp = 24.dp)
@Composable fun PixelText(text: String, modifier: Modifier = Modifier, color: Color = Pocket.colors.text, pixel: Dp = 2.dp)  // bitmap font: A-Z a-z 0-9 punctuation + ÇŞĞÜÖİIÊÎÛ çşğüöıêîû ÉÈÀÂ éèàâ; unknown chars fall back gracefully
@Composable fun PixelPanel(modifier: Modifier = Modifier, color: Color = Pocket.colors.surface, border: Color = Pocket.colors.outline, corner: Int = 2 /* stepped corner in pixels */, pixel: Dp = 2.dp, content: @Composable BoxScope.() -> Unit)
enum class ButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }
@Composable fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: PixelIcon? = null, kind: ButtonKind = ButtonKind.PRIMARY, enabled: Boolean = true)
@Composable fun PixelIconButton(icon: PixelIcon, onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = Pocket.colors.text, enabled: Boolean = true)
@Composable fun PocketTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})
@Composable fun PocketTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String? = null, singleLine: Boolean = false, minLines: Int = 1, maxLines: Int = Int.MAX_VALUE, monospace: Boolean = false, password: Boolean = false)
@Composable fun PocketChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: PixelIcon? = null)
@Composable fun SectionHeader(text: String, modifier: Modifier = Modifier)
@Composable fun PocketSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null)
@Composable fun TypingIndicator(modifier: Modifier = Modifier, color: Color = Pocket.colors.fireMid)   // three hopping embers
@Composable fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null)  // includes a small Mascot
@Composable fun PocketDialog(title: String, onDismiss: () -> Unit, confirm: (@Composable () -> Unit)? = null, dismiss: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)
```

## Package `dev.pocketagent.app.data` + `hermes` (owner: agent ENGINE)

```kotlin
data class Bot(val id: String, val name: String, val avatar: String /* AvatarId name */, val color: Long /* ARGB */,
    val tagline: String, val systemPrompt: String, val model: String = "" /* "" = Hermes default */,
    val effort: String = "", val builtIn: Boolean = false, val createdAt: Long = System.currentTimeMillis())
enum class Role { USER, BOT, TOOL, NOTE, ERROR }
data class Message(val id: String, val convId: String, val role: Role, val botId: String?, val text: String,
    val createdAt: Long, val toolName: String? = null, val toolStatus: String? = null,
    val streaming: Boolean = false, val durationMs: Long? = null)
enum class GroupMode { ALL_IN_TURN, MENTION_ONLY }   // group: every bot answers in order / only @mentioned (or the first bot)
data class Conversation(val id: String, val title: String, val botIds: List<String>, val createdAt: Long, val updatedAt: Long,
    val pinned: Boolean = false, val groupMode: GroupMode = GroupMode.ALL_IN_TURN, val lastPreview: String = "") { val isGroup: Boolean get() = botIds.size > 1 }
object BotPresets { val templates: List<Bot>; const val HERMES_ID = "hermes" }
class ChatRepository(context: Context) {
    val bots: StateFlow<List<Bot>>; val conversations: StateFlow<List<Conversation>>   // pinned first, then newest
    fun messages(convId: String): StateFlow<List<Message>>
    fun bot(id: String): Bot?; fun conversation(id: String): Conversation?
    fun saveBot(bot: Bot); fun deleteBot(id: String) /* no-op for HERMES_ID */; fun newId(): String
    fun createConversation(botIds: List<String>, title: String? = null): Conversation
    fun renameConversation(id: String, title: String); fun setPinned(id: String, pinned: Boolean)
    fun setGroupMode(id: String, mode: GroupMode); fun setConversationBots(id: String, botIds: List<String>)
    fun deleteConversation(id: String); fun deleteMessage(convId: String, messageId: String); fun clearConversation(convId: String)
    fun search(query: String): List<Pair<Conversation, Message>>
}
class ChatEngine(...) {
    val busy: StateFlow<Set<String>>                 // convIds generating now
    val speaking: StateFlow<Map<String, String>>     // convId -> botId answering now
    fun send(convId: String, text: String, onlyBotIds: List<String> = emptyList())
    fun regenerateLast(convId: String); fun editAndResend(convId: String, messageId: String, newText: String)
    fun stop(convId: String)
}
// package dev.pocketagent.app.hermes
enum class HermesDoc { SOUL, MEMORY, USER, CONFIG }        // CONFIG is read-only (regenerated on start)
data class SkillInfo(val name: String, val description: String, val category: String, val path: String)
class HermesHome(...) {
    fun read(doc: HermesDoc): String?; fun write(doc: HermesDoc, text: String) /* throws for CONFIG */
    fun docInfo(doc: HermesDoc): String   // path inside the guest + size, for display
    fun listSkills(): List<SkillInfo>; fun readSkill(path: String): String; fun writeSkill(path: String, text: String)
    fun tailLog(kind: LogKind, maxBytes: Int = 200_000): String
}
enum class LogKind { APP, RUNTIME, API }
data class TermLine(val text: String, val kind: TermKind)
enum class TermKind { CMD, OUT, ERR, INFO }
class TerminalSession(...) {
    val lines: StateFlow<List<TermLine>>; val running: StateFlow<Boolean>; val history: List<String>
    fun run(command: String); fun cancel(); fun clear()
}
```
Access from UI: `PocketApp.from(context).chat` (ChatRepository), `.engine` (ChatEngine),
`.hermesHome` (HermesHome), `.terminal` (TerminalSession), `.runtime.state` (existing
`StateFlow<RuntimeState>`). Start/stop/setup stay in `MainViewModel` (existing).

## Navigation and screens (owners: agents CHAT and CONTROL)

```kotlin
// package dev.pocketagent.app.ui   (file ui/Nav.kt, owner CHAT)
sealed interface Route {
    data object Home : Route; data object Chats : Route; data class Chat(val convId: String) : Route
    data object Bots : Route; data class BotEditor(val botId: String?) : Route
    data object Hermes : Route; data object Terminal : Route; data class Settings(val section: String? = null) : Route
}
class Navigator { val current: Route; fun go(route: Route); fun back(): Boolean; fun tab(route: Route) /* resets stack to a root tab */ }
val LocalNavigator: ProvidableCompositionLocal<Navigator>
```
Screen entry points (no parameters besides those shown; they read services/ViewModels themselves):
- CHAT owns: `ui/home/HomeScreen()`, `ui/chat/ChatListScreen()`, `ui/chat/ChatScreen(convId: String)`.
- CONTROL owns: `ui/bots/BotsScreen()`, `ui/bots/BotEditorScreen(botId: String?)`,
  `ui/hermes/HermesScreen()`, `ui/hermes/TerminalScreen()`, `ui/settings/SettingsScreen(section: String?)`.
  Settings sections: `"provider"`, `"appearance"`, `"language"`, `"runtime"`, `"about"`.
Bottom tabs (CHAT): Home, Chats, Bots, Hermes, Settings.
