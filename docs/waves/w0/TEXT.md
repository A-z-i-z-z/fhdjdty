# Wave 0, build area TEXT: contract draft (feature 0.11, "One function for every text")

Draft by the TEXT drafter, 2026-11-25. Written for the PASS case. Everything below was checked against the tree at commit `344829f`; counts and line numbers come from commands run for this draft. Package of all new Kotlin: `dev.pocketagent.app.ui.text` (folder `app/src/main/java/dev/pocketagent/app/ui/text/`, called `T/` below). `R` = `dev.pocketagent.app.R`.

## 1. Scope

All items are core. Cut order if the session runs out is marked CUT 1, 2, 3.

| # | Work item | What it is |
|---|---|---|
| W1 | Core, pure | `TextKey`, `TextSource`, `LayeredTexts`, `TextFormat`, `PluralRules` (no `android.*`, no Compose: JVM-testable) |
| W2 | Facade | `Texts` object (context-free and context forms), `t()` and `tn()` composables, `ProvideTexts`, language choice, env cache, `install`, `describe` |
| W3 | Packs | Pack file format, parser and validator (`LanguagePacks`), `PackTextSource`, `PackStore` (`files/lang/<tag>.json`). No UI makes packs in Wave 0; the layer must work when a file is dropped there |
| W4 | Protected texts | `assets/text/protected-keys.txt` and `ProtectedTexts`; a pack entry for a protected key is dropped at load |
| W5 | Engine texts | `res/values/strings_engine.xml` (+ `values-tr`, `values-fr`): 56 interface keys (section 5.1) plus the label keys of W9 |
| W6 | Notes and kinds | Translator-note comment format; `tools/strings-kinds.txt` (key globs to kind of control) |
| W7 | `tools/strings-check.ps1` | The CI check (section 3.6), also `-ScanDump` for the robot |
| W8 | Stretched language | `tools/make-test-language.ps1` (debug only), `TestLanguage` constants |
| W9 | Label keys. CUT 1 | `EnumLabels` + 75 keys (`eng_icon_*`, `eng_avatar_*`). Loses: icon-only buttons without a description and the avatar picker (`PixelButton.kt:178`, `BotEditorScreen.kt:408-409`) keep speaking English enum names |
| W10 | Mirroring table | `Mirroring` (which icons turn in a right-to-left layout) |
| W11 | Plurals. CUT 3 | 10 count-bearing keys in `strings_chat.xml` and `strings_control.xml` (en, tr, fr) become `<plurals>` (5.3). Loses: "1 bots" stays |
| W12 | Capitals. CUT 3 | Section-header keys are written in capitals by their author so `SectionHeader.kt:37` can stop calling `uppercase(locale)` (5.4). Loses: the case change in code stays |
| W13 | Runtime and voice texts. CUT 2 | The 20 keys `eng_rt_*`, `eng_tts_*`. They sit in `runtime/` and `voice/`, which no apply-agent owns (section 11, item 1). Loses: Home's red error line, the terminal's start-up lines and the voice error stay English |
| W14 | Tests | Section 8, plus `TestTexts` (test helper) |

## 2. Files

I own all of these exclusively. Exception, justified: no other area owns the string files, so W11 and W12 edit named lines in other string files (listed).

| File | Change |
|---|---|
| `T/TextSource.kt` | new: W1 |
| `T/PluralRules.kt` | new: W1 |
| `T/LanguagePack.kt` | new: W3 (`LanguagePack`, `EnglishCatalog`, `PackResult`, `PackProblem`, `LanguagePacks`, `PackTextSource`) |
| `T/ProtectedTexts.kt` | new: W4 |
| `T/Mirroring.kt` | new: W10 (pure) |
| `T/EnumLabels.kt` | new: W9 |
| `T/ResourceTextSource.kt` | new: Android adapters `ResourceTextSource`, `ResourceCatalog` (an `EnglishCatalog` over `Resources.getIdentifier`) |
| `T/PackStore.kt` | new: file IO for `files/lang/` |
| `T/Texts.kt` | new: `object Texts`, `object TestLanguage` |
| `T/TextsCompose.kt` | new: `t`, `tn`, `ProvideTexts`, `LocalTextsRevision`. Kept apart from `Texts.kt` so unit tests that call `Texts` never load Compose |
| `app/src/main/assets/text/protected-keys.txt` | new: W4 (5.5) |
| `res/values/strings_engine.xml`, `values-tr/`, `values-fr/` | new: W5, W9, W13. I write the tr and fr files too |
| `res/values{,-tr,-fr}/strings_chat.xml`, `strings_control.xml` | edit: the 10 plural keys (5.3); section-header keys (5.4) |
| `res/values{,-tr,-fr}/strings_routines.xml` | edit: section-header keys `rtn_ideas`, `rtn_section_*` (5.4) |
| `tools/strings-check.ps1`, `tools/make-test-language.ps1`, `tools/strings-kinds.txt`, `tools/strings-check.baseline.json` | new |
| `app/src/test/java/dev/pocketagent/app/text/*` | new tests (section 8) |

Nothing else is touched: not the manifest (`android:supportsRtl="true"` is already at `AndroidManifest.xml:31`), not gradle.

## 3. Shared symbols I PROVIDE

### 3.1 For every area (compose and non-compose)

