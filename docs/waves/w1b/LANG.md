# Wave 1b, area LANG: contract draft (ten languages: stage 1 code, tools and glossaries; stage 4 translation)

`J/` = `app/src/main/java/dev/pocketagent/app/`, `T/` = `app/src/test/java/dev/pocketagent/app/`, `R/` = `app/src/main/res/`, `A/` = `app/src/main/assets/`; package `dev.pocketagent.app` is written `app`. Line numbers were read in the work tree on 2026-11-25 (it has uncommitted Wave 0 defect fixes, so a line may move: sites are named by quoted text). Sources: plan 1.8 (lines 858 to 879) and 1.7, section 6 step 5, section 13 row 3, section 8 and 11 (the translation risk, line 3185); design language 2.2, 8.4, 8.16, 10, 13.1 to 13.5; `docs/waves/w1b/SPLIT.md` 4.6, 8, 9, 10, 11, 13; `docs/waves/w0-contract.md` 3.6, 5.8, 6, 8.3; `docs/waves/w1a-contract.md` 3.0, 3.12, 7.3, 11; `docs/waves/w1/APPEAR.md` line 35; and the code named below. **Everything 1b takes from Wave 1a is taken from the 1a contract, not from today's code; 1a is not in the work branch.**

## 1. What the plan and the design language ask

