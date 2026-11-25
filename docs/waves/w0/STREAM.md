# Wave 0, area STREAM: streaming without stutter (plan feature 0.9, findings A4, A5)

Draft contract section. One mid-size agent builds all of it in one session, without Gradle. Everything is in
package `dev.pocketagent.app.ui.chat` unless a path says otherwise. Line numbers were taken from the tree at
commit `344829f`. Baseline facts read from the code: `ChatScreen.kt` is 1,129 lines (`ChatBody` = 149-1084, 12
`collectAsState` calls, 7 `Toast.makeText`); the only caller of `ChatScreen(convId)` is `ui/AppRoot.kt:409`; the only
caller of `MessageRow` and `PendingRow` is `ChatScreen.kt`; `parseMarkdown`, `markdownToPlain`, `MdBlock`, `MdSpan`
are used by `service/Presence.kt:52`, `ChatListScreen.kt:269`, `StarredScreen.kt:113` and
`VoiceAndHighlightTest.kt:167` and must keep their signatures and results.

## 1. Scope (all core; Wave 0 has no extras)

| # | Work item | Plan words |
|---|---|---|
| W1 | Engine: a trailing update 60 ms after the last piece of text, with a lock so the timer thread can read the text (`data/StreamPush.kt`, `ChatEngine.kt` Turn only) | "trailing update" |
| W2 | `StreamSmoother`: pure release-rate algorithm (speed = backlog / 0.35 s, floor 40 chars/s, flush in 150 ms, safe cuts) | "StreamSmoother" |
| W3 | Incremental Markdown: parser records block line ranges; `MarkdownStream` keeps finished blocks, re-parses only the open tail; property test | "incremental Markdown with a property test" |
| W4 | `StreamingMarkdown` composable: smoother driven per frame, stream-parsed blocks, fade of the last 8 characters; used by the bot bubble while it streams | design language 8.2 |
| W5 | Split `ChatScreen.kt` into `ChatScreenState` + `ChatTopArea` + `ChatMessageList` + `ChatInputArea` + `ChatDialogs`; only the list observes messages | "split into a state holder and four composables" |
| W6 | Tool output opens in `ToolOutputSheet` (DESIGN's `PocketSheet`); the scroll box inside the list goes (`ChatMessages.kt:515-525`) | "tool output opens in a sheet" |
| W7 | Streaming row is not animated as a list item (and, see section 11 risk 2, no row animates its placement while a message streams) | "streaming row is not animated" |
| W8 | testTags, unit tests (section 8) | robot |

**Cut first if the session runs out** (in this order): (1) the fade of the last eight characters (`fadeLast`, W4):
text still streams smoothly, only the soft edge is missing. (2) `ToolOutputChunks` and the chunked list in the
sheet (W6): fall back to one `SelectionContainer { Text }` in `verticalScroll`, capped at 20,000 characters; a huge
output opens slower. (3) The `RowLayout` / `ChatRowActions` restructure of `MessageRow` (W5): keep today's
parameter list plus one new `onOpenToolOutput`, and wrap the lambdas in `remember` inside `ChatMessageList`; without
stable inputs the rows recompose on every update again, which is the jank A5 describes. Never cut W1, W3, W5.

## 2. Files (STREAM owns these exclusively)

| File | Change |
|---|---|
| `data/ChatEngine.kt` | `Turn` only (lines 453-671) and the companion constant (676): trailing push, lock. Nothing else |
| `data/StreamPush.kt` | NEW. Pure timing policy (needs a one-line ownership amendment, section 7) |
| `ui/chat/ChatScreen.kt` | Shrinks to about 150 lines: `ChatScreen` (unchanged body), `ChatBody` (layout of the four composables), `LocalChatAvatarModifier`. `MembersDialog` moves to `ChatDialogs.kt` |
| `ui/chat/ChatScreenState.kt` | NEW. State holder, `rememberChatScreenState`, `ChatShape`, `SearchView`, `RowGrouping`, effects (drafts, share inbox, recognizer cleanup) |
| `ui/chat/ChatSlashActions.kt` | NEW. `ChatScreenState.executeSlash` (today lines 528-680) as an extension function |
| `ui/chat/ChatTopArea.kt` | NEW. Top bar, search bar, group bar, mode and override banners (today 731-788) |
| `ui/chat/ChatMessageList.kt` | NEW. The list, stick-to-newest, find/deep-link scrolling, empty state, jump button (today 463-521, 789-868) |
| `ui/chat/ChatInputArea.kt` | NEW. Approval card, `ChatInputBar` call, voice input with its launchers (today 270-374, 869-895) |
| `ui/chat/ChatDialogs.kt` | NEW. All menus, sheets and dialogs, the attach and export launchers, `MembersDialog` (today 376-461, 898-1084, 1086-1129) |
| `ui/chat/ToolOutputSheet.kt` | NEW. `ToolOutputSheet`, `ToolOutputChunks` |
| `ui/chat/StreamSmoother.kt` | NEW. Pure Kotlin |
| `ui/chat/MarkdownStream.kt` | NEW. Pure Kotlin incremental parser |
| `ui/chat/StreamingMarkdown.kt` | NEW. The composable that joins smoother, stream and blocks |
| `ui/chat/Markdown.kt` | Parser records each top-level block's line range (`parseMarkdownLines`); `parseMarkdown` and `markdownToPlain` behave exactly as before |
| `ui/chat/MarkdownView.kt` | `MarkdownText` keeps its signature; blocks drawn by new `MdBlocks`; `buildSpans`/`rememberAnnotated` get `fadeLast` |
| `ui/chat/ChatMessages.kt` | `MessageRow` new signature, tool card opens the sheet, bot bubble uses `StreamingMarkdown`, testTags |
| `res/values/strings_stream.xml` | NEW, prefix `stm_`, English only |
| `app/src/test/java/dev/pocketagent/app/ui/chat/*Test.kt`, `.../data/StreamPushTest.kt`, `ChatEngineTrailingTest.kt` | NEW, section 8 |

Not mine and called unchanged: `ChatInput.kt`, `ChatQuickBar.kt`, `ChatCommon.kt`, `ChatSearch.kt`, `SlashPalette.kt`,
`AttachmentUi.kt`, `SkillPickerSheet.kt`, `ChatLogic.kt`, `ChatListScreen.kt`, `StarredScreen.kt` (section 4).

## 3. Shared symbols STREAM provides

### 3.1 `data/StreamPush.kt` (package `dev.pocketagent.app.data`)

```kotlin
internal class StreamPush(val intervalMs: Long = 60L, val trailingMs: Long = 60L) {
    enum class Action { PUSH, ARM, NONE }
    /** A piece of text or reasoning arrived at [nowMs]. PUSH: write now (>= intervalMs since the last write). ARM: remember it and
     *  start the one trailing timer (none was running). NONE: remembered, the running timer will see it. */
    fun onChange(nowMs: Long): Action
    /** The trailing timer woke at [nowMs]: -1 = nothing pending (timer ends); 0 = write now (timer ends); n > 0 = last change was
     *  too recent, sleep n ms and call again (n = lastChange + trailingMs - nowMs). */
    fun onTimer(nowMs: Long): Long
    /** A write happened outside the policy (a forced push): nothing is pending any more. */
    fun onForced(nowMs: Long)
}
```
Initial `lastPushAt = Long.MIN_VALUE / 4` (so the first piece pushes at once, as today). Not thread-safe: the caller holds `Turn.lock`.

### 3.2 `ChatEngine.Turn` change (exact edit list, all inside `ChatEngine.kt`)

- Line 460 `private var lastPush = 0L` is replaced by `private val lock = Any()`, `private val gate = StreamPush(PUSH_INTERVAL_MS, TRAILING_PUSH_MS)`, `private var trailing: Job? = null`. Imports `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`.
- `push(force)` (478-485) becomes: `val id = bubbleId ?: return; val now = System.nanoTime() / 1_000_000; if (force) { gate.onForced(now); writeNow(id); return }; when (gate.onChange(now)) { PUSH -> writeNow(id); ARM -> armTrailing(); NONE -> Unit }`. `writeNow(id)` is the old tail of `push` (copy `text`/`reasoning` to strings, `repo.updateMessage(convId, id) { it.copy(text = t, reasoning = r) }`).
- `armTrailing()`: `trailing = scope.launch { var wait = TRAILING_PUSH_MS; while (true) { delay(wait); val next = synchronized(lock) { val n = gate.onTimer(nowMs()); if (n == 0L) bubbleId?.let { writeNow(it) }; n }; if (next <= 0L) break; wait = next } }`.
- Locking (needed because the timer thread reads the `StringBuilder`s): rename today's `fun onEvent(ev)` (531) to `private fun handle(ev: ChatEvent)` and add `fun onEvent(ev: ChatEvent) = synchronized(lock) { handle(ev) }`; rename `fun finish(outcome)` (635) to `private fun finishLocked(...)` and add `fun finish(outcome: ChatOutcome): Boolean = synchronized(lock) { trailing?.cancel(); trailing = null; finishLocked(outcome) }`. Lock order is always `Turn.lock` then `ChatRepository.lock`; the repository never calls back into a Turn, so no inversion.
- Companion (676): add `private const val TRAILING_PUSH_MS = 60L`. The call sites 543, 550, 559 stay `push(false)`.
- Behaviour contract: text of the last piece is in the repository at most `TRAILING_PUSH_MS` plus scheduling after it arrived, even when no further piece comes; during a steady stream writes are still at most one per 60 ms; the final `closeBubble` write is unchanged.

### 3.3 `StreamSmoother` (pure Kotlin, no Android, no Compose)

```kotlin
class StreamSmoother(
    val backlogSeconds: Double = 0.35, val minCharsPerSecond: Double = 40.0, val flushMs: Long = 150L,
    val maxStepSeconds: Double = 0.1, val maxMarkerRun: Int = 8,
) {
    /** Characters released so far; always a safe cut of the last target seen, 0..target.length. */
    var shown: Int; private set
    fun reset(shown: Int = 0)                                   // also clears carry, last frame time, finish time
    fun hasBacklog(targetLength: Int): Boolean                   // shown != targetLength
    /** One frame. Returns the new [shown]. [target] only ever grows, except that the engine may shorten it once at the end. */
    fun step(nowNanos: Long, target: CharSequence, finished: Boolean): Int
    companion object {
        /** A row that is already older than [replayWindowMs] when first drawn shows everything at once; a fresh one starts at 0. */
        fun initialShown(textLength: Int, ageMs: Long, replayWindowMs: Long = 1_500L): Int
        /** Nearest allowed cut at or after [proposed]: not between a high and a low surrogate; unless [finished], not inside a run of
         *  the same marker character from "*_~" and backtick (extended to the run's end, at most [maxMarkerRun] more), and not
         *  inside a run of '#' that starts a line. Never above target.length. */
        fun safeCut(target: CharSequence, proposed: Int, finished: Boolean, maxMarkerRun: Int = 8): Int
        /** Alpha of the k-th character from the end, k = 0 the newest. */
        val FADE_ALPHAS: FloatArray   // 0.15, 0.25, 0.35, 0.5, 0.65, 0.8, 0.9, 0.97
    }
}
```
`step` algorithm, exactly: (1) if `shown > len` then `shown = len`, carry = 0. (2) first call with `finished = true` stores `finishedAt = now`; a call with `finished = false` clears it. (3) `dt = lastNanos unset ? 0 : ((now - lastNanos) / 1e9).coerceIn(0.0, maxStepSeconds)`; `lastNanos = now`. (4) `backlog = len - shown`; if `backlog <= 0`: carry = 0, **lastNanos = unset** (so the first frame after an idle pause releases nothing), return. (5) if finished and `now - finishedAt >= flushMs` ms: `shown = len`, return. (6) `speed = max(backlog / backlogSeconds, minCharsPerSecond)`; if finished: `speed = max(speed, backlog / max(remainingFlushSeconds, 0.016))`. (7) `carry += speed * dt`; `whole = floor(carry)`; if > 0: `carry -= whole`, `shown = safeCut(target, min(shown + whole, len), finished)`.

### 3.4 Incremental Markdown

`Markdown.kt` additions (the existing public API is unchanged):

```kotlin
internal class MdParsed(val blocks: List<MdBlock>, val startLines: IntArray, val endLines: IntArray)  // line indexes of the top-level blocks
/** Same result as parseMarkdown. endLines[i] is the first line the block did NOT consume (the line that ended it, or lines.size when the
 *  input ended it). */
internal fun parseMarkdownLines(lines: List<String>): MdParsed
```
Implementation: `parseBlocks(lines, ranges: MutableList<IntArray>? = null)`; the Quote recursion passes null. Record `[start, i]` after each top-level block is appended (paragraph: its first line index, and `i` at the moment `flushPara()` runs, including the end-of-input flush). `parseMarkdown(src) = parseMarkdownLines(normalized.split('\n')).blocks`, byte-for-byte the same blocks as today.

```kotlin
// MarkdownStream.kt. Not thread-safe (main thread); no Android imports.
@Immutable
class MdSnapshot internal constructor(val stable: List<MdBlock>, val tail: List<MdBlock>) {
    val blocks: List<MdBlock>            // stable + tail (a new list; for tests, not for the UI)
    companion object { val EMPTY: MdSnapshot }
}
class MarkdownStream {
    val snapshot: MdSnapshot
    val length: Int                      // raw characters appended so far
    internal var parsedLines: Long       // lines handed to the parser so far (test counter)
    fun append(piece: String): MdSnapshot
    fun replace(text: String): MdSnapshot                       // start over with text
    /** Brings the stream to exactly full[0 until upTo]: appends the new piece when full still starts with what was appended
     *  (compared character by character), otherwise replace(). Idempotent: safe to call during composition. */
    fun sync(full: String, upTo: Int = full.length): MdSnapshot
}
```
Algorithm (exact). State: `raw` (StringBuilder of the raw text), `heldCr`, `stable` (ArrayList, append-only), `tail` (String: normalized text from the first unfinished block's first line on). `append`: (a) normalize the piece with carry: if `heldCr` and the piece starts with `'\n'` drop that `'\n'`; `heldCr = false`; `s = piece.replace("\r\n","\n").replace('\r','\n')`; `heldCr = piece.endsWith('\r')`. (b) `tail += s`. (c) Only if `s` contains `'\n'`: split `tail` at `'\n'`; the last element is the partial line, the others are the complete lines (m of them); `p = parseMarkdownLines(complete)`; let k = number of blocks with `endLines[i] < m` (they are a prefix); move `p.blocks[0 until k]` to `stable` (publish a new list instance only when it grew); `tail` = lines from `startLines[k]` (or from `m` when k == all blocks) joined with `'\n'`, partial line included. (d) always: `tailBlocks = parseMarkdownLines(tail.split('\n')).blocks`; snapshot = (stable, tailBlocks). `parsedLines` counts both parses. Property that the tests enforce: for every prefix of the input, `snapshot.blocks == parseMarkdown(prefix)` and `stable` only ever grows.

### 3.5 `StreamingMarkdown.kt`

```kotlin
@Composable
internal fun StreamingMarkdown(
    text: String,                       // msg.text: everything received so far
    streaming: Boolean,                 // msg.streaming
    createdAtMs: Long,                  // msg.createdAt (age decides initialShown)
    modifier: Modifier = Modifier,
    color: Color = Pocket.colors.onBotBubble,
    fontSize: TextUnit = 15.sp,
    onRunCode: ((lang: String, code: String) -> Unit)? = null,
)
```
Behaviour: `remember` one `StreamSmoother` (reset to `initialShown(text.length, now - createdAtMs)`), one `MarkdownStream`, `shownState = mutableIntStateOf(smoother.shown)`, `rememberUpdatedState` of `text` and `!streaming`. Level = `Pocket.anim`. OFF: `upTo = text.length`, no frame loop. FULL and REDUCED: `LaunchedEffect(level)` loop: `snapshotFlow { shownState.intValue != textNow.value.length }.first { it }` (no frame callbacks while idle), then `while (shownState.intValue != textNow.value.length) { val t = withFrameNanos { it }; shownState.intValue = smoother.step(t, textNow.value, finishedNow.value) }`. In the body: `upTo = min(shownState.intValue, text.length)`; `snap = stream.sync(text, upTo)`; draw `MdBlocks(snap.stable, snap.tail, color, fontSize, onRunCode, fadeLast = if (level == FULL && (streaming || upTo < text.length)) 8 else 0)`. The state read happens in this composable only, so only it recomposes per frame. REDUCED keeps the smoothing but no fade.

`MarkdownView.kt`: `internal fun MdBlocks(stable, tail, color, fontSize, onRunCode, fadeLast: Int)` draws a `Column(spacedBy(6.dp))`; every block is `key(absoluteIndex)` (stable blocks 0 until n, tail blocks n + i) so a block moving from tail to stable keeps its composition. Only the last tail block, when Paragraph/Heading/Item, gets `fadeLast`: `buildSpans(spans, codeBg, linkColor, uri, fadeLast, baseColor)` adds, after building, one `addStyle(SpanStyle(color = baseColor.copy(alpha = baseColor.alpha * FADE_ALPHAS[k])), len - 1 - k, len - k)` for k in 0 until min(fadeLast, len). `rememberAnnotated` keys on `fadeLast` and `baseColor`. `MarkdownText(text, ...)` (static: finished messages, previews elsewhere) keeps `remember(text) { parseMarkdown(text) }`.

### 3.6 The chat state holder and its four composables (internal to `ui.chat`; written so four files agree)

```kotlin
@Immutable internal data class ChatShape(val count: Int, val lastId: String?, val lastBotId: String?, val lastBotIsLast: Boolean, val lastStreaming: Boolean)
internal fun chatShapeOf(msgs: List<Message>): ChatShape        // lastBotId/lastBotIsLast/lastId exactly as ChatScreen.kt 519-521; lastStreaming = msgs.lastOrNull()?.streaming == true
@Immutable internal data class SearchView(val active: Boolean, val query: String, val matchIds: List<String>, val position: Int, val currentId: String?, val outlineId: String?)
internal fun searchViewOf(active: Boolean, query: String, matches: List<String>, pos: Int, flashId: String?): SearchView   // position = -1 when no match else pos.coerceIn(0, lastIndex); currentId only when active; outlineId = if (active) currentId else flashId (lines 482-485)
@Immutable internal data class RowGrouping(val showHeader: Boolean, val isLastInRun: Boolean, val showDay: Boolean)
internal fun groupingOf(older: Message?, m: Message, newer: Message?, sameDay: (Long, Long) -> Boolean = ::sameCalendarDay): RowGrouping   // lines 818-821
@Immutable internal data class RowLayout(val grouping: RowGrouping, val showName: Boolean, val isGroup: Boolean, val canRetry: Boolean, val canRegenerate: Boolean, val isSpeaking: Boolean, val highlighted: Boolean)
@Stable internal class ChatRowActions(
    val onLongPress: (Message) -> Unit, val onRetry: () -> Unit, val onCopy: (String) -> Unit, val onSpeak: (Message) -> Unit,
    val onFork: (Message) -> Unit, val onRunCode: (lang: String, code: String) -> Unit, val onOpenImage: (Attachment) -> Unit,
    val onOpenMemory: () -> Unit, val onChatWithBot: (String) -> Unit, val onAddBotToChat: (String) -> Unit,
    val onEditBot: (String) -> Unit, val onOpenToolOutput: (Message) -> Unit,
)   // built once with remember(state) in ChatMessageList; every lambda reads current values from the state at call time
@Composable internal fun MessageRow(msg: Message, bot: Bot?, layout: RowLayout, actions: ChatRowActions,
    botsById: Map<String, Bot>, convBotIds: List<String>, modifier: Modifier = Modifier)       // replaces ChatMessages.kt:104-131
```

```kotlin
@Stable
internal class ChatScreenState(val convId: String, val app: PocketApp, val scope: CoroutineScope, val listState: LazyListState, initialInput: String) {
    val repo: ChatRepository get() = app.chat
    val engine: ChatEngine get() = app.engine
    val messagesFlow: StateFlow<List<Message>>            // repo.messages(convId): initialised FIRST (init order); the only handle to the message list
    fun messagesNow(): List<Message> = messagesFlow.value // for event handlers: reads, never observes
    var context: Context; var haptic: HapticFeedback; var conversation: Conversation    // refreshed by ChatBody in a SideEffect (language changes, new conv)
    val shape: State<ChatShape>                           // messagesFlow.map(::chatShapeOf).distinctUntilChanged() collected into a State
    val matches: State<List<String>>                      // while searching: findMatchingMessageIds(messages, searchQuery), distinctUntilChanged; else empty
    // input   : var input: String; var editing: Message?; var preEditDraft: String; var pendingAttachments: List<Attachment>
    //           var pendingCameraFile: File?; var spark: Int; var celebrateTrigger: Int
    // voice   : var listening: Boolean; var voiceBaseText: String; var activeRecognizer: SpeechRecognizer?
    // dialogs : var menu, showControls, showAttach, rename, confirmDelete, confirmClear, showMembers, exportMenu: Boolean
    //           var skillPickerQuery: String?; var pendingTerminalCmd: String?; var previewImage: Attachment?
    //           var actionsFor: Message?; var selectFor: Message?; var toolOutputFor: String?   // NEW: message id
    // search  : var searching: Boolean; var searchQuery: String; var searchPos: Int; var flashId: String?
    // list    : var stick: Boolean = true
    // (every var above is `by mutableStateOf`, private set only where the owner file says so; names are today's local names)
    fun openSearch(q: String); fun closeSearch(); fun cancelVoice()          // cancel/destroy recognizer, listening = false
    fun doSend(); fun executeSlash(p: SlashCommands.ParsedSlash)              // bodies moved verbatim from lines 682-728 and 528-680
    fun doFork(upToId: String?); fun attachImport(loader: suspend () -> Attachment)
    fun scrollToNewest()                                                      // stick = true; scope.launch { listState.animateScrollToItem(0) }
    fun say(text: String)                                                     // the ONLY Toast in STREAM's files; later replaced by DESIGN's PetNote
    fun toastNotSent()                                                        // say(context.getString(R.string.ui_send_not_accepted))
}
@Composable internal fun rememberChatScreenState(conv: Conversation): ChatScreenState
```
`rememberChatScreenState` is `remember(conv.id)` and also hosts these effects: the draft saver and its `DisposableEffect` (today 208-216), the share-inbox effect (219-233) and the recognizer cleanup (239-244). The `LifecycleResumeEffect` of lines 174-180 stays in `ChatBody`.

The four composables (each collects only what it needs; none but `ChatMessageList` collects `messagesFlow`):

```kotlin
@Composable internal fun ChatTopArea(state: ChatScreenState, conv: Conversation, shape: ChatShape, search: SearchView)
@Composable internal fun ChatMessageList(state: ChatScreenState, conv: Conversation, search: SearchView, modifier: Modifier = Modifier)
@Composable internal fun ChatInputArea(state: ChatScreenState, conv: Conversation)
@Composable internal fun ChatDialogs(state: ChatScreenState, conv: Conversation, shape: ChatShape)
```

| Composable | Observes | Does not observe |
|---|---|---|
| `ChatScreen` | `repo.conversations`, `repo.loaded` | messages |
| `ChatBody` | `state.shape`, `state.matches`, `state.searching/searchQuery/searchPos/flashId` | messages |
| `ChatTopArea` | `engine.busy/status/speaking`, `repo.bots`, `shape.count` | messages |
| `ChatMessageList` | **`state.messagesFlow`**, `engine.busy`, `engine.speaking`, `app.voice.speakingId`, `repo.bots`, `ChatScrollTarget.pending`, `state.stick/flashId/celebrateTrigger`, `Pocket.anim` | `state.input` |
| `ChatInputArea` | `vm.state`, `engine.busy/status/approvals`, `app.presencePrefs.settings`, `state.input/editing/pendingAttachments/listening/spark`, appearance language | messages |
| `ChatDialogs` | the dialog flags, `engine.busy`, appearance, `shape`; `state.messagesNow()` at event time and when a sheet opens | messages; `ToolOutputSheet` observes the single message it shows |

`ChatBody` (what stays in `ChatScreen.kt`): `val state = rememberChatScreenState(conv)`; `SideEffect` that sets `state.context/haptic/conversation`; `LifecycleResumeEffect(convId)` (today 174-180); `BackHandler(enabled = state.searching) { state.closeSearch() }`; `val shape by state.shape`; `val search = searchViewOf(state.searching, state.searchQuery, state.matches.value, state.searchPos, state.flashId)`; `Column(Modifier.fillMaxSize().testTag("chat.screen")) { ChatTopArea; ChatMessageList(modifier = Modifier.weight(1f).fillMaxWidth()); ChatInputArea }`; then `ChatDialogs`. Also `val LocalChatAvatarModifier = staticCompositionLocalOf<Modifier> { Modifier }`, applied to the `AvatarStack` in `ChatTopArea` (a hook for NAV's shared element, if S3 passes; default does nothing).

`ChatMessageList` rules (it computes `val shape = chatShapeOf(msgs)` itself from the messages it observes): `showPending = generating && (shape.count == 0 || !shape.lastStreaming)`; row `Modifier`: `if (Pocket.anim == OFF) Modifier else if (m.streaming) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null) else Modifier.animateItem(placementSpec = if (shape.lastStreaming) null else <today's default>)`; the `"pending"` item is never animated. Everything else (stick logic, find scroll, deep link scroll, jump button, `NewrozCelebrationOverlay`, empty state with `EmptyChatQuickActions`) moves unchanged.

### 3.7 `ToolOutputSheet.kt`

```kotlin
@Composable internal fun ToolOutputSheet(messageId: String, messages: StateFlow<List<Message>>, onDismiss: () -> Unit)
internal object ToolOutputChunks {
    const val MAX_CHARS = 60_000; const val CHUNK_CHARS = 2_000
    /** text.take(maxChars) (one char less when that would split a surrogate pair) cut into pieces of at most [chunk] chars, each ending
     *  after a newline when one exists in the last quarter of the piece. join("") of the result == the kept prefix. "" -> emptyList(). */
    fun split(text: String, maxChars: Int = MAX_CHARS, chunk: Int = CHUNK_CHARS): List<String>
}
```
The sheet watches `messages.map { list -> list.firstOrNull { it.id == messageId } }.distinctUntilChanged()` (live while the tool runs; dismisses itself when the message disappears). Content: status line (existing `ui_chat_tool_running/done/stopped`), a `LazyColumn(Modifier.weight(1f, fill = false))` of chunks in monospace 12 sp inside one `SelectionContainer`, a truncation line when text is longer than `MAX_CHARS`, a Copy button (`copyToClipboard(context, msg.text)`) and Close.

## 4. Symbols STREAM consumes

| From | Symbol (exact, as today unless marked NEW) |
|---|---|
| DESIGN | **NEW** `@Composable fun PocketSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit)` in `ui/components/PocketSheet.kt`. The content slot gets a bounded height (at most 90 % of the screen) and is **not** wrapped in `verticalScroll`, so a `LazyColumn` with `weight(1f, fill = false)` works inside it. Dismiss by scrim, drag and back |
| DESIGN | Unchanged: `Pocket.colors`, `Pocket.appearance`, `Pocket.anim: AnimLevel` (the effective level; Wave 0 makes it respect the system setting and battery saver, same name), `AnimLevel`, `BubbleStyle`, `ThemeId`, `AppearanceStore.get(ctx).appearance`, `PocketTopBar(title, onBack, subtitle, leading, actions)`, `PixelIconButton(icon, onClick, modifier, contentDescription, tint, enabled)`, `PixelIconView`, `PixelButton`, `ButtonKind`, `PixelPanel`, `PocketChip(label, selected, onClick, modifier, icon)`, `PocketDialog`, `EmptyState`, `TypingIndicator`, `PixelIcon` entries used today |
| NAV | `ChatScreen(convId: String)` called at `AppRoot.kt:409`; `Route.Chat`, `Route.Hermes`, `Route.Terminal`, `Route.Settings(String)`, `Route.BotEditor(String)`, `Route.Chats`, `LocalNavigator.current` with `back()`, `tab()`, `go()` |
| SWEEP (they may rewrite internals, never these signatures) | `ChatCommon.kt`: `ActionSheet(title: String?, actions: List<SheetAction>, onDismiss)`, `SheetAction(icon, label, danger = false, onClick)`, `ConfirmDialog(title, body, confirmLabel, onConfirm, onDismiss, danger = true)`, `RenameDialog(initial, onSave, onDismiss)`, `BotAvatar`, `AvatarStack(bots: List<Bot?>, size, modifier)`, `conversationTitle`, `copyToClipboard(ctx, text)`, `shareText(ctx, text)`, `sameCalendarDay`, `ChatShareInbox`, `ChatRepository.openOneToOne`, `Any?.accepted()`, `GeneratingEmber`, `currentLocale`, `clockText`, `dayLabel`, `botColor`. `ChatInput.kt`: `ChatInputBar(...)` with every parameter it has today, `ApprovalCard(approval, botName, onChoice)`, `mentionedBotIds`, `mentionName`. `ChatQuickBar.kt`: `GroupParticipantBar`, `ChatOverridesBanner`, `ChatControlsSheet`. `ChatSearch.kt`: `ChatSearchBar(query, onQueryChange, matchCount, position, onPrev, onNext, onClose, modifier)`. `SlashPalette.kt`: `ActiveModeBanner`, `EmptyChatQuickActions`, `NewrozCelebrationOverlay`. `AttachmentUi.kt`: `AttachSheet`, `ImagePreviewDialog`, `ForgedBotCard`, `MessageAttachmentsView`. `SkillPickerSheet(initialQuery, onUseSkill, onOpenInspector, onDismiss)`. `ChatLogic.kt`: `findMatchingMessageIds`, `cycleIndex`, `chatUsageTotals`, `exportMarkdown`, `exportFileName`, `ExportLabels`, `ChatScrollTarget` |
| Engine and data | `ChatEngine.send/runLoop/editAndResend/regenerateLast/stop/resolveApproval/addLocalNote/notifyMemoryUpdated`, `busy/status/speaking/approvals`, `STATUS_WAKING`; `ChatRepository` as today; `app.voice.speakingId`, `toggleSpeak`, `voiceOverride()`; `SyntaxHighlight.toTerminalCommand/isRunnable/highlight`; `SlashCommands`; `app.drafts`, `app.sounds`, `app.attachments`, `app.terminal`, `app.hermesHome`, `app.presence`, `app.presencePrefs` |

## 5. Strings: `res/values/strings_stream.xml`, prefix `stm_`, English only

| Key | English | Translator note |
|---|---|---|
| `stm_tool_card_open` | `Open full output` | Spoken click label of the tool card (screen reader only). Not drawn. |
| `stm_tool_sheet_fallback_title` | `Tool output` | Title of the sheet when the tool has no name. At most 24 characters, one line. |
| `stm_tool_sheet_live` | `Still running. This output updates as it arrives.` | Line under the title while the tool runs. Two lines at most. |
| `stm_tool_sheet_copy` | `Copy output` | Button label, at most 16 characters. |
| `stm_tool_sheet_truncated` (plurals) | other: `Only the first %1$s characters are shown (%2$s in all).` | Both numbers are already formatted with the user's number format; the plural is chosen by the first count. Add the forms your language needs. One or two lines. |

Everything else the moved code shows keeps its existing `ui_*` key (`ui_chat_tool_running`, `ui_chat_tool_done`,
`ui_chat_tool_stopped`, `ui_chat_no_output`, `ui_dlg_close`, ...). No key is renamed or removed. Moved
`ctx.getString(R.string.ui_...)` calls stay in the holder's methods so that WORDS can convert each one in place.

## 6. Manifest entries

None. (`ChatScreen` is reached through the existing route; no new activity, permission, query or receiver.)

## 7. REQUESTS to other areas

| To | File | Exact change wanted |
|---|---|---|
| DESIGN | `ui/components/PocketSheet.kt` | Provide the signature in section 4 (one sheet, bounded content height, no outer `verticalScroll`); testTag `"sheet"` on its panel |
| DESIGN | `ui/components/PocketTopBar.kt` | testTag `"top.back"` on the back button, `"top.title"` on the title; keep all parameters |
| DESIGN | `ui/components/PocketDialog.kt`, the one `ConfirmDialog` | testTags `"dialog.confirm"` and `"dialog.cancel"` on the two buttons |
| DESIGN / SWEEP | PetNote | Tell STREAM (lead) the call that replaces `Toast.makeText(ctx, text, SHORT)`; then SWEEP changes only the body of `ChatScreenState.say` in `ChatScreenState.kt` (one line) |
| NAV | `ui/AppRoot.kt` | `Modifier.semantics { testTagsAsResourceId = true }` at the root (else none of the tags below reach the robot); keep `ChatScreen(route.convId)`; if S3 passes, provide `LocalChatAvatarModifier` around it |
| SWEEP | `ui/chat/ChatInput.kt` | testTags (no other change): text field `"chat.input"`, send `"chat.send"`, stop `"chat.stop"`, attach `"chat.attach"`, mic `"chat.mic"`, edit cancel `"chat.edit.cancel"`, each approval choice button `"chat.approval.<choice>"` |
| SWEEP | `ui/chat/ChatCommon.kt` | `ActionSheet` row i gets testTag `"sheet.action.<i>"` (0-based); keep signatures of section 4 |
| ROBOT | practice model | `LONG`: the 3,000 characters must contain a heading, a bullet list, a numbered list, one fenced code block, a table, bold, inline code and a link (so the proof exercises every block kind). New keyword `PAUSE`: sends `First half of the answer.`, then no piece for 5 s, then ` Second half.` and finishes. `RUN_TOOL`: the tool result has at least 120 lines of 80 characters |
| ROBOT | scenarios | The robot has no long press: for the message menu use `adb shell input swipe x y x y 700` on the bounds of `chat.msg.bot`. A 200-message chat for the streaming budget is made by writing `files/chat/<id>.json` with `run-as` before the scenario (the twin allows it); STREAM adds no seeding code |
| LEAD | ownership table | STREAM also owns the NEW `data/StreamPush.kt`, `res/values/strings_stream.xml` and its tests; SWEEP may change the one body named above in STREAM's `ChatScreenState.kt` |

## 8. Tests (JVM unit tests only)

Kept pure: `StreamPush`, `StreamSmoother`, `MarkdownStream` and the parser (the only Android-ish import is the
`@Immutable` annotation, already on the test classpath), `chatShapeOf`, `searchViewOf`, `groupingOf` (the day test is
a parameter), `ToolOutputChunks`. Time is always a parameter (`nowNanos`, `nowMs`). No Compose, no Context.

| Class (package `dev.pocketagent.app.ui.chat` unless noted) | Tests | Proves |
|---|---|---|
| `StreamPushTest` (`dev.pocketagent.app.data`) | `firstChangePushesAtOnce`, `changeInsideIntervalArmsOneTimer`, `manyChangesArmOnlyOnce`, `timerWaitsUntilSixtyMsAfterLastChange`, `timerPushesWhenQuiet`, `timerEndsWhenNothingPending`, `leadingPushWhileArmedLeavesTimerHarmless`, `forcedPushClearsPending` | The policy: leading throttle as today plus exactly one trailing write |
| `ChatEngineTrailingTest` (package `dev.pocketagent.app`) | `lastPieceBeforeAPauseIsPublishedWhileStreamIsStillOpen` (fake backend sends `Delta("a")`, `Delta("b")` at once, then blocks on a latch; the test polls the repository up to 1 s for the bot text `"ab"`, then releases the latch), `finalTextAndStreamingFlagAfterFinish`, `noWriteAfterBubbleClosed` | The engine really writes the trailing text; the lock and cancel do not break the end of a turn. Existing `ChatEngineTest` must pass unchanged |
| `StreamSmootherTest` | `neverReleasesMoreThanTheTarget`, `releasedCountOnlyGrowsWhileTargetGrows`, `speedFollowsTheBacklog` (350 chars: about 100 released after 0.1 s, plus or minus 15 %), `neverSlowerThanFortyPerSecond`, `steadyArrivalSettlesNearThreeHundredFiftyMsBehind` (200 chars/s for 5 s at 120 Hz: backlog 70 plus or minus 25), `finishFlushesWithin150Ms`, `firstFrameAfterIdleReleasesNothing`, `cutNeverSplitsASurrogatePair`, `cutNeverEndsInsideAMarkerRun`, `cutIgnoresMarkersWhenFinished`, `shrinkingTargetIsClamped`, `initialShownReplaysOnlyFreshMessages` | Section 3.3 rates and safe cuts |
| `MarkdownStreamTest` | `randomSplitsGiveTheSameBlocksAsParsingWhole` (property: fixed seeds, 400 generated documents from weighted line templates: headings, paragraphs with bold, italic, code and links, bullet and numbered lists with nesting and continuation lines, quotes, tables, rules, fenced code with blank lines inside, unclosed fences, `\r\n` and lone `\r`, emoji; each cut at 25 random points including inside `\r\n`, inside a surrogate pair and empty pieces; **after every piece** `snapshot.blocks == parseMarkdown(textSoFar)`), `stableBlocksOnlyGrow` (previous `stable` is an element-wise prefix of the next), `finishedBlocksAreParsedOnce` (400 short paragraphs in 7-char pieces: `parsedLines` below 4 times the line count), `crlfSplitAcrossPieces`, `blankLineInsideFenceDoesNotEndTheBlock`, `unclosedFenceStaysInTail`, `paragraphBecomesTableWhenSeparatorArrives`, `listItemContinuationJoinsLate`, `syncAppendsWhenTextExtends`, `syncReplacesWhenTextDiffers`, `syncIsIdempotent` | Section 3.4 exactness |
| `MarkdownBlocksTest` | twelve fixed snippets (each block kind, nesting, unclosed fence) against hand-written expected blocks; `rangesCoverEveryBlock` (`startLines`/`endLines` ascending, no overlap); `markdownToPlainStillFlattensCodeFences` | The refactor of `Markdown.kt` changed no result |
| `ChatShapeTest` | `emptyChat`, `lastBotIsNotLastWhenToolFollows`, `notesAreIgnoredForLastBotIsLast`, `streamingFlagFollowsLastMessage` | Same booleans as lines 519-521 and 466 |
| `SearchViewTest` | `noMatches`, `positionIsClamped`, `flashOutlineWhenNotSearching` | Lines 482-485 |
| `RowGroupingTest` | `firstMessageShowsHeaderAndDay`, `sameRoleAndBotIsAGroup`, `dayChangeShowsDay` (lambda `sameDay`) | Lines 818-821 |
| `ToolOutputChunksTest` | `joinEqualsKeptPrefix`, `splitsAfterNewlineWhenPossible`, `neverSplitsSurrogatePair`, `emptyTextGivesNoChunks`, `cutsAtMaxChars` | Section 3.7 |

## 9. Robot hooks

testTags (exact strings; `testTagsAsResourceId` comes from NAV; all are in STREAM's files unless marked SWEEP/DESIGN):

| Tag | On |
|---|---|
| `chat.screen` | `ChatBody` root column |
| `chat.find`, `chat.controls`, `chat.more` | the three top-bar buttons (search, sliders, more) |
| `chat.search` | `ChatSearchBar` (whole bar, via its `modifier`) |
| `chat.list` | the `LazyColumn` |
| `chat.empty` | the empty-chat block |
| `chat.msg.user`, `chat.msg.bot`, `chat.msg.tool`, `chat.msg.error`, `chat.msg.note` | one per message row (several rows share a tag; the robot takes the last) |
| `chat.msg.bot.live` | replaces `chat.msg.bot` on a row while `msg.streaming` is true (the robot waits for it, then waits for it to vanish) |
| `chat.pending` | `PendingRow` |
| `chat.jump` | the jump-to-latest button |
| `chat.approval` | wrapper around `ApprovalCard` (inner tags: SWEEP request) |
| `chat.tool.sheet`, `chat.tool.output`, `chat.tool.copy`, `chat.tool.close` | the sheet's content column, the output list, the Copy and Close buttons |
| `chat.input`, `chat.send`, `chat.stop`, `chat.attach`, `chat.mic` | SWEEP request |
| `sheet`, `sheet.action.<i>`, `dialog.confirm`, `dialog.cancel`, `top.back` | DESIGN / SWEEP requests |

Proof scenarios STREAM expects (ROBOT writes them): `chat-stream-long` (send `LONG`; `expect chat.msg.bot.live`; wait for it to vanish; `shot`; frame stats by `phone-perf` in the "2,000-character answer into a long chat" scenario: at least 95 % within 8.3 ms on the release build); `chat-stream-pause` (send `PAUSE`; within 1.5 s `expect "First half of the answer."` while `chat.stop` is still visible); `chat-tool-sheet` (send `RUN_TOOL`; tap `chat.msg.tool`; `expect chat.tool.sheet`; `expect chat.tool.output`; tap `chat.tool.close`). That typing no longer recomposes the message list cannot be seen from outside; it is a code-review check (section 3.6, observation table).

## 10. Experiment branches

None of S1 to S7 changes this section (written for the PASS case). If S3 (shared elements) passes, NAV provides `LocalChatAvatarModifier` and nothing else changes here; if it fails, the local keeps its default `Modifier` and nothing happens. If S1 (predictive back) passes, the `BackHandler(enabled = state.searching)` in `ChatBody` stays as it is (it only consumes the back press while the search bar is open). If S5 fails (the practice model then runs in the app process), the keywords of section 7 are the same.

## 11. Risks, and where the plan is wrong or incomplete

1. **The engine change is bigger than "the update timing only".** A timer thread cannot read `text` and `reasoning` (`StringBuilder`s mutated by the generation thread, `ChatEngine.kt:458-459`) without a lock, so `onEvent` and `finish` take `Turn.lock`. Without the lock the trailing write can tear. This is the one place where STREAM's edit reaches beyond `push`; it adds no behaviour apart from the timer.
2. **"The streaming row is not animated" is not enough.** `animateItem()` re-targets the placement of every older row each time the streaming row grows (reverse layout: they shift up by the new height), so each frame starts a new spring for every visible row. Section 3.6 therefore switches placement animation off for all rows while the last message streams. Removal and insertion of rows keep their fade.
3. **A5's counts.** The plan says the screen collects sixteen states; `ChatBody` collects 12 (`ChatScreen.kt:161-171, 219, 496`; 16 is the whole file). The cited `ChatMessages.kt:515-518` is really 515-525 (the `AnimatedVisibility` plus the `verticalScroll` box). Neither changes the work.
4. **Compose skipping depends on instance equality.** `Message` holds `List` fields, so it counts as unstable; with Kotlin 2.4's default strong skipping a row is skipped only when its `Message` is the same instance. That holds today (`ChatRepository.updateMessage` replaces only the changed element). Nobody may add a step that rebuilds all messages per update; nobody may pass fresh lambdas into `MessageRow` (hence `ChatRowActions`).
5. **Smoother versus the engine's final rewrite.** `closeBubble` can replace the text (BotForge cleanup, `trimEnd`, thought filter, `ChatEngine.kt:487-525`), so the final text need not extend what was shown. `MarkdownStream.sync` compares and falls back to a full re-parse; `StreamSmoother.step` clamps `shown`. Both are tested.
6. **Per-frame cost for very long answers.** `sync` compares the whole text each frame (O(n), about 50 microseconds at 100,000 characters) and re-parses only the open block. A single code block of 100,000 characters re-highlights per frame; accepted for Wave 0 (answers of that size are rare). Wave 2's typed blocks can cap it.
7. **TalkBack.** Streaming text is not a live region; a screen reader reads whatever is on screen when focused. No worse than today; Wave 2 revisits.
8. **Caret and cadence.** `BlinkingCaret` (`ChatMessages.kt:447-462`) is left as it is; design pattern 11's 6 frames per second needs `Ease.steps` from DESIGN and is a Wave 2 change. The look stays: "apart from motion nothing looks different".
9. **Toasts and tokens in STREAM's files.** Seven `Toast.makeText` calls collapse into one (`say`), which lowers the ratchet count; STREAM adds no raw `tween(`, `RoundedCornerShape(` or literal duration beyond what it moves. SWEEP does not touch STREAM's files, hence the one-line exception in section 7.
10. **Kotlin gotchas.** In `ChatScreenState`, `messagesFlow` must be declared before `shape` and `matches` (init order). No KDoc may contain a slash-star sequence (`app/.../ui/chat/` followed by a star in prose opens a nested comment). No string has leading or trailing spaces (aapt2). `rememberLauncherForActivityResult` must stay unconditional (never inside `if (state.showAttach)`), so the launchers live at the top of `ChatDialogs` and `ChatInputArea`.

**Open questions for the lead:** (a) PetNote's call signature, to fill `say`; (b) agree that the tool card keeps its one-line preview and gains `PixelIcon.MORE` in place of the up/down chevron (the only visible change of W6); (c) is a 20,000 to 60,000 character cap on tool output in the sheet acceptable, or must the full text always be reachable (then add a "Share as file" action in Wave 2)?