```kotlin
// T/TextsCompose.kt  -- Compose
@Composable fun t(@StringRes id: Int, vararg args: Any): String                 // = stringResource(id, *args), layered
@Composable fun tn(@PluralsRes id: Int, count: Int, vararg args: Any): String   // = pluralStringResource; caller passes count again in args when the text shows it
@Composable fun ProvideTexts(content: @Composable () -> Unit)                   // NAV puts it inside LocaleScope (section 7)
val LocalTextsRevision: ProvidableCompositionLocal<Int>                         // t/tn read it so a pack change recomposes readers

// T/Texts.kt  -- no Compose
object Texts {
    fun install(context: Context)                                                  // idempotent; first thing in PocketApp.onCreate after AppLog.init
    fun get(context: Context, @StringRes id: Int, vararg args: Any): String       // for onClick lambdas, toasts, notifications, services
    fun getQuantity(context: Context, @PluralsRes id: Int, count: Int, vararg args: Any): String
    fun text(@StringRes id: Int, vararg args: Any): String                        // no Context: engine, terminal, data classes (needs install)
    fun textQuantity(@PluralsRes id: Int, count: Int, vararg args: Any): String
    fun localeOf(context: Context): Locale                                         // chosen language, else the context's locale
    fun reloadPacks(context: Context)                                              // after a file in files/lang/ changed; bumps revision
    val revision: StateFlow<Int>
    fun describe(): String                                                         // one line for Diagnostics (3.5)
}
object TestLanguage { const val TAG = "en-XZ"; fun isAvailable(context: Context): Boolean } // true only where values-en-rXZ is in the build
```

Behaviour contract (all forms):
- Language: `localeOf(context)` = `Locale.forLanguageTag(AppearanceStore.get(context).appearance.value.language)` when that string is not blank, else `context.resources.configuration.locales[0]`. `t`/`tn` use `LocalConfiguration.current.locales[0]` (what `LocaleScope` provides, `AppRoot.kt:136-161`). `Texts.get(ctx)` therefore needs no `withAppLanguage()` (`ReplyNotifier.kt:25`); callers may leave it.
- Layers, first non-null wins: (1) the language pack of the locale (`files/lang/<tag>.json`, tag lower-case, then language only), (2) the app resources in that locale, (3) the app resources in `Locale.ENGLISH` (= `values/`). A layer whose template makes `String.format` throw is skipped. If every layer fails the result is the entry name (never throws, never blank), logged once through `AppLog.w("Text", ...)`.
- Format: `args.isEmpty()` returns the template unformatted (as `Resources.getString(id)` does, so `%%` stays `%%`); otherwise `String.format(locale, template, *args)`.
- Text is plain: no markup, no `AnnotatedString`.
- `text()`/`textQuantity()` before `install`: return `"#<id>"` (never throw). Tests call `TestTexts.installEnglish()` (3.7).
- `install` also (a) loads `protected-keys.txt` from assets, (b) self-checks that entry names survive the build (`resources.getResourceEntryName(R.string.app_name) == "app_name"`); if not, packs are switched off, `AppLog.w` says so and `describe()` shows `namesIntact=false`; built-in languages are not affected.
- Thread-safe; `Texts` holds one immutable snapshot per (locale tag, revision) in a `ConcurrentHashMap`.

### 3.2 Pure core (`T/TextSource.kt`, `PluralRules.kt`, no `android.*`)

