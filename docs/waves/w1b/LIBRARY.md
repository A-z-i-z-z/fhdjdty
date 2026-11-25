# Wave 1b, area LIBRARY: contract draft

`J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`; package `dev.pocketagent.app` is written `app`. Line numbers were read in the work tree on 2026-11-25 (branch `ccr-bb6d7bcc-9eojyu`, with the uncommitted Wave 0 defect fixes). Sources: plan 4.1 (lines 415 to 436) and 1.6 (834 to 841), design language 8.15 (shelves) and 8.16 (kinds and states), 2.1/2.2 (tokens, type), pattern 3, 8, 18 (motion), 9 (components), 10 (accessibility), 13 (rooms for words), `docs/waves/w1b/SPLIT.md` 4.4, 6, 9 to 11, `docs/waves/w0-contract.md` 3.3 to 3.6 and 3.12, `docs/waves/w1a-contract.md` 3.10 and 3.11 (sprite names, tag style). **Nothing of Wave 1a is needed to build this area**, except sprite names that have fallbacks (section 2.3); where 1b areas supply a symbol (`Route.Library`, `EmptyState(art =)`, the `library` debug extra) it is taken from the SPLIT cut and listed in section 7, not from today's code.

Kind of screen (DL 8.16): **List**. Two shelves in 1b, Files and Saved; every later wave adds a shelf here and nowhere else (plan 4.1).

## 1. What the plan and the design language ask

| # | Ask | Where |
|---|---|---|
| L1 | A tab "Library" with "what exists today: saved messages and the files given to the agent" | plan 1.6 l.840 to 841; 4.1 l.425, 435 to 436 |
| L2 | "A row of tabs under its title that scrolls sideways, each with its count"; "a shelf that is empty is not shown, except the first two" (in 1b both shelves are the first two, so both always show) | DL 8.15 |
| L3 | Kind List: title bar, filter chips, rows of one height, swipe actions only where there is one main action | DL 8.16 table |
| L4 | Five states: Empty (a small picture, one sentence, the one action that fills it), Loading (dither-shimmer blocks in the shape of the content, never a spinner), Error (what happened, what was kept, what can be done, Retry), Offline, Needs permission | DL 8.16 |
| L5 | List changes animate with `animateItem()` (fade FAST, placement glide); tab switch cross-fades the content in FAST; haptic TICK for chip and list selection | DL patterns 3, 8; 4 |
| L6 | Every control has a spoken label; text at double size and mirrored are looked at in every wave; a touch target is 48 dp | DL 10; plan principle 13 |
| L7 | Rooms: chip 18 characters, screen title 28, first line of a row 40 with an ellipsis; a translation may be a third longer | DL 13.2 |
| L8 | No capitals made by code, headings in sentence case, no exclamation marks, a button says what happens | DL 13.5 |
| L9 | The Library shows what the user gave and saved; it is read only in 1b: nothing in it deletes an attachment (a chat points at it) | SPLIT 4.4 |
| L10 | No new permission, no manifest change, optional stored fields only, nothing written on the owner's phone by merely looking | SPLIT 5, 10 item 10; 1a contract 1.2 |

Taken from the plan's wording and from decisions assumed (owner may change): the Files shelf lists photos and documents the user attached in a chat (it is "the files given to the agent"); files the agent made itself are the "Made" shelf of Wave 5 and are not here.

## 2. What exists today (read in the tree) and what 1a changes

### 2.1 The data