| Ask | Where | Item |
|---|---|---|
| Ten languages: English (the source), Spanish, French, German, Portuguese, Italian, Turkish, Russian, Indonesian, Kurmanji; Latin or Cyrillic letters only; scripts that need more than glyphs wait for Wave 12 | plan l.858 to 862 (decision 3, "the list as written": assumed, owner may change) | B16 |
| The picker shows each language under its own name, "System" first. One setting becomes three: language of the interface, language of answers ("as I write" by default or fixed), language of dictation | plan l.863 to 865 | B17 |
| A glossary per language `docs/i18n/<code>.md` (fixes how the app's own words are said and how the user is addressed); a mid-size model translates with it, each text's note and everything translated before; a second agent reads every language for length, placeholders, tone; `strings-check` proves nothing is missing | plan l.866 to 870, step 5 of section 6 | B18 |
| A language nobody who speaks it has read is marked "machine translated" in the picker; the owner reads one page for Turkish (the sixty most visible texts, `docs/i18n/tr-review.md`); a page for Kurmanji if the owner wishes | plan l.871 to 874, decision 3 | B19 |
| Plural rules and dates for a language Android cannot format come from the app's text function; each of the ten is checked for this on the phone | plan l.877 to 879 | B20 |
| Voice of the text: familiar address unless the glossary says otherwise; a button says what happens; no exclamation marks except Pêt's; Pêt's lines "written afresh for each language, from a description of Pêt's character, not translated"; capitals written by the author, never made by code; the English source avoids gendered forms and each glossary says how a language chooses; the app's own words (Canvas, Space, Fireside, ledger, woodpile) are translated once in the glossary | DL 13.5 | B18 |
| Rooms: tab 10, button 22, chip 18, title 28, row 40, Pêt note 80, widget 14 English characters; a translation may use `floor(room * 1.4)` (the stretched test language is 40 percent longer) | DL 13.2, `tools/strings-check.ps1` | B18 |
| Tier 1 type for all ten once ART-B has drawn the glyphs; body text stays in the system's font | DL 13.1, plan l.860 to 862 | B21 (ART-B) |
| Every screen is one of six kinds with five states; the Language section is a Settings list | DL 8.16 | section 3.7 |
| Accessibility: a spoken label on every control; a picture at double text size, one in the stretched language and one mirrored, in every wave | DL 10, plan principle 13 | section 5 |
| 1b "Proven when": the five main screens photographed in each of the ten languages, nothing missing and nothing cut; the owner read the Turkish page; each of the ten checked for plurals and dates on the phone | plan l.915 to 917, l.877 to 879 | section 5 |

## 2. What exists today, and what 1a will have changed by then

**Interface language.** `Appearance.language: String = ""` (`J/ui/theme/Appearance.kt` line 31, comment `"", "en", "tr", "fr"`; key `language` at `K_LANG`, read in `load()` as `prefs.getString(K_LANG, d.language) ?: ""`, written in `update()`). `""` is the system language. `J/ui/AppRoot.kt` `LocaleScope(overrideLanguage ?: appearance.language, overrideRtl)` builds a localised configuration with `Locale.forLanguageTag(language)` and provides `LocalAppLanguage`; `J/ui/text/AppLocale.kt` `resolveAppLocale(language, configurationLocale)` and `Texts.localeOf(context)` (`J/ui/text/Texts.kt`) read the same value; `Texts.layeredFor` asks the resources of the chosen locale, then the English resources, so a missing key shows English. `J/service/ReplyNotifier.kt` `withAppLanguage()` reads it for notifications. `J/DebugOverrides.kt` forces a tag from the debug link in memory, for composition only (`J/DebugLinks.kt` accepts any tag matching `^[A-Za-z]{2,8}([-_][A-Za-z0-9]{1,8})*$`, `system` clears it).

**The picker.** `J/ui/settings/AppearanceSection.kt` has `internal fun LanguageSection()` (the KDoc line `System / English / Türkçe / Français; applies immediately.` and the four `ChoiceRow`s, lines 272 to 285) and `J/ui/settings/SettingsScreen.kt` has `internal fun languageName(code)` (the `when` returning English, Türkçe, Français, else `ctl_lang_system`, lines 184 to 190) used by the section's summary line (`Section.LANGUAGE -> languageName(ap.language)`, line 174) and `Section.LANGUAGE -> LanguageSection()` (line 114). The language names are literals in code and `tools/scenarios/leak-allow.txt` allows `^English$`, `^T.rk.e$`, `^Fran.ais$`. Keys in use: `ctl_set_language` ("Language"), `ctl_lang_intro` ("Pick the language of the app. It changes immediately."), `ctl_lang_system` ("System language"), `ctl_lang_system_sub` ("Follow the language of the phone."), in `R/values/strings_control.xml` lines 164, 202 to 204 and the `-tr`, `-fr` copies. **1a leaves all of this alone** (`docs/waves/w1/APPEAR.md` line 35: the old section "stays as it is: LANG takes it out in Wave 1b").

**Answers.** `J/data/Transcript.kt`: `const val STYLE_RULE` ("Reply only as yourself, in the language of the user's latest message unless the user asks for another language ..." plus `BotForge.CAPABILITY_RULE`), appended to every bot's system message by `systemPrompt(conv, bot, participants)` as `parts += STYLE_RULE`, then `"[chat reference ${conv.id}/${bot.id}]"`. `Transcript.build(...)` calls it and is called once, from `J/data/ChatEngine.kt` (`Transcript.build(conv, bot, repo.bots.value, history, imageLoader)`). Each starter bot's persona ends with `BotPresets.LANGUAGE_RULE` (`J/data/BotPresets.kt` line 22, "Reply in the language of the user's latest message (Kurmanji, Sorani, Turkish, English, French or anything else)..."); personas are copied into `bots.json` when a bot is created, and `BotPresets.upgradeLegacy` compares a saved persona with `LegacyBotPrompts` and with the current text. **Read in the Hermes source (`dist/hermes-src`, `gateway/platforms/api_server_openai_routes.py` lines 713 to 733, 936 and `api_server.py` lines 895 to 907): every `system` message of a request is joined with "\n" and the whole text goes into `sha256(system + "\n" + first user message)`, which is the chat's Hermes session id.** So any change of the system text of a running chat starts a new Hermes session (a new sandbox and session record; the history itself survives because the app always sends the whole transcript). This is experiment E-B4's first half, answered by reading; its second half ("does a second system message count") is answered too: yes, all of them are joined.

**Dictation.** `J/ui/chat/ChatInputArea.kt`: the block `val sttLocaleTag = remember(ap.language) { when (ap.language.lowercase().take(2)) { "tr" -> "tr-TR"; "fr" -> "fr-FR"; "en" -> "en-US"; else -> java.util.Locale.getDefault().toLanguageTag() } }` (lines 79 to 86), used by `RecognizerIntent.EXTRA_LANGUAGE` at two sites (lines 99 and 157). `J/voice/VoiceEngine.kt` (line 427) sets the system TTS locale for `tr` and `fr` only, everything else US English, and `TtsTextCleaner.detectSupertonicLang` knows "tr", "fr", "en": **speech out for the new languages is Wave 8** (SPLIT 7) and this area does not touch `voice/`.

**Plurals and dates.** `J/ui/text/PluralRules.kt` already knows `en de it es tr ku` (one or other), `fr pt` (0 and 1 are one), `ru` (one, few, many), everything else other, so `id` is right without an edit; `T/text/PluralRulesTest.kt` pins en, tr, ku, fr, pt, ru, id, ja. Resource plurals go through `ResourceTextSource.plural` = `Resources.getQuantityText`, i.e. the platform's rules (ICU). Dates in the app already use the locale's formatters (`J/ui/chat/ChatCommon.kt` `DateFormat.getTimeInstance/getDateInstance(.., locale)`, `J/ui/routines/RoutineUiParts.kt` `ofLocalized*`, 1a's `HomeFormat.date` with `getBestDateTimePattern`), so no code of the app formats a date by a fixed English pattern (the `SimpleDateFormat(.., Locale.US)` sites are logs and file names).

**Texts and tools.** 1,115 keys in 12 files in `R/values/` (`strings.xml` 112, `strings_chat` 286, `strings_control` 387, `strings_engine` 138, `strings_routines` 111, seven small ones); `values-tr` and `values-fr` hold the same 12 files. Only `strings_engine.xml` and the Wave 0 files carry the `<!-- t: ... | kind: ... -->` note on every key (older keys have none: the ratchet in `tools/strings-check.baseline.json` counts 939 warnings). `tools/strings-check.ps1` (665 lines) accepts any folder `^values-([a-z]{2,3}(-r[A-Z]{2})?|b\+.+)$`, checks xml, parity, placeholders, blank, stretched, protected, note, length (rooms from `tools/strings-kinds.txt`, translation `floor(room * 1.4)`), and has `-ScanDump` for a `uiautomator` dump. It reads only `[kinds]` and `[plural-allow]` of `tools/strings-kinds.txt`. `tools/make-test-language.ps1` writes `app/src/debug/res/values-en-rXZ/` (stretched) and `values-en/` (plain copy). `T/text/ResXml.languageDirs()` hard-codes `listOf("tr", "fr")`, and `ResourceParityTest` loops `listOf("tr", "fr")`. **`T/TwinOverlayTest.labelsAreOverriddenInAllThreeLocales` and `app/src/twin/res/values{,-tr,-fr}/strings_build.xml`**: the test twin overrides `app_name`, `srf_ask_agiri`, `srf_tile_label` in every locale folder, because a `values-tr` of the main source set beats the twin's `values`. Without a twin file per new folder the twin would show "Agirî" in Spanish. The three tool files `tools/strings-check.ps1`, `tools/make-test-language.ps1` (and `tools/strings-kinds.txt`, unchanged today) are modified in the work tree by the Wave 0 defect fixes: LANG starts after that work is committed.

**What 1a will have added by then (from its contract, section 3.12 and 7.3):** the files `strings_sky.xml` (`sky_`), `strings_weather.xml` (`wx_`), `strings_fire.xml` (`fire_`), `strings_pet.xml` (`pet_`, among them the eight `pet_quip_*`), `strings_home.xml` and `strings_home_hero.xml` (`hm_`), `strings_appearance.xml` (`apr_`), about 125 keys already in the `w1a` branch and an estimated 300 to 450 when 1a is done, each with a note; its TRANSLATE step writes Turkish and French for them. LANG consumes them as translation input; it edits no 1a file.

## 3. The design

### 3.0 Decisions made here (the lead may veto, each is "assumed, owner may change")

1. **Fixed answer language ships, with a per-chat snapshot** (SPLIT open point 6 asked to wait for E-B4; reading the Hermes source answers it). A chat's system text must never change after its first turn. So the line is added only when the setting is a fixed language, and the language is frozen per conversation at its first turn: the setting changes new chats only (the screen says so). "As I write" adds nothing, so every existing chat's system text stays byte for byte what it is today and no session id changes.
2. **`BotPresets.kt` is not edited** (the split listed it). Personas are saved copies, `LANGUAGE_RULE` is part of every derived session id and of `upgradeLegacy`'s comparison, and the fixed-language paragraph can override it from the system message, which comes after the persona. The split's "BotPresets.kt (the LANGUAGE_RULE line)" becomes: no change.
3. **Plural quantities, as the code and CLDR give them:** en, de, tr, ku: one, other. es, fr, it, pt: one, other, and `many` allowed but optional (it only appears for multiples of a million; Android falls back to other). ru: one, few, many, other all required. id: other only (a `one` item would be ignored and is an error here).
4. **Table is data in two places that a test keeps equal:** the Kotlin table (`LangTable`) and a new section `[plural-rules]` of `tools/strings-kinds.txt`, which the PowerShell tools read. A test (`LangTableTest.pluralSectionMatchesTheTable`) fails if they differ.
5. **Only languages with `shipped = true` are offered** in the picker, so the slices before stage 4 never show a language that has no texts. `en`, `tr`, `fr` are shipped from the first slice; the lead flips the other seven in one edit at the end of stage 4.
6. **The Welcome's first row is the phone's own language** (DL 8.4), the Settings list has "System" first (plan 1.8). Both are `LanguageChoices`; the mode decides.
7. **Pêt's lines are written afresh in all nine non-English languages**, Turkish and French included (the existing `pet_quip_*` of 1a are translations; the Pêt pass replaces them where the reader keeps the new line).
8. **Parity stays green between slices:** the two supplement agents (tr, fr) run after slice C and again after slice D, for the keys of that slice, so `ResourceParityTest` is green at the end of every slice. The seven new folders do not exist until slice E (a folder that exists must be complete; a shipped language must have its folder).

### 3.1 `J/ui/text/Languages.kt` (new, pure: no `android.` and no `androidx.` import; slice A)

```kotlin
package dev.pocketagent.app.ui.text

data class Lang(
    val tag: String,               // stored value and Locale tag, lower case: "en","es","fr","de","pt","it","tr","ru","id","ku"
    val ownName: String,           // the language under its own name, never translated: "Español"
    val englishName: String,       // for the model: "Spanish"
    val folder: String,            // "values", "values-es", ..., "values-in", "values-ku"
    val requiredPlurals: List<String>,
    val optionalPlurals: List<String>,
    val reviewed: Boolean,         // false = shown with the mark "machine translated"
    val shipped: Boolean,          // true = offered in the picker (texts exist)
    val alphabet: String,          // every NON-ASCII character the pixel font must draw for this language's titles, tabs and chips
) { val allowedPlurals: Set<String> get() = (requiredPlurals + optionalPlurals).toSet() }

object LangTable {
    const val ENGLISH = "en"
    const val TEST_LANGUAGE = "en-XZ"                 // the stretched debug language; never a Lang
    val all: List<Lang>                               // plan order, below
    val shipped: List<Lang>                           // all.filter { it.shipped }
    fun byTag(tag: String): Lang?                     // exact stored tag
    fun normalize(tag: String): String?               // see the table of forms; null = not one of the ten
    fun find(tag: String): Lang?                      // byTag(normalize(tag))
    fun match(locale: java.util.Locale): Lang?        // by language: "in" and "id" give id, "kmr" and "ku" give ku; region ignored
    fun folderOf(tag: String): String?
}
```

The table (one entry per row; `reviewed` false except English; `shipped` true for en, tr, fr only at first):

| tag | ownName | englishName (model) | folder | required plurals | optional | alphabet (non-ASCII only) |
|---|---|---|---|---|---|---|
| `en` | English | English | `values` | one, other | | empty |
| `es` | Español | Spanish | `values-es` | one, other | many | `ÁÉÍÓÚÜÑáéíóúüñ¿¡` |
| `fr` | Français | French | `values-fr` | one, other | many | `ÀÂÆÇÈÉÊËÎÏÔŒÙÛÜŸàâæçèéêëîïôœùûüÿ«»` |
| `de` | Deutsch | German | `values-de` | one, other | | `ÄÖÜäöüß` |
| `pt` | Português | Portuguese (as written in Brazil) | `values-pt` | one, other | many | `ÁÀÂÃÇÉÊÍÓÔÕÚÜáàâãçéêíóôõúü` |
| `it` | Italiano | Italian | `values-it` | one, other | many | `ÀÈÉÌÒÓÙàèéìòóù` |
| `tr` | Türkçe | Turkish | `values-tr` | one, other | | `ÇĞİÖŞÜÂÎÛçğıöşüâîû` |
| `ru` | Русский | Russian | `values-ru` | one, few, many, other | | `АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя` |
| `id` | Bahasa Indonesia | Indonesian | `values-in` (E-B2 may change it) | other | | empty |
| `ku` | Kurdî (Kurmancî) | Kurmanji Kurdish (Kurdish in Latin script) | `values-ku` | one, other | | `ÇÊÎŞÛçêîşû` |

`normalize` forms (every one is a test case): trims and lower-cases; the region and script are dropped (`en-GB`, `pt_PT`, `es-419`, `zh-Hans` gives null, `ku-Latn` gives `ku`); `in` gives `id`; `kmr` gives `ku`; `iw`, `xx`, `""`, `en-XZ` give null (`en-XZ` is the test language, not a language of the app).

```kotlin
enum class PickerMode { SETTINGS, WELCOME }
enum class RowKind { SYSTEM, FIRST, LANGUAGE }
data class LangRow(val value: String, val lang: Lang?, val kind: RowKind)   // value = what is stored in Appearance.language ("" for SYSTEM and FIRST)
object LangSelect {
    fun systemLang(phone: java.util.Locale, available: (Lang) -> Boolean = { it.shipped }): Lang?   // the language the system choice gives; null when the phone's language is not available (English is then used)
    fun rows(phone: java.util.Locale, mode: PickerMode, available: (Lang) -> Boolean = { it.shipped }): List<LangRow>
    fun answerValues(available: (Lang) -> Boolean = { it.shipped }): List<String>      // "" (as I write), then the tags in table order
    fun dictationValues(available: (Lang) -> Boolean = { it.shipped }): List<String>   // "" (same as the app), then the tags in table order
}
```
`rows` in SETTINGS: `LangRow("", systemLang(phone), SYSTEM)` first, then every available language in table order as `LangRow(tag, lang, LANGUAGE)` (the plan's order, not alphabetical: open point 13 of the split). In WELCOME: no System row; the first row is `LangRow("", systemLang(phone) ?: English, FIRST)` (the phone's own language first, or English when the phone's language is not available), then the other available languages in table order without the one already shown. A row for English when the system choice resolves to English is shown once.

```kotlin
data class LanguageSettings(val answer: String = "", val dictation: String = "")   // "" = as I write / same as the app
object LanguageSettingsCodec {
    fun cleanChoice(raw: String?): String             // "" or a normalised tag of the ten; anything else gives ""
    fun cleanSnapshot(raw: String?): String?          // a normalised tag, or null for blank/unknown
    fun snapshotKey(convId: String): String           // "snap." + convId
    const val KEY_ANSWER = "answer"; const val KEY_DICTATION = "dictation"
}
object PluralProbe {                                  // the pure half of LangProbe (3.5)
    val COUNTS: List<Int>                             // 0, 1, 2, 3, 5, 11, 21, 22, 25, 100
    fun mismatches(tag: String, platformCategories: List<String>): List<Int>   // the counts where PluralRules.category(tag, n) and the platform's category differ; "many" from the platform and "other" from the app agree for es, fr, it, pt (CLDR's many is for multiples of a million)
}
```

### 3.2 `J/ui/text/AnswerLanguage.kt` (new, pure; slice A) and the edit of `J/data/Transcript.kt` (slice C)

```kotlin
object AnswerLanguage {
    const val NEW_CHAT_TURNS = 1                      // a chat with at most this many USER/BOT messages is "new"
    fun line(tag: String): String                     // "" for "" or an unknown tag; else the paragraph below
    data class Resolution(val tag: String, val remember: Boolean)
    fun resolve(current: String, snapshot: String?, historyTurns: Int): Resolution
}
```
`resolve`: a known non-empty `snapshot` wins (`remember = false`); else when `historyTurns <= NEW_CHAT_TURNS` and `current` is a known tag the result is `Resolution(current, remember = true)`; else `Resolution("", false)`. So a chat that has history and no snapshot (every chat that exists when the app is updated, and every chat made while the setting was "as I write") answers as today forever.

The paragraph (English, a constant in the file, not a string resource: it goes to the model, like `STYLE_RULE`), with `<name>` = `englishName`:
`Language of your replies: write your replies in <name>, whatever language the user writes in. This replaces any earlier instruction about which language to reply in. Switch only if the user explicitly asks for another language, or if your role (for example translating) requires one. Code, identifiers and commands stay in English.`
No exclamation mark; at most 400 characters; no newline.

`J/data/Transcript.kt` (edit by quoted text):
```kotlin
@Volatile var answerLanguage: (convId: String, historyTurns: Int) -> String = { _, _ -> "" }   // set once by LanguagePrefs.install
fun systemPrompt(conv: Conversation, bot: Bot, participants: List<Bot>, historyTurns: Int = Int.MAX_VALUE): String
```
In `systemPrompt`, directly after `parts += STYLE_RULE`: `val fixed = AnswerLanguage.line(answerLanguage(conv.id, historyTurns)); if (fixed.isNotEmpty()) parts += fixed`; the `[chat reference ...]` line stays last. `build` passes `historyTurns = windowed.count { it.role == Role.USER || it.role == Role.BOT }`. `STYLE_RULE` itself is unchanged (`T/TranscriptTest` pins `STYLE_RULE + "\n\n[chat reference c1/hermes]"`), and with the default provider the output is byte for byte today's. The default `historyTurns` (`Int.MAX_VALUE`) means "an old chat" so a caller that does not know gets today's text.

### 3.3 `J/ui/text/DictationLanguage.kt` (new, pure; slice A) and the edit of `ChatInputArea.kt` (slice C)

```kotlin
data class DictationPlan(val tag: String, val assured: Boolean)    // tag for RecognizerIntent.EXTRA_LANGUAGE; assured = the recogniser is expected to listen in it
object DictationLanguage {
    val DEFAULTS: Map<String, String>    // en en-US, es es-ES, fr fr-FR, de de-DE, pt pt-BR, it it-IT, tr tr-TR, ru ru-RU, id id-ID
    fun plan(dictation: String, interfaceLanguage: String, phone: java.util.Locale): DictationPlan
}
```
Rules, in order. (1) The language: `LangTable.find(dictation)` when `dictation` is non-blank; else `LangTable.find(interfaceLanguage)` when that is non-blank; else `LangTable.match(phone)`. A blank or unknown dictation value acts as "same as the app". (2) No language found (a phone in a language that is not one of the ten, with the System choice): `DictationPlan(phone.toLanguageTag(), true)` (today's behaviour). (3) `ku`: `DictationPlan(phone.toLanguageTag(), false)`; no assured recogniser for Kurmanji is known, the phone's own language is used and the screen says so. (4) Otherwise the region comes from the phone when the phone's language is the same language (`LangTable.match(phone)?.tag == lang.tag`, giving `es-MX`, `pt-PT`, `en-GB`; the phone's `in` becomes `id`), else `DEFAULTS[lang.tag]`; `assured = true`.

`J/ui/chat/ChatInputArea.kt`: replace the block quoted in section 2 with
```kotlin
val langPrefs = remember { LanguagePrefs.get(ctx) }
val langChoice by langPrefs.settings.collectAsState()
val sttLocaleTag = remember(ap.language, langChoice.dictation) {
    DictationLanguage.plan(langChoice.dictation, ap.language, java.util.Locale.getDefault()).also { LangProbe.logDictation(it) }.tag
}
```
plus the two imports. The two `putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag)` sites stay. Nobody else edits `ChatInputArea.kt` in 1b (BARS works in `ChatTopArea.kt`); the file has uncommitted edits of the Wave 0 fixes today, so this edit waits for them.

### 3.4 `J/ui/text/LanguagePrefs.kt` (new; Android; slice C)

```kotlin
class LanguagePrefs private constructor(context: Context) {
    val settings: StateFlow<LanguageSettings>
    fun setAnswer(value: String)                          // cleaned by LanguageSettingsCodec; one apply()
    fun setDictation(value: String)
    fun answerFor(convId: String, historyTurns: Int): String   // reads the snapshot, calls AnswerLanguage.resolve, writes it when remember; returns the tag ("" = no line)
    companion object {
        const val FILE = "language_settings"
        fun get(context: Context): LanguagePrefs           // process singleton on the application context
        fun install(context: Context)                      // Transcript.answerLanguage = { id, turns -> get(context).answerFor(id, turns) }
    }
}
```
Declare `appContext` and `prefs` before `state` (init order). **Nothing is written at creation or on a read**, so on the owner's phone `shared_prefs/language_settings.xml` does not exist until the owner changes one of the two choices or starts a chat after choosing a fixed answer language. With `BuildConfig.TEST_HOOKS` `answerFor` logs `I/Lang: answer conv=<first 8 characters> tag=<tag or auto> remember=<bool>` through `AppLog.i`.

Stored data (everything optional; the previous build never opens it):

| Item | Where | Default when absent | Why the previous build ignores it |
|---|---|---|---|
| `Appearance.language` takes the values `es de pt it ru id ku` besides `"" en tr fr` | prefs `appearance`, key `language` (unchanged) | `""` | an old build reads any string and builds `Locale.forLanguageTag`, finds no folder and shows English; its own picker shows no selection |
| `answer`, `dictation` (strings), `snap.<convId>` (a tag) | new prefs file `language_settings` | `""` (as I write, same as the app), no snapshot | no code of the old build opens the file |

**Migration for an installation that exists:** none. The three settings read as defaults, so the answers are exactly today's (the system text is byte for byte unchanged), dictation behaves as today for tr, fr, en and the phone's language, and the interface language stays what it is. `language_settings` is not in `BackupCore.PREF_SCHEMA` (`BackupManager.kt` stays untouched: SPLIT 7); a restore returns the answer and dictation choices to defaults, which is harmless.

### 3.5 `J/ui/text/LangProbe.kt` (new; Android; slice C; one file added to the split's list)

The check of B20 on the phone, in builds with `BuildConfig.TEST_HOOKS` only (every function returns at once otherwise):
```kotlin
object LangProbe {
    fun pluralLine(context: Context, tag: String): String        // "0:many,1:one,2:few,..." the category the PLATFORM picks for each PluralProbe.COUNTS in the locale of tag
    fun dateLine(context: Context, tag: String): String          // 2027-03-21 formatted with getBestDateTimePattern(locale, "EEEEdMMMM") and DateTimeFormatter
    fun log(context: Context, tag: String)                       // one line: I/Lang: probe tag=ru plural=<line> agree=<true|false> date="<text>"
    fun logDictation(plan: DictationPlan)                        // I/Lang: dictation tag=tr-TR assured=true   (once per change)
}
```
`pluralLine` resolves a new plurals resource, `R.plurals.lang_probe_plural` (section 3.8, `translatable="false"`, items `zero one two few many other` whose text is the quantity's own name) with `Resources` of the locale (`createConfigurationContext(Configuration.setLocale)`); the text returned IS the platform's category. `agree` is `PluralProbe.mismatches(tag, categories).isEmpty()`. This is also the proof that `ku` has plural rules on this phone (if ICU has none for `ku`, the line shows `1:other` and the lead decides: E-B2). SHELL calls `LangProbe.log(app, tag)` from the debug link (section 7).

### 3.6 `J/ui/settings/LanguageSection.kt` (new; slice C) and what APPEAR-B deletes

```kotlin
@Composable internal fun LanguageSection()                       // the Settings section; same name and place as today's
@Composable internal fun languageName(code: String): String      // the own name of a stored tag; "" or unknown gives t(R.string.ctl_lang_system)  (same signature as today's: APPEAR-B deletes the old one in the same commit)
@Composable internal fun LanguageChoices(
    selected: String,                       // Appearance.language ("" = system)
    onPick: (value: String) -> Unit,        // the value to store
    mode: PickerMode = PickerMode.SETTINGS,
    modifier: Modifier = Modifier,
    showMark: Boolean = true,               // the "machine translated" line under a language
    rowTag: (LangRow) -> String = { LangTags.ui(it) },
)
object LangTags { /* 3.9 */ }
```
`LanguageChoices` composes one private `LangChoiceRow` per `LangSelect.rows(Locale.getDefault(), mode)`: a `PixelPanel` with `Modifier.selectable(selected, role = Role.RadioButton, onClick)`, `testTag(rowTag(row))`, `heightIn(min = Space.touch)`, accent fill and border when selected and a `PixelIcon.CHECK` (as `ChoiceRow` in `CtlUi.kt`, which has no tag parameter and so is not reused). Title: `lang.ownName` (the System row: `t(R.string.ctl_lang_system)`); subtitle: the System row `t(R.string.ctl_lang_system_sub)`, a language with `!reviewed` and `showMark` `t(R.string.lang_mt_mark)`. A row is selected when `selected` equals its `value`, or, for a LANGUAGE row, when `LangTable.find(selected)?.tag` equals its tag. `onPick` plays `rememberHaptics().play(HapticCue.TICK)` first. (Extra: the own name is drawn with `SpanStyle(localeList = LocaleList(tag))` so a screen reader says it in its own language.)

`LanguageSection` order, top to bottom, inside the Settings accordion (the section's own `settings.section.language` header is Settings'):
1. `Muted(t(ctl_lang_intro))`.
2. `LanguageChoices(ap.language, { store.update { it.copy(language = it2) } }, SETTINGS)` (store = `AppearanceStore.get(ctx)`, applies at once).
3. when `LangSelect.systemLang(phone) == null`: `Muted(lang_system_fallback)` tag `lang.system.fallback`. When the stored tag is not blank and `LangTable.find(stored)?.shipped != true`: `Muted(lang_unavailable, stored)` tag `lang.stale`.
4. `Muted(lang_mt_note)` tag `lang.mt.note` while any offered language has `reviewed == false`.
5. `SubHeading(lang_answers_title)`, `Muted(lang_answers_sub)`, a `ChipRow` of `PocketChip(text, selected, onClick)` for `LangSelect.answerValues()` (text: `lang_answers_auto` for `""`, else `ownName`), tags `LangTags.answer(value)`; `Muted(lang_answers_scope)` tag `lang.answer.scope`.
6. `SubHeading(lang_dictation_title)`, `Muted(lang_dictation_sub)`, the same chips for `dictationValues()` (`lang_dictation_auto` for `""`), tags `LangTags.dictation(value)`; when `DictationLanguage.plan(...).assured == false`: `Muted(lang_dictation_ku_note)` tag `lang.dictation.note`.
7. when the interface or the answer language is not `en`, `fr` or `tr` (what the voices know today): `Muted(lang_voice_note)` tag `lang.voice.note` (extra, section 6).
Spacing `Space.s` between blocks; only `Space`, `Corner` and the Wave 0 components; no `N.dp` literal, no case change in code (lint ratchet).

**Five states (DL 8.16, kind: Settings list).** *Content:* as above. *Empty:* not applicable (the list always has at least System and English). *Loading:* not applicable (the table is in memory and `LanguagePrefs` reads its file when created, before the first frame of this screen). *Error:* the one error of this screen is a saved language this build does not offer (a build rolled back, a backup from a later build): `lang.stale` says what happened, what was kept (the saved value is not overwritten) and what to do (pick another); nothing is greyed out. *Offline:* not applicable (no network). *Needs permission:* not applicable here (the recogniser's microphone permission is asked in the chat, where the microphone button is; this screen only chooses a language).

**Motion, haptics, sound.** No animation of its own: the whole screen switching language is the feedback (`LocaleScope` recomposes); with the animation level OFF nothing differs. Row press uses the same press sink as other rows (`Dur.TAP` through `rememberPixelPress`, as `PocketChip`); chips give their own `HapticCue.TICK`; rows play `HapticCue.TICK` through `rememberHaptics()`. No sound (the old rows had none).

**Deleted by APPEAR-B in the same commit** (section 7): `LanguageSection()` and its KDoc in `AppearanceSection.kt`; `languageName` in `SettingsScreen.kt`. `SettingsScreen.kt`'s call sites (`Section.LANGUAGE -> LanguageSection()` and `languageName(ap.language)`) stay and bind to the new functions. Old keys `ctl_lang_intro`, `ctl_lang_system`, `ctl_lang_system_sub`, `ctl_set_language` stay in use (so the lint ratchet gains no unused key).

### 3.7 Texts: `R/values/strings_lang.xml` (prefix `lang_`, owned by LANG; English only here; every key carries its note)

| Key | English | Note (what the author writes above it) |
|---|---|---|
| `lang_mt_mark` | Machine translated | line under a language's own name in the picker, for a language no speaker has read yet; up to 24 characters; kind: row |
| `lang_mt_note` | A language marked Machine translated was translated by an AI model, and no speaker has read it yet. Some texts may sound odd. English fills any gap. | body under the list; 3 lines; the words "Machine translated" must equal `lang_mt_mark` |
| `lang_answers_title` | Language of answers | sub-heading of the answer choice; up to 28 characters; kind: title |
| `lang_answers_sub` | Choose the language the agent answers in, or let it follow what you write. | under the heading; 2 lines; body |
| `lang_answers_auto` | As I write | chip: the agent answers in the language of your latest message (today's behaviour); up to 18 characters; kind: chip |
| `lang_answers_scope` | A new choice applies to new chats. Chats you already started keep the way they answer. | 2 lines; body |
| `lang_dictation_title` | Language of dictation | sub-heading; up to 28 characters; kind: title |
| `lang_dictation_sub` | The language your phone listens for when you dictate a message. | 2 lines; body |
| `lang_dictation_auto` | Same as the app | chip: follows the app language, or the phone's when the app follows the system; up to 18 characters; kind: chip |
| `lang_dictation_ku_note` | Your phone's speech recogniser may not understand Kurmanji. It then listens in your phone's own language. | shown when Kurmanji is chosen for dictation; 2 lines; body |
| `lang_system_fallback` | Your phone's language is not available in Agirî yet, so English is used. | shown under the list when the phone's language is none of the offered ones; 2 lines; Agirî is the app's name, never translate it |
| `lang_unavailable` | The saved language (%1$s) is not part of this version. English is used until you pick another. | %1$s is the stored language tag such as `ja`; 2 lines; body |
| `lang_voice_note` | Reading answers aloud works well in English, French and Turkish for now. Other languages may sound odd. | 2 lines; body (extra) |
| `lang_probe_plural` | (plurals, `translatable="false"`, items zero, one, two, few, many, other; the text of each item is its own quantity name) | read by `LangProbe` only; never shown; not translated |

`R/values-tr/strings_lang.xml` and `R/values-fr/strings_lang.xml` for the thirteen translatable keys are written by the supplement agents after slice C (3.0 item 8), not by LANG's code agent. `tools/strings-kinds.txt` gains `chip lang_answers_auto`, `chip lang_dictation_auto`, `title lang_answers_title`, `title lang_dictation_title` (the notes already say it; the glob lines make the length check read them even where a note is edited).

### 3.8 Tools (slice B; all PowerShell 5.1 and 7, ASCII only, as the existing tools)

**`tools/strings-kinds.txt`** gains four sections (the parser of `strings-check.ps1` and of the new tool reads sections by name and ignores unknown ones):
- `[plural-rules]`: one line per language folder suffix, `<suffix> <required...> [? <optional...>]`: `en one other`, `es one other ? many`, `fr one other ? many`, `de one other`, `pt one other ? many`, `it one other ? many`, `tr one other`, `ru one few many other`, `in other`, `ku one other`. The set of suffixes is also the set of folders the app accepts.
- `[passes]`: `<pass> <file>` lines giving the order of stage 4 (3.10).
- `[pet-voice]`: globs of the keys that are written afresh, not translated: `pet_quip_*`, `ui_home_quip_*`, `ui_cmd_pet_quip*`, `wel_pet_*`, `wn_note`.
- `[review-page]`: `<glob> <count>` lines that pick the sixty most visible texts for the owner's page (3.10).

**`tools/strings-check.ps1`** (changes, each a new check name in the output): `plural-form` (ERROR: a `<plurals>` of a language lacks a required quantity, or has one the language does not allow, for entries not marked `translatable="false"`; languages are read from `[plural-rules]`, the English `values` folder counts as `en`); `folder` (ERROR: a language folder under `src/main/res` whose suffix is not in `[plural-rules]`, for example `values-b+id`, `values-es-rES` or `values-id` next to `values-in`); `-ScanDump <dump> -ExpectLang <suffix>` (ERROR `english-leak`, exit 4: a text of the dump, three or more letters, not on the allow list, that equals an English value of `values/` whose value in the expected language is different, which means the screen shows English although the language has a text; the stretched-language mode of today is used when `-ExpectLang` is absent); an INFO line per language, `untranslated?=N`, counting texts equal to English (informational, never counted by the ratchet). The `lengthKnown` baseline is not touched: **a text that cannot fit its room in a language is a finding for the English author (the room claimed in its note), not a baseline edit** (1a ruling 2).

**`tools/i18n-worklist.ps1`** (new): `powershell -NoProfile -ExecutionPolicy Bypass -File tools\i18n-worklist.ps1 -Lang es [-Pass 1|2|3] [-File strings_chat.xml] [-Max 120] [-Ref fr,tr] [-OutDir dir] [-PetPass] [-Summary] [-ReviewPage tr [-Count 60] [-OutFile path]]`. `-Summary` prints per language and file the missing and total keys. Without `-Summary` it writes batch files `worklist-<lang>-p<pass>-<nn>.txt` of at most `-Max` keys, one entry per missing key:
```
=== strings_chat.xml : ui_nav_chats  [string]  kind=tab  room=10  max=14
EN: Chats
NOTE: <the note, or "none">
REF fr: Discussions
REF tr: Sohbetler
```
A plurals entry lists `PLURAL es: one, other (many optional)` and the English `one` and `other`. Keys matching `[pet-voice]` are left out of ordinary batches. `-PetPass` writes the Pêt batch: key, the situation (the note, without any English wording of the line itself, which the tool strips to what follows "bubble" or the first clause), the maximum length, and no English text. `-ReviewPage tr` writes `docs/i18n/tr-review.md` (3.10). Exit 0, or 3 for a bad call. Reads English, notes, the languages that exist; writes only to `-OutDir`/`-OutFile`; never edits a strings file.

**`tools/make-test-language.ps1`:** no change of logic. The stretched language is regenerated from the frozen English at the end of stage 4 and `-Check` stays a CI step. The mirrored test language is the existing debug link `rtl 1` (it mirrors any language); a mirrored pseudo-language is Wave 12.

### 3.9 Test tags (exact)

`LangTags`: `ui(row: LangRow)` gives `lang.ui.system` for the SYSTEM row, else `lang.ui.<tag>` (so `lang.ui.es`; WELCOME overrides it through `rowTag`); `answer(value)` gives `lang.answer.auto` for `""`, else `lang.answer.<tag>`; `dictation(value)` gives `lang.dictation.auto` or `lang.dictation.<tag>`; constants `MT_NOTE = "lang.mt.note"`, `SYSTEM_FALLBACK = "lang.system.fallback"`, `STALE = "lang.stale"`, `ANSWER_SCOPE = "lang.answer.scope"`, `DICTATION_NOTE = "lang.dictation.note"`, `VOICE_NOTE = "lang.voice.note"`. Tags are written with a `when`/constant table, not by changing case. INTEGRATE-B adds them to `T/ScenarioFilesTest.kt`'s known tags (patterns `lang\.ui\.[a-z]+`, `lang\.answer\.[a-z]+`, `lang\.dictation\.[a-z]+`).

### 3.10 `docs/i18n/*` and stage 4

**Files.** `docs/i18n/README.md` (the process of 3.10 and the rules below, one page), `docs/i18n/<code>.md` for `en es fr de pt it tr ru id ku`, `docs/i18n/pet-voice.md`, `docs/i18n/tr-review.md` (generated in stage 4), optional `docs/i18n/ku-review.md`.

**Shape of a glossary** (the template; `en.md` is the source's own, listing what the English must never do and the app's coined words with their meaning):
1. *Address*: the form of "you" and its reason; how a button is written (infinitive, imperative, noun); the polite or familiar form kept; what to do about gender.
2. *Typography*: quotes, spaces, special letters, apostrophes and suffixes, the escapes in the XML (`\'`, `&amp;`, `\n`, ` `).
3. *Terms*: a table `English term | meaning in the app | translation | forms and notes (plural, case, gender)`. Must define: Agirî, Hermes, Pêt, Newroz, Dengbêj (kept as written), Home, Chats, Library, Bots, Agent, Settings, Routine, Skill, Memory, Soul, Usage, Budget, Provider, Runtime, Practice model, Thinking effort, Forge, Mode, Saved, Fork, Slash command, Diagnostics, Backup, hearth, fire, woodpile, kindling, ledger, Canvas, Space, Fireside, Recipe (the last six are named by the plan for later waves; they are decided now so no later wave says them another way).
4. *Short forms*: the approved shorter word for each long term, for tab, button and chip rooms.
5. *Do not translate*: product names, file names (`MEMORY.md`), commands, model ids, keys of `assets/text/protected-keys.txt`.
6. *Decisions log*: each reader finding that changed a rule, with the date.
The tr and fr glossaries start from what ships: the author first extracts how `values-tr` and `values-fr` say each term today and records it; it fills gaps and never re-decides a shipped word without a reader's finding.

**Starting rules per language** (the glossary author checks them, the readers hold the text to them):
| Language | Address and buttons | Traps to write down |
|---|---|---|
| es | tú (never voseo, neutral Spanish); buttons in the infinitive ("Guardar") | `¿ ¡` opening marks; gender-neutral welcome ("Te damos la bienvenida", not bienvenido/a); `tú` verbs only |
| fr | vous, as shipped (`ctl_lang_intro` says "Choisissez"); buttons in the infinitive | a no-break space (` `) before `: ; ? !` and inside `« »`, as the shipped files already do; elision `l\'`; `Pêt` and `Agirî` unchanged |
| de | du; buttons in the imperative or the infinitive of the shipped style ("Speichern") | noun capitals written by the author; long compounds: use the short form list; `ß` |
| pt | você, Brazilian words ("arquivo", "tela"); buttons in the infinitive | `one` covers 0 and 1; gender-neutral phrasing; no `tu` forms |
| it | tu; buttons in the infinitive ("Salva" only where the shipped style of the app says imperative: pick one and write it down) | apostrophe elision `l\'`, accents on final vowels (`è`, `più`) |
| tr | sen, as shipped; buttons as shipped | apostrophe before a suffix (`Pêt\'i`, `Agirî\'ye`; vowel harmony of `ê` is front); never put a suffix after a placeholder: rephrase; dotted İ and dotless ı |
| ru | ты; buttons in the infinitive ("Сохранить") | three plural forms (one, few, many) in every `<plurals>`; avoid past-tense gendered verbs ("Вы вошли"); `е` for `ё` except where it would be ambiguous (`все`/`всё`); the longest rooms (tab 14) need the short forms |
| id | kamu; buttons in the imperative without "-lah" | no plural marking (other only); avoid the long "memper-...-kan" forms in tabs and chips; the folder is `values-in` |
| ku | tu; buttons in the imperative | Latin Hawar letters `Ç Ê Î Ş Û` used consistently; ezafe `-a`/`-ê`; prefer a Kurmanji word to a Turkish or Arabic loan where one is in use; `Pêt` and `Dengbêj` as written; say that a native speaker has not read it |

**`docs/i18n/pet-voice.md`**: the description of Pêt's character, from which each language's lines are written afresh. Contents: Pêt is the small Kurdistan newt who keeps the fire; curious, warm, a little shy; notices the hour, the weather and what the agent is doing; speaks in short present-tense lines of at most 44 characters in a bubble and at most 80 in a note; names a small concrete thing (the fire, a stone, a leaf, a notebook, a scroll) rather than a feeling; never sarcastic, never lecturing, never calls itself an AI, no emoji, no idiom that only works in English; uses the glossary's address form; an exclamation mark is allowed (DL 13.5) but at most one per line and in at most a third of the lines of a language; six English tone samples for situations that no key uses (the author writes them; they are examples of voice, not lines to translate); the list of situations is the keys of `[pet-voice]` with their notes. The writer of a language reads this page, the glossary and the notes, and not the English lines (`-PetPass` hides them).

**Stage 4 (slice E), who does what.** Input: the English files frozen across 1a and 1b (the key count is read from `strings-check` at the freeze; estimate 1,115 today plus 300 to 450 of 1a plus about 250 of 1b, so about 1,700; under about 1,900 the work is three passes per language, above it four: SPLIT open point 12), the notes, the glossaries, `i18n-worklist.ps1`, the existing `values-tr` and `values-fr` as read-only reference.
1. **Glossary check (G, extra):** the Opus reader of each language reads its glossary once before any translation (about one page) and reports; the author applies it. Skipped when time is short: the final reading catches it.
2. **Seven translators (Sonnet, one per `es de pt it ru id ku`)**, each alone in its folder `R/values-<folder>/`, three passes in the order of `[passes]`: pass 1 the screens of 1a and 1b (`strings_shell`, `strings_bars`, `strings_library`, `strings_welcome`, `strings_start`, `strings_lang`, `strings_appearance_b`, `strings_home`, `strings_home_hero`, `strings_appearance`, `strings_sky`, `strings_weather`, `strings_fire`, `strings_pet`: sets the tone), pass 2 chat and base (`strings_chat`, `strings_routines`, `strings`, `strings_presence`, `strings_surface`), pass 3 settings and engine (`strings_control`, `strings_engine`, `strings_config`, `strings_privacy`, `strings_robot`, `strings_stream`, `strings_design`). Each works from `worklist` batches of at most 120 keys, reads its glossary, each key's note, `REF fr` and `REF tr`, and the files it has already written (a `grep` of its own output for a term before using a new word: "everything translated before"). Rules (the same as `w0-contract.md` section 6): placeholders identical and in the same form; escapes as English; plurals with the required quantities of the language; protected texts untouched; length at most `floor(room * 1.4)`; no exclamation mark outside Pêt's lines; same file names and key order as English; UTF-8 without BOM, LF.
3. **Pêt pass:** after its three passes each translator, and the two supplement agents for `tr` and `fr`, writes the `[pet-voice]` keys from `docs/i18n/pet-voice.md` and `-PetPass`, into the same files.
4. **Two supplements (Sonnet, `tr` and `fr`):** the keys of 1b that are not yet there. They first run after slice C and after slice D (3.0 item 8), and once more at the freeze for any key that changed.
5. **Twin overlays (the translators' last step, or Haiku):** `app/src/twin/res/values-<suffix>/strings_build.xml` for each of the seven (copy of the `values-tr` one; the same three keys and values; `values-in` for Indonesian), because without it the test twin shows the owner's label in that language.
6. **Nine readers (Opus, one per non-English language including `tr` and `fr`), report only,** into `docs/waves/w1b/translate/read-<code>.md`: placeholders, length against the rooms, tone and the glossary, meaning errors; each finding is an exact replacement line. The language's translator applies the findings; Haiku re-runs the checks; no second review (plan step 6).
7. **Haiku, mechanical:** `powershell -NoProfile -ExecutionPolicy Bypass -File tools\strings-check.ps1` (exit 0; 2 means a ratchet got worse), `tools\make-test-language.ps1` then `-Check` and `-SelfTest`, the unit tests `ResourceParityTest`, `PluralsCoverageTest`, `TwinOverlayTest`, `PixelFontCoverageTest` (its second part), a grep for `!` outside the Pêt keys, a grep for `app_name` equality, `sh -n` on nothing new.
8. **The owner's page:** `i18n-worklist.ps1 -ReviewPage tr` writes `docs/i18n/tr-review.md`: instructions in Turkish and English, then a table `# | where it appears | English | Turkish | your correction`, sixty rows chosen by `[review-page]` (target 5 tabs, 10 Settings titles, 15 Home texts, 10 chat texts, 8 Welcome texts, 8 Pêt lines, 4 plurals); the exact globs are written when English is frozen. The corrections become the glossary (decisions log) and a fix round of the Turkish files; then the lead flips `reviewed` of `tr`. The Kurmanji page (`-ReviewPage ku`) is an extra for the owner to ask for.
9. **Outputs:** `R/values-es|de|pt|it|ru|in|ku/*.xml` (every English file), the 1b keys and the Pêt keys in `values-tr`, `values-fr`, nine `A/whatsnew/0.3.<code>.md` (translated from START's final English page; the file names are START's rule, SPLIT open point 9), the regenerated `app/src/debug/res/values-en-rXZ/` and `values-en/`. The lead then flips `shipped` of the seven in `Languages.kt`.

## 4. Unit tests (JUnit 4, JVM; a repo file is read with `Assume.assumeTrue(file.exists())`)

| Class | Tests (name: what it proves) |
|---|---|
| `T/text/LangTableTest` | `tenLanguagesInPlanOrder` (the tags `en es fr de pt it tr ru id ku`); `ownNamesAreExact` (the ten names of the table above); `tagsFoldersAndNamesAreUnique`; `foldersMatchTheTable`; `normalizeForms` (the list of 3.1); `matchLocale` (`Locale("in","ID")` gives id, `Locale.forLanguageTag("pt-PT")` gives pt, `ja` gives null); `alphabetsAreNonAsciiAndComplete` (no ASCII character; ru has all 66 letters including Ёё; tr has `İıĞğŞşÇçÖöÜü`; fr has `œŒÿŸ«»`; es has `¿¡ñÑ`; de has `ßäöüÄÖÜ`; pt has `ãõçÃÕ`; ku has `êîûşçÊÎÛŞÇ`; en and id are empty); `pluralRowsAreRight` (ru four required, id only other, others one and other); `allowedIncludesRequired`; `onlyEnglishIsReviewed` (the lead edits it with the flag); `shippedAtFirstAreEnTrFr`; `pluralSectionMatchesTheTable` (Assume `tools/strings-kinds.txt`: its `[plural-rules]` equals the table, folder suffix by folder); `rowsSettingsSystemFirst` (phone Turkish, all available: `""`, then the ten in order); `rowsWelcomePhoneFirst` (phone Russian: first row `""` with ru, then the nine others in order; phone Japanese: first row `""` with English, then the nine others; the phone's language is not listed twice); `rowsOnlyAvailable` (with the default, only en, fr, tr); `systemLangNullWhenUnavailable`; `answerAndDictationValues` (`""` first, then the tags); `languageSettingsCodec` (`cleanChoice("es")`, `("pt-BR")` gives pt, `("xx")` and `(null)` give `""`; `cleanSnapshot("")` is null; `snapshotKey("c1") == "snap.c1"`). |
| `T/text/AnswerLanguageTest` | `autoAddsNothing` (`line("")`, `line("xx")` are empty); `fixedLineGolden` (the exact string for `es`, with "Spanish"); `lineNamesEveryLanguage` (each of the nine contains its `englishName` and "replaces any earlier instruction", has no `!`, is at most 400 characters, has no newline); `resolveTable` (current es, no snapshot, 1 turn: es and remember; 2 turns: `""`; 600 turns: `""`; snapshot fr wins at any turns; blank snapshot ignored; unknown current: `""`); `systemTextUnchangedForAuto` (with the default provider `Transcript.systemPrompt(...)` equals the old composition exactly: for a 1:1 chat persona, mode directive, `STYLE_RULE`, the chat reference line, joined by blank lines; for a group chat the group framing first; `Transcript.build`'s system message is the same string); `fixedParagraphComesAfterStyleRuleBeforeReference`; `systemTextStableAcrossTurns` (a provider backed by an in-memory map: turn 1 with `historyTurns = 1` and setting es gives es and remembers; turn 2 and turn 5 give the same system message string; the setting flipped to de does not change it; a second chat started now gets de; an old chat (history 40, no snapshot) gets `""` whatever the setting; the system messages of turns 1 and 3 are string-equal, which is what the derived session id depends on); `groupBotsShareTheLine` (two bots in turn see the same snapshot); `providerIsReset` (an `@After` restores the default provider). |
| `T/text/DictationLanguageTest` | `defaultsForTheTen` (`dictation = "tr"` on a US phone gives tr-TR and assured; `"es"` gives es-ES; `"pt"` pt-BR; `"id"` id-ID; and so on); `autoFollowsTheInterface` (`""` with interface `fr` gives fr-FR); `autoWithSystemFollowsThePhone` (interface `""` and phone `Locale("de","AT")` gives de-AT; phone `ja-JP` gives ja-JP, assured); `fixedBeatsInterface`; `regionFromPhoneOnlyForTheSameLanguage` (dictation pt with phone pt-PT gives pt-PT, with phone es-MX gives pt-BR; es with phone es-MX gives es-MX); `kuIsNotAssured` (tag is the phone's, assured false, whatever the phone); `indonesianPhone` (`Locale.forLanguageTag("id-ID")` and a legacy `Locale("in","ID")` both give id-ID); `unknownValueActsAsAuto`. |
| `T/text/LanguagePrefsTest` | `cleanedChoicesNeverCarryUnknownTags`; `defaultsAreAutoAndAuto`; `snapshotKeysDoNotCollideWithTheSettingKeys` (`snap.answer` is not `answer`); `installSetsTheProvider` (only the pure part: `LanguagePrefs` itself needs a Context and is proven on the phone) |
| `T/text/PluralRulesTest` (extended) | `tenLanguagesTable` (for each tag the categories of 0, 1, 2, 3, 5, 11, 12, 21, 22, 25, 100, 101, 111 equal a written table: en, de, es, it, tr, ku one at 1 only; fr, pt one at 0 and 1; ru: 0 many, 1 one, 2 and 3 few, 5 many, 11 and 12 many, 21 one, 22 few, 25 many, 100 many, 101 one, 111 many; id all other); `inAndIdAreOther`; `millionIsOtherInAppRules` (es, fr, it, pt give other for 1,000,000: the documented simplification); `pluralProbeMismatches` (`PluralProbe.mismatches("es", ["other","one",...])` is empty; `many` from the platform at es counts as agreeing; a wrong `ru` category is reported with its count) |
| `T/text/PluralsCoverageTest` (new) | `everyPluralsHasItsLanguagesQuantities`: for every `values*` folder under `src/main/res` that maps to a language of the table, every `<plurals>` not marked `translatable="false"` contains every required quantity and nothing outside `allowedPlurals`; `probeHasSixQuantities` (`lang_probe_plural` in `values/` has zero, one, two, few, many, other); `noUnknownFolder` (a `values-xx` folder that is not a table folder fails, `values-en-rXZ` lives under `src/debug` and is not looked at) |
| `T/text/ResourceParityTest` (extended), `T/text/ResXml.kt` | `ResXml.languageDirs()` is built from `LangTable.all` (the folders that exist), not from `listOf("tr","fr")`; the test loops over every language folder that exists, and adds `everyShippedLanguageHasItsFolder` (shipped implies the folder exists) and `aFolderThatExistsIsComplete` (the existing parity check for every present folder, so a half-written language fails) |
| `T/TwinOverlayTest` (edit) | `labelsAreOverriddenInAllThreeLocales` becomes `labelsAreOverriddenInEveryShippedLocale`: `values` plus the folder of every language that exists under `R/` (and `values-in` for Indonesian), each with the same three keys and the same values |

Pure by construction: `Lang`, `LangTable`, `LangSelect`, `LanguageSettingsCodec`, `PluralProbe`, `AnswerLanguage`, `DictationLanguage` take values, never a Context, a clock or a file. `LanguagePrefs`, `LangProbe` and the composables are proven on the phone.

## 5. How it is proven on the phone

**Owner-debug build (the owner's package; `"safe": true`; memory pins and pictures only; nothing is written):** `tools/scenarios/w1b-languages.json` (the lead writes it after INTEGRATE-B).
| # | Picture or check | How |
|---|---|---|
| L1 | The five main screens (Home, Chats, Library, Bots, Agent) in each of the ten languages, in NEWROZ dark and NERGIZ light, nothing missing, nothing cut | debug link `reset 1`, `lang <tag>` for `en es fr de pt it tr ru id ku`, `route home|chats|library|bots|hermes`; a picture each; the `AgiriCut` log (`CutProbe`) empty; `strings-check.ps1 -ScanDump <dump> -ExpectLang <suffix>` exits 0 for the nine (it flags English where the language has a text) |
| L2 | Settings > Language in each language | route `settings:language`: nodes `lang.ui.system` and `lang.ui.<tag>` for the offered languages, `lang.mt.note`, `lang.answer.auto`, `lang.dictation.auto`; the dump's texts of the rows are the ten own names exactly (the scenario compares them with the table through a `contains` wait) |
| L3 | Plurals and dates for each of the ten | the debug link `lang` makes SHELL call `LangProbe.log`; `app.log` has one `I/Lang: probe tag=<t> plural=... agree=true date="..."` line per language (`agree=false` fails; the date text of each of the nine differs from the English one); the Chats list's times and the Bots count are in the pictures of L1 |
| L4 | Stretched, mirrored, double text | `lang en-XZ` and `rtl 1` of Settings > Language; text at 2.0 (needs the lead's `-AllowSystemSettings`): the rows wrap, nothing is cut |
| L5 | The owner's data unchanged | read only: `shared_prefs/language_settings.xml` does not exist after the run; `appearance.xml`'s `language` is what it was |
| L6 | Pixel font titles in `ru` and the accented Latin ones | pictures of L1 judged by LOOK-B with ART-B's contact sheet; the tab labels in each language (a label that does not fit the bar shows icons only, SHELL's rule) |

**Test twin (`dev.pocketagent.app.debug`, practice model, zero tokens; `pm clear` allowed):** `tools/scenarios/w1b-lang-settings.json`, not `safe`.
| # | Check | How |
|---|---|---|
| L7 | Interface language is kept | tap `lang.ui.ru`, `am force-stop`, reopen: the screen is Russian |
| L8 | The fixed answer language and the stable system text | tap `lang.answer.es`; new chat, send a plain message; `app.log` has `Lang: answer conv=... tag=es remember=true`; tap `lang.answer.auto`; send again in the same chat: `Lang: answer ... tag=es remember=false` (the chat keeps its snapshot); start a second chat: `tag=auto` and no line; a chat that was seeded before the choice (the debug link `seed`) and gets a message now logs `tag=auto remember=false` |
| L9 | Dictation tag | tap `lang.dictation.tr`; open a chat and tap the microphone: `app.log` has `Lang: dictation tag=tr-TR assured=true`; with `lang.dictation.ku`: `assured=false` and the node `lang.dictation.note` |
| L10 | The Welcome's first step uses the same list | WELCOME's scenario reads `welcome.lang.<tag>` nodes (their tags come from `rowTag`) |

**What only the owner's eyes can judge:** that the picker reads well (order, own names, the wording of the mark and its note); the Turkish page (`docs/i18n/tr-review.md`, about ten minutes); the Kurmanji page if asked; how the pixel font looks in Russian and with the new accents (ART-B's sheets, judged by LOOK-B first). Whether any language is right is not something an agent can claim: every language but English stays marked "machine translated" until a speaker has read it, and Turkish flips after the owner's page.

## 6. Core and extras, and the order of cutting (first cut first)

**Core:** `LangTable`, `LangSelect`, `LanguageSettings` codec, `LanguageChoices`, `LanguageSection` with the three settings (interface, answers, dictation), `LanguagePrefs`, `AnswerLanguage` with the `Transcript` edit and the snapshot, `DictationLanguage` and the `ChatInputArea` block, `strings_lang.xml`, `PluralProbe` and `LangProbe.log`, `plural-form` and `folder` checks, `[plural-rules]`, `i18n-worklist.ps1`, the ten glossaries and `README.md`, `pet-voice.md`, the seven translators, the two supplements (per slice), the Pêt pass, the nine readers, the twin overlays, the extended parity and twin tests, `tr-review.md`, the regenerated stretched language, the nine whatsnew pages.
**Extras, cut in this order:** 1. `docs/i18n/ku-review.md` (the owner's page for Kurmanji); 2. the Pêt pass for `tr` and `fr` (they keep the 1a translations); 3. the glossary check G of stage 4; 4. `-ExpectLang` of the dump scan (the other checks stay); 5. `lang_voice_note`; 6. `LangProbe.dateLine` (the plural line stays); 7. the locale span on the own names. **If the lead vetoes decision 1 of section 3.0:** `AnswerLanguage.line` returns `""` for every tag, the answer chips and `lang_answers_*` are not composed (the keys stay, so parity stays), nothing else changes. **Never cut:** the table and its folders, the picker with the three settings (answers only as in the veto sentence), the glossaries, the plural check, the twin overlays, the parity extension, the alphabets handed to ART-B, `LanguageChoices` (the Welcome calls it).

## 7. REQUESTS to other areas, and what LANG provides

**Requests (exact):**
- **APPEAR-B** (`J/ui/settings/AppearanceSection.kt`, `J/ui/settings/SettingsScreen.kt`, `J/ui/theme/Appearance.kt`): in the same commit as LANG's `LanguageSection.kt`, delete `internal fun LanguageSection()` with its KDoc (from `/** System / English / Türkçe / Français; applies immediately. */` to its closing brace) from `AppearanceSection.kt`, and `internal fun languageName(code: String)` (the `@Composable` and its `when`) from `SettingsScreen.kt`; leave the call sites `Section.LANGUAGE -> LanguageSection()` and `languageName(ap.language)`; drop imports that become unused; change the comment on `Appearance.language` to `/* "" (system) or one of the ten tags of LangTable */`. A duplicate would not compile.
- **SHELL** (`J/DebugLinks.kt`, `apply`): in `when (val l = req.language) { ... is Setting.Set -> DebugOverrides.setLanguage(l.value) }` add `LangProbe.log(app, l.value)` after the call (the function does nothing without `BuildConfig.TEST_HOOKS`); no new extra, no change to `DebugRequest`. Add to `T/DebugLinksTest.kt` nothing (the call is a log).
- **WELCOME-UI:** step one calls `LanguageChoices(selected = Appearance.language, onPick = { store.update { it.copy(language = value) } }, mode = PickerMode.WELCOME, showMark = ..., rowTag = { WelcomeTags.lang(it.lang?.tag ?: "en") })`; the first row stores `""`. The Pêt lines of the Welcome are named `wel_pet_*` with a note stating the situation (they go through the Pêt pass), and every `wel_` key has its note.
- **START:** `wn_note` (the Pêt note) keeps that name and is Pêt's voice; the English `A/whatsnew/0.3.md` is final before stage 4 (nine translations are made from it, named `0.3.<code>.md`: the code of the folder suffix, so `0.3.id.md`, `0.3.ku.md`).
- **ART-B:** `LangTable.all[*].alphabet` is the audit input (non-ASCII only); `PixelFontCoverageTest` reads it. The ten alphabets need no glyph beyond what ART-B's table lists; any character a glossary asks for later (for example a typographic `’` or `…`) is a finding for ART-B, not an edit here.
- **Every area that writes English strings** (SHELL, BARS, LIBRARY, WELCOME, START, APPEAR-B, and 1a's areas for the keys they still add): a note on every key (`<!-- t: where it appears, placeholders, room | kind: ... -->`), kind from the list tab, button, chip, title, row, pet, widget, no exclamation mark outside Pêt's, no case change in code, count nouns as `<plurals>` (never `%1$d things`), no key whose English needs a gender for the reader or the agent.
- **INTEGRATE-B** (`J/PocketApp.kt`): `slot: LANG: try { dev.pocketagent.app.ui.text.LanguagePrefs.install(this) } catch (e: Throwable) { AppLog.e("App", "language prefs failed", e) }` directly after the `Texts.install(this)` line (line 111 today). Also: `T/ScenarioFilesTest.kt` known tags (section 3.9); `tools/scenarios/leak-allow.txt` lines for the own names, nothing broader: `^Espa.ol$`, `^Deutsch$`, `^Portugu.s$`, `^Italiano$`, `^Bahasa Indonesia$`, `^Kurd. \(Kurmanc.\)$`, and `^[А-Яа-я]+$` for `Русский` (a console that prints `?` for letters needs the same wildcards as the existing `^T.rk.e$`); `tools/size-budget.txt` (seven languages add about 0.5 to 0.7 MiB of resources; the whole of 1b is at most 2 MiB); flip `shipped` of the seven and `reviewed` of `tr` in `Languages.kt` when the lead says; the regenerated stretched language and its `-Check` in CI.
- **The lead's robot pass:** `w1b-languages` and `w1b-lang-settings` of section 5; the leak-allow lines above.

**LANG provides:** `Lang`, `LangTable`, `LangSelect`, `LangRow`, `PickerMode`, `RowKind` (SHELL, WELCOME-UI, APPEAR-B); `LanguageChoices`, `languageName`, `LangTags` (WELCOME-UI, Settings summary); `LanguagePrefs.get/install`, `LanguageSettings` (INTEGRATE-B, `ChatInputArea`); `Transcript.answerLanguage`; `AnswerLanguage`, `DictationLanguage`, `DictationPlan`; `LangProbe.log` (SHELL); `PluralProbe`; the keys `lang_*`; the glossaries, `pet-voice.md`, `i18n-worklist.ps1`, the `[plural-rules]` data (INTEGRATE-B, every later wave: texts of later waves are translated in their own wave with these).

## 8. Risks

1. **Translation quality is unproven.** No speaker has read any of the nine; Kurmanji is the weakest case for a model. The mark, English behind every gap, a reader per language and the glossaries are the defences (plan risk, line 3185); nothing claims a human author.
2. **Length.** German and Russian run long: a tab (room 10, a translation 14), a chip (18, 25) and a button (22, 30) are tight, and the baseline of known length violations never rises. Rule: short forms in the glossary; a room that cannot be met is a finding for the English author.
3. **Changing the system text splits a chat's Hermes session** (read in the source, section 2). The snapshot keeps a chat's text constant; a forked chat has a new id and few turns of history, so it starts with `""` unless forked at turn one (accepted: a fork is a new session anyway). A bug in `historyTurns` would silently change old chats: `systemTextStableAcrossTurns` and `autoAddsNothing` pin it.
4. **Persona text still says "in the language of the user's latest message".** The fixed-language paragraph comes after it and says it replaces any earlier instruction; a model may still follow the persona. The proof is on the twin only with the practice model (the line is sent; whether a real model obeys is a real-model check with Hydrogen at low effort, one message, left to the lead).
5. **ICU may not know `ku`** (plural rules and date names). The probe shows it per language. Fallback: `ResourceTextSource.plural` would route `ku` through `PluralRules` (an edit of Wave 0's TEXT file; the lead decides), and dates would be `DateTimeFormatter` ISO style.
6. **Folder name for Indonesian** (`values-in`, `values-id`, `values-b+id`) and the Locale of `ku` (some platform data treat plain `ku` as Arabic script, which would mirror the layout): E-B2 on the twin decides; the table has one line for each (`folder`, `tag` may become `ku-Latn`; `normalize` accepts both).
7. **The test twin's label** in the seven new folders (section 2): forgotten twin files show "Agirî" on the twin; `TwinOverlayTest` pins it.
8. **Speech out in the new languages** reads Spanish text with the English voice (`TtsTextCleaner` knows three languages; `VoiceEngine` sets the system TTS to tr, fr or US): the note `lang_voice_note` says so; Wave 8 fixes it.
9. **Backup:** the interface language is in `appearance` (in the backup schema), the answer and dictation choices are not; a restore keeps the language and forgets the two choices.
10. **The tool files are being edited today** (Wave 0 defect fixes): slice B waits for that commit; `strings-check.ps1` is 665 lines and the new checks are small, but its ratchet logic is shared, so the plural check must not add a counted warning for English.
11. **English changes after a supplement** make `tr` and `fr` drift (nothing records the source of a translation). The readers read tr and fr against the frozen English; this is the only defence.
12. **Parity between slices** depends on the two supplement runs; if one is skipped the unit tests are red until slice E.

## 9. Open points for the lead

1. **Fixed answer language by snapshot (3.0 item 1):** shipped, new chats only, stated on the screen. If a per-chat control is wanted sooner (Wave 2's chat settings), the snapshot can be edited there. Veto: section 6.
2. **Three claims outside the split's list:** `T/text/ResXml.kt`, `T/TwinOverlayTest.kt` and the seven `app/src/twin/res/values-*/strings_build.xml`, plus the new `J/ui/text/LangProbe.kt` and the four sections of `tools/strings-kinds.txt`. Nobody else edits them in 1b; confirm.
3. **E-B2 (twin):** the folder for Indonesian, the tag for Kurmanji (`ku` or `ku-Latn`), and what `Locale.forLanguageTag("ku")` mirrors; the `LangProbe` lines answer the plural and date half. Until then the table says `values-in` and `ku`.
4. **Pêt lines in `tr` and `fr`** are replaced by fresh ones when the reader keeps them (3.0 item 7); the owner will see new Turkish quips on the review page. Say if the shipped ones must stay.
5. **Per-language `reviewed`:** all nine start marked, French included (no speaker has read it); only Turkish flips, after the owner's page.
6. **Welcome shows "machine translated" under each language** by default (`showMark = true`); WELCOME-UI may hide it if the step is too crowded. The lead's taste.
7. **Which texts are "the sixty most visible":** the composition is fixed (3.10 item 8); the globs are written at the English freeze because 1a's Home key names are not in the tree yet.
8. **Extra files for stage 4:** the readers' reports go to `docs/waves/w1b/translate/read-<code>.md`; confirm the folder.