```kotlin
data class TextKey(val id: Int, val name: String)        // id = R.string/R.plurals int; name = entry name ("ui_nav_home")
interface TextSource {
    fun string(key: TextKey): String?                    // raw template, not formatted; null = this layer has none
    fun plural(key: TextKey, count: Int): String?        // template of the form chosen for count; null = none
}
object TextFormat { fun format(locale: Locale, template: String, args: Array<out Any>): String? }  // null on IllegalFormatException
class LayeredTexts(val locale: Locale, layers: List<TextSource>, onMissing: (TextKey) -> Unit = {}) {
    fun string(key: TextKey, args: Array<out Any>): String
    fun plural(key: TextKey, count: Int, args: Array<out Any>): String
}
object PluralRules { fun category(language: String, n: Long): String }   // "zero|one|two|few|many|other"
```
`PluralRules.category` (only for pack plurals; resources use the platform's rules): `en de it es tr ku` one if n==1; `fr pt` one if n in 0..1; `ru` one if n%10==1 and n%100!=11, few if n%10 in 2..4 and n%100 not in 12..14, else many; `id` other; every other language `other` (Wave 12 extends; `many` for millions in es/it/fr/pt is left out on purpose).

### 3.3 Packs (`T/LanguagePack.kt`, pure) and the file

File `files/lang/<tag>.json`, UTF-8, at most 1,048,576 bytes:
```json
{ "format": 1, "language": "es", "name": "Español",
  "strings": { "ui_nav_home": "Inicio" },
  "plurals": { "prs_notif_running_answering": { "one": "...%1$d...", "other": "...%1$d..." } } }
```
```kotlin
class LanguagePack(val language: String, val name: String, val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)
interface EnglishCatalog { fun string(name: String): String?; fun pluralOther(name: String): String? }   // null = no such key
enum class PackProblem { NOT_JSON, WRONG_FORMAT, TOO_LARGE, TOO_MANY_ENTRIES, BAD_LANGUAGE }
sealed class PackResult {
    class Ok(val pack: LanguagePack, val ignoredProtected: List<String>, val ignoredBadPlaceholders: List<String>, val ignoredUnknown: Int) : PackResult()
    class Bad(val problem: PackProblem) : PackResult()
}
object LanguagePacks {
    const val FORMAT = 1; const val MAX_BYTES = 1_048_576; const val MAX_ENTRIES = 6000; const val MAX_VALUE_CHARS = 4000
    fun parse(json: String, english: EnglishCatalog, protected: ProtectedTexts): PackResult
    fun placeholders(template: String): List<String>        // sorted tokens %N$x and %x, "%%" excluded
}
class PackTextSource(pack: LanguagePack) : TextSource
```
`parse` rules: `format` must equal 1; `language` must be a valid BCP-47 tag and equal the file's tag (checked by `PackStore`, `BAD_LANGUAGE`); keys must match `^[a-z][a-z0-9_]{1,80}$`; an entry is dropped (and counted) when its key is protected, when English has no such key, when its value is blank or over 4,000 characters or holds a control character other than `\n`, or when its placeholders differ from English (for plurals: `other` must equal English `other`; other forms must be a subset). A plural needs an `other` form. Bad pack: ignored as a whole, logged, never crashes.

### 3.4 Protected texts and the other small tables

```kotlin
class ProtectedTexts(val exact: Set<String>, val prefixes: List<String>) {
    fun isProtected(name: String): Boolean                     // exact match or startsWith(prefix)
    companion object { fun parse(lines: List<String>): ProtectedTexts }   // '#' comments, blank lines skipped, "foo_*" = prefix
}
object Mirroring { val MIRRORED: Set<String>; fun iconMirrors(iconName: String): Boolean }   // names of PixelIcon entries
object EnumLabels { @StringRes fun icon(enumName: String): Int?; @StringRes fun avatar(enumName: String): Int? }  // null for an unknown name; CUT 1
```
`Mirroring.MIRRORED = {BACK, SEND, PLAY, SHARE}` (read from `assets/pixel/icons.pxl`: `icon_back` points left, `icon_send` and `icon_play` point right, `icon_share` is the node-and-arrow shape). STAGE or DESIGN confirm on the contact sheet. Nothing else turns; the scene never mirrors.

### 3.5 Android adapters (not covered by JVM tests)

```kotlin
class ResourceTextSource(resources: Resources) : TextSource      // getString/getQuantityText, null on NotFoundException
class ResourceCatalog(resources: Resources, packageName: String) : EnglishCatalog
object PackStore { fun dir(context: Context): File; fun load(context: Context, tag: String, english: EnglishCatalog, protected: ProtectedTexts): PackResult? }  // null = no file
```
`Texts.describe()` format: `texts: locale=tr layers=pack(tr,412 entries)+res+en namesIntact=true protected=41 ignored=3`.

### 3.6 Scripts (Windows PowerShell 5.1 and `pwsh` 7; UTF-8 without BOM, LF; no `&&`, no ternary)

`powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1 [-ResDir app\src\main\res] [-Factor 1.4] [-WriteBaseline] [-FailOnUnused] [-Quiet]`
`powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1 -ScanDump <uiautomator-dump.xml> [-Allow <regex-file>]`
Exit codes: `0` pass (warnings allowed); `1` at least one error; `2` a ratchet got worse (warnings above `tools/strings-check.baseline.json`); `3` bad call or unreadable file; `4` (only `-ScanDump`) leaks found. Output: `ERROR <check> <file>:<key>[:<lang>] <detail>` / `WARN ...`, last line `strings-check: languages=en,tr,fr keys=N errors=E warnings=W (baseline B)`.
Shipped languages = every `values-xx` / `values-xx-rYY` / `values-b+...` directory under `-ResDir`, except the stretched tag. Errors:
1. XML not well-formed, or an element other than `string`, `plurals`, `item`, `string-array`, `xliff:g`.
2. Parity per (file, key): a key missing in a language, or present only in a language (orphan). `translatable="false"` keys are exempt both ways. Same file names in every language.
3. Placeholders: sorted multiset of `%N$x`/`%x` equal to English (plurals: see 3.3); more than one placeholder in an English text without numbers (`%s %s`).
4. Blank translation; duplicate key within a language.
5. `values-en-rXZ` under `src/main/res` (must never be in the release build); `protected-keys.txt` names a key that does not exist; stretched files (when present under `app/src/debug/res/values-en-rXZ`) differ in keys or placeholders, or hold a text under 1.4 times English length.
6. A key in `strings_engine.xml` without a note (5.2).
7. Length: each key has a kind (annotation `kind:` in its note, else first glob match in `tools/strings-kinds.txt`, else `body` = no limit). Rooms (English characters, design language 13.2): tab 10, button 22, chip 18, title 28, row 40, pet 80, widget 14. English text over room: error. Translation over `floor(room * Factor)`: error; between room and that: counted, not an error. Length = characters with each placeholder counted as 2. Existing violations are listed in the baseline file by `-WriteBaseline` (lead runs it once, then it only shrinks).
Warnings (ratchet, count in baseline): key without note outside `strings_engine.xml`; unused key (no `R.string.<k>`, `R.plurals.<k>`, `@string/<k>` anywhere; error with `-FailOnUnused`, which the lead switches on after WORDS); count noun with a bare number (`%N$d` followed by a word of 3+ letters, units `min s h d MB KB MiB chars` excepted) in a `<string>`, unless listed in `tools/strings-kinds.txt` section `[plural-allow]` (`ctl_bak_done`, `ctl_bak_restore_confirm_body`, `ui_cmd_pet_quip1`, `ui_cmd_pet_quip3`, `ctl_budget`, `ctl_voice_downloading`: several numbers in one line, cannot be one plural). It also prints, informationally, the keys that the manifest and `res/xml` reference (`@string/`: today `app_name`, `srf_ask_agiri`, `srf_sc_*` six, `srf_tile_label`); only built-in languages can supply them.
`-ScanDump`: reads the `text` and `content-desc` of every node; a node is a leak when it has 3 or more letters and neither `[` nor `]` (the stretched language brackets every resource text) and matches no regex of `-Allow`. Prints `LEAK <text>` and exits 4.

`powershell ... -File tools\make-test-language.ps1 [-ResDir app\src\main\res] [-OutDir app\src\debug\res\values-en-rXZ] [-Tag en-XZ] [-Check] [-SelfTest]`: writes one file per English strings file, same names. Exit `0` ok; `1` error; `3` with `-Check` when the output is missing or stale (a hash of the English files is in the header comment). `-SelfTest` runs built-in assertions and exits 0 or 1. Algorithm per `<string>` and per `<plurals>` item (`translatable="false"` skipped): split the raw XML text into atoms kept verbatim (`%N$x`, `%x`, `%%`, `\\.`, `\uXXXX`, `&...;`, `<...>`) and text runs; in a text run each word gets its vowels `aeiouAEIOU` replaced by `åéîöüÅÉÎÖÜ`, then its own marked vowels are appended in order (cycling) until it has at least `ceil(1.4 * letters)` letters (a word without vowel gets `x`); the whole text is wrapped in `[` `]`. Lengthened texts never leak an unescaped `'`. Header comment: `GENERATED by tools/make-test-language.ps1; do not edit; debug builds only`. The tag `en-XZ` is language English (so plural rules stay English) with the unused region XZ.

### 3.7 Test helper (test source set)

```kotlin
object TestTexts { fun installEnglish(); fun reset() }   // reads app/src/main/res/values/strings*.xml, maps ids through R.string/R.plurals reflection, calls Texts.installForTest
```
`Texts.installForTest(source: TextSource, nameOf: (Int) -> String?, locale: Locale)` and `Texts.resetForTest()` are `internal`.

### 3.8 Recipe for WORDS: every replacement form

| Today | Becomes |
|---|---|
| `stringResource(R.string.x)` / `stringResource(R.string.x, a, b)` (814 sites in 37 files, 31 of them `stringResource(if (c) R.string.a else R.string.b)`) | `t(R.string.x)` / `t(R.string.x, a, b)`; same argument expressions |
| `stringResource(labelRes)` with an `Int` variable (`TabSpec.label`, `HermesTab.label`, `RoutineUiParts` `nameRes`, `BackupUi.Working.labelRes`) | `t(labelRes)` |
| `ctx.getString(R.string.x, a)` / `l.getString(...)` / `app.getString(...)` / `localized().getString(...)` / `appContext.withAppLanguage().getString(...)` (96 with `R.string.`, plus 5 with a variable id: `FireTileService.kt:82`, `BotShareUi.kt:91`, `BackupSection.kt:229`, `ChatScreen.kt:543,671`) | `Texts.get(ctx, R.string.x, a)` (same receiver as first argument). Never touch `prefs.getString`, `sp.getString`, `JSONObject.getString`, `Cursor.getString` |
| `ctx.resources.getQuantityString(R.plurals.x, n, n)` (`AgentService.kt`, 1 site) | `Texts.getQuantity(ctx, R.plurals.x, n, n)` |
| `Toast.makeText(ctx, R.string.x, ...)` (Int overload) | `Toast.makeText(ctx, Texts.get(ctx, R.string.x), ...)` |
| a literal listed in 5.1 (file and line given) in `data/`, `hermes/`, `runtime/`, `voice/` without a Context | `Texts.text(R.string.eng_k, args)` |
| the same with a Context in the class (`AttachmentStore.context`, `RuntimeManager.context`) | `Texts.get(context, R.string.eng_k, args)` |
| the 10 keys of 5.3 | `tn(R.plurals.k, n, n)`; with a context `Texts.getQuantity(ctx, R.plurals.k, n, n)` |
| `import androidx.compose.ui.res.stringResource` | remove; add `import dev.pocketagent.app.ui.text.t` (and `tn`, `Texts` as needed) |
| a local named `t` in scope of a new `t(` call | rename the local first: `ChatScreen.kt:500,689`, `ChatCommon.kt:260`, `ChatMessages.kt:452`, `BotsScreen.kt:133` |

Not changed by WORDS: `TabSpec`/`HermesTab` ids, `ExportLabels` defaults (`ChatLogic.kt:116-120`, the chat screen passes translated ones), `BackupException` and `BotShare` messages (the UI already maps `BackupReason` at `BackupSection.kt:212-229` and `BotImportProblem` at `BotShareUi.kt:62-91`; the English stays for logs), bot preset names and taglines, voice names, endonyms ("English", "Türkçe", "Français").

## 4. Symbols I CONSUME

| Symbol | From | Exact form |
|---|---|---|
| `AppearanceStore.get(context).appearance.value.language: String` | DESIGN (`ui/theme/Appearance.kt:39,44,102`) | exists today; `""` = system |
| `AppLog.w(tag: String, msg: String, t: Throwable? = null)` | `util/AppLog.kt:34` | exists |
| `LocaleScope` providing `LocalContext`, `LocalConfiguration`, `LocalLayoutDirection` | NAV (`AppRoot.kt:136`) | unchanged behaviour required |
| `PixelIcon`, `AvatarId` enum names | DESIGN, STAGE | only by name in tests (`PixelIcon.entries`, `AvatarId.entries` must all have an `EnumLabels` and, for `MIRRORED`, a real name) |
| `R.string.*`, `R.plurals.*` | generated | all new keys appear in `R` after my files exist |

## 5. Strings

### 5.1 File `strings_engine.xml`, prefix `eng_`. Key, English text, arguments, note. `"` is written `\"`, `'` as `\'` in XML. Text is today's text unchanged.

Group CE (ChatEngine.kt; apply with `Texts.text`):

| Key | English | Args | Note | Today |
|---|---|---|---|---|
| `eng_no_bots` | This chat has no bots (they were deleted). Add a bot to the chat first. | | Error line in a chat whose bots were all deleted | `:209,231,261,281` |
| `eng_loop_pass` | Loop mode: pass %1$d/%2$d | 1 pass, 2 total | Note line when a loop pass starts; keep "/" | `:233` (pass 1) |
| `eng_loop_refine` | Loop mode: pass %1$d/%2$d — refining… | same | Note line from pass 2; same wording as eng_loop_pass | `:237` |
| `eng_approval_send_failed` | Could not send your answer to Hermes: %1$s | 1 reason, technical, English | Error after Allow or Deny failed | `:304` |
| `eng_internal_error` | Internal error: %1$s | 1 "Class: message" | Unexpected failure; the argument is technical | `:322` |
| `eng_hermes_not_running` | Hermes is not running. Start it from the home screen, wait until it says Running, then try again. | | "Running" is the state shown on Home | `:375` |
| `eng_hermes_start_failed` | Hermes could not start. Open the home screen to see why. | | | `:389` |
| `eng_bot_not_found` | Bot not found (it was deleted). | | | `:400` |
| `eng_budget_blocked` | The daily token budget (%1$d tokens) is reached, so this message was not sent to the model. Raise or turn off the limit under Settings > Usage, or try again tomorrow. | 1 limit | "Settings" and "Usage" are the translated names of ui_nav_settings and ctl_set_usage | `:406-407` |
| `eng_model_foreign` | The model \"%1$s\" set for this chat or bot belongs to another provider, so the default model of the current provider is used instead. Change it in the chat controls or the bot editor. | 1 model id | Shown once per chat | `:423-424` |
| `eng_approval_pending` | Hermes asks permission before it runs this and is waiting for an answer:\n%1$s | 1 command, or description, or both on two lines (built in code) | The reason on an approval card. PROTECTED | `:592-593` |
| `eng_cut_off` | The answer was cut off (length limit reached). | | | `:602` |
| `eng_run_abnormal` | The run ended abnormally: finish_reason=%1$s | 1 reason code | Technical code stays | `:605` |
| `eng_run_abnormal_code` | The run ended abnormally: finish_reason=%1$s error_code=%2$s | 1, 2 codes | Same, with a code | `:605` |
| `eng_budget_warn` | Daily token budget reached: %1$d tokens used today. Hermes keeps answering; change the limit under Settings > Usage. | 1 tokens | Warning note, once per day | `:628-629` |
| `eng_empty_reply` | The bot returned an empty reply. | | | `:648` |

Stays literal English on purpose (technical or sent to the model): `HTTP ${code}: body` (`:661`, asserted by `ChatEngineTest.kt:176`), `outcome.message`, the loop prompt (`:239-240`), "Former participant" (`:103`), everything in `Transcript.kt`, `BotForge.kt`, `LegacyBotPrompts.kt`, `SlashCommands.kt` mode prompts, `Diagnostics.kt`, `ProviderConfig` summaries, `AppLog` lines.

Group TERM (hermes/TerminalSession.kt; `Texts.text`; keep the `Diag.redactor.redact(...)` around `eng_term_run_failed`):

| Key | English | Args | Note | Today |
|---|---|---|---|---|
| `eng_term_busy` | A command is still running. Wait for it or cancel it first. | | | `:232` |
| `eng_term_not_installed` | The Hermes runtime is not installed yet. Start Hermes once from the home screen so it can set itself up, then try again. | | | `:242` |
| `eng_term_cancelled` | [cancelled] | | Terminal line in square brackets; keep them | `:250` |
| `eng_term_exit_code` | [exit code %1$d] | 1 code | Keep the brackets | `:251` |
| `eng_term_run_failed` | Could not run the command: %1$s | 1 "Class: message" | | `:255` |

Group ATT (data/AttachmentStore.kt, field `context`; `Texts.get(context, ...)`; they reach a toast via `ui_attach_failed`):
`eng_attach_decode` Could not decode image (`:50`) · `eng_attach_encode` Could not encode JPEG (`:64`) · `eng_attach_too_large` File is larger than %1$d MiB (1 limit; `:100`) · `eng_attach_open` Could not open selected document (`:105`).

Group UI literals (note for all: shown on screen, one short phrase):
`eng_preview_hi` Hi! (`AppearanceSection.kt:246`, chat-bubble preview) · `eng_preview_hello` Hello (`:249`) · `eng_memory_md_used` MEMORY.md (%1$d/%2$d): (`ChatScreen.kt:617`; file name stays, args used and limit) · `eng_user_md_used` USER.md (%1$d/%2$d): (`:619`) · `eng_doc_not_created` (not created yet) (`HermesHome.kt:63`; code puts a space before it) · `eng_theme_newroz` Newroz, `eng_theme_zagros` Zagros, `eng_theme_semender` Semender, `eng_theme_nergiz` Nergiz, `eng_theme_kawa` Kawa, `eng_theme_roj` Roj (`AppearanceSection.kt:216-221` `themeName`; proper names, translators normally leave them; they remove the case changes at `SettingsScreen.kt:158` and `ChatScreen.kt:630,634`).

Group RT, CUT 2 (runtime/RuntimeManager.kt, field `context`; the text is what Home shows as `RuntimeState.Error.reason`; each argument that is an exception or a size stays technical English):

| Key | English | Args | Today |
|---|---|---|---|
| `eng_rt_delete_failed` | Could not delete the runtime: %1$s | 1 message | `:227` |
| `eng_rt_stub_rootfs` | This build contains no rootfs (stub build). Install the APK produced by CI. | | `:277` |
| `eng_rt_stamp_unreadable` | Cannot read %1$s: %2$s. The runtime was NOT reinstalled, so nothing was deleted. Fix the file's permissions, or use Delete runtime files to reinstall from scratch. | 1 file name, 2 "Class: message"; "Delete runtime files" = translated `btn_reset_runtime` | `:285-286` |
| `eng_rt_no_space` | Not enough free storage: need about %1$s, have %2$s free plus %3$s reclaimable from the old runtime | sizes, formatted | `:302` |
| `eng_rt_extract_failed` | Extraction failed: %1$s | 1 "Class: message" | `:338` |
| `eng_rt_stub_proot` | Stub build: no proot binary bundled. | | `:376` |
| `eng_rt_proot_missing` | proot binary missing: %1$s | 1 path | `:378` |
| `eng_rt_bad_config` | Invalid configuration: %1$s (fix it under Setup) | 1 list; "Setup" = translated `ctl_set_provider` | `:381` |
| `eng_rt_not_healthy` | Hermes did not become healthy within 20 minutes (process still alive; see runtime log). Press Stop, then Start. | | `:429` |
| `eng_rt_foreign_port` | Port %1$d is answered by a process that is not our Hermes (another app?). Press Start to retry on a new port. | 1 port | `:439` |
| `eng_rt_port_unannounced` | The API on port %1$d answers /health, but our Hermes never announced that port in its own output within 90 s, so the API key was NOT sent (could be another app on the port, or Hermes logging differently). Press Start to retry. | 1 port | `:442` |
| `eng_rt_key_rejected` | Hermes is up but rejected our API key (HTTP 401): %1$s | 1 body, up to 200 chars | `:453` |
| `eng_rt_exited` | Hermes exited unexpectedly: %1$s | 1 `ExitRecord.describe()`, English technical; used when `ExitRecord.hint(code)` is null | `:519` |
| `eng_rt_exited_hint` | Hermes exited unexpectedly: %1$s - %2$s | 2 hint, English technical; used when a hint exists | `:519` |
| `eng_rt_term_stub` | This build contains no proot or rootfs (stub build). | | `:694` |
| `eng_rt_term_not_installed` | The Hermes runtime is not installed yet. | | `:696` |
| `eng_rt_term_shell_failed` | Could not start the shell: %1$s | 1 "Class: message" | `:713` |
| `eng_tts_error` | System TTS error (%1$d) | 1 code (`voice/VoiceEngine.kt:459`) | |
| `eng_tts_unavailable` | System TTS unavailable | (`:469`) | |
| `eng_tts_init_failed` | System TTS init failed (%1$d) | 1 status (`:518`) | |

`RuntimeState.Starting.step`, `Stopped.reason`, `Extracting.detail` stay English: they are logic and diagnostics (`HomeScreen.kt:400` and `RuntimeSection.kt:78` match `"still starting"`).

Group A11Y, CUT 1: key `eng_icon_<enum name lower-case>` (59) and `eng_avatar_<enum name lower-case>` (16). Note: "Spoken name of an icon button or avatar when no other description is given; one or two words." English, icons: HEARTH Home, CHATS Chats, BOTS Bots, SCROLL Hermes files, TERMINAL Terminal, GEAR Settings, SEND Send, STOP Stop, PLUS Add, BACK Back, MORE More, COPY Copy, EDIT Edit, TRASH Delete, PIN Pin, GROUP Group, TOOL Tool, MEMORY Memory, SKILL Skill, SOUL Soul, SEARCH Search, REFRESH Refresh, PLAY Play, CHECK Done, CLOSE Close, PALETTE Palette, GLOBE Language, BUG Debug, DOWN Down, UP Up, SPARK Spark, SHARE Share, USER You, KEY Key, INFO Info, MIC Microphone, CLIP Attach, CAMERA Camera, IMAGE Image, FILE File, BOLT Bolt, FORGE Forge, VOLUME Volume, MUTE Mute, FORK Fork, SLIDERS Controls, FILTER Filter, APPICON App icon, WAVE Wave, MUSIC Music, CLOCK Clock, STAR Starred, STAR_OFF Not starred, BELL Notifications, CHART Chart, ARCHIVE Archive, DOWNLOAD Download, UPLOAD Upload, MOON Moon, LINK Link (the key for STAR_OFF is `eng_icon_star_off`). Avatars: the enum name in sentence case (Newt, Wolf, Fox, Owl, Eagle, Goat, Partridge, Bee, Lynx, Cat, Robot, Sun, Mountain, Horse, Bear, Dragon). A unit test (section 8) fails when an enum gains an entry without a key.

tr and fr: translated by the implementer from the English and the note. Reuse the words already in the app: Settings (`ui_nav_settings`: Ayarlar / Réglages), Home (`ui_nav_home`: Ana sayfa / Accueil), Usage and budget (`ctl_set_usage`), Provider and model (`ctl_set_provider`), Runtime (`ctl_set_runtime`), Delete runtime files (`btn_reset_runtime`). Keep brackets, `MEMORY.md`, `finish_reason`, `error_code`, `/health`, `proot` and `HTTP 401` untouched. No exclamation marks except `eng_preview_hi`.

### 5.2 Note format (new and from now on)

An XML comment directly above the element, no blank line between: `<!-- t: <what it says, where it appears, what each placeholder is, how much room> | kind: <tab|button|chip|title|row|pet|widget|body> -->`. `kind:` is optional. Every key of `strings_engine.xml` has one (error otherwise). The 925 older strings have none: warning, ratchet.

### 5.3 Plural conversions (W11): same key names, `<string>` becomes `<plurals>` in en, tr and fr

| Key | English one | English other | Call sites today (all become `tn(R.plurals.k, n, n)`) |
|---|---|---|---|
| `ui_new_selected` | %1$d selected | %1$d selected | `NewChatSheet.kt:139` |
| `ui_chat_members_count` | %1$d member | %1$d members | none (unused today) |
| `ui_unread_cd` | %1$d unread | %1$d unread | `ChatListScreen.kt:336` |
| `ui_home_unread` | %1$d unread | %1$d unread | `HomeScreen.kt:318` |
| `ctl_bots_count` | %1$d bot | %1$d bots | `BotsScreen.kt:72` |
| `ctl_bot_chars` | %1$d character | %1$d characters | `BotEditorScreen.kt:254` |
| `ctl_chars` | %1$d character | %1$d characters | `HermesDocs.kt:158` |
| `ctl_usage_turns` | %1$d reply | %1$d replies | `UsageSection.kt:127,144,211` |
| `ctl_bots_import_title` | Import %1$d bot? | Import %1$d bots? | `BotShareUi.kt:212` |
| `ctl_bots_imported` | %1$d bot imported | %1$d bots imported | `BotShareUi.kt:219` (a `ctx.getString`: `Texts.getQuantity(ctx, R.plurals.k, n, n)`) |

tr: `one` and `other` get the same text (Turkish takes no plural after a number); fr: `one` covers 0 and 1. Visible change: English "1 bots" becomes "1 bot".

### 5.4 Capitals written by the author (W12)

`SectionHeader.kt:37` upper-cases every header text in code. The keys that reach `SectionHeader(` become upper-case in all three languages: found by grep `ctl_bot_section_identity`, `_model`, `_personality`, `_voice`, `ctl_bots_share_title`, `ctl_bots_templates`, `rtn_ideas`, `rtn_section_bot`, `_model`, `_what`, `_when`, `ui_home_bots`, `ui_home_recent`, `ui_home_talk_title`; the implementer finds the rest with `rg -n -A2 "SectionHeader\(" app/src/main/java`. Turkish is written with the author's own `İ`/`I` (i becomes İ, ı becomes I), French keeps accents on capitals. A key used somewhere other than a section header gets a new key `eng_h_<name>` for the header instead of changing the old one. The result must look exactly as today.

### 5.5 `protected-keys.txt` (one name per line; `name_*` is a prefix; names checked to exist by `strings-check`)

Decisions and their words: `ok`, `cancel`, `ui_dlg_cancel`, `ui_home_btn_cancel`, `ui_chat_appr_title`, `ui_chat_appr_who`, `ui_chat_appr_once`, `ui_chat_appr_session`, `ui_chat_appr_always`, `ui_chat_appr_deny`, `prs_approval_title`, `prs_public_approval`, `prs_action_allow`, `prs_action_deny`, `eng_approval_pending`, `ui_run_term_title`, `ui_run_term_body`. Delete and Clear confirmations: `ui_dlg_delete_title`, `ui_dlg_delete_body`, `ui_dlg_delete_confirm`, `ui_dlg_clear_title`, `ui_dlg_clear_body`, `ui_dlg_clear_confirm`, `ctl_bot_delete_title`, `ctl_skill_delete_title`, `ctl_voice_delete_title`, `ctl_rt_reset_body`, `ctl_rt_reset_confirm`, `ctl_usage_clear_title`, `ctl_usage_clear_confirm`, `ctl_save_confirm_title`, `ctl_save_confirm_body`. Create and import: `ui_new_create`, `ctl_skill_create`, `ctl_bak_create`, `ctl_bots_import_confirm`. Recording line: `ui_voice_listening`. Backup warnings: `ctl_bak_never`, `ctl_bak_restore_confirm_title`, `ctl_bak_restore_confirm_body`, `ctl_bak_restore_confirm`, `ctl_bak_err_halfway`, `ctl_bak_err_safety`. Prefix for prices that later waves add: `price_*`. The file starts with a comment saying who may change it (a decision of the owner, listed in `AGENTS.md`).

## 6. Manifest entries

None. `android:supportsRtl="true"` is already present (`AndroidManifest.xml:31`); NAV leaves it. `assets/text/` is picked up by gradle without a change.

## 7. REQUESTS to other areas

- NAV: `ui/AppRoot.kt:118-120`: write `LocaleScope(appearance.language) { ProvideTexts { PocketTheme(appearance) { AppShell() } } }` and import `dev.pocketagent.app.ui.text.ProvideTexts`.
- NAV: the debug-only language link: sets `AppearanceStore.get(ctx).update { it.copy(language = <tag extra>) }`; it must accept `TestLanguage.TAG` (`en-XZ`) and `""`. Debug builds only.
- CONFIG: `PocketApp.kt`: right after `AppLog.init(this)` (line 102; the `:restart` process returns earlier, at line 100, and needs no texts): `dev.pocketagent.app.ui.text.Texts.install(this)`.
- CONFIG: `diag/Diagnostics.kt`: add `Texts.describe()` as one line in the settings section.
- BUILD: `.gitignore`: line `app/src/debug/res/values-en-rXZ/`. `tools/local-build.ps1`: before building the twin run `tools\make-test-language.ps1` (non-zero aborts). CI: before `assembleDebug` run `pwsh tools/make-test-language.ps1`, and before the unit tests `pwsh tools/strings-check.ps1` (non-zero fails the job). The release type must not drop `en` (no `resourceConfigurations`/`localeFilters` without `en`) and must keep resource entry names (no `android.enableResourceOptimizations` name collapsing, or a `res/raw/keep.xml` with `tools:keep="@string/*,@plurals/*"`); `Texts.install` detects a loss (3.1) but then packs are lost.
- DESIGN: `PixelIconView` and `PixelIcon` buttons: mirror the sprite horizontally when `LocalLayoutDirection.current == LayoutDirection.Rtl && Mirroring.iconMirrors(icon.name)`. `PixelButton.kt:178`: label fallback `EnumLabels.icon(icon.name)?.let { t(it) } ?: icon.name`. `SectionHeader.kt:37`: after W12 lands, drop `uppercase(locale)` (safe in either order).
- ROBOT: `tools/lint-ui.ps1`: add ratchet counters, exempting `ui/text/**`: `stringResource\(`, `pluralStringResource\(`, `getString\(\s*R\.string\.` and `getQuantityString\(` under `ui/`, `service/`, `routines/`, `data/`, `hermes/`, `runtime/`, `voice/`; and `\.(uppercase|lowercase|capitalize)\(|replaceFirstChar` under `ui/` (baseline is today's count, which includes logic uses). Scenario `stretched-all` (section 9). `ui/settings/RuntimeSection.kt:78` keeps the English `"still starting"` match for now.
- WORDS, lead: apply the recipe of section 3.8. Directories to add to WORDS's list: `runtime/` (`RuntimeManager.kt`, owned by CONFIG, so run after CONFIG) and `voice/VoiceEngine.kt`. Also fix the three tests that assert English (section 8, last row).

## 8. Tests (JVM, `app/src/test/java/dev/pocketagent/app/text/`; pure files import neither `android.*` nor Compose; `Texts`, adapters and `t()` are verified on the phone)

| Class . test | Proves |
|---|---|
| `TextResolverTest.firstLayerWins`, `.nullFallsThrough`, `.badFormatFallsThrough`, `.noArgsIsNotFormatted`, `.missingEverywhereReturnsName`, `.pluralUsesLayerForm` | layer order, never throws, `%%` untouched without args |
| `PluralRulesTest.table` | en/tr/fr/pt/ru/id/ku values for 0,1,2,5,11,21,22 |
| `LanguagePackTest.valid`, `.protectedDropped`, `.unknownKeyDropped`, `.placeholderMismatchDropped`, `.pluralNeedsOther`, `.tooLarge`, `.notJson`, `.wrongFormat`, `.tooManyEntries`, `.controlCharDropped`, `.packSourceUsesPluralRules` | section 3.3 rules |
| `ProtectedTextsTest.parseAndMatch`, `.shippedFileNamesExist` | comments, prefixes; every exact name of `assets/text/protected-keys.txt` exists in `values/strings*.xml` (reads the XML from disk) |
| `ResourceParityTest.keysAndPlaceholders` | en/tr/fr same keys per file, same placeholders (second line of defence behind strings-check) |
| `EngineStringsTest.notesPresent`, `.unescapedQuotesAbsent`, `.noExclamationMarks` | every `eng_` key has a note; apostrophes escaped; the voice rule |
| `EnumLabelsTest.everyIconAndAvatarHasAKey` | `PixelIcon.entries`, `AvatarId.entries` against `EnumLabels` and `R.string` reflection |
| `MirroringTest.namesAreRealIcons` | `MIRRORED` names exist in `PixelIcon.entries` |
| `TestTextsTest.englishRoundTrip` | `TestTexts.installEnglish()` then `Texts.text(R.string.eng_no_bots)` equals the XML text |
| (others' tests, WORDS fixes) | add `@Before TestTexts.installEnglish()` to `ChatEngineTest` (asserts "another provider" at `:131`), `RuntimeManagerInitTest` (`:89` asserts "Cannot read rootfs.stamp"), `HermesHomeTest` (`:169` asserts "not created yet") |

## 9. Robot hooks

No screens, so no `testTag`. Hooks: constant `TestLanguage.TAG = "en-XZ"`; NAV's debug language link (section 7); robot scenario `stretched-all` visits every route with the language set, takes the screenshot and the dump, and runs `tools\strings-check.ps1 -ScanDump <dump> -Allow tools\scenarios\leak-allow.txt` (ROBOT owns the allow file: bot names, chat content, numbers). Exit 4 fails the scenario. Practice-model keywords that show TEXT's texts: `ASK_APPROVAL` (`eng_approval_pending`, `ui_chat_appr_*`); ROBOT may add two keywords `CUTOFF` (`finish_reason: length`, shows `eng_cut_off`) and `EMPTY` (shows `eng_empty_reply`). No new debug link beyond NAV's.

## 10. Experiment branches

None blocks TEXT. If S2 (R8) fails and shrinking stays off: entry names survive, nothing changes. If S2 passes but the optimised build collapses resource names: `Texts.install` switches packs off and says so in `describe()`; Wave 12 then needs a generated name table (not needed in Wave 0).

## 11. Risks, and what in the plan is wrong or impossible

1. WORDS's directory list (`ui/`, `service/`, `routines/`, `data/`, `hermes/`) leaves out `runtime/` and `voice/`, where 20 interface texts live (`RuntimeManager.kt` 17 lines, `VoiceEngine.kt:459,469,518`; Home shows `Error.reason` at `HomeScreen.kt:410-412`). Needs a decision (W13).
2. Plan line 726 says no translation may be longer than the room of its control; `docs/design-language.md` 13.2 says what the control does when a text is longer and says translations are on average a third longer. A strict check would fail most translations. I implement English <= room and translation <= room * 1.4 (the stretch factor). Lead: confirm.
3. `supportsRtl` is already on (`AndroidManifest.xml:31`); the plan lists it as work.
4. NOTE and ERROR lines are stored text (`ChatEngine.addIssue` writes `Message.text`). Moving them to resources makes them follow the language at the time they are written, not the language at display time; old chats keep what they had. Only a typed message (a code and arguments, a data change) would fix that; not in this wave.
5. English is matched in code: `HomeScreen.kt:400` and `RuntimeSection.kt:78` (`"still starting"`), `BotShareUi.kt:62-70`. Translating `RuntimeState.Starting.step` or `BotShare` messages would break them; hence they stay English (5.1).
6. `RoutineScheduler.kt` formats its 9 texts with the application context (`app.getString`, lines 221-316), so they follow the phone's language, not the one chosen in the app; `Texts.get` fixes that. Visible only when the two differ.
7. Glued sentences remain (they cannot be fixed by a mechanical replacement): `NewChatSheet.kt:139`, `UsageSection.kt:127`, `ChatMessages.kt:205,209`, `ChatListScreen.kt:312`, `HermesScreen.kt:138`, `BotEditorScreen.kt:355`, `SettingsScreen.kt:194,196`. Fixed in the wave that touches each screen.
8. Three identical "Cancel" keys exist (`cancel` `strings.xml:5`, `ui_home_btn_cancel` `strings_chat.xml:31`, `ui_dlg_cancel` `:83`); all are protected; merging them is a later job.
9. `R` must be on the unit-test classpath for `TestTexts` and `EnumLabelsTest`; no test uses it today. If not, both read `R$string` by `Class.forName` with the compiled `R.jar` added by BUILD.
10. aapt2 trims whitespace and `\'` matters (AGENTS.md "Hard-won facts"): `eng_doc_not_created` has no leading space; every `'` in the new file is `\'`.
11. `-FailOnUnused` is off until WORDS has run; the 56 keys are "unused" before it.

## 12. Open questions for the lead

1. Factor 1.4 for translation length (risk 2)? 2. Who applies `runtime/` and `voice/` (risk 1); recommended: WORDS, after CONFIG. 3. W11 changes visible English ("1 bots" to "1 bot"): accepted? 4. Tag `en-XZ` for the stretched language (one place to change: `-Tag` and `TestLanguage.TAG`). 5. Are bot preset names and taglines (`BotPresets.kt:17-140`, stored into `bots.json` as the user's own data) meant to stay English? 6. Should `files/lang/` go into backups now or in Wave 12 (plan 9.2 says Wave 0 and 12)?