- **Attachments** are fields of messages: `Message.attachments: List<Attachment>` (`J/data/Models.kt`, class `Attachment(id, name, mime, kind: AttachmentKind { IMAGE, DOCUMENT }, localPath, guestPath, sizeBytes, textExcerpt)`). The files live in `files/chat/attachments/`: `AttachmentStore.importImage` writes `<id>.jpg` (`AttachmentStore.kt`, line 63), `importDocument` writes `<id>_<safeName>` (line 90); `appDir` is `File(paths.chatDir, "attachments")` (line 27, private; `paths.chatDir` = `File(filesDir, "chat")`, `runtime/Paths.kt` line 28), and a copy goes to `/root/.hermes/attachments/` for Hermes (not the Library's business).
- `AttachmentStore.resolveIn(dir, path)` (companion, public, line 257) maps a stored path to a file inside `dir` by BASE NAME only (`[A-Za-z0-9._-]{1,200}`, no leading dot, canonical parent must equal `dir`): an absolute path from another profile or a restore still finds the file, an escaping path gives null. `loadThumbnail(path, maxPx)` (line 139) decodes a scaled bitmap through the same rule (blocking: IO thread only). Both are used by the chat (`AttachmentUi.kt`, private `rememberAttachmentThumb` at line 246, not reusable: the Library writes its own).
- **Nothing ever deletes an attachment file.** `ChatRepository.deleteConversation` (line 329) removes the messages file, not the attachments, so files of deleted chats stay ("orphans"). A forked chat (`forkConversation`, line 274) clones its messages with the SAME attachment objects, so one file can be referenced by several chats.
- **Message loading.** Messages load per conversation, lazily; `ChatRepository.starredMessages()` (line 247) is the model for a scan that does not keep what it reads: for every conversation it uses the in-memory slot when loaded, else `readList(messageFile(id), ...)` and drops it again; newest first, at most 300; blocking, IO thread only. `ChatRepository.indexIntact` (line 56, `@Volatile var ... private set`, public read) is false when `conversations.json` could not be read, and then the list of conversations is empty without that being the truth. `ChatRepository.loaded: StateFlow<Boolean>` flips when the index has been read.
- **The saved-messages screen** is `J/ui/chat/StarredScreen.kt` (124 lines): `fun StarredScreen()` (line 53) with a `PocketTopBar`, `produceState` that calls `repo.starredMessages()` on `Dispatchers.IO`, a local `removed` set so an unstarred row disappears at once, `scope.launch(Dispatchers.IO + NonCancellable) { repo.setStarred(cv.id, m.id, false) }` (line 94) so leaving the screen cannot drop the change, a `LazyColumn` of the private `StarredRow` (line 104: avatar stack, chat title, time, speaker, three lines of text, a STAR button labelled `ui_act_unstar`), tap = `ChatScrollTarget.set(cv.id, m.id)` then `nav.go(Route.Chat(cv.id))`. Empty state: `EmptyState(ui_starred_empty_title, ui_starred_empty_body)` (strings_chat.xml lines 304 to 305, already in tr and fr). It is reached from the star button in the Chats top bar (`ChatListScreen.kt:138`) and from Home's Saved tile (`HomeScreen.kt:365`), both `nav.go(Route.Starred)`. `Route.Starred` has key `starred`, tag `screen.starred`, no bottom bar (`Nav.kt` lines 110, 119, 136, 154).
- **Helpers I reuse, all `internal` in the same module** (`J/ui/chat/ChatCommon.kt`, `ChatLogic.kt`): `relativeTime(ms)` (composable, line 192: "now / 5 min / 12:30 / Yesterday / date"), `titleOf(conv, botsById, untitled)` (line 123), `AvatarStack(bots, size, modifier)` (line 99), `markdownToPlain`, `ChatScrollTarget.set(convId, messageId)` (`ChatLogic.kt` line 57). Texts reused unchanged (they have tr and fr): `ui_starred_empty_title`, `ui_starred_empty_body`, `ui_starred_you`, `ui_act_unstar`, `ui_chats_untitled`, `ui_chat_deleted_bot`, `ui_chat_empty_reply`, `ui_filter_all`, `ui_chat_retry`, `ctl_loading`.
- **The FileProvider** exists: `AndroidManifest.xml` lines 193 to 200 (`${applicationId}.fileprovider`, `grantUriPermissions`), `R/xml/file_paths.xml` already holds `<files-path name="attachments" path="chat/attachments/"/>`. Used today for camera capture and the log export. Opening or sharing an attachment needs no manifest, no path and no permission.
- **Components** (Wave 0, `J/ui/components/`): `PocketTopBar(title, onBack, subtitle, leading, actions, ember, titleModifier)`, `PocketChip(text, selected, onClick, modifier, icon)` (gives `HapticCue.TICK` itself), `Skeleton(modifier, height, corner)` (tag `skeleton`, spoken "loading"), `EmptyState(title, body, modifier, action)` (no picture parameter today: always `Mascot(IDLE)` at 96 dp or a spark icon when the mascot is off), `PixelButton(text, onClick, modifier, icon, kind, enabled)`, `PixelIconButton(icon, onClick, modifier, contentDescription, tint, enabled)`, `ActionSheet(title, actions, onDismiss)` with `SheetAction(icon, label, danger, onClick)`, `PetNotes.show(text, kind)`, `PixelIcon` (FILE, IMAGE, STAR, MORE, SHARE, CHATS exist), `PixelText`, `PixelImage`, `SpriteBank.find(name)`.
- **Tokens** (`ui/theme/Tokens.kt`, `Motion.kt`, `Haptics.kt`): `Space.xxs..xxl`, `Space.gutter`, `Space.touch`, `Type.title/body/label`, `PixelType.small/title`, `Pocket.motion.fast()/glide()`, `HapticCue.TICK`, `rememberHaptics()`. The lint ratchet (`tools/lint-ui.ps1`) counts `N.dp` literals, `.uppercase(`, `.lowercase(`, `delay(<digits>` and `durationMillis = <digits>` per file; **a new file starts at 0**, so every size below is a `Space` expression (section 3 lists them) and no timing is a literal.

### 2.2 What is missing

There is no Library: no route, no screen, no scan of attachments, no `Route.Library`, no `PixelIcon.LIBRARY`. The five tabs are Home, Chats, Bots, Hermes, Settings (`AppRoot.kt` lines 205 to 209).

### 2.3 What 1a and the other 1b areas will have changed by then (relied on through the registry, not through today's code)

| Needed | From | If it is missing |
|---|---|---|
| `Route.Library(shelf: String?)` (keys `library`, `library:<shelf>`), root id, tab index 2, `NavTags.TAB_LIBRARY`, `screen.library`, `RouteContent` line, the `library` debug extra | SHELL (SPLIT 4.1) | nothing of mine compiles into the app until it exists: slice A (pure files) does not need it; slice C does |
| `EmptyState(..., art: String? = null)`; the Home Saved tile target `Route.Library("saved")` | BARS (SPLIT 4.2) | `art` is passed by name, so only the call sites wait; without the parameter the screen shows Pêt as today |
| Sprites `empty_jar`, `empty_cup`, `tile_library`, `empty_scroll` (1a extras of `illustrations.pxl`, 1a contract 3.10, may have been cut by LOOK), and a new `empty_basket` | ART-B (SPLIT 4.7) and 1a ART-WORLD | `SpriteBank.find` returns null and `EmptyState` falls back to `Mascot` (section 3.3 `LibraryShelf.artFor`) |
| `PixelIcon.LIBRARY` | SHELL | not used by my files (the tab icon is SHELL's) |
| Nothing from 1a's scene, sky, fire, Pêt or Home | | none: the Library draws no scene |

## 3. The design

### 3.1 Files

Create (all new):

| File | Kind | Slice |
|---|---|---|
| `J/data/LibraryIndex.kt` | pure (no `android.` or `androidx.` import): model, dedupe, sort, filter, counts, sizes, orphans | A |
| `J/data/LibraryDebug.kt` | pure plus one `StateFlow`: the pin grammar, the pin holder, the synthetic scan (added to the SPLIT list: the proof needs it, section 5) | A |
| `J/ui/library/LibraryShelf.kt` | pure: shelf keys, the rule of which shelves show, the state of a shelf, art candidates (added to the SPLIT list: the pure shelf logic must be JVM-testable and a file with Compose imports is not slice A) | A |
| `J/ui/library/LibraryTags.kt` | constants only | A |
| `J/data/LibraryFiles.kt` | Android: the scan that reads the disk, the open and share intents, the memo | C |
| `J/ui/library/LibraryScreen.kt`, `LibraryShelves.kt`, `FilesShelf.kt`, `SavedShelf.kt` | Compose | C |
| `R/values/strings_library.xml` (`lib_`) | strings | C (the composables need it to compile) |
| `T/data/LibraryIndexTest.kt`, `T/data/LibraryPinTest.kt`, `T/data/LibraryMessagesTest.kt`, `T/ui/library/LibraryShelvesTest.kt` | tests | A (the last but one: A if the lead accepts R5, section 7) |

Modify (existing):

- `J/ui/chat/StarredScreen.kt`: extract the list body (3.7). Public `fun StarredScreen()` keeps its name, parameters and look.
- `J/data/ChatRepository.kt`: one ADDED method `libraryMessages()` (3.2 and request R5; no other line of the file changes).

Nobody else's file is edited by this area. The manifest, `file_paths.xml`, `Nav.kt`, `AppRoot.kt`, `DebugLinks.kt`, `EmptyState.kt`, the Home files and `PixelIcon.kt` are asked for in section 7.

### 3.2 The model: `J/data/LibraryIndex.kt` (package `app.data`, pure)

```kotlin
enum class LibraryKind { PHOTO, DOCUMENT }                 // from AttachmentKind: IMAGE gives PHOTO, DOCUMENT gives DOCUMENT
enum class LibraryFilter { ALL, PHOTOS, DOCUMENTS }
enum class LibrarySort { NEWEST, NAME, SIZE }               // SIZE = largest first

/** What one pass over the chats finds (ChatRepository.libraryMessages()). Newest first. */
class LibraryMessages(val starred: List<Pair<Conversation, Message>>, val attached: List<Pair<Conversation, Message>>) {
    companion object { val EMPTY: LibraryMessages }
}

/** One file of the Files shelf. [key] = base name inside files/chat/attachments (unique); the chat is the OLDEST message that holds it. */
data class LibraryFile(
    val key: String, val name: String, val mime: String, val kind: LibraryKind,
    val sizeBytes: Long,        // the real length of the file on disk, never the stored sizeBytes
    val addedAt: Long,          // createdAt of that message
    val conv: Conversation, val messageId: String,
)
class LibraryFileCounts(val all: Int, val photos: Int, val documents: Int, val totalBytes: Long) { fun of(filter: LibraryFilter): Int }
class LibraryOrphans(val count: Int, val bytes: Long)       // files on disk that no message references
class LibraryBuild(val files: List<LibraryFile>, val referenced: Set<String>, val missing: Int, val capped: Boolean)
/** Everything the screen shows, read in one pass. [orphans] is null when it could not be known (a capped scan). */
class LibraryScan(val files: List<LibraryFile>, val saved: List<Pair<Conversation, Message>>, val missing: Int, val orphans: LibraryOrphans?) {
    fun withoutSaved(messageId: String): LibraryScan        // the unstarred row leaves at once
}

object LibraryIndex {
    const val MAX_SAVED = 300                    // equals starredMessages()
    const val MAX_ATTACHED_MESSAGES = 5_000      // messages with attachments one scan keeps
    const val MAX_FILES = 5_000
    fun baseName(path: String): String           // text after the last '/' or '\\'; "" for a blank path
    fun isFlatName(name: String): Boolean        // the rule of AttachmentStore.resolveIn: [A-Za-z0-9._-]{1,200}, not starting with '.'
    fun kindOf(kind: AttachmentKind): LibraryKind
    /** One row per FILE. A key that is not a flat name, or whose [sizeOf] is null, is dropped and counted in `missing` (never listed, never counted). */
    fun buildFiles(attached: List<Pair<Conversation, Message>>, sizeOf: (key: String) -> Long?, max: Int = MAX_FILES): LibraryBuild
    fun orphans(onDisk: List<Pair<String, Long>>, referenced: Set<String>): LibraryOrphans
    fun view(files: List<LibraryFile>, filter: LibraryFilter = LibraryFilter.ALL, sort: LibrarySort = LibrarySort.NEWEST, locale: Locale = Locale.getDefault()): List<LibraryFile>
    fun counts(files: List<LibraryFile>): LibraryFileCounts
    fun sizeText(bytes: Long, locale: Locale): String
}
```

Rules, exact:

- **Dedupe.** Group by `key` (the base name of `Attachment.localPath`); keep the row of the message with the smallest `(createdAt, message id)`: the chat where the file was first given. Forked chats therefore never double a file, and if the first chat is deleted the next oldest takes over.
- **Existence.** `sizeOf(key)` returns the file's real length, or null when the file is not there. A null drops the row and adds one to `missing` (a restore without files, or an attachment that was never copied). The Library lists what can be opened; the chat itself still shows its own badge.
- **Caps.** `capped = attached.size >= MAX_ATTACHED_MESSAGES || distinct keys > max`; rows beyond `max` (newest kept) are cut.
- **Sort.** NEWEST: `addedAt` descending, then `key` ascending (stable). NAME: `java.text.Collator.getInstance(locale)` at strength PRIMARY on `name`, then `key`. SIZE: `sizeBytes` descending, then `addedAt` descending, then `key`.
- **Filter.** PHOTOS = kind PHOTO, DOCUMENTS = kind DOCUMENT, ALL both.
- **Counts.** `all`, `photos`, `documents`, `totalBytes` over the files given.
- **Orphans.** `onDisk` = the regular files of the attachments folder with their length; the orphans are those whose name is not in `referenced`; the scan passes null for the whole value when `capped` (a cut scan cannot say what is unreferenced).
- **`sizeText`** (the unit symbols are international and never translated; the same set the chat uses today): below 1,024 bytes `"<n> B"`; below 1 MiB `"<bytes / 1024> KB"` (floor, no decimals); below 1 GiB and above, one decimal, floored, with the locale's decimal separator: `"1.4 MB"`, `"1,4 MB"` (tr), `"2.0 GB"`. Negative gives `"0 B"`. Uses `java.text.NumberFormat` with `minimumFractionDigits = maximumFractionDigits = 1` and `RoundingMode.FLOOR`.

**`ChatRepository.libraryMessages()`** (the one added method; request R5; same pattern as `starredMessages()`, one pass instead of two):

```kotlin
/**
 * One pass over every conversation for the Library (Wave 1b): the starred messages (exactly what [starredMessages] returns: newest first,
 * at most 300) and the messages that carry attachments (newest first, at most 5000). Reads message files that are not in memory without
 * keeping them. Blocking disk reads: call off the main thread.
 */
fun libraryMessages(): LibraryMessages {
    val starred = ArrayList<Pair<Conversation, Message>>()
    val attached = ArrayList<Pair<Conversation, Message>>()
    for (c in _conversations.value) {
        val s = synchronized(lock) { slots[c.id] }
        val msgs = if (s != null && s.loaded) s.flow.value
        else readList(messageFile(c.id), "messages") { ChatJson.decodeMessages(it, c.id) } ?: emptyList()
        for (m in msgs) {
            if (m.starred) starred += c to m
            if (m.attachments.isNotEmpty()) attached += c to m
        }
    }
    return LibraryMessages(
        starred = starred.sortedByDescending { it.second.createdAt }.take(LibraryIndex.MAX_SAVED),
        attached = attached.sortedByDescending { it.second.createdAt }.take(LibraryIndex.MAX_ATTACHED_MESSAGES),
    )
}
```

`starredMessages()` stays as it is (the old screen and `ChatRepositoryTest` use it).

### 3.3 The shelf logic: `J/ui/library/LibraryShelf.kt` (pure) and the tags

```kotlin
package app.ui.library
object LibraryShelf {
    const val FILES = "files"; const val SAVED = "saved"
    val ALL: List<String>                         // listOf(FILES, SAVED): the order of the row, the order of DL 8.15 (Made and Pictures will come first and between)
    const val ALWAYS_SHOWN = 2                    // DL 8.15: the first two shelves always show
    fun parse(key: String?): String               // null, blank or unknown gives FILES (an old or mistyped link never fails)
    fun entries(counts: Map<String, Int?>): List<ShelfEntry>      // one entry per ALL key in order; null count = not known yet
    fun visible(entries: List<ShelfEntry>): List<ShelfEntry>      // the first ALWAYS_SHOWN always; a later one only when count > 0
    fun artFor(shelf: String, error: Boolean, exists: (String) -> Boolean): String?   // the first existing of: error: "empty_cup"; FILES: "empty_basket", "tile_library", "empty_scroll"; SAVED: "empty_jar"; else null (EmptyState falls back to Pêt)
}
data class ShelfEntry(val key: String, val count: Int?)

enum class LibraryUiKind { LOADING, EMPTY, CONTENT, ERROR }           // the four states that exist; Offline and Needs permission do not (3.8)
object LibraryStates { fun of(loading: Boolean, failed: Boolean, count: Int): LibraryUiKind }
// failed beats loading beats empty beats content: a failed refresh shows the error even over a stale list, because the list may be wrong.
```

`LibraryTags` (`J/ui/library/LibraryTags.kt`, exact; no case change in code, `when` tables):

```kotlin
object LibraryTags {
    const val SHELVES = "library.shelves"        // the sideways row
    const val LIST = "library.list"              // the LazyColumn of the visible shelf (one at a time)
    const val SUMMARY = "library.summary"        // the quiet count line (Files)
    const val EMPTY = "library.empty"; const val EMPTY_ACTION = "library.empty.action"
    const val SKELETON = "library.skeleton"; const val ERROR = "library.error"; const val RETRY = "library.retry"
    const val ORPHANS = "library.orphans"        // extra
    fun shelf(key: String): String               // "library.shelf.files" | "library.shelf.saved" (a when table; unknown key gives "library.shelf")
    fun filter(f: LibraryFilter): String          // "library.filter.all" | ".photos" | ".documents"
    const val SORT = "library.sort"               // extra
    fun row(i: Int): String                       // "library.row.<i>": row i of the visible list, display order, from 0
    fun more(i: Int): String                      // "library.more.<i>" (extra)
}
```
`screen.library` is SHELL's (`NavTags.screen`).

### 3.4 The disk side: `J/data/LibraryFiles.kt` (Android)

```kotlin
class LibraryFiles(private val attachmentsDir: File, private val source: () -> LibraryMessages) {
    /** BLOCKING (IO thread). Never writes. Throws only what the source throws. */
    fun scan(): LibraryScan
    companion object {
        fun forApp(app: PocketApp): LibraryFiles      // File(app.filesDir, "chat/attachments"), source = { app.chat.libraryMessages() }
        internal fun onDisk(dir: File): List<Pair<String, Long>>   // regular files and their lengths; an empty list when the folder is missing
    }
}
```
`scan()`: `source()`, then `LibraryIndex.buildFiles(m.attached, sizeOf = { key -> AttachmentStore.resolveIn(attachmentsDir, key)?.takeIf { it.isFile }?.length() })`, then `orphans = if (build.capped) null else LibraryIndex.orphans(onDisk(dir), build.referenced)`, then `LibraryScan(build.files, m.starred, build.missing, orphans)`. The folder is never created by the Library (`AttachmentStore.appDir` creates it with `mkdirs()`; `File.listFiles()` of a missing folder is null and gives an empty list). One log line, only in builds with test hooks: `I/Library: scan files=<n> saved=<m> missing=<k> orphans=<j> in <ms> ms`.

Open and share (extra X4, section 6):

```kotlin
object LibraryOpen {
    /** The content URI of a file of the Files shelf through the FileProvider (authority packageName + ".fileprovider"), null when the file is gone. */
    fun uriFor(context: Context, file: LibraryFile): Uri?
    /** ACTION_VIEW with the file's mime type and FLAG_GRANT_READ_URI_PERMISSION; false when no app can open it. */
    fun view(context: Context, file: LibraryFile): Boolean
    /** ACTION_SEND through a chooser (the share sheet) with the stream and FLAG_GRANT_READ_URI_PERMISSION; false when it cannot start. */
    fun send(context: Context, file: LibraryFile): Boolean
}
```
Both start the activity with `FLAG_ACTIVITY_NEW_TASK` only when the context is not an activity (`LocalContext` is the activity, so no flag), catch `ActivityNotFoundException` and `SecurityException`, and never throw. The app targets API 28, so no `<queries>` entry is needed. Nothing leaves the phone except by this explicit tap.

`LibraryMemo` (extra X3, in the same file): `internal object LibraryMemo { @Volatile var last: LibraryScan? }` set after every successful scan and by `withoutSaved` on an unstar; used only as the first value of the screen's state so a return to the tab shows the list at once while the refresh runs. In memory, never on disk.

### 3.5 The debug pin: `J/data/LibraryDebug.kt`

For the proof (section 5) every state and a large list must be reachable without touching the owner's data and without needing files. A pin replaces the scan with a synthetic one; it is in memory, `TEST_HOOKS` builds only, cleared by `reset=1`.

```kotlin
enum class LibraryPinState { READY, LOADING, ERROR }
data class LibraryPin(val files: Int, val saved: Int, val orphans: Int, val state: LibraryPinState) {
    companion object { fun parse(text: String): LibraryPin? }   // null when any item is bad
}
object LibraryDebug {
    val pin: StateFlow<LibraryPin?>
    fun pin(p: LibraryPin?)                    // never throws; null unpins
}
object LibrarySynthetic { fun scan(pin: LibraryPin, nowMs: Long): LibraryScan }   // pure, deterministic
```
Grammar of the debug extra `library` (SHELL parses the `off` case; the rest is `LibraryPin.parse`): items comma separated, lower case, a repeated key: the last wins, the whole value is rejected when one item is bad: `files:<0..3000>`, `saved:<0..300>`, `orphans:<0..999>`, `state:ready|loading|error`. An absent count is 0, an absent state is `ready`. **A pinned Library makes no repository call and no disk read at all**, so it is safe on the owner's package. `state:loading` never leaves the loading state; `state:error` shows the error with the text of a failed scan; Retry re-runs the pinned state (so it stays an error).

`LibrarySynthetic.scan`: file `i` (0 up) has key and name `photo-<i padded to 3>.jpg` (kind PHOTO, mime `image/jpeg`) when `i % 3 != 2`, else `paper-<i>.pdf` / `table-<i>.csv` alternating (kind DOCUMENT); size `(i * 7919L + 1203L) % 5_000_000L + 100L`; `addedAt = nowMs - i * 3_600_000L`; chat `seed-lib-<i % 5>` with a blank title and `botIds = ["hermes"]` (so the row says "Hermes", no English literal in code); message id `seed-lib-m<i>`. Saved row `j`: message `seed-lib-s<j>`, role BOT, text `sample <j>`, `createdAt = nowMs - j * 5_400_000L`, `starred = true`. `orphans` count gives `LibraryOrphans(count, count * 40_000L)` (null when 0 is not asked: 0 gives null). Thumbnails of synthetic rows draw the generic icon (no file).

### 3.6 The screen: `J/ui/library/LibraryScreen.kt`

```kotlin
@Composable fun LibraryScreen(shelf: String?)          // SHELL's RouteContent: is Route.Library -> LibraryScreen(route.shelf)
```

State it keeps and where:

| State | Where | Notes |
|---|---|---|
| The selected shelf | **the route**: `LibraryShelf.parse(shelf)`; a tap on a shelf tab calls `nav.tab(Route.Library(key))` | Single source of truth, so the Home Saved tile (`nav.tab(Route.Library("saved"))`) always lands on Saved even after the user picked Files earlier. All Library routes share the stable root id 6 (R1), so `tab()` keeps the entry id: no screen transition, only the cross-fade below |
| The scan result | `produceState<LibraryPhase>` inside `LibraryScreen` | not saved; every visit rescans (a few hundred ms); the memo (X3) shows the last result first |
| The filter of Files | `rememberSaveable { mutableStateOf(LibraryFilter.ALL) }` in `FilesShelf` | enum is Serializable; survives process death with the entry |
| The sort of Files (X2) | `rememberSaveable { mutableStateOf(LibrarySort.NEWEST) }` | |
| Unstarred ids | `rememberSaveable { mutableStateOf(emptySet<String>()) }` | applied to `scan.saved` before counting |
| Scroll positions | `rememberLazyListState()` per shelf body | reset when the shelf changes (the body is one `Crossfade` child); accepted |
| The open More menu (X4) | `remember` | closes on rotation; fine |

```kotlin
internal sealed interface LibraryPhase {
    data object Loading : LibraryPhase
    data class Ready(val scan: LibraryScan) : LibraryPhase
    data class Failed(val why: LibraryFailure) : LibraryPhase
}
internal enum class LibraryFailure { INDEX_UNREADABLE, SCAN_FAILED }
```
Loading (the producer, off the main thread): `app.chat.loaded.first { it }`; if `!app.chat.indexIntact` the phase is `Failed(INDEX_UNREADABLE)`; else `LibraryFiles.forApp(app).scan()` inside `try`, a `Throwable` other than `CancellationException` is logged (`AppLog.e("Library", "scan failed", e)`) and gives `Failed(SCAN_FAILED)`. The producer reruns when the Retry counter (`rememberSaveable { mutableIntStateOf(0) }`) changes. When `BuildConfig.TEST_HOOKS` and `LibraryDebug.pin` is not null the producer returns the pinned phase instead (`Loading` for `state:loading`, `Failed(SCAN_FAILED)` for `error`, else `Ready(LibrarySynthetic.scan(pin, AppClock.nowMillis()))`), without any read.

Layout, top to bottom (one Column filling the screen):

```
┌──────────────────────────────────┐
│ Library                          │  PocketTopBar(title = lib_title); a tab root: no back, no gear
├──────────────────────────────────┤
│ Files 12   Saved 7               │  ShelfTabs: scrolls sideways, chosen tab underlined (accent, 1 design pixel steps)
│ (All) (Photos) (Documents) (Sort)│  Files only: filter chips (PocketChip); Sort is X2
│ 12 files · 38.2 MB               │  summary (Type.label, muted, tag library.summary)
│ ▣ trip-map.jpg                 ⋮ │  row: thumbnail 48 dp, name, "Photo · 214 KB · Yesterday", "In Trip plan", More (X4)
│ ▤ budget.pdf                   ⋮ │
│   ...                            │
│ 2 files from deleted chats ...   │  X1, last item of the list
└──────────────────────────────────┘
```
`PocketTopBar(title = t(R.string.lib_title))`, then `ShelfTabs`, then `Crossfade(targetState = selected, animationSpec = Pocket.motion.fast())` whose child draws the shelf body: `FilesShelf(...)` or `SavedShelf(...)`, each with the same five-state `when` (3.8). The `Crossfade` is pattern 8 (content cross-fades in FAST); at animation level OFF `Pocket.motion.fast()` snaps. The bottom bar's height is already left free by the shell; the lists add `Space.l` bottom content padding.

### 3.7 The shelf tabs and the shared bodies: `LibraryShelves.kt`

```kotlin
@Composable internal fun ShelfTabs(entries: List<ShelfEntry>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)
@Composable internal fun shelfName(key: String): String                 // t(lib_shelf_files) | t(lib_shelf_saved)
@Composable internal fun shelfCountText(key: String, count: Int): String   // tn(lib_files_count, count, count) | tn(lib_saved_count, count, count)
@Composable internal fun LibraryLoading(rows: Int = 6, modifier: Modifier = Modifier)        // tag library.skeleton
@Composable internal fun LibraryEmpty(art: String?, title: String, body: String, modifier: Modifier = Modifier)   // EmptyState + the one action; tags library.empty, library.empty.action
@Composable internal fun LibraryProblem(why: LibraryFailure, onRetry: () -> Unit, modifier: Modifier = Modifier)  // EmptyState(art = empty_cup) + Retry; tags library.error, library.retry
```

- **`ShelfTabs`**: a `Row(Modifier.horizontalScroll(rememberScrollState()).testTag(LibraryTags.SHELVES))` of one tab per `LibraryShelf.visible(entries)`. A tab is `Modifier.selectable(selected, role = Role.Tab, onClick)` with `heightIn(min = Space.touch)`, horizontal padding `Space.m`; content: the shelf name (`PixelText(pixel = PixelType.small)` when `Pocket.appearance.pixelFontTitles`, else `Text(style = Type.title)`), then, when the count is known, the count in `Type.label` muted (formatted with `NumberFormat.getIntegerInstance(appLocale())`, so `1,234` or `1.234`); the chosen tab has a 2-design-pixel underline in `Pocket.colors.accent` (drawn in `drawBehind`, `Density.u(2)`), the others none. The click gives `HapticCue.TICK` (`rememberHaptics()`). Spoken label: `t(lib_shelf_cd, name, shelfCountText(...))` ("Files, 12 files"), name only while the count is not known. Tag `LibraryTags.shelf(key)`. A tab that is hidden by `visible` is not composed. Mirrored layouts order the row from the right by themselves.
- **Rooms**: a shelf name is a chip-kind text (18), the underline and the count do not add to it; a longer translation just widens the tab and the row scrolls (DL 13.2).
- **`LibraryLoading`**: `rows` times a Row of `Skeleton(Modifier.size(Space.touch), height = Space.touch)` and a Column of two `Skeleton` lines (widths 0.6 and 0.4 of the row); the wrapper Column carries `LibraryTags.SKELETON`. Never a spinner (DL pattern 18).
- **`LibraryEmpty`**: `EmptyState(title, body, art = <art>, action = { PixelButton(t(lib_action_chats), { nav.tab(Route.Chats) }, Modifier.testTag(LibraryTags.EMPTY_ACTION), icon = PixelIcon.CHATS) })` inside a `Box(fillMaxSize, Center)` tagged `LibraryTags.EMPTY`. The one action that fills either shelf is "in a chat", so the button goes to the Chats tab.
- **`LibraryProblem`**: `EmptyState(t(lib_error_title), t(<lib_error_index or lib_error_scan>), art = <empty_cup>, action = { PixelButton(t(R.string.ui_chat_retry), onRetry, Modifier.testTag(LibraryTags.RETRY), icon = PixelIcon.REFRESH) })` tagged `LibraryTags.ERROR`. `INDEX_UNREADABLE` says the list of chats could not be read, that nothing was deleted, and to try again (the honest case: `ChatRepository.indexIntact == false` means the shelves are empty only for this run); `SCAN_FAILED` says a shelf could not be read and that chats and files were not changed.

### 3.8 The five states of both shelves

| State | Files shelf | Saved shelf |
|---|---|---|
| **Loading** (no result yet; `LibraryStates.of(loading = true, ...)`) | `LibraryLoading()` below the tabs and filters; the tabs show names without counts | the same |
| **Empty** (`scan.files.isEmpty()`, or `scan.saved` empty after unstars) | `LibraryEmpty(art = LibraryShelf.artFor(FILES, false, exists), lib_files_empty_title, lib_files_empty_body)` with the button "Open chats"; no filter chips, no summary | `LibraryEmpty(art = artFor(SAVED, ...) = "empty_jar", ui_starred_empty_title, ui_starred_empty_body)` (the existing wording, "Long-press a message and choose Save message to keep it here."), button "Open chats" |
| **Content** | the filter chips, the summary line, `LazyColumn` of `FileRow`s (3.9); a filter that matches nothing while the shelf has files shows one quiet line (`lib_filter_none_photos` or `lib_filter_none_documents`, `Type.body`, muted, top aligned as the Chats screen does for `ui_filter_empty`) | `SavedShelf` = `StarredList(...)` (3.10) |
| **Error** (`Failed`) | `LibraryProblem` under the tabs; the tabs show names without counts; Retry reruns the scan | the same (both shelves share one scan, so both fail together) |
| **Offline** | **Not applicable.** The Library reads files on the phone and needs no network; nothing on it is greyed out. | Not applicable |
| **Needs permission** | **Not applicable.** It reads the app's own private folder; opening or sharing a file hands one file to one other app through a FileProvider grant on that one intent, no Android permission and no dialog of ours. | Not applicable |

### 3.9 The Files rows: `FilesShelf.kt`

```kotlin
@Composable internal fun FilesShelf(scan: LibraryScan, filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, sort: LibrarySort, onSort: (LibrarySort) -> Unit, botsById: Map<String, Bot>, modifier: Modifier = Modifier)
@Composable private fun FileRow(file: LibraryFile, chatTitle: String, thumb: ImageBitmap?, modifier: Modifier, onOpenChat: () -> Unit, onMore: (() -> Unit)?)
@Composable private fun rememberLibraryThumb(key: String, isPhoto: Boolean): ImageBitmap?
```

- **Filter row**: `Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.m, vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s))` of `PocketChip(t(ui_filter_all))`, `PocketChip(t(lib_filter_photos), icon = PixelIcon.IMAGE)`, `PocketChip(t(lib_filter_documents), icon = PixelIcon.FILE)`, tags `LibraryTags.filter(f)`; (X2) a fourth chip `PocketChip(t(lib_sort_chip, sortName), false, { open sort sheet }, icon = PixelIcon.FILTER)`, tag `LibraryTags.SORT`, whose `ActionSheet(t(lib_sort_title), [Newest, Name, Largest])` rows call `onSort`. A chip gives `HapticCue.TICK` itself.
- **Summary**: `Text(tn(R.plurals.lib_files_summary, n, n, LibraryIndex.sizeText(total, appLocale())), style = Type.label, color = c.textMuted)`, gutter padding, tag `LibraryTags.SUMMARY`; `n` and the total are of the files shown after the filter.
- **List**: `LazyColumn(modifier.testTag(LibraryTags.LIST), contentPadding = PaddingValues(bottom = Space.l))` of `items(rows, key = { it.key })`; each row `Modifier.testTag(LibraryTags.row(i)).animateItem(fadeInSpec = Pocket.motion.fast(), placementSpec = Pocket.motion.glide(), fadeOutSpec = Pocket.motion.fast())` (DL pattern 3). With X1 a last `item(key = "orphans")` shows `tn(R.plurals.lib_orphans, count, count, sizeText)` in `Type.label` muted with tag `LibraryTags.ORPHANS`, only when `scan.orphans != null && count > 0` and the filter is ALL.
- **A row** (min height `Space.touch + Space.xl` = 72 dp, `Row` with `Alignment.CenterVertically`, padding `horizontal = Space.gutter, vertical = Space.s`): thumbnail `Space.touch` square (a stepped clip `PixelShape(Corner.chip)` with a 1-pixel `c.outline` border; photos draw the decoded bitmap with `ContentScale.Crop`; documents and photos that fail to decode draw `PixelIconView(FILE or IMAGE, tint = c.accentSoft, size = Space.xl)` centred on `c.surface`); then a Column: the name (`Type.body` semibold, `maxLines = 1`, ellipsis; the full name is one tap away because the tap opens the chat and the More sheet's title is the full name), the meta line `t(lib_file_meta, kindName, sizeText, relativeTime(addedAt))` (`Type.label`, muted, `maxLines = 2`), the chat line `t(lib_file_chat, chatTitle)` (`Type.label`, accent, `maxLines = 1`, ellipsis); then, with X4, `PixelIconButton(PixelIcon.MORE, onClick = onMore, contentDescription = t(lib_file_more_cd, file.name), modifier = Modifier.testTag(LibraryTags.more(i)))`. The row is `clickable(onClickLabel = t(lib_file_goto), role = Role.Button)`: `ChatScrollTarget.set(file.conv.id, file.messageId)`, then `nav.go(Route.Chat(file.conv.id))` (the chat opens scrolled to the message; Back returns to the Library). The row is one merged semantics node (name, meta, chat), so a screen reader reads "trip-map.jpg, Photo 214 KB Yesterday, In Trip plan, Open the chat this file came from".
- **`chatTitle`** = `titleOf(file.conv, botsById, t(R.string.ui_chats_untitled))`, computed in the screen, never in a lambda.
- **Thumbnails**: `rememberLibraryThumb` is `produceState<ImageBitmap?>(null, key)` that decodes on `Dispatchers.IO` through `PocketApp.from(ctx).attachments.loadThumbnail(key, THUMB_PX)` (`THUMB_PX = 144`, a named `const val`: 48 dp at a density up to 3) and keeps the result in a private `android.util.LruCache<String, ImageBitmap>(48)` (about 4 MB at most) so a fast scroll does not decode twice. Only rows that are composed (visible plus the lazy list's prefetch) decode. Synthetic pinned rows have no file and stay on the icon.
- **Text and spoken labels**: no row draws a raw enum name; `kindName` is `t(lib_kind_photo)` or `t(lib_kind_document)`.

More menu (X4): `ActionSheet(file.name, listOf(SheetAction(PixelIcon.FILE, openLabel) { ... }, SheetAction(PixelIcon.SHARE, shareLabel) { ... }, SheetAction(PixelIcon.CHATS, chatLabel) { ... }), onDismiss)`; the three labels (`lib_act_open`, `lib_act_share`, `lib_act_chat`) and the failure text are computed in the shelf composable BEFORE the sheet is composed (the Wave 0 phone proof found text inside Dialog windows following the system language: texts handed in as strings are safe). `LibraryOpen.view` or `send` returning false shows `PetNotes.show(openNone, PetNoteKind.ERROR)`.

### 3.10 The Saved shelf and the extraction of `StarredScreen.kt`

`SavedShelf(scan, removed, onUnstar, botsById, modifier)` draws `StarredList(...)` over `scan.saved` filtered by `removed`; empty, loading and error as in 3.8.

`J/ui/chat/StarredScreen.kt` becomes (behaviour of the old screen unchanged, line for line, except the picture and the list animation):

```kotlin
@Composable fun StarredScreen()      // same signature; same top bar, loading blank, empty state (now with art = "empty_jar"), same unstar logic
@Composable internal fun StarredList(
    items: List<Pair<Conversation, Message>>,         // never null: the caller handles loading and empty
    botsById: Map<String, Bot>,
    onUnstar: (Conversation, Message) -> Unit,
    modifier: Modifier = Modifier,                    // goes on the LazyColumn
    contentPadding: PaddingValues = PaddingValues(),
    listTag: String? = null,                          // the Library passes LibraryTags.LIST
    rowTag: (index: Int) -> String? = { null },       // the Library passes LibraryTags::row
)
internal fun unstarInBackground(scope: CoroutineScope, repo: ChatRepository, convId: String, messageId: String)
//   = scope.launch(Dispatchers.IO + NonCancellable) { repo.setStarred(convId, messageId, false) }   (today's line 94, moved)
```
`StarredList` owns the texts the rows need (`ui_chats_untitled`, `ui_starred_you`, `ui_chat_deleted_bot`, computed inside it), the `LazyColumn`, the item key `cv.id + ":" + m.id`, the tap (`ChatScrollTarget.set` then `LocalNavigator.current.go(Route.Chat(cv.id))`), the unstar button, and `Modifier.animateItem(...)` as above (the old screen had none; pattern 3 names saved messages explicitly). `StarredRow` gets a `modifier: Modifier = Modifier` first optional parameter for the tag. The `removed` set and the call to `unstarInBackground` stay with the caller in both screens. No new `N.dp` literal, no `.uppercase(` is added to this old file (its ratchet counts do not rise: it keeps its existing literals); new lines use tokens. `Route.Starred`, `screen.starred`, `StarredScreen()` in `AppRoot` are unchanged.

### 3.11 Motion, touch, sound (by token name)

| Moment | Token |
|---|---|
| Shelf switch: body cross-fade | `Pocket.motion.fast()` (`Dur.FAST`), DL pattern 8; no slide |
| Rows enter, leave, move | `animateItem` with `Pocket.motion.fast()` (fade) and `Pocket.motion.glide()` (placement), pattern 3 |
| Skeleton shimmer | the component's own `Loop.SHIMMER`, pattern 18; still at REDUCED and OFF |
| Shelf tab and chip choice | `HapticCue.TICK` (chips give it themselves; the tab calls `rememberHaptics().play(HapticCue.TICK)`) |
| Unstar | `HapticCue.TICK` |
| Sound | none (the Library is silent; no `SoundCue` is added) |
| Entrance, flourish, Pêt note | none; at animation level OFF everything snaps |

### 3.12 Texts: `R/values/strings_library.xml`, prefix `lib_` (English only; LANG stage 4 translates; every key carries its `<!-- t: ... | kind: ... -->` note; apostrophes `\'`; no leading or trailing space in a string)

Existing keys reused (already in tr and fr, nothing to translate): `ui_filter_all`, `ui_starred_empty_title`, `ui_starred_empty_body`, `ui_starred_you`, `ui_act_unstar`, `ui_chats_untitled`, `ui_chat_deleted_bot`, `ui_chat_empty_reply`, `ui_chat_retry`, `ctl_loading`.

| Key | English | Note: kind and room |
|---|---|---|
| `lib_title` | Library | title, up to 28; the top bar of the Library tab (the tab's own label is SHELL's `shell_tab_library`) |
| `lib_shelf_files` | Files | chip, up to 18; the first shelf: photos and documents the user gave to the agent |
| `lib_shelf_saved` | Saved | chip, up to 18; the second shelf: saved messages |
| `lib_shelf_cd` | %1$s, %2$s | body; spoken label of a shelf tab: %1$s the shelf name, %2$s its count as text (from `lib_files_count` or `lib_saved_count`) |
| `lib_files_count` (plurals) | one: %1$d file; other: %1$d files | body; %1$d the number of files; Russian needs one, few, many, other |
| `lib_saved_count` (plurals) | one: %1$d saved message; other: %1$d saved messages | body; same |
| `lib_files_summary` (plurals) | one: %1$d file · %2$s; other: %1$d files · %2$s | body, one line; %1$d the number of files shown, %2$s their total size (such as 38.2 MB, units are not translated) |
| `lib_filter_photos` | Photos | chip, up to 18 |
| `lib_filter_documents` | Documents | chip, up to 18 |
| `lib_filter_none_photos` | None of your files are photos. | body, one line; shown when the Photos filter matches nothing |
| `lib_filter_none_documents` | None of your files are documents. | body, one line |
| `lib_kind_photo` | Photo | row; first word of a file's meta line |
| `lib_kind_document` | Document | row |
| `lib_file_meta` | %1$s · %2$s · %3$s | row; meta line of a file: %1$s Photo or Document, %2$s size, %3$s when it was given (a time or a date); may take two lines |
| `lib_file_chat` | In %1$s | row; names the chat the file was first given in; %1$s is the chat's title, cut by the screen with an ellipsis |
| `lib_file_goto` | Open the chat this file came from | body; spoken action of a file row |
| `lib_files_empty_title` | No files yet | title, up to 28 |
| `lib_files_empty_body` | Attach a photo or a document in a chat and it will rest here. | body, 2 lines |
| `lib_action_chats` | Open chats | button, up to 22; the one action that fills either empty shelf |
| `lib_error_title` | Could not read the Library | title, up to 28 |
| `lib_error_index` | The list of your chats could not be read, so the shelves are empty for now. Nothing was deleted. Try again. | body, 3 lines; what happened, what was kept, what can be done |
| `lib_error_scan` | A shelf could not be read. Your chats and files were not changed. Try again. | body, 3 lines |
| **Extras** | | |
| `lib_orphans` (plurals) | one: %1$d file from a deleted chat is still on this phone (%2$s). other: %1$d files from deleted chats are still on this phone (%2$s). | body, up to 2 lines; X1 |
| `lib_sort_title` | Sort files | title, up to 28; the sheet's title (X2) |
| `lib_sort_chip` | Sort: %1$s | chip, up to 18 (%1$s is one of the next three) |
| `lib_sort_newest`, `lib_sort_name`, `lib_sort_size` | Newest, Name, Largest | row (chip when after "Sort:") |
| `lib_file_more_cd` | More actions for %1$s | body; spoken label of the More button, %1$s the file name (X4) |
| `lib_act_open` | Open the file | row |
| `lib_act_share` | Share the file | row |
| `lib_act_chat` | Go to the chat | row |
| `lib_open_none` | No app on this phone can open this file. | pet, up to 80 |

29 keys (3 plurals). Quantities required per language: English one and other; Spanish, French, Italian one and other (many optional); German, Portuguese, Turkish, Kurmanji one and other; Russian one, few, many, other; Indonesian other only (`strings-check` enforces this once LANG's plural check is in). No capital is made by code; "Library", "Files", "Saved" are written as such by the author, and a sentence-case heading is the translator's.

### 3.13 Stored data and migration

None. The Library adds no preference, file, database field or message field; it reads message files and the attachments folder and never writes them (the unstar of the Saved shelf is the existing `ChatRepository.setStarred`, one message per call, exactly as the old screen does it). Nothing the previous build could misread exists, so the rollback APK is unaffected and an existing installation needs no migration: the first start after the update shows the Library with whatever attachments and saved messages it already has. Reading on the owner's phone writes nothing. `rememberSaveable` state lives in the activity's saved state only.

## 4. Unit tests (JUnit 4, JVM; time, locale and files are parameters; repo files read with `Assume.assumeTrue(file.exists())`)

`T/data/LibraryIndexTest`:
- `baseNameTakesTheLastSegment` ("/data/user/0/p/files/chat/attachments/ab12.jpg", a Windows path with backslashes, "plain.pdf", "", "a/").
- `flatNameRule` (accepts `ab12.jpg`, `ab_notes-1.txt`; rejects `.hidden`, `a b.txt`, `../x`, a 201-character name, "").
- `kindMapsFromAttachmentKind`.
- `oneRowPerFileAndTheOldestMessageWins` (the same key in two chats, the fork case; the older message's chat is `conv`).
- `tieBreaksByMessageId` (equal `createdAt`).
- `sizeComesFromTheFileNotTheMessage` (`sizeOf` returns 10, the stored `sizeBytes` is 99).
- `missingFilesAreDroppedAndCounted` (`sizeOf` null for one key; `missing = 1`; not in `files`; not in `counts`).
- `unsafeKeysAreNeverListed` (a path whose base name fails the flat rule counts as missing and `sizeOf` is not called for it).
- `capKeepsNewestAndFlagsCapped` (`max = 2` of 3 files; `capped = true`) and `scanOfMaxMessagesIsCapped` (5,000 messages gives `capped = true`).
- `sortNewestIsStable` (equal times ordered by key), `sortByNameUsesTheCollator` (English: "b.txt", "A.txt", "c.txt" gives A, b, c; Turkish `Locale("tr")`: "Ilk.txt" and "ılk.txt" order by the collator, not by code point), `sortBySizeLargestFirstThenNewest`.
- `filtersPhotosAndDocuments` and `countsMatchTheFilters` (`all`, `photos`, `documents`, `totalBytes`; `of(filter)`).
- `orphansAreTheFilesNobodyReferences` (names and lengths, a referenced name is not counted) and `orphansEmptyFolder`.
- `sizeText` table: 0 gives "0 B", 1023 "1023 B", 1024 "1 KB", 1536 "1 KB" (floor), 1048575 "1023 KB", 1048576 "1.0 MB" (English) and "1,0 MB" (`Locale("tr")`), 1468006 "1.3 MB" (floor of 1.4), 2147483648 "2.0 GB", -5 "0 B".
- `libraryScanWithoutSavedDropsOneRow` (`withoutSaved`).

`T/data/LibraryPinTest`: `parseAcceptsEveryItem` (`files:12,saved:3,orphans:2,state:error`); `absentCountsAreZeroAndStateIsReady`; `lastRepeatedKeyWins`; `rangesAreEnforced` (`files:3001`, `saved:301`, `orphans:1000` rejected; 3000, 300, 999 accepted); `anyBadItemRejectsTheWhole` (`files:3,bogus:1`; `state:slow`; `files:-1`; `files:abc`; empty string; case: `FILES:3` is rejected, lower case only); `syntheticIsDeterministicAndOrdered` (two calls equal; `addedAt` strictly decreasing; keys unique; kinds mix; sizes at least 100); `syntheticSavedRowsAreStarredAndNewestFirst`; `syntheticChatsNeedNoRepository` (every `conv.botIds == ["hermes"]`, blank titles); `holderPinsAndUnpins` (`LibraryDebug.pin` then null).

`T/data/LibraryMessagesTest` (the method of R5; same helper pattern as `ChatRepositoryTest`: `ChatRepository(dir, CoroutineScope(Dispatchers.IO), 20L)`, `TemporaryFolder`): `starredPartEqualsStarredMessages` (in memory and after reopening from files); `attachedIsNewestFirst`; `attachedIsCappedAt5000` (5,001 messages with one attachment each); `chatsNotLoadedAreReadFromFilesWithoutKeepingThem`; `messagesWithoutAttachmentsAreNotListed`; `aMessageBothStarredAndAttachedIsInBoth`.

`T/ui/library/LibraryShelvesTest`: `parseUnknownGivesFiles` (null, "", "made", "SAVED" is unknown: lower case keys only, "saved" is Saved); `orderIsFilesThenSaved`; `firstTwoAlwaysShown` (both counts 0 and null); `aLaterShelfNeedsItems` (a third entry with count 0 is hidden, with 1 shown, with null hidden; the rule is exercised on a hand-made `ShelfEntry` list); `entriesFollowTheCountsMap`; `artPrefersTheFirstExisting` (`empty_basket` missing, `tile_library` present: gives `tile_library`; none present gives null; error gives `empty_cup` first; SAVED gives `empty_jar`); `statePrecedence` (failed beats loading beats empty beats content, all eight combinations with `count` 0 and 3); `tagsAreWellFormedAndUnique` (all `LibraryTags` constants and the function outputs match `^library(\.[a-z]+)+(\.[0-9]+)?$` and are distinct; `shelf("x")` is `library.shelf`; `filter` covers all three enum values); `tagsDoNotCollideWithHomeAndNav` (none starts with `home.` or `tab.`).

Existing tests that must stay green: `ChatRepositoryTest` (untouched), `AppLinksTest` and `DebugLinksTest` (SHELL edits them), `ScenarioFilesTest` (INTEGRATE-B adds the tags of section 5).

## 5. Proof on the phone (owner-debug is "safe": pins and pictures only, the Library reads and writes nothing there; the twin for what needs files or a wipe) and what only the owner's eyes can judge

Pins I need (all in memory, `TEST_HOOKS` builds only, none writes): `route library`, `route library:saved`, `route library:files` (SHELL), the new extra **`library`** with the grammar of 3.5 (R2), and the existing `lang`, `rtl`, `theme`, `dark`, `reset` of Wave 0 and 1a. Link form (the robot adds the token): `adb shell am start -a dev.pocketagent.app.DEBUG -n dev.pocketagent.app/dev.pocketagent.app.MainActivity --es route library --es library files:24,saved:7 --es theme newroz --es dark 1 --es token <T>`.

Scenario `w1b-library` (safe: it reads, navigates and takes pictures; it uses only the link, `tap`, `wait`, `expect`, `swipe`, `shot`, `a11y`, `scanDump`; the lead writes it after INTEGRATE-B and adds it to `safeNames`):

1. `link` `{reset: 1, route: library, library: "files:24,saved:7", theme: newroz, dark: 1}`; `wait` tag `screen.library`; `wait` `library.list`; `expect` `library.shelf.files` `selected: true`; `expect` count of `library.row.*` at least 3; `shot lib-files-newroz`; `a11y`.
2. `swipe` up twice, `expect` `library.row.9`; `shot lib-files-scrolled`.
3. `tap` `library.filter.photos`; `expect` `library.row.0`; `shot lib-photos`; `tap` `library.filter.documents`; `shot lib-documents`; `tap` `library.filter.all`.
4. `tap` `library.shelf.saved`; `wait` `library.row.0`; `expect` `library.shelf.saved` selected; `shot lib-saved`; `a11y`; `tap` `library.shelf.files` back.
5. States: `link` `{library: "files:0,saved:0"}` then `route library`: `expect` `library.empty` and `library.empty.action`, `shot lib-empty-files`; tap `library.shelf.saved`, `expect` `library.empty`, `shot lib-empty-saved`. `link` `{library: "state:loading"}`: `expect` `library.skeleton`, `shot lib-loading`. `link` `{library: "state:error"}`: `expect` `library.error`, `library.retry`, `shot lib-error`; `tap` `library.retry`; `expect` `library.error` still there (a pinned error stays an error).
6. Light theme: `link` `{theme: nergiz, dark: 0, library: "files:24,saved:7"}`; `shot lib-files-nergiz`; `tap` `library.shelf.saved`; `shot lib-saved-nergiz`.
7. Languages and layout: `link` `{lang: tr}`, `{lang: fr}`, `{lang: en-XZ}` with `scanDump` on the en-XZ pass, `{rtl: 1}`; one `shot` each of the Files shelf; later (after stage 4) `{lang: ru}`, `{lang: es}`, `{lang: de}`, `{lang: pt}`, `{lang: it}`, `{lang: in}` (tag `id` folder, E-B2) and `{lang: ku}` for item 2 of the SPLIT proof (the five main screens in ten languages). Cut check at font 2.0 only with the lead's `-AllowSystemSettings`.
8. Large list: `link` `{library: "files:3000,saved:300"}`; `swipe` loop; `phone-perf` scenario `library-scroll` (the lead may add as P8: frames within 8.3 ms, budget of DL 11 "a table of 3,000 rows": at least 95 percent, none over 33 ms; measured on the optimised twin, owner-debug for the record).
9. Real data (read only): `link` `{library: off, route: library}`: `expect` no crash, `shot lib-real` (never committed; pictures stay in `dist/`, which is git-ignored; they may show the owner's file names). Compare the counts with `dist\owner-backup\` by reading that folder only.
10. Tap and open: needs a real row, so **twin only**: a seed `library:<files>:<saved>` (R7) then `tap` `library.row.0`, `expect` `screen.chat` and the log `I/Nav: top=chat:seed-...`, `key BACK`, `expect` `screen.library`; `tap` `library.more.0` (X4), `expect` `sheet`, `tap` `sheet.action.2` (Go to the chat). Without R7 this step is skipped and the row tap is proved by eye on the owner's real data only when one file exists.

New tags for `ScenarioFilesTest` known tags (INTEGRATE-B): `library.shelves`, `library.list`, `library.summary`, `library.empty`, `library.empty.action`, `library.skeleton`, `library.error`, `library.retry`, `library.orphans`, `library.sort`, and the patterns `library\.shelf\.(files|saved)`, `library\.filter\.(all|photos|documents)`, `library\.row\.\d+`, `library\.more\.\d+`; `screen.library` and `tab.library` are SHELL's.

Also on the phone: the five-state pictures in NEWROZ dark and NERGIZ light; one at double text size; one mirrored; one in the stretched language with no cut text; `dumpsys` shows no new permission; `git diff` of the manifest for the slice is empty; `app.log` has the `I/Library: scan ...` line and no error.

**What only the owner's eyes can judge:** whether the shelf tabs read as tabs and not as chips; whether a photo row looks right with its 48 dp thumbnail (taste: bigger thumbnails, a grid of pictures, belongs to the Pictures shelf of a later wave); whether the empty pictures (a basket, a jar) fit; whether the Library feels alive enough with only two shelves; whether tapping a photo should open the chat (assumed, owner may change: SPLIT 4.4 says the chat, the file itself is in the More sheet, an extra). The reviewer's contact-sheet judgement of the new sprites is ART-B's (`docs/waves/w1b/look/`).

Cost (budgets: DL 11): a scan reads every conversation's message file once; for 100 chats of 200 KB that is about 20 MB of JSON, estimated 300 to 600 ms on this phone, all on the IO dispatcher, with the skeleton (or, with X3, the last result) on screen meanwhile; to be measured with the `I/Library` line on the twin with `seed chats:...` (and the real data on the owner's phone read only). The old screen does the same pass for 300 starred messages today. Thumbnails: 144 px decode on IO, an LRU of 48. Nothing runs while the tab is not on screen; no timer, sensor or network.

## 6. Core and extras, and the order of cutting (first cut first)

Core: `LibraryScreen` and `LibraryShelf.FILES/SAVED`, both shelves with counts, the five states as 3.8 says, filter chips for Files (All, Photos, Documents), thumbnails, `StarredList` and the extraction, `ChatRepository.libraryMessages`, `LibraryIndex`, tags, `LibraryDebug` and its pin (a cut keeps the symbols with empty bodies: SHELL calls `LibraryDebug.pin`), strings with plurals, the tests of section 4, `animateItem`.
Extras, cut in this order: **X1** the orphans line (`LibraryOrphans` and `LibraryIndex.orphans` stay, tested, unused); **X2** the sort chip and sheet (`LibrarySort` stays, NEWEST is used); **X3** `LibraryMemo` (stale-first); **X4** the More button and sheet with open and share (`LibraryOpen` removed; this is the SPLIT cut list's item 8, "the Library's second action on a file"); after those, nothing else is cut. Never cut: the Library route's content (a screen with two shelves), the gate against writing (the Library never writes), `Route.Starred` working, the five states, the spoken labels.

## 7. REQUESTS to other areas (exact) and what I provide

**Requests**

- **R1, SHELL.** (a) `Route.Library(val shelf: String? = null)` with keys `library` and `library:<shelf>` (the shelf text is kept as written; my `LibraryShelf.parse` handles any text), `tabIndex()` 2, `showsBar()` true, `NavStack.rootIdFor(Route.Library(any shelf))` = the one stable id 6 (so `nav.tab(Route.Library("saved"))` over `Route.Library(null)` keeps the entry id: no transition, same saved state), `NavTags.TAB_LIBRARY = "tab.library"`, `NavTags.screen(Route.Library) = "screen.library"`. (b) `RouteContent`: `is Route.Library -> LibraryScreen(route.shelf)` (import `dev.pocketagent.app.ui.library.LibraryScreen`); `Route.Starred -> StarredScreen()` stays. (c) `DebugLinks.stackFor(Route.Library(...)) = listOf(route)`. (d) The tab uses `PixelIcon.LIBRARY` and the SHELL label; the bar must tolerate that a tap on the tab of the screen already shown does nothing or scrolls, my chips are what changes the route. (e) `Route.Starred` keeps counting as the Library tab for `tabIndex()` as the SPLIT says.
- **R2, SHELL.** A third debug extra after `whatsnew` in `DebugRequest`: `const val EXTRA_LIBRARY = "library"`; parameter `library: Setting<LibraryPin> = Setting.Keep` appended LAST (after `firstrun` and `whatsnew`); in `parseParts`: `off` gives `Setting.Clear`, otherwise `LibraryPin.parse(text)` or `rejected += "library"`; in `apply`, after the other pins: `Keep` nothing, `Clear` `LibraryDebug.pin(null)`, `Set` `LibraryDebug.pin(value)`; `reset=1` also calls `LibraryDebug.pin(null)`; the name goes into the read list of `DebugLinks.parse`; `DebugLinksTest` gets a case per form (`files:12,saved:3`, `off`, a bad value rejected). Imports `dev.pocketagent.app.data.LibraryDebug` and `LibraryPin`.
- **R3, BARS.** (a) The Home Saved tile target becomes `nav.tab(Route.Library(LibraryShelf.SAVED))` (or the literal `"saved"`); the star button of the Chats top bar and `Route.Starred` stay as they are. (b) `EmptyState(title, body, modifier, action, art: String? = null)`: I call it with named arguments `art = ...`; when `art` is given and `SpriteBank.find(art)` is not null it draws that sprite instead of Pêt, otherwise Pêt as today. (c) The one `art =` argument in the old `StarredScreen` call is MINE (the SPLIT says the StarredScreen call belongs to LIBRARY); BARS does not touch `StarredScreen.kt`.
- **R4, ART-B.** Sprite names I look up with `SpriteBank.find` (all optional, each with a fallback): `empty_jar` (the Saved shelf's empty picture, 32 by 32, as 1a's ART-WORLD planned it for the saved screen: a jar with two stars and room for more), `empty_cup` (the Error picture), and **one new picture `empty_basket`** (32 by 32, 2 frames like the other empties: an empty wicker basket by a cold stone with a tiny flame spark, for "no files yet"; fallback chain `tile_library` then `empty_scroll`); plus `tile_library` and `PixelIcon.LIBRARY` which the SPLIT already gives ART-B. If 1a's LOOK kept `empty_jar` and `empty_cup`, nothing to draw for those.
- **R5, LEAD (ownership of one existing file).** `J/data/ChatRepository.kt` belongs to nobody in 1b: allow LIBRARY exactly one ADDED method `libraryMessages()` (code in 3.2; it uses `LibraryMessages` and `LibraryIndex.MAX_SAVED` and `MAX_ATTACHED_MESSAGES` of `LibraryIndex.kt`) and the new test file `T/data/LibraryMessagesTest.kt`; no existing line changes. **If refused**, `LibraryFiles.forApp` uses this fallback instead, which needs no edit but keeps every chat's messages in memory after the first Library visit (`messagesSnapshot` keeps them): `source = { val a = app.chat; val rows = a.conversations.value.flatMap { c -> a.messagesSnapshot(c.id).map { c to it } }; LibraryMessages(starred = rows.filter { it.second.starred }.sortedByDescending { it.second.createdAt }.take(LibraryIndex.MAX_SAVED), attached = rows.filter { it.second.attachments.isNotEmpty() }.sortedByDescending { it.second.createdAt }.take(LibraryIndex.MAX_ATTACHED_MESSAGES)) }` (one line to swap; `LibraryMessagesTest` is then dropped).
- **R6, LEAD / INTEGRATE-B (tools and tests, after the slice).** `ScenarioFilesTest` known tags (section 5); the robot's `link` op must pass the `library` debug extra through; `tools/scenarios/w1b-library.json` (steps 1 to 9 of section 5, `"safe": true`, added to `safeNames`) and `w1b-library-open.json` (step 10, twin only); `tools/scenarios/leak-allow.txt` entries for the synthetic file names (`photo-`, `paper-`, `table-`) and the text `sample` if `scanDump` flags them in the stretched language; `tools/lint-ui-baseline.json` and `tools/strings-check.baseline.json`: only entries for the new files, all at 0 (never raise `StarredScreen.kt`'s numbers); the existing scenarios that tap `tab.settings` are SHELL's list, not mine.
- **R7, LEAD / ROBOT (optional, twin only).** A `DebugSeeder` spec `library:<files>:<saved>` (files 1..40, saved 0..40) that makes ONE conversation `seed-library` with that many messages carrying tiny generated attachments (a few valid JPEG and text files written into `files/chat/attachments/` with names starting `seed-lib-`) and that many starred messages; removed by `Spec.Clear`. It gives the twin proof of "tap a row, the chat opens at the message" and of the More sheet. Without it those steps stay with the owner's eyes.
- **R8, LANG.** The 29 keys of 3.12 are translated in stage 4 into the other nine languages and `tr`/`fr`; the three `<plurals>` need the quantities of 3.12; every key carries its kind in its note, so `tools/strings-kinds.txt` needs no line for `lib_`. The reused keys already exist in `tr` and `fr`.

**What I provide** (exact; used by others)

| Symbol | Form | Consumer |
|---|---|---|
| `LibraryScreen` | `@Composable fun LibraryScreen(shelf: String?)` in `app.ui.library` | SHELL (`RouteContent`) |
| `LibraryShelf` | `const val FILES = "files"`, `const val SAVED = "saved"`, `fun parse(key: String?): String` | BARS (Home tile), SHELL (optional) |
| `LibraryDebug`, `LibraryPin` | `LibraryDebug.pin(p: LibraryPin?)`, `LibraryPin.parse(text: String): LibraryPin?` in `app.data` | SHELL (`DebugLinks`) |
| `StarredScreen` | unchanged signature | `AppRoot`, deep links |
| `LibraryTags` | section 3.3 | `ScenarioFilesTest`, scenarios |
| `ChatRepository.libraryMessages()` | `fun libraryMessages(): LibraryMessages` (if R5 is accepted) | `LibraryFiles` only |
| Strings | `lib_*` | LANG |

## 8. Risks

1. **The scan is a full read of all chats.** Large histories make it slow (estimate in section 5); it is off the main thread with a skeleton, memoised by X3, and measured on the twin. A later wave (Spaces, archive) may want an index file; not here.
2. **The Saved shelf shows at most the newest 300**, exactly as the old screen (`starredMessages()` cap); the chip count then says 300 for 400 saved messages. Accepted and documented; a lifted cap is a Wave 2 matter.
3. **Orphans and storage.** Files of deleted chats stay on the phone and nothing here can remove them (the plan's "what is stored" screen is Wave 11). X1 only says so.
4. **A forked chat shares attachment files.** The Library shows one row per file in the oldest chat; if that chat is later deleted the row moves to the next oldest, never disappears while any chat holds the file.
5. **BARS's `EmptyState(art =)` and SHELL's `Route.Library` are not in the tree when slice A is built.** Slice A has no dependency on them (pure files only); slice C compiles all of 1b together, so a missing symbol shows up at INTEGRATE-B as a compile error with a one-line fix.
6. **`nav.tab` as the shelf switch** depends on `rootIdFor(Library) = 6` for every shelf (R1). If SHELL gave each shelf its own id, a tap on a shelf tab would play a screen transition; the scenario (`expect` stays on `screen.library`) still passes but the picture shows a cross-fade of the whole screen. The fix is one line in `NavStack`.
7. **Sheets at normal animation speed** were invisible on the phone proof of Wave 0 (fix in progress, `docs/waves/w0/phone-proof.md` defect 1); only the More sheet (X4) and the sort sheet (X2) use one, and the core path (tap a row, tap a shelf) does not.
8. **Dialog text in the system language** (defect 2 of that proof): my sheets get their labels as strings computed in the composable, so they are unaffected whichever way it is fixed.
9. **Synthetic file names are English-looking data** in the stretched-language `scanDump`: R6 adds them to `leak-allow.txt`.
10. **A cut scene.** The Library has no scene; a missing sprite only changes the empty picture to Pêt.
11. **`ChatRepository` edit** is additive and tested; if two agents touch the file in 1b the lead must serialise them (nobody else is named).
12. **Right-to-left**: rows and tabs mirror by Compose; thumbnails, sizes and file names stay left-to-right text inside their own runs (DL 13.4 applies to the user's file names: a name in Arabic in a Latin UI sets its own direction by its first strong letter, which Compose does by default).
13. **Kotlin traps for an agent that cannot compile**: no slash-star inside a comment ("the attachments folder", not a path ending in a star); declare the `LazyListState` and every `remember` before the `LazyColumn`; `0xFF...` colours need `.toInt()` (none are used); `Crossfade` and `animateItem` take `Pocket.motion.fast()` with the type inferred (`animationSpec = Pocket.motion.fast()`); `import androidx.compose.ui.semantics.Role` conflicts with `app.data.Role` in files that use both: alias one (`StarredScreen.kt` already aliases the semantics one as `SemRole`; keep that); `PaddingValues()` not `PaddingValues(0.dp)` (the ratchet counts the literal); no `by lazy` before `init` use; format sizes and numbers with an explicit `Locale`.

## 9. Open points for the lead

1. **R5: who may add `ChatRepository.libraryMessages()`** (default: LIBRARY adds it, 18 lines and a test; the fallback of R5 needs no edit but keeps all messages in memory).
2. **R7: the twin's library seed** (default: the lead's robot pass writes it; without it the "tap a row opens the chat" proof is by eye on the owner's real data).
3. **Taste for the owner (assumed, owner may change):** tapping a file row opens its chat (SPLIT 4.4); opening or sharing the file itself is the extra More sheet. The alternative is to open the picture in the existing preview for photos.
4. **The Chats top bar's star button** keeps `Route.Starred` (default); pointing it at `Route.Library("saved")` is a Chats change for Wave 2. Say if 1b must do it (a one-line BARS or SHELL edit in `ChatListScreen.kt:138`).
5. **Files means "given by the user".** Attachments on BOT messages (nothing makes them today) would also be listed; Wave 6 will separate them into the Made shelf. Default: list every attachment of every message until then.
