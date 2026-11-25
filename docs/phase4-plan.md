# Agirî: the road to 1.0, master plan (second edition)

Written 2026-11-24 by the lead agent; second edition the same day, after the owner's notes.
**Nothing in this file is built yet.**

**What this is.** The plan for making Agirî the best AI experience a phone can hold. It has to do
everything the leading assistants do on a phone (a workspace for what it makes, documents and slides,
pictures, research with sources, voice, memory, projects, tasks), and then what only an agent that
lives on the phone can do: use the phone, run a real computer in the pocket, show what left
the device, and be a place one likes to open. It is a programme of fourteen waves (0 to 13). Each wave
is at least the size of Phase 3 (the five largest ship in two halves), ends in a build that was
checked on the real phone, and can be the last one without leaving anything half-done.

**What changed in this edition.**
- **A creative workspace.** The Canvas (a panel for whatever the agent makes, with versions and with
  editing together), documents, slides, sheets, pictures and a gallery of ready-made recipes
  (Waves 5 and 6).
- **Finding things out.** Deep research, sources on every answer with a mark that says whether the
  app itself received the page, a library for every thing, study tools (Wave 7); and spaces, which
  other apps call projects, from Wave 2 on.
- **Everything else a complete phone assistant has, and more:** an interpreter, a private recorder,
  audio overviews, tasks and reminders, night shift, writing tools inside every app, a live wallpaper
  and a bedside clock, private chats and an app lock, moving to a new phone without a cloud.
- **A global app.** English is the first language and the source of every text. A first set of
  languages comes in Wave 1, the difficult scripts in Wave 12. Every new name in this plan is English.
  The hearth, the mountain and Pêt stay as the app's character, as shipped; nothing new is named in
  Kurdish, and cultural days are a choice of region, not a default.
- **The safety spine moves forward.** The bridge, the gate and the keeper are built in Wave 3, because
  pictures, sources and costs need them long before the phone's powers do.

**How it was made.** From a survey of: all 26,000 lines of the app; screenshots of every screen in
dark and light; the source code of Hermes v0.23.3 exactly as it sits inside the app's Linux (the code,
not its documentation); Android's rules for an app that targets API 28 and runs on Android 17; the
speech models the embedded sherpa-onnx library can run; and what the leading assistants offer on
phones in late 2026. Section 3 lists what was found, with file and line. The first edition was
attacked by an independent reviewer (a second strong model with the code and the Hermes source in
front of it); its thirty findings are worked in, and the gate, the test twin and the private signing
key exist because of that review. The second edition was attacked again by two such reviewers, one
for facts and safety and one for the product as a whole. Their sixty findings, eight of them serious,
are worked in: they changed how blocks, imports and prices meet the gate, how research runs
unattended, where things live in the app, and how much one wave is allowed to hold.

**How to use it.**
- **The owner:** sections 1, 2 and 4 say what you get and in which order. Section 13 lists the few
  decisions that are yours. Everything else can be left to the agents.
- **The lead agent:** section 6 is the way of working, section 7 the waves, section 8 the experiments
  that must come first. Start every wave by writing its contract, `docs/waves/w<N>-contract.md`, in
  the style of `docs/phase3-plan.md` (exact names, one owner per file).
- **Every agent that touches the interface:** `docs/design-language.md` is binding.

## 1. The idea

Today the fire on the home screen is a picture of the agent. At 1.0 the fire **is** the agent: it
shows what Hermes is doing, you talk to it, it warms the whole app, and it burns firewood that you can
see. Seven promises carry the plan:

1. **Hand-made and fast.** It looks and moves like a crafted pixel game, at the full 120 Hz of the
   screen, under a sky that is the real sky above you.
2. **It shows its work and answers to you.** Every step it takes is visible, it can be stopped and
   redirected while it works, its cost is shown in money, and its sources are real.
3. **It makes things.** Documents, slides, sheets, pages, diagrams, charts, pictures and small apps
   appear in a workspace of their own, the Canvas, where they have versions and can be changed
   together, by hand or by asking.
4. **It finds things out.** It can spend half an hour reading fifty pages and come back with a report
   in which every claim leads to the page it came from, and it can answer from your own files.
5. **You can talk to it.** A hands-free conversation by the fire, an interpreter between two people, a
   recorder that writes down a meeting, and all of the listening and speaking can stay on the phone.
6. **It has hands.** It can use the phone (alarms, calendar, clipboard, notifications, apps, a real
   browser), start on events, and work through a list at night while the phone charges. Every tool it
   uses passes one gate that is yours.
7. **It is yours.** No account, no server of ours, any model from any provider, your language. After
   every answer you can see what the agent's tools and the app itself sent out.

### A day with Agirî 1.0

- **Night.** The phone is on its charger. That is the night shift: the two jobs you queued in the
  evening (a research question, the weekly review) are done under the budget you set. The screen shows
  the bedside clock: the fire low, the stars that are really overhead, the time.
- **Morning.** You pull the cable. The morning brief is ready: the weather, today's calendar, what is
  due, what the night brought. On the home screen the hearth widget shows dawn over the mountain.
- **On the way.** You hold the side button. A small sheet rises over whatever app you are in, you ask,
  the answer streams into the sheet, you go on.
- **At work.** "Compare these three suppliers and make me a deck." You watch it work: searched, read
  fourteen pages, wrote. A report opens in the Canvas with numbered sources, and each source carries a
  mark: the app itself received that page's text. "Now the slides." A deck appears; you fix one title by
  hand, ask for a picture on slide three ("about 4 cents. Create?"), plug the phone into the screen of
  the meeting room, and present: slides on the big screen, notes and a timer in your hand.
- **Lunch.** The waiter speaks another language. Interpreter: you speak, the phone speaks; they speak,
  the phone speaks. The listening and the voices stay on the phone.
- **Afternoon.** You record a meeting. The recording never leaves the phone; what you get is a
  transcript with speakers, the decisions and the tasks. "Remind me to call the dentist at five." A
  card asks once; you tap Allow.
- **In another app.** You select a clumsy paragraph in an e-mail, choose "Rewrite, shorter" from the
  selection menu, and the paragraph is replaced where it stands.
- **Evening.** You open the app. The sky is the real evening sky with tonight's moon, and it rains in
  the scene because it rains outside. The woodpile by the fire is half gone: half of today's budget.
  You talk by the fire without touching the screen. On the way home the report is read to you as a
  conversation between two voices. Before sleep you queue two jobs for the night.
- **All of it** in your language, for a fraction of what a message costs today, and with nothing
  leaving the phone that is not on a list: what you send to the model, what the agent looks up for
  you, what you chose to download. The list is one tap away under every answer.

### What changes, in numbers

| | Today | Target at 1.0 |
|---|---|---|
| The build the owner runs | A debug build (unoptimised by design) | An optimised build, compiled ahead of time, signed with a private key |
| Frames drawn in time (8.3 ms) while scrolling and streaming | Never measured | At least 95 % |
| Animation timings in the code | 15 unrelated values | 6 durations and 3 springs, used everywhere |
| Gestures | Tap, long-press | Plus back swipe, row swipes, quote swipe, hold to put out the fire |
| Fixed cost of one message | About 11,000 prompt tokens | Measured, then cut: goal 6,000 for the full agent, 2,500 for light bots |
| What you see while it works | A tool name and "running" | Steps with time and result, sub-agents, stop, steer |
| What it can make | Text in a chat bubble | Documents, slides, sheets, PDFs, pages, diagrams, charts, pictures, small apps, each with versions |
| Sources | Links, if the model writes them | Numbered citations; a mark says whether the app itself received each page, and warns when a quote is not in it |
| Long work | One answer at a time, lost if the app dies | Research runs of up to forty minutes in the background, resumable, with a budget |
| Talking | Dictation into the text field | Hands-free talk, interpreter, recorder with transcript, audio overviews, on the device if wanted |
| What the agent can do on the phone | Run commands inside its Linux | Alarms, calendar, clipboard, notifications, apps, links, tasks, a browser, behind one gate |
| Outside the app | Notifications, one tile, three shortcuts | Widgets, quick question over any app, writing tools in every app, live progress, a live wallpaper, a bedside clock |
| Cost is shown as | Tokens | Tokens and money, and the woodpile |
| What left the phone | Not shown | Listed under every answer |
| Interface languages | English, Turkish, French | English first, eighteen at 1.0 including right-to-left and East Asian scripts, and any other through a pack the user can make |
| Checks on the phone | By hand, every time, on the owner's own app | Scripted, repeatable, at zero tokens, on a test twin |

## 2. Where Agirî stands

### 2.1 Against the best

What the leading assistants offer on a phone in November 2026, what Agirî has today, and what this
plan brings. The middle column was read in each vendor's own release notes or product pages (the pages
are listed in section 14); a cell says what was found there, not that nobody else has it.

| What | The best on phones today | Agirî today | Agirî at 1.0 | Wave |
|---|---|---|---|---|
| A workspace for what it makes | ChatGPT (pages and slides, editable on the phone), Claude (artifacts, documents and decks in any chat), Gemini (Canvas), Perplexity (Labs), Le Chat (Canvas) | No | The Canvas: twelve kinds of output, versions, editing by hand and by asking, export | 5 |
| Office files | ChatGPT, Claude, Copilot (previews of Word, Excel and PowerPoint) | No | Real DOCX, PPTX, XLSX and PDF, made by real libraries on the phone, drawn natively, presented from the phone | 6 |
| Pictures | ChatGPT, Gemini, Grok, Le Chat, Meta | Reads pictures | Makes and edits pictures through your own provider, the price shown first; a gallery; pixel tools | 6 |
| Video and music | Gemini, Grok, Meta | No | Not planned (cost, section 12). Narrated slides as a video file | 8 |
| Research with sources | ChatGPT, Gemini, Perplexity, Le Chat | The agent searches the web; links arrive as text | Deep research in the background; numbered sources; a mark when the app itself received the page's text; "Check this" on any answer | 7 |
| Your own files as knowledge | NotebookLM, projects in ChatGPT and Claude | A file can be attached to a message | A library, spaces with their own knowledge, answers that cite your files | 7 |
| Audio overviews | NotebookLM | No | Two voices, made on the phone, playable offline | 8 |
| Voice conversation | ChatGPT, Gemini Live, Meta, Grok | Dictation; answers read aloud | Fireside: hands-free, can be interrupted, listening and voices on the device if wanted | 8 |
| Live camera and screen | Gemini Live, Grok, Meta | No | Live look: a photo, or a frame of the screen, inside a talk; one consent per look | 8 |
| Interpreter, recorder with transcript | Samsung, Pixel, Apple, Copilot | No | Both, on the device | 8 |
| Memory you can inspect | ChatGPT, Claude, Gemini, Le Chat, Meta | Hermes's memory files, editable as text | Memory as cards with source and date; nothing is remembered without review, if you want | 3, 11 |
| Projects | ChatGPT, Claude, NotebookLM | No | Spaces: instructions, files, chats, bots, and later their own knowledge and look | 2, 7 |
| Scheduled work and daily briefs | ChatGPT (tasks, Pulse), Gemini (Daily Brief, background jobs), Claude (repeating jobs), Samsung (Now Brief) | Routines: daily, interval, once | Routines on events, watching a page, night shift, the morning brief | 9 |
| Acting for you | ChatGPT (a browser on signed-in sites, purchases), Claude (computer use, beta), Gemini (mail and calendar), Comet | Commands inside its own Linux | Phone powers behind one gate, a browser of its own; no purchases, calls or messages in your name | 9 |
| Connections to your services | ChatGPT, Claude, Gemini | No | Calendar and contacts through Android itself, cloud files through the phone's file picker, folders you grant, guided setups for a calendar, files, mail and notes, 76 ready descriptions of outside services with sign-in | 4, 9 |
| Study tools | ChatGPT (flashcards, quizzes), NotebookLM | No | Flashcards with spaced repetition, quizzes, a reader with highlights | 7 |
| Writing help inside other apps | Apple, Pixel, Samsung | "Ask Agirî" in the selection menu | Rewrite, translate, summarise; the text is replaced where it stands | 10 |
| Home screen and lock screen | ChatGPT (voice on the lock screen), Copilot and Le Chat (widgets), Samsung | One tile, three shortcuts, notifications | Widgets, a quick question over any app, live progress, a live wallpaper, a bedside clock | 10 |
| Your own bots, bots together | Gemini (Gems), Le Chat (agents); ChatGPT retired group chats in 2026 | Custom bots, group chats, a bot forge | The Council (debate, compare, relay), a gallery, bots shared as cards | 11 |
| Private chats and locks | ChatGPT (locked and temporary chats), Pixel Journal | Everything is local | Private chats that leave no trace, an app lock, a privacy centre, masking of personal details | 11 |
| Choice of model | One vendor each | Seven ways to connect | Any provider, with prices, compared side by side | 4, 11 |
| Without the vendor's cloud | PocketPal, Google's AI Edge Gallery (small local models) | Speaking happens on the device | Listening and speaking on the device; an optional offline mind | 8, 13 |
| Languages | Dozens | 3 | English first, ten in Wave 1, eighteen at 1.0, and any other through a pack you can make yourself | 1, 12 |

### 2.2 What people complain about, and the answer here

The same survey collected what reviewers and users dislike about AI apps on phones.

| The complaint | The answer in this plan | Wave |
|---|---|---|
| Limits and paywalls: the best features need the dearest plan, and the meter runs out in the middle of work | Your key, your budget, nothing held back. The cost of every answer is shown in money, and you set the limit | 4 |
| Answers lost when the app goes to the background | What has arrived is saved every few seconds, and a turn that was cut off can be continued instead of paid for twice; a turn keeps running without the screen; a notification when it ends; the fire shows it | 3 |
| Voice that mishears, cuts you off or lags | Listening on the device with the model of your choice, a hold-to-talk fallback, and a talk that can always be interrupted | 8 |
| Wrong answers given with a straight face | Sources you can check: a mark for every page the app really received, a warning when a quote is not in it, "Check this" on any answer | 7 |
| Privacy: profiles built from what you do | No account, no server of ours, no measurement. The ledger lists what left the phone | 3, 11 |
| Features that exist only in some countries, or vanish with an update | A sideloaded, open app: it works wherever your provider works, and nobody can take a feature away | all |
| Nothing works offline | Speech, the library, your documents and the scene work offline; messages wait in an outbox; an optional offline mind | 3, 8, 13 |

### 2.3 What sets Agirî apart

Every wave has to match the best in its area and add at least one thing from this list.

**Only here** (nothing in the pages read does these):

1. **A place, not a tool.** A living pixel world under the real sky of your hour, with the weather
   outside your window. It is also your wallpaper, your widget and your bedside clock.
2. **One gate that is yours.** Every tool the agent uses passes it, and so does everything the agent
   wants to keep or create. Whatever lasts (a memory, a skill, a bot, a scheduled job) waits for your
   review, and what a turn changed in your files can be undone.
3. **The ledger.** Under every answer: which provider got how much, what was searched and where,
   which pages were fetched, what it cost. Kept by the app, not told by the model.
4. **A real computer in the pocket.** Documents are built by the same libraries a desktop uses, data
   is analysed with real Python, and the files are yours, in open formats, in a folder every app on
   the phone can open.
5. **Present from the phone.** Slides on the big screen, notes and a timer in the hand, made and
   shown by the same app.
6. **Pixel craft.** Turn a photo into pixel art, collect keepsakes, send postcards that carry their
   content inside the picture.

**Rare, and here together** (others have one or two of these; no single app has them all):

7. **Sources you can check.** A citation carries a mark when the app itself received that page's
   text, and a warning when a quoted sentence is not in it. The mark says the page was read. It does
   not say the page is right.
8. **Work while you sleep.** Queued work runs while the phone charges, under a ceiling, and waits for
   you in the morning, with no subscription tier in the way.
9. **Ears and a voice that can stay on the phone.** A recorder, an interpreter and audio overviews
   without a cloud for the sound, in the same app as the agent.
10. **Cost you can see.** Money, not tokens; the woodpile by the fire; light minds that carry a
    quarter of today's prompt.
11. **Any model, and several at once.** Compare answers side by side, let bots debate, change
    provider in the middle of a chat.
12. **Nobody in between.** No account, no cloud of ours, no telemetry. Backups are locked files, and
    a move to a new phone goes from phone to phone.

### 2.4 What Agirî will not copy

- A feed, companions, streaks, or anything built to keep you scrolling.
- Purchases, calls or messages sent in your name.
- An account, advertising, a subscription.
- Video and music generation, for now: there is no route to them that costs nothing (section 12).

## 3. What the survey found

Every row is something read in the code, measured, or read on a page that is named, and it is the
reason for a part of the plan. Paths starting with `app/` are in this repository (under
`app/src/main/java/dev/pocketagent/app/` unless a full path is given); paths marked **H:** are in
Hermes's source (`/root/.hermes/hermes-agent/` inside the guest). "Wave" says where the plan answers
it. Rows A1 to A15, B1 to B22 and C1 to C9 stood in the first edition and were checked by its
reviewer; the others are new.

### 3.1 Look, motion and speed

| # | Finding | Evidence | Wave |
|---|---|---|---|
| A1 | The app the owner uses is a **debug build**: no code shrinking, no ahead-of-time compilation. Compose is several times slower that way, so every animation today is judged at its worst | `app/build.gradle.kts` (`isMinifyEnabled = false`), `.github/workflows/build.yml:168` (`assembleDebug`) | 0 |
| A2 | There is no motion system: fifteen unrelated durations (from 100 to 2,400 ms), no springs except one, and the app ignores the system's "remove animations" setting | `ui/AppRoot.kt:193-207`, `ui/components/PocketSwitchRow.kt:78`, `ui/theme/Appearance.kt:18` | 0 |
| A3 | Most things do not animate at all: send becoming stop, button presses (filled buttons jump one pixel with no easing and no ripple), tab icons, list inserts and removals (five lists), sheets and dialogs beyond the platform default, settings sections, loading | `ui/chat/ChatInput.kt:228-238`, `ui/components/PixelButton.kt:136-145`, `ui/chat/ChatListScreen.kt:173-196` | 0, 1, 2 |
| A4 | Streaming text arrives in 60 ms lumps with no trailing update (the last words before a pause wait for the next piece), and the whole message is parsed again on each update | `data/ChatEngine.kt:478-485, 676`, `ui/chat/MarkdownView.kt:71` | 0 |
| A5 | The chat screen is one composable of about 940 lines that collects sixteen states; every streamed update recomposes all of it. A scrollable box sits inside the scrolling list (tool output) | `ui/chat/ChatScreen.kt:149-171`, `ui/chat/ChatMessages.kt:515-518` | 0 |
| A6 | Going back loses a screen's state (the route switch keeps no saved state per screen), so lists return to the top | `ui/AppRoot.kt:260-268` (inferred from the code, to be confirmed in Wave 0) | 0 |
| A7 | No gestures: no back swipe that follows the finger, no row swipes, no pull, no drag | no `draggable`, `anchoredDraggable` or swipe handler anywhere under `ui/` | 0, 2 |
| A8 | The home scene is drawn as hundreds of rectangles per frame through Compose, on a clock of about 40 updates per second that is not tied to the display. It cannot be reused in a widget or a notification | `ui/pixel/HearthScene.kt:235-365`, `ui/pixel/PixelImage.kt:193-208` | 0 |
| A9 | The scene is always night. In the light themes it ends with a hard edge above a cream page | `ui/pixel/HearthScene.kt:189`, screenshot `dist/review-shots/v23_home_light.png` | 1 |
| A10 | Two shape languages on one screen (stepped panels next to rounded pills, chips and code blocks); about 600 literal `dp` and 290 literal `sp` values; twelve Android toasts, three private confirm dialogs, five differently styled sheets | `ui/chat/ChatMessages.kt:168, 410, 517, 539`, `ui/chat/ChatCommon.kt:281-314` | 0 |
| A11 | A long answer sits in a bordered box 84 % wide beside an avatar column, with four action icons under every reply | `ui/chat/ChatMessages.kt:197, 294, 354-403`, screenshot `dist/plan-shots/p3_chat.png` | 2 |
| A12 | The pixel font ignores the text-size setting and cuts titles early; the capital İ loses its dot | `ui/components/PixelText.kt:53`, screenshot `dist/phone-shots/09_group_chat.png` | 0, 12 |
| A13 | Home spends its best space on a status card with a full-width button that the automatic start made nearly useless | `ui/home/HomeScreen.kt:216-222, 393-445` | 1 |
| A14 | The script that builds the pixel art lives in the git-ignored `dist/` folder: the art cannot be rebuilt from the repository | `dist/craft_art.py` | 0 |
| A15 | The version code is the build number, and local builds have run ahead of CI (32 on the phone, 14 in CI): Android would refuse the next CI builds as downgrades. The signing key and its password lie in the public repository (meant for test builds). And agents test on the very installation that holds the owner's chats and credentials | `app/build.gradle.kts:17, 88, 99-112`, `AGENTS.md` | 0 |

### 3.2 The agent

| # | Finding | Evidence | Wave |
|---|---|---|---|
| B1 | Hermes has a second, richer way to run a turn that the app never uses, the **Runs API**: tool steps with duration, error flag and a result preview; sub-agent start and finish with tokens and cost; the model that really answered; cached tokens; a real **stop**; **steer** (add an instruction to a running turn); and the stream can be left and joined again without ending the run | **H:** `gateway/platforms/api_server_runs.py:287-293` (routes), `:124-146` (events), `:132-135` (usage), `:1277` (resume with `Last-Event-ID`), `:1440-1491` (steer), `:494-531` (it accepts `conversation_history`, so the app can keep owning the transcript) | 3 |
| B2 | The agent is told about **24 tools** on every message. Among them the browser set, whose first use would try to download a 170 MB Chromium that cannot run under proot | **H:** `toolsets.py:11-36, 191-195`, `tools/browser_launcher.py:155-175` | 0 (stop-gap), 4 |
| B3 | Which tools exist is set only in the configuration (`platform_toolsets.api_server`), never per request. Other MCP servers are merged in unless `no_mcp` is listed | **H:** `gateway/platforms/api_server.py:2216-2217`, `hermes_cli/tools_config.py:572-575, 737-757` | 4 |
| B4 | The fixed prompt can be measured offline, part by part (`hermes prompt-size --platform api_server --json`), and has several switches besides tools: guidance blocks, memory blocks, the skills index, a platform hint | **H:** `hermes_cli/subcommands/prompt_size.py`, `hermes_cli/config_defaults.py:153-182, 1261-1264`, `agent/system_prompt.py:316-320` | 4 |
| B5 | One gateway can serve **several profiles** (each with its own configuration, so its own tools) under `/p/<profile>/`. That is the way to give light bots a light mind without restarting anything | **H:** `gateway/platforms/api_server_profile_prefix.py:56-83`, `hermes_cli/profiles.py:1091-1116`, `gateway/config.py:46-60` | 4 |
| B6 | Web search and page reading already work **without any key** (a ring of free public endpoints). Queries then go to those vendors, which the owner should know and be able to switch off | **H:** `plugins/web/keyless_mcp.py:24-26`, `hermes_cli/config_defaults.py:380-389` | 0, 3 |
| B7 | Hermes speaks MCP as a client (HTTP with headers, `${VAR}` from `.env`, include and exclude lists per server), and the Python `mcp` package is installed in the guest. So the app can offer its own tools to the agent | **H:** `tools/mcp_tool_config.py:742-762`, `tools/mcp_tool_transport.py:119, 636-646` | 3 |
| B8 | The browser tool can attach to a browser that is already running through a DevTools address (`BROWSER_CDP_URL`). The phone has such a browser: the system WebView | **H:** `tools/browser_session_cdp.py:24-57, 92-108` | 9 |
| B9 | Hermes can push its events (tool calls, session ends, cron runs) to a URL, signed | **H:** `agent/outbound_webhooks.py:1-7` | 3 |
| B10 | Hermes keeps cost per session in money and ships price tables | **H:** `persistence/sqlite/_schema_ddl.py:162-166`, `agent/usage_pricing.py:113-148` | 4 |
| B11 | Files the agent makes reach a streaming client only as a text tag (`MEDIA:<path>`); nothing is shown | **H:** `gateway/platforms/api_server.py:707-747`, `api_server_openai_routes.py:1190-1192` | 2 |
| B12 | 134 optional skills ship inside the guest and install without network; 76 MCP server descriptions too | **H:** `tools/skills_hub_official.py:34-48`, `optional-mcps/` | 4 |
| B13 | Side tasks (summaries, vision) use an "auto" model that, with an OpenRouter key present, may fall to a paid model | **H:** `hermes_cli/config_defaults.py:730-751` | 0, 4 |
| B14 | Sub-agents can run on a cheaper model (`delegation.model`); the number of steps per answer has a setting (`agent.max_turns`) | **H:** `hermes_cli/config_defaults.py:1278-1345`, `gateway/run.py:1597-1613` | 4, 7 |
| B15 | The question tool (`clarify`) is not available to API clients by default; text parts are cut silently at 65,536 characters; audio and file parts are refused | **H:** `toolsets.py:194`, `gateway/platforms/api_server.py:192-193, 482-489` | 2, 3 |
| B16 | Telegram would work from inside the same process but installs its library on first use; e-mail needs no library at all | **H:** `plugins/platforms/telegram/adapter.py:262-279`, `plugins/platforms/email/adapter.py:4-24` | 13 |
| B17 | Files the agent makes are **deleted by the next update** that brings a new Linux image (every CI build does): only a fixed list of names inside Hermes's own folder is carried over, and the agent works in `/root`. The launcher already accepts extra mounts, which is the way out | `runtime/UserState.kt:17-30` (the list), `runtime/RuntimeManager.kt:222-224, 253-259` (delete, park, restore), `runtime/pocketagent-proot.sh:32, 134-135` (`PA_EXTRA_BIND`) | 2 |
| B18 | Hermes can hold back what the agent wants to remember, or to save as a skill, until someone approves it. For an API client the waiting writes are plain files in a folder; nobody reads that folder today | **H:** `tools/write_approval.py:1-13, 77-93, 169-192`, `hermes_cli/config_defaults.py:1268, 1432` | 3, 11 |
| B19 | Hermes has an official **gate before every tool call**: a plugin's `pre_tool_call` receives the tool, its arguments and the session, and may veto the call or send any tool to the human-approval gate; `post_tool_call` reports what returned. Shell hooks do the same from the configuration alone. A user plugin must be listed under `plugins.enabled`. Nothing uses this today | **H:** `hermes_cli/plugins.py:1-9, 113-116, 531, 1137-1182`, `agent/shell_hooks.py:1-5` | 3 |
| B20 | The gateway reads its configuration again for every request, so a changed tool list applies to the next message without a restart. A second profile switches profile serving on by itself, and from then on every profile reads only its own `.env` | **H:** `gateway/platforms/api_server.py:2216-2217`, `hermes_cli/config_defaults.py:2105-2123` | 4 |
| B21 | Besides memory and skills, the agent can leave lasting things behind: jobs in Hermes's own scheduler (`cronjob_manage` is one of its default tools), and files under `hooks/` and `plugins/`, which are code the gateway runs and which survive updates | **H:** `toolsets.py:11-36`, `gateway/hooks.py:1-9`; `runtime/UserState.kt:27` | 0, 3 |
| B22 | Hermes dies with the app or is swept at the next start: the app starts it as a child process and kills every guest process before a start. A turn that was running when the app's process died is lost today, even if Hermes itself was still alive | `runtime/RuntimeManager.kt:374, 400-406, 564-570`; **H:** `gateway/platforms/api_server_runs.py:164-168, 280-281` (a run's events and status are kept in memory, the last 1,000 events) | 3 |

### 3.3 Making things and finding things out

Read in Hermes's source on 2026-11-24. The short version: most of what a creative workspace needs is
already inside Hermes as skills and tools. What is missing is everything a person sees and touches.

| # | Finding | Evidence | Wave |
|---|---|---|---|
| D1 | Hermes ships a skill that creates, reads and edits **Word, PowerPoint and Excel** files: nineteen scripts that turn a plain JSON description into a real file (`docx_create.py`, `pptx_create.py`, `xlsx_create.py`), edit one in place, read one back, handle comments and tracked changes, and fill templates | **H:** `skills/productivity/microsoft-office/` | 6 |
| D2 | The libraries those scripts need are an optional extra (`documents`) that is **not** in the app's Linux image; Hermes's own Docker image bakes it in. A second extra (`documents-pdf`) adds PDF reading. The skill does not use Hermes's own Python: it looks for an interpreter that has the libraries and otherwise builds a side environment (`hermes pm environment documents`) by downloading it into a folder that an update deletes | **H:** `pyproject.toml:384-400`, `pm/cli.py:957-962`, `skills/productivity/microsoft-office/SKILL.md:44-75` | 6 |
| D3 | The description of a deck is small and regular: a slide size, and per slide a layout (title, title and content, section, two columns, title only, blank), a title, bullets with levels, tables, charts (bar, line, pie), pictures, shapes and speaker notes. A document is a list of blocks (headings, paragraphs with runs, lists, tables, pictures). The app can therefore **draw the same description natively**, without the office suite that Hermes's own preview script needs and the image does not have | **H:** `skills/productivity/microsoft-office/scripts/pptx_create.py`, `docx_create.py`, `pptx_render.py` | 6 |
| D4 | A skill for PDF files (create, merge, split, extract, forms) exists, built on libraries with permissive licences | **H:** `skills/productivity/pdf/` | 6, 7 |
| D5 | A skill for **citations** exists: a small script owns the list of sources, gives every address one number, keeps the quoted evidence, and marks what could not be verified. Where its list is kept can be given with every call (`--ledger`), or once through the environment (`HERMES_CITATION_LEDGER`, which is read only when Hermes starts) | **H:** `skills/research/grounded-citations/SKILL.md:54, 234`, `scripts/sources.py:28, 68` | 7 |
| D6 | Hermes's picture tool works through paid outside services. None of them talks to Google directly (the folders are deepinfra, fal, krea, meta-ai, openai, openai-codex, openrouter, xai; OpenRouter resells Google's picture models among others). The tool is one of the agent's default tools and wakes up as soon as a provider for it is configured. So it has to leave the agent's list, and pictures through the owner's own credentials are made by the app itself, behind a price | **H:** `tools/image_generation_tool.py:593-607`, `plugins/image_gen/`, `toolsets.py:15` | 0, 6 |
| D7 | After every tool call a plugin is handed the tool's name, its arguments and **its result**. That is how the app can know which pages were really fetched and what was sent out, without trusting the model's own account | **H:** `model_tools.py:713-720`, `hermes_cli/plugins.py:1137-1182` | 3, 7 |
| D8 | Hermes can take a snapshot of the working folder before the agent changes files (a hidden git store, twenty snapshots per folder, files over 10 MB skipped). It is off by default. A snapshot is taken once per turn, and only before the file tools and before commands that look destructive, so what a script changes is not covered. Restoring is offered as a typed command that an API client does not have, though the functions behind it can be called. The store is not on the list of what survives an update | **H:** `hermes_cli/config_defaults.py:483-503`, `agent/tool_executor.py:1049-1062`, `agent/checkpoints/manager.py:79, 1071-1242`, `gateway/slash_commands.py:245` | 3 |
| D9 | Sub-agents can run in parallel: up to ten children by default, one level deep, on a cheaper model if set, each with up to 250 steps. For a turn that comes through the API they are detached from the turn unless `delegation.gateway_mode` is `inline`; a child cannot ask for an approval (its asks are refused); and a plugin is told which child belongs to which parent | **H:** `hermes_cli/config_defaults.py:1323, 1330`, `tools/delegate_tool_config.py:30-55, 120-137`, `tools/delegate_tool.py:396-402` | 7 |
| D10 | Skills that become features with little more than a good screen: action items from a document or a meeting, a weekly review, a price watch, papers from arXiv, transcripts of videos, flashcards with spaced repetition, feeds and blog watching, a personal wiki, notebooks, diagrams, maps | **H:** `skills/productivity/`, `skills/research/`, `skills/media/`, `optional-skills/` | 6, 7, 9 |
| D11 | Hermes can sign in to outside services that speak MCP and keeps the result in its own folder (`mcp-tokens/`, which is not on the list of what survives an update). Its browser sign-in opens a browser inside the guest, which cannot reach Android's; a sign-in by code exists as a command (`hermes mcp login`) | **H:** `tools/mcp_oauth.py:224-251, 408-425`, `tools/mcp_oauth_device.py:1-5` | 4 |
| D12 | Every library the document and data work needs has a build for the guest's Python (3.14, arm64) on the public package index: the office set is about 13 MB to download, the data set (numpy, pandas, matplotlib) about 43 MB. Two tempting tools are left out for their licences (PyMuPDF, pandoc) | Package index, read 2026-11-24 | 4, 6, 7 |
| D13 | Diagrams and mathematics can be drawn offline inside a web view: Mermaid (5.4 MB as a file, MIT) and KaTeX (about 0.5 MB with its fonts, MIT). No pure-Python or native renderer for either exists | Package registry, read 2026-11-24 | 5 |
| D14 | A plugin is told about more than tools: before and after every request to the model (`pre_api_request`, `post_api_request`), around Hermes's own side calls (`pre_auxiliary_call`, `post_auxiliary_call`), and when a sub-agent starts and stops. So the app can count what a turn costs while it runs, not only at its end, and can stop it | **H:** `hermes_cli/plugins.py:114-141` | 3, 4 |
| D15 | Two of Hermes's defaults send text out without being asked: its speech tool (`text_to_speech`, whose default provider is a public cloud voice) and the titles it gives sessions (a call to the model, on by default) | **H:** `toolsets.py:11-36`, `hermes_cli/config_defaults.py:759-763, 1060-1063` | 0 |
| D16 | Reading a page without a key is done by outside services, not by the phone. Results may come from a cache of twenty minutes; a long page arrives as its head and tail with the middle left in a file; a page that redirects comes back under another address | **H:** `plugins/web/keyless_mcp.py:1-27`, `tools/web_result_cache.py:1-7`, `tools/web_tools_truncate.py:1-20`, `tools/web_tools_extract.py:5-7` | 3, 7 |
| D17 | If a plugin's check before a tool call ends in an error, Hermes lets the call through | **H:** `agent/agent_runtime_helpers.py:2034-2047` | 3 |

### 3.4 Android, services and speech

| # | Finding | Source | Wave |
|---|---|---|---|
| C1 | Targeting API 28 costs little: almost no feature in this plan is gated by it. The one hard limit: an update can never install silently; the user confirms each one | Android `PackageInstaller.SessionParams` reference | 10 |
| C2 | Shared-element transitions are stable in Compose since 1.10 and work with plain `AnimatedContent` (not inside dialogs and Material sheets) | Compose animation release notes | 0 |
| C3 | Back gesture with progress, Live Updates in Samsung's Now Bar, conversation bubbles and the digital-assistant role have **no documented rule for an API 28 app on Android 17**. Each needs a short experiment on the phone, and each has a fallback | Android developer documentation; section 8 | 0, 10 |
| C4 | Android 17 makes audio started from the background fail silently, and limits memory per app | Android 17 behaviour changes (all apps) | 0, 8 |
| C5 | Classic widgets can show pictures, lists and simple frame animation; Android 17 limits a widget's bitmaps | App widgets documentation | 10 |
| C6 | Events that can start a dead app: Bluetooth connected (manifest receiver), charging and network (job scheduler), notifications (listener). Not: charger plugged in as a plain broadcast | Implicit broadcast exceptions list | 9 |
| C7 | The embedded sherpa-onnx 2.0.3 already contains streaming and offline speech recognition, voice activity detection (Silero, TEN) and keyword spotting. No new library is needed for a voice mode | classes in `dist/sherpa-classes.jar` | 8 |
| C8 | Best small Turkish model found: `seda-v0.1` (streaming, 73 MB, Apache-2.0, about 15 % word errors, author's numbers). Whisper small is better (about 12 %) at 358 MB. The phone's own recogniser is probably better than both | Hugging Face model cards | 8 |
| C9 | Listening while speaking (echo cancellation) depends on the phone's firmware | Android audio pre-processing documentation | 8 |
| C10 | The embedded speech library can speak far more than the app offers: besides the installed voice model (31 languages) it loads Piper voices (a catalogue of dozens of languages, among them a Kurmanji voice of 24 MB under a non-commercial licence), Kokoro and Kitten voices. It also contains language identification, punctuation, speaker separation, noise removal and a recogniser for 1,600 languages (365 MB, Apache-2.0). None of this has been heard on the phone yet | classes in `dist/sherpa-classes.jar`; the `tts-models` release of sherpa-onnx, read 2026-11-24 | 8 |
| C11 | Drawing a PDF page works on every supported Android. Reading its **text** and filling its forms are platform calls only from Android 15; the owner's phone has them, older phones fall back to the guest's library | Android `PdfRenderer` and `PdfRenderer.Page` reference | 6, 7 |
| C12 | Capturing the screen: the strict rule of Android 14 (a new consent for every capture session) is written for apps that target API 34 or later. One consent per capture is valid under both readings, so that is the design | Android 14 behaviour changes | 8 |
| C13 | An entry in the text-selection menu can **return** text that replaces the selection; one entry needs one activity. How many entries One UI shows, and in which order, is not documented | Android `Intent.ACTION_PROCESS_TEXT` reference | 10 |
| C14 | A second screen (cable or wireless) can show its own content through the platform's `Presentation` class since Android 4.2. How DeX behaves is not documented | Android `Presentation` reference | 6 |
| C15 | The platform's own fingerprint and face dialog exists from API 28 without any library; unlocking with the phone's PIN in the same dialog needs API 30 | Android `BiometricPrompt.Builder` reference | 11 |
| C16 | A recording that continues with the screen off must be started while the app is visible | Android foreground-service types | 8 |
| C17 | Android 17 limits the memory of every app. Whether it counts Hermes and its children (250 to 300 MB today) together with the app is not documented | Android 17 behaviour changes; `adb shell am memory-limiter status` | 0 |
| C18 | Recognising speech on the device through the platform exists from Android 12; the default recogniser "is likely to stream audio to remote servers". Which engine and which languages this phone has must be looked up on the phone | Android `SpeechRecognizer` reference | 8 |
| C19 | Android 16 gives background jobs a quota. Work that must really finish (a night's research) belongs in a foreground service while charging, not in a job | Android 16 behaviour changes | 9 |
| C20 | Weather without a key or an account exists: the Norwegian Meteorological Institute's service is free for any use with credit, wants an identifying request header, and asks clients to cache. Open-Meteo is free for non-commercial use only | `api.met.no` documentation and licence; Open-Meteo terms | 1 |
| C21 | A table of the stars one can see is small and free: the HYG database (CC BY-SA 4.0) has about 1,600 stars brighter than magnitude 5. The licence asks for credit and for sharing the derived table under the same terms, which a public repository does; it is the one share-alike piece of data the app bundles, and section 5 names it as the exception | HYG repository; apparent-magnitude table | 1 |
| C22 | **Pictures cost money everywhere.** Google's picture models have no free allowance; one picture costs about 3 to 15 US cents depending on model and size. They are called like a normal message that answers with a picture, and every picture carries an invisible watermark | Google's model, pricing and image-generation pages, read 2026-11-24 by a research agent; to be read again before use | 6 |

### 3.5 The market

Read in November 2026 on the vendors' own pages (section 14). What it means for the plan:

| # | Finding | Wave |
|---|---|---|
| E1 | A workspace for made things is now ordinary on a phone: five of the leading assistants have one, and ChatGPT's pages and slides became editable on phones this very month. An assistant without one looks unfinished | 5, 6 |
| E2 | Research with sources is standard. None of the pages read describes a check that a cited page was really fetched: a citation is what the model says it used | 7 |
| E3 | Voice is the most praised feature (talks that can be interrupted, a live camera) and the most complained about (mishearing, cut-offs, lag, limits). All of it runs in the vendor's cloud | 8 |
| E4 | Briefs and background work are the direction everyone moves in: nightly cards, a spoken daily brief, jobs that keep running for days. Almost all of it sits behind the dearest subscription | 9 |
| E5 | Audio overviews made from one's own sources are loved, and exist in one product | 8 |
| E6 | The phone makers build AI into the system: writing tools in every text field, an interpreter that detects the language, a recorder that writes, briefs, a watch on a web page for a price or a restock. A sideloaded app can offer most of these through Android's ordinary doors | 8, 9, 10 |
| E7 | Locks and private chats arrived this year (locked chats, temporary chats with memory). The complaints are about the opposite: one browser assistant builds advertising profiles from what its user does | 3, 11 |
| E8 | The loudest complaints: limits and paywalls, answers lost in the background, voice errors, wrong answers, features missing by country, features that vanish, nothing offline | 2.2 |
| E9 | The open and local apps are simple chats with small on-device models, or a terminal. Hermes's own route to Android is a terminal emulator; its documentation says that background processes can be killed there and that its package was broken at the time. An independent Android app for Hermes exists on F-Droid, with local models and broad permissions. None of them is a designed product with a workspace, a voice mode, widgets or a gate | 13 |
| E10 | What the others have that this plan leaves out on purpose: video and music generation, purchases, companions, a social feed | 2.4, 12 |

## 4. The waves at a glance

| Wave | Name | The owner gets | Version | Agents |
|---|---|---|---|---|
| 0 | **Foundation** | The same app, visibly faster and smoother; state kept when going back; every text behind one function, so any language can be added; a private signing key and a test twin, so that no experiment touches the owner's data; a test robot and a practice model that costs nothing; all old "never tried" items tried | 0.2 | 10 |
| 1 | **Home** | A living home: the real sky of the hour, tonight's moon, the stars that are really overhead, the weather if you allow it, seasons, a scene per theme, a fire that reacts, Pêt with a life, the woodpile; a welcome for new users; ten languages | 0.3 | 11 |
| 2 | **Conversation** | A new chat: answers like pages, the work timeline, questions you answer with a tap, gestures, versions of an answer side by side, files and pictures from the agent shown inline, a workspace that survives updates, spaces for your projects, a faster chat list with archive and labels, postcards | 0.4 | 10 |
| 3 | **Engine** | Stop and steer, sub-agents visible, answers that are saved as they arrive and can be continued, undo for what the agent changed; one gate before every tool, a review of everything lasting, and the ledger: what left the phone, under every answer | 0.5 | 8 |
| 4 | **Thrift** | A switch for every ability, light bots, the weight of the prompt shown and cut, cost in money, long chats made compact, a skill shop and packs, connections to outside services | 0.55 | 8 |
| 5 | **Canvas** | A workspace for what the agent makes: documents, code, pages, small apps, diagrams, charts, tables; versions; editing by hand and by asking; export; a place on the home screen | 0.6 | 8 |
| 6 | **Create** | Real Word, PowerPoint, Excel and PDF files; slides you present from the phone; pictures through your own provider with the price shown first; pixel tools; forty recipes; a scanner | 0.65 | 9 |
| 7 | **Know** | Deep research in the background; sources you can check; the Library grown up; spaces with their own knowledge; real data analysis on the phone; flashcards and quizzes | 0.7 | 9 |
| 8 | **Voice** | Fireside, a hands-free talk; listening on the device; voices for many languages; an interpreter; a private recorder that writes; audio overviews; a look through the camera or at the screen | 0.75 | 9 |
| 9 | **Hands** | The agent uses the phone, with a permission per ability; tasks and reminders; routines that start on events and watch pages; night shift; the morning brief; a browser of its own | 0.8 | 8 |
| 10 | **Everywhere** | Widgets, a quick question over any app, writing tools in every app, live progress, a live wallpaper, a bedside clock, the workspace in the phone's file picker, updates from inside the app, encrypted backups, a move to a new phone | 0.85 | 10 |
| 11 | **Memory** | Memory you can see and tidy, search across everything, the Council (bots that debate and compare), a bot gallery, an avatar forge, a journal and keepsakes, private chats, an app lock, a privacy centre | 0.9 | 8 |
| 12 | **World** | Right-to-left, East Asian and Indic scripts; eight more languages and a workshop to make your own; scenes and days of your region; accessibility; tablets, DeX, keyboard and pen; battery, heat and storage care | 0.95 | 8 |
| 13 | **One** | 1.0: hardening, the guide inside the app, the launch edition, and the optional stars (reach your agent from outside, a wake word, an offline mind) | 1.0 | 6 + 1 |

**Order and dependencies.** Wave 0 comes first because it multiplies everything after it: a fast build
makes every animation better, a test robot makes every wave cheaper to prove, and one text function
makes every later screen translatable for free. "Needs" means it cannot be built without; "uses" means
one of its pieces waits, or falls back, until that wave exists.

| Wave | Needs | Uses, if already built |
|---|---|---|
| 1 Home | 0 | 4 (the woodpile in money; until then in tokens), 8 (the "Talk by the fire" suggestion) |
| 2 Conversation | 0 | 1 (the tab bar, the first Library and the row slots on Home; in the shorter order below the first two are built here) |
| 3 Engine | 2 (the timeline it feeds, the block parser) | |
| 4 Thrift | 3 (the run engine, the bridge, the gate's meter) | |
| 5 Canvas | 2 (the block parser, file cards, the workspace) | 3 (the gate for what a small app asks, undo, the ledger), 4 (the cheap helper) |
| 6 Create | 5, 3 (the gate, for everything with a price) and 4 (prices, the token for Google, packs) | |
| 7 Know | 3 (runs, sub-agents, the record of fetched pages), 4 (prices for the estimate, the cheaper model for readers, the data pack) and 5 (a report opens in the Canvas) | 6 (slides from a report) |
| 8 Voice | 1 (the scene and the fire) and 4 (the downloader, direct calls) | 3 (a real stop), 7 (reports to read aloud); its two extras that work on decks need 6 |
| 9 Hands | 3 (the bridge and the gate) and 4 (the switches) | 1 (the weather in the brief), 6 (recipes), 7 (research for the night), 8 (`speak` during a talk) |
| 10 Everywhere | 1 and 2 | 3, 4, 5 (pinned items), 6 (recipes), 7 (study cards), 8 (the talk as a door), 9 (tasks, the brief) |
| 11 Memory | 2 and 3 (the keeper) | 4 (prices in a comparison), and 5 to 9, each of which adds things to search and to keep |
| 12 World | 0 (the text function) | Covers whatever is built by then |
| 13 One | Everything built by then | |

```
0 Foundation ─┬─ 1 Home ─────────────────────────────────┬─ 8 Voice        (also needs 4)
              │                                          └─ 10 Everywhere  (also needs 2)
              └─ 2 Conversation ─┬─ 3 Engine ─┬─ 4 Thrift ─┬─ 6 Create     (also needs 5)
                                 │            │            ├─ 7 Know       (also needs 5)
                                 │            │            └─ 9 Hands
                                 │            └─ 11 Memory
                                 └─ 5 Canvas
12 World   needs 0 and covers whatever is built by then
13 One     needs everything built by then
```

**Two valid orders.** The order of the numbers is the recommended one: the app becomes beautiful
first and capable second, and every later feature is born into the finished look. If the owner wants
the workspace sooner, the valid shortcut is 0, 2, 3, 4, 5, 6, 7 and then 1 and the rest. The tab bar
and the first Library then move from Wave 1 into Wave 2; nothing else in Waves 2 to 7 needs the new
home, they only look plainer until it exists.

**Size and time, honestly.** Phase 3 was eight agents and about eight areas of work in one working
day. Measured against it the waves here are bigger: about 122 agent tasks, and several waves hold two
or three times what Phase 3 did. Three things keep that from becoming a plan that never ships:
- **Halves.** Waves 1, 6, 7, 8 and 10 are built and shipped in two halves, named in each. The first
  half is a build of its own, proven on the phone like any other and marked with an "a" (0.3a); the
  second completes the version. That makes nineteen builds.
- **Core and extras.** Every wave names its extras: features that are wanted, but that the wave ships
  without if it runs long (section 6). An extra that was dropped goes on a list that Wave 13 or a
  later version picks up.
- **A wave is never stretched** (section 6).

The pace is set by how often the phone and its owner are at hand for the proving. At one build a
week, nineteen builds end in late April 2027. Proposed date for 1.0: **21 June 2027**, the June
solstice, which leaves about eight weeks in hand. If every extra is wanted inside 1.0, a date in late
summer is the honest one.

### 4.1 Where things live at 1.0

A plan with this many features needs a map, or every wave builds a door of its own. This is the map.
One agent owns it in every wave (the owner of `ui/Nav.kt`), and a wave may add a shelf or a row,
never a new top-level place. The design language draws it (section 8.15).

| Tab | What it holds |
|---|---|
| **Home** | The scene, the prompt, and rows that appear only when they have something to show: Spaces, Due today, Pinned, the night's report |
| **Chats** | Every conversation, with search, labels, archive and a filter by space |
| **Library** | Every *thing*: what the agent made (Made), files you gave (Files), pictures (Pictures), what you saved (Saved, with its collections), the reading list, recordings, study cards. One place with shelves: the gallery, the toolbox of small apps and the inbox are shelves of it, not places of their own |
| **Bots** | Your bots, the gallery of characters, the Council's ways of talking |
| **Agent** | The agent itself: its mind (soul, memory, "About you"), **Abilities** (one screen for Hermes's own toolsets and, from Wave 9, the phone's powers), skills and packs, connections, **Safety and privacy** (the rules, the approval mode, the log, the waiting room; from Wave 11 also what is stored and what left), the terminal |

- **Settings** leaves the tab bar for a gear on Home and in the Agent tab: appearance, languages,
  voice, models and usage, backups, about.
- **A space is the only way to group.** It is a filter over Chats and the Library and a row on Home,
  never a sixth tab.
- **A switch has one home.** Which abilities exist is decided under Abilities and nowhere else; how
  strict the gate is, under Safety and privacy and nowhere else. Other screens link there.
- The tab bar changes once, in Wave 1. The Library starts with what exists today (saved messages and
  the files given to the agent) and gains a shelf with almost every wave.

## 5. Principles and guardrails

What stays exactly as it is:

- **$0.** No paid services. Tests run on the practice model first. The few real-model checks use the
  model and the allowance the owner has chosen for testing (today the cheapest model at low effort),
  inside the test twin, under the app's own daily budget set to "block". Every feature in this plan
  states what it costs in tokens, and none adds tokens silently. Features that cost money by their
  nature (pictures) are built and proven against a stand-in that costs nothing; a real picture is
  made only when the person holding the phone has seen its price and tapped it. One limit is stated
  plainly: the agent's Linux must hold the provider's key in order to reach the model, so the key can
  never be the defence. The defences are the gate, the removal of every tool with a price from the
  agent's list, the budgets, and the ledger.
- **One APK, sideloaded, `applicationId` unchanged, target API 28.** Speech models, script fonts and
  heavy libraries stay downloads on request. The APK may grow by at most 15 MB over the whole plan,
  and each kind of thing has its share: 8 for the document libraries in the Linux image, 2.5 for the
  renderers of diagrams and mathematics, 2 for languages, 1.5 for art and the guide, 1 for sound and
  the small speech helpers. CI measures each share.
- **Hermes stays unmodified.** The app only writes configuration, skills, hooks, MCP entries and one
  plugin (the gate of Wave 3), and talks to documented endpoints. Small helper scripts the app needs
  inside the guest (the practice model, the prompt-weight helper, the `agiri` command) are written by
  the app at each start, exactly like `.env` and `config.yaml` today, so they need no new Linux image.
  The image itself changes twice: in Wave 6 it gains the document libraries, built into the side
  environment that Hermes's own office skill looks for (finding D2), and, only if the owner wants it,
  the Telegram library in Wave 13.
- **No secrets anywhere** but the app's private files; everything that might hold one goes through the
  redactor. The repository stays free of names, ids and serials.
- **No new libraries in the app.** Everything planned uses platform APIs, Compose, and the sherpa-onnx
  that is already there. The single possible exception is `androidx.profileinstaller` in Wave 0, if it
  is not already on the classpath through Compose. The two renderers bundled for the Canvas are plain
  script files that run inside a locked web view; they and every bundled font or data table are
  pinned, permissively licensed and listed in `NOTICE`. Nothing with a copyleft or non-commercial
  licence is bundled; such things (a voice, a font) can only be downloads the user asks for. The one
  exception is the small table of stars, whose licence asks that the derived table be shared, which
  the public repository does.

New rules this plan adds:

1. **Measure, then claim.** Smoothness, start time and prompt size are numbers with budgets
   (`docs/design-language.md` section 11, and Wave 4). A wave reports them before and after.
2. **Experiment before contract.** Anything that depends on undocumented behaviour of Android 17,
   One UI 9 or Hermes is tried on the phone for at most an hour before a single line of contract is
   written. Every experiment in section 8 has a fallback, so no wave can be blocked by one.
3. **Every wave ships.** Compiled, reviewed once, proven on the phone, committed, documented.
4. **One door and one gate.** The agent reaches the phone only through the bridge, where every ability
   has its own switch (Off, Ask, Allow) and every use is logged. And every tool call, Hermes's own
   tools included, passes one gate in the app with three rules: after outside content, effects ask;
   after private data, the ways out ask; lasting changes ask. Nothing that can speak in the owner's
   name or spend without asking (calls, SMS, purchases, messages) is offered at all, so the worst case
   stays small. The same policy covers what does not look like a tool: a block in an answer that
   would create or change something lasting (a bot, an app, a theme), and everything that comes in
   from a file, which stays outside content for good.
5. **Thrift is a feature.** Helper tasks (titles, summaries, translations) never ride on the
   11,000-token prompt.
6. **A gesture is never the only way.** Whatever a swipe or a hold does, a labelled control does too.
7. **The owner is the art director.** A few taste gates (design language, section 12) are shown as
   pictures side by side; everything else the agents decide.
8. **Match, then add.** A wave is not finished when it equals what the best apps do in its area; it
   also carries at least one thing from section 2.3.
9. **What the agent makes is a file you own.** It lies in the workspace in an open format, has a
   history, can be exported, and survives updates and a move to another phone.
10. **Money is asked for, never spent.** Whatever costs more than an ordinary message says its price
    first, and the default answer is "ask every time".
11. **What leaves the phone is listed.** A new feature that sends anything anywhere adds its line to
    the ledger, or it is not built.
12. **English first, and a character without a flag.** Every text is written in English first and
    every new name is an English word; other languages follow from it in the same wave. The hearth,
    the mountain and Pêt are the app's character as shipped. Days and scenes of a culture or a region
    are a choice the user makes, never a default, and no feature depends on one.
13. **For everyone, in every wave.** A screen is not finished until it has labels a screen reader can
    speak, survives text at double size, and has been photographed mirrored. The deep pass is Wave 12;
    the debt is never allowed to pile up until then.

## 6. How a wave is run

The method of Phase 3, tightened where it cost time.

1. **Experiments** (lead, on the phone, at most an hour each). The result goes into `AGENTS.md` under
   "Hard-won facts". A failed experiment switches that feature to its fallback.
2. **Contract** (lead): `docs/waves/w<N>-contract.md` with the ground rules of Phase 3, the ownership
   table, exact signatures of everything shared, one string file and prefix per agent, and the tests.
   For every new screen it names the kind of screen and its five states (design language, section
   8.16), and it marks each feature as core or extra. It lists every entry the wave adds to the
   manifest and names the one agent who writes them.
3. **Build** (mid-size model, in parallel): each agent edits only its own files, writes its texts in
   English only, does not run Gradle, and reports deviations and `REQUESTS`.
4. **Integrate** (one agent): compile with `tools\local-build.ps1`, close the seams, run the unit tests.
5. **Translate** (mid-size model, from Wave 1): the wave's new English texts go into every shipped
   language. Each text travels with a note its author wrote (where it appears, what its placeholders
   mean, how much room it has), with the language's glossary, and with everything translated before,
   so words stay the same from wave to wave. A second agent reads each language for length,
   placeholders and tone. The check that no text is missing or overflowing is automatic (Wave 0
   builds it). What is translated is more than labels: the recipes of Wave 6 and the guide of
   Wave 13 are texts of their waves and are counted as such.
6. **Review** (strong model, one round): one reviewer per area and one for the seams. Findings are fixed
   once. What does not block goes to the follow-up list in `AGENTS.md`. No second review of fixes.
7. **Look:** screenshots of every changed screen in the dark and the light default theme, plus one in
   the stretched test language, one at double text size and one mirrored, judged against the design
   language by a reviewer who looks at the pictures. The robot also proves that every control on a
   new screen has a label a screen reader can speak. A taste gate, if the wave has one.
8. **Prove:** the phone robot's scenarios on the practice model (zero tokens), then the wave's few
   real-model checks with `google/gemini-4-hydrogen` at low effort, then the smoothness numbers.
9. **Ship:** commit, push, prerelease. Update `docs/APP_OVERVIEW.md`, `AGENTS.md`, `README.md`, and
   (from Wave 1, which builds it) the in-app "What's new" page.

**Timebox.** If a wave's build does not compile within one working session, features are moved to the
next wave. A wave is never stretched.

**Core and extras.** The contract of a wave marks each feature as core or extra. Core is what the
wave is for, and it ships. Extras are built last, by the agents that finished early, and are the
first thing dropped when the timebox is reached. Each wave below names its extras.

**The first wave, concretely.** Session one: the numbers of today's build (feature 0.2), then
experiments S1 to S7, each written into `AGENTS.md` as it ends. Session two: the contract of Wave 0,
then its ten agents in parallel, then the integration. Session three: the review, the fixes, the
robot's first twenty scenarios, the old debt (feature 0.4), the numbers again, and the build goes to
the owner as 0.2.

**What the owner does:** unlock the phone when asked; answer the taste gates; grant a permission when a
feature needs it for the first time; once, in the phone's developer options, turn on "stay awake while
charging" so tests do not stop at the lock screen (optional, the owner's choice); once, in Wave 0,
import a test credential into the test twin, because the twin never sees the owner's own.

## 7. The waves in detail

Each wave lists: the goal, the features with enough detail to write a contract from, the agents and
what each owns, the experiments it waits for (section 8), and how it is proven. Signatures shown here
are sketches for the contract writer, not the contract. A feature is numbered by its wave: 5.3 is the
third feature of Wave 5.

### 7.0 Wave 0, Foundation: fast, consistent, testable, ready for the world

**Goal.** No new feature. The same app becomes fast, keeps its state, speaks one visual language, can
be tested by a script at zero tokens, and can be put into any language without touching a screen
again. Everything later stands on this.

**0.1 The owner's build and the test twin** (findings A1, A15; experiments S2, S4, S6)
- A `release` build type for the owner: code shrinking and optimisation on (R8), resource shrinking on,
  not debuggable, **not obfuscated** (`-dontobfuscate`, so the crash traces in `app.log` stay readable).
- **A test twin.** The debug build gets an identity of its own (`dev.pocketagent.app.debug`, shown as
  "Agirî Test" with a marked icon) and installs next to the owner's app (about 1.4 GB more on the
  phone). Agents, the robot and every experiment work on the twin: it can be wiped, filled with
  practice chats and read with `run-as` without ever touching the owner's chats, memory or credentials.
  The owner's app changes only when a wave ships. Everything that spells the package name (link
  actions, `shortcuts.xml`, the paths in the tools) takes it from the build.
- **A private signing key.** Today's key lies in the public repository: fine for a test build, wrong
  for the app that holds the owner's data. The owner's build moves to a new key that exists only as a
  CI secret and as a file on the owner's PC outside the repository. A proof of rotation lets it install
  over today's build with all data (S6). The committed key keeps signing the twin, so the fast local
  loop stays as it is.
- **Version numbers that only go up.** The owner's build takes its version code from CI (10,000 plus
  the run number), above every build installed so far. The twin counts on its own.
- `app/proguard-rules.pro` keeps what is found by name at run time: `com.k2fsa.sherpa.onnx.**` (its
  native code looks up methods and fields by name; see the JNI trap in `AGENTS.md`), the pieces of
  commons-compress and xz that are loaded by reflection, and enum names that are stored in preferences.
- Compose's baseline profile is packaged (check that `androidx.profileinstaller` is on the classpath).
  `tools\local-build.ps1` builds and installs the twin by default. With `-Release`, and the key present
  on the PC, it builds the owner's variant and after installing runs
  `adb shell cmd package compile -m speed -f dev.pocketagent.app`, so the phone runs compiled code at once.
- CI publishes two files per build: `Agiri-<n>.apk` (the owner's build) and `Agiri-<n>-test.apk` (the
  twin). The checksum file lists both. CI also records the size of the app
  without the Linux image and fails a build that outgrows the allowance of section 5.
- A non-debuggable build cannot be read with `run-as`, so Diagnostics gets **Export logs** (one zip of
  `app.log`, `runtime.log`, `last-launch.txt`, all redacted, through the share sheet).

**0.2 Smoothness as numbers** (`tools/phone-perf.ps1`)
- Drives the scenarios of `docs/design-language.md` section 11 with `adb shell input`, reads
  `dumpsys gfxinfo dev.pocketagent.app framestats`, and prints per scenario: frames, the share within
  8.3 ms, the 90th, 95th and 99th percentile, the worst frame. Saves JSON under `dist/perf/<build>/`.
- First run: the numbers of today's debug build and of the first release build, side by side, in
  `docs/perf/w0.md`. That file is the baseline every later wave is compared with.

**0.3 The phone robot** (`tools/phone-test.ps1`, scenarios in `tools/scenarios/*.json`)
- A small vocabulary: `link` (start an intent), `tap` (by test tag, text or description, found in a
  `uiautomator dump`), `type`, `wait`, `expect`, `expectNot`, `shot`, `kill`, `forceStop`, `notification`,
  `noCrash` (nothing fatal in logcat and `app.log`). Output: a folder with screenshots and a report.
- The app sets `testTagsAsResourceId` at its root and gives every important control a stable `testTag`,
  so scenarios do not depend on the interface language.
- Debug builds accept two extra links: open any route, and pin the clock to a given date and time (the
  sky and the calendar of Wave 1 are tested that way).
- The robot works on the test twin (it reads `app.log` with `run-as`). A short smoke list that needs
  no private files also runs against the owner's build after each release.
- Two checks run on every screen the robot visits, from the first wave on: every control has a label
  a screen reader can speak (read from the same dump the robot taps by), and nothing is cut at double
  text size.

**0.4 The practice model** (zero tokens; experiment S5)
- The mock model of the CI smoke test (`rootfs/smoke/mock_llm.py`, standard library only) grows a few
  scripted behaviours, chosen by a keyword in the user's message: `RUN_TOOL`, `ASK_APPROVAL` (a command
  that Hermes flags), `LONG` (3,000 characters at a steady rate), `THINK` (reasoning, then an answer),
  `SLOW`, `FAIL`, `MEMORY`, `FORGE`, `MEDIA` (answers with a file tag), each with fixed usage numbers.
- Later waves add their own keywords (`ASK`, `CANVAS`, `DECK`, `SOURCES`, `PICTURE` and so on), so that
  every feature of this plan can be shown and tested without a real model.
- It ships as an app asset. A switch under Settings > Runtime, "Practice model (no tokens)", starts it
  inside the guest and points Hermes at it the way the custom-endpoint mode does, but from a
  configuration of its own: the owner's setup, the custom-endpoint slot included, is untouched and
  comes back when the switch is turned off. Everything the app later sends by itself (the helpers and
  direct calls of Wave 4) follows the same switch, so a test can never spend.
- **Then the old debt is paid.** Every item that `docs/APP_OVERVIEW.md` lists as "not yet tried on the
  phone", and that a script can do, becomes a robot scenario on the practice model: the approval card
  and its notification, the inline reply from a notification, budget warn and block, idle stop, the
  automatic restart after Hermes is killed, bot export and import, creating and deleting a skill,
  terminal colours, editing `SOUL.md`, `/loop`, an update that replaces the Linux image over an
  installation with user data, and a restore into another installation (a backup of the twin restored
  into a wiped twin). Each ends as "works" or as a finding that is fixed in this wave.
- Three need a person or a key and stay on a short list for the owner: the camera shutter, spoken
  dictation, and a new provider with a real key.

**0.5 Tokens, motion, touch** (design language, sections 2 to 4)
- New `ui/theme/Tokens.kt`, `Motion.kt`, `Haptics.kt`. The effective animation level also respects the
  system's animation setting and battery saver.
- A ratchet lint (`tools/lint-ui.ps1`, run in CI): counts raw `tween(`, `Toast.makeText`,
  `RoundedCornerShape(` and literal durations under `ui/`. The count is stored; a build that raises it
  fails. It only ever goes down.

**0.6 Components v2** (design language, section 9)
- `PocketSheet`, the one `ConfirmDialog`, `PetNote`, `SwipeRow`, `Skeleton`, `EmberLine`, panel variants,
  the press pattern, the dither scrim. Then a sweep by a small model replaces the twelve toasts, three
  dialogs and five sheets, and moves the ten files with the most literal values onto the tokens.
- Pixel font v2 and the `PaletteContrastTest`.

**0.7 Navigation v2** (findings A6, A7; experiments S1, S3)
- The navigator's entries get ids; a `SaveableStateHolder` keeps each screen's state while it is on the
  stack. Lists come back where they were.
- Transitions follow patterns 7 to 9. One `SharedTransitionLayout` around the app carries the shared
  elements. The back gesture drives the outgoing screen (if S1 passes; otherwise the same animation
  plays after the gesture ends).

**0.8 The stage** (finding A8)
- `PixelStage` and `SceneRenderer` as in the design language, section 6.1. In this wave the picture
  stays exactly as it is today: a test renders a fixed state at a fixed time before and after and
  compares the pixels.
- The frame clock is tied to the display and capped at 30 frames per second. Sprites are parsed off the
  main thread at start. The art builder moves from `dist/` to `tools/art/` with a README and a command
  that prints a contact sheet of every sprite (finding A14).

**0.9 Streaming without stutter** (findings A4, A5)
- Engine: a trailing update 60 ms after the last piece of text.
- `StreamSmoother` and incremental Markdown as specified in the design language, section 8.2. A
  property test proves that parsing a document in random pieces gives the same result as parsing it whole.
- `ChatScreen.kt` is split into a state holder and four composables (top area, message list, input,
  dialogs). Only the list observes the messages. Tool output opens in a sheet instead of scrolling
  inside the list. The streaming row is not animated as a list item.

**0.10 Safety stop-gaps** (findings B2, B6, B13, B15, B21, C4)
- In the generated configuration: `platform_toolsets.api_server` without the browser set (until Wave 9
  gives the agent a real browser) and without Hermes's own scheduler tool (`cronjob_manage`: routines
  are the app's way to schedule, a Hermes job cannot run while the gateway sleeps, and it is one way
  for a misled agent to leave something behind; Wave 9 gives the agent `routine_create`, which asks).
  Side-task models are pinned to the main model, and `security.allow_lazy_installs` is `false`. The
  golden fixtures change in the same commit and the CI smoke test proves Hermes still starts.
- Two more tools leave the agent's list, because each can send something out, or spend, without being
  asked (findings D6, D15): Hermes's own picture tool (`image_generate`) and its speech tool
  (`text_to_speech`; the app speaks with its own voice, on the phone). Hermes's habit of asking the
  model for a session title is switched off (`auxiliary.title_generation.model_upgrade_enabled`).
- Settings says in plain words where web lookups go (free public search services, named) and has the
  switch for them (`web.keyless_fallback`). The default stays on, as today.
- At every start the app lists what lies in the folders Hermes runs code from (`hooks/`, `plugins/`).
  Anything it did not write itself is reported in Diagnostics; Wave 3 turns the report into a quarantine.
- Diagnostics shows why the app's processes last ended (Android's exit reasons). After a crash the next
  launch offers "Copy report" instead of silence. A request part longer than 60,000 characters is split
  so Hermes cannot cut it silently.

**0.11 One function for every text** (new; the ground for the languages of Waves 1 and 12)
- `ui/text/Texts.kt`: `t(id, vararg args)` and `tn(id, count, vararg args)` for composables, and
  `Texts.get(context, id, …)` for services and notifications. Behind them one `TextSource` answers
  from, in this order: a language pack loaded from a file (Wave 12 lets a user make one), the app's
  resources in the chosen language, English.
- **English is the source.** A new text is only ever written in `values/strings*.xml`; every other
  language is derived from it. The engine's texts that exist only in English today (notes, errors,
  the terminal's info lines) move into resources.
- A sweep replaces every direct `stringResource(` and `getString(` with the function. The ratchet lint
  of 0.5 counts the direct calls; the count reaches zero in this wave and stays there.
- **Text hygiene:** no sentence is glued together from pieces; every count goes through a plural;
  dates, numbers, sizes and money go through the platform's formatters with the chosen language; no
  text is baked into a picture. The case of a text is never changed in code (capitals are written by
  the author, because turning letters into capitals goes wrong in several languages), and every text
  carries a short note for its translators.
- **A stretched test language** (debug builds only): a script makes it from English, with marked
  letters and 40 % more length. In it, a text that never went through the function stays plain
  English and a label that is too tight gets cut, so both show up in a screenshot. The robot takes one
  screenshot of every screen in it.
- **Ready for mirrored layouts:** `start` and `end` instead of `left` and `right` everywhere (lint),
  `supportsRtl` on, a table that says which icons point and must turn. A debug link forces a
  right-to-left layout so that the robot can photograph every screen mirrored. The pixel scene itself
  is never mirrored. A flaw in a screen is fixed in the wave that builds or changes that screen; only
  what needs new machinery (letters that join, a second kind of type) waits for Wave 12.
- `tools/strings-check.ps1` runs in CI: every key exists in every shipped language, placeholders
  match, no orphan keys, and no translation is longer than the room its kind of control has (a tab
  label, a button, a title: the limits are in the design language, section 13).
- Three kinds of text can only come from built-in languages, because Android reads them without the
  app running: the launcher label, shortcut labels and the entries in other apps' menus.
- **Protected texts.** The words a decision hangs on (Allow, Deny, the reason on an approval card, a
  price, Create, Cancel, the line shown while recording, the warnings of a backup) can never be
  replaced by a pack that came from a file. They come only from the languages built into the app,
  and a pack's entries for them are ignored.

**0.12 Room to grow, measured** (finding C17; experiment S7)
- What the app, Hermes and a web view weigh together in the heaviest moment this plan will ever
  create (a run with three sub-agents, a web view open, a speech model loaded), put together from
  today's parts and read with `dumpsys meminfo` and `adb shell am memory-limiter status`.
- The numbers go into `docs/perf/w0.md`. They set two caps that later waves use: how many sub-agents
  may run at once (Wave 7), and whether a speech model must be unloaded before a web view opens
  (Waves 5 and 8). From this wave on, every wave's proof lists its peak.

**Extras in this wave:** none. Everything here is needed by what follows.

| Agent | Owns |
|---|---|
| BUILD | `app/build.gradle.kts` (the two variants, version codes, signing), `app/proguard-rules.pro`, `.github/workflows/build.yml`, `tools/local-build.ps1`, the twin's own resources (`app/src/debug/`) |
| ROBOT | `tools/phone-test.ps1`, `tools/phone-perf.ps1`, `tools/scenarios/*`, `tools/lint-ui.ps1`, the practice model asset and its starter (`runtime/PracticeModel.kt`), `ui/settings/RuntimeSection.kt`, the memory measurement of 0.12 |
| DESIGN | `ui/theme/*`, `ui/components/*`, their tests |
| NAV | `ui/AppRoot.kt`, `ui/Nav.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `AppLinks.kt` (with the debug links for a forced language and a mirrored layout) |
| STAGE | `ui/pixel/*`, `tools/art/*`, the renderer test |
| STREAM | `data/ChatEngine.kt` (the update timing only), `ui/chat/ChatScreen.kt` and its new parts, `ui/chat/ChatMessages.kt`, `ui/chat/Markdown*.kt`, `ui/chat/StreamSmoother.kt` |
| CONFIG | `runtime/HermesConfig.kt`, `runtime/ProviderConfig.kt`, `runtime/RuntimeManager.kt` (the practice model's launch, the check of `hooks/` and `plugins/`), `rootfs/smoke/fixtures/*`, `diag/*`, `data/Transcript.kt`, `PocketApp.kt`, the privacy row in `ui/settings/BehaviourSection.kt` |
| TEXT | `ui/text/*` (the function, the source, packs, the stretched test language, the list of protected texts), `tools/strings-check.ps1`, `tools/make-test-language.ps1`, `res/values/strings_engine.xml` (the engine's texts as resources; WORDS puts them to use), the tests |
| SWEEP (small model, runs after DESIGN and STREAM) | Mechanical replacements (toasts, dialogs, sheets, literal values) in `ui/home`, `ui/bots`, `ui/hermes`, `ui/routines`, `ui/settings`, and the files of `ui/chat` that STREAM does not own |
| WORDS (small model, runs last, after TEXT and SWEEP) | In every file under `ui/`, `service/`, `routines/`, `data/` and `hermes/`: direct text calls and the engine's literal texts become `t(`, `left` and `right` become `start` and `end`. Nothing else |

**Proven when:** `docs/perf/w0.md` shows before and after, and the budgets for scrolling and streaming
are met on the optimised build (or the remaining gap is listed with its cause); the robot runs at least
twenty scenarios green on the practice model; every debt item is closed or filed; speech, Hermes,
backup and restore work in the optimised build; the owner's app has moved to the private key with
nothing lost, and the twin runs beside it; the whole app runs in the stretched test language with no
plain English left and no label cut; a mirrored screenshot of every screen exists, and every control
the robot met has a spoken label; the agent's list of tools no longer holds the browser set, the
scheduler, the picture tool or the speech tool, and a turn on the practice model makes no call for a
title; the memory numbers of 0.12 are recorded; apart from motion nothing looks different.

### 7.1 Wave 1, Home: the living hearth

**Goal.** The first screen becomes the reason people show the app to someone: a small world under
the real sky. A new user is led in by the hand, and is greeted in their own language.

**1.1 Home 2.0** (design language, section 8.1; finding A13)
- The collapsing hero, the prompt card overlapping the scene, the avatar row, recent chats with swipe
  actions, illustrated tiles, the entrance that plays once per process.
- The fire is the switch (tap to light, hold to put out), with the same action as a labelled menu entry.
- Suggestions under the prompt come from a pure function of the moment (`HomeSuggestions.pick`): continue
  the last chat, an unread routine result, a routine idea when there is none, "What can you do?" for a
  new user, and from Wave 8 "Talk by the fire".
- The woodpile shows what is left of today's budget (hidden when no budget is set): in tokens at
  first, in money once Wave 4 knows prices.

**1.2 The real sky** (design language, section 6.2; findings A9, C21)
- `SkyClock`: a pure function from date, time and place to the sun's height and direction, the moment
  of day, the blend between palettes, the moon's phase and place, and the turn of the star field.
  Unit-tested against known sunrise times, known full and new moons and the known places of three
  bright stars. It takes its clock as a parameter, so the robot can pin any date.
- **A place without a permission.** By default the place is guessed from the phone's time zone (a
  small table of zones with a typical latitude and longitude). That makes a sky that is right to a
  few hundred kilometres, and nothing is asked. The user can pick a city from an offline list, or
  allow the phone's approximate location.
- **Real stars.** About 900 stars down to magnitude 4.5 from the HYG catalogue, each at its true place
  for the hour and the place, its colour reduced to three pixel tints. The credit and the derived
  table lie in the repository, as the catalogue's licence asks. The four bright planets come from
  low-precision formulas (right to about one degree, which is less than a pixel of this sky). A tap on
  a bright star or a planet shows its name.
- Palettes per moment are computed from each theme's colours by a small tint table, not drawn by hand
  forty-two times.
- **Seasons** by date and hemisphere: the snow line on the mountain, the meadow's flowers, the colours
  of autumn.
- `SceneCalendar`: the sky's own days come first and are on by default: solstices and equinoxes, the
  large meteor showers, full moons, the eclipses of the coming years from a small table. Then the
  app's anniversary and days the user adds (a birthday). Days of a culture or a region are lists the
  user chooses from; none of them is on by default. This wave ships a first handful from different
  parts of the world, among them the one the app has known since its first version; Wave 12 adds
  the rest.
- A scene variant per theme, a dithered horizon into the page colour, and status-bar icons that follow
  the sky's brightness.

**1.3 The weather, if you allow it** (finding C20)
- Off until the user turns it on, in the Welcome or under Appearance. Then the scene shows what is
  outside: rain, snow, fog, wind in the sparks and the grass, clouds that hide the stars, lightning
  far away. A tap on the sky shows one line ("11°, light rain") with the credit the service asks for.
- The source is the Norwegian Meteorological Institute's open service: no key, no account. The
  request carries the place rounded to one decimal (about ten kilometres) and nothing else that is
  personal. Answers are cached as the service asks. It is asked at most once an hour, and only while
  the app or its widget is in use.
- The service wants every request to name the app and a contact address for its operators. Which
  address that is, is the owner's decision (section 13); until it is made the sky simply stays clear.
- A debug link pins the weather, like the clock, so the robot can photograph snow in July.

**1.4 The fire** (design language, section 6.3)
- `FireFeed`: one process-wide value, "how hard is the agent working", fed by the chat engine (a turn
  starts, text arrives, a tool runs) and decaying when idle. The home fire, the thin fire line in the
  chat's top bar and, later, the widget all read it.
- The living edge around the hand-keyed flame, sparks with wind, wind from tilting the phone (the
  sensor is on only while Home is on screen at the full animation level), heat haze on Android 13 and later.

**1.5 Pêt** (design language, section 7)
- `PetBrain` (a pure state machine with a seeded random source, unit-tested), fifteen new animations (the weather's two among them),
  the shadow, reactions to engine events, the small Pêt for top bars, Pêt notes, eight empty-state pictures.
- Pêt knows the weather and the hour: shelter under a leaf when it rains, sleep when the night is deep.

**1.6 The shell**
- Bottom bar v2 (the hopping ember, tab icons that play once when chosen), the fire line in top bars,
  and the start screen: the system's splash shows the flame, which then travels to its place in the
  scene (experiment S8; without it, a plain fade).
- "What's new": after an update a Pêt note opens one short page per version
  (`assets/whatsnew/<version>.md`, in every interface language, drawn by the app's own Markdown view).
- **The tab bar of section 4.1:** Home, Chats, Library, Bots, Agent. The Library opens with what
  exists today (saved messages and the files given to the agent); Settings moves behind a gear.

**1.7 "Welcome", the first-run wizard** (design language, section 8.4)
- Five steps on one scene. The trick that makes it fast: unpacking the Linux starts in the background
  **at the first step**, so the minute it takes is spent choosing a language and typing a key, and the
  agent is ready when the user is.
- The model step leads with the free path: a three-picture guide to a Google AI Studio key and a button
  that opens the key page in the browser. "I use something else" opens the full provider form.
- It never shows again after it was finished or skipped, and never to an existing installation.
- **Try before a key.** The last step offers a short guided chat on the practice model of Wave 0: it
  costs nothing, needs no account, and shows a question, a tool step and an answer before the user has
  typed a key anywhere.
- **What a free key costs.** The step that offers a free key says in one sentence, with a link to
  the provider's own terms as they read when the wave is built, what the provider may do with what
  is sent on a free key, and that such a key is not offered in every country. The other providers
  stand one tap away.

**1.8 Ten languages**
- English (the source), Spanish, French, German, Portuguese, Italian, Turkish, Russian, Indonesian and
  Kurmanji. All of them are written with Latin or Cyrillic letters, which the app's own pixel font
  learns in this wave (about 130 new glyphs: the Cyrillic alphabet in both cases and the accented
  Latin letters still missing). Scripts that need more than new glyphs wait for Wave 12.
- The picker shows each language under its own name, with "System" first. One setting becomes three:
  the language of the interface, the language of answers ("as I write" by default, or fixed), and the
  language of dictation.
- What protects quality: a glossary per language (`docs/i18n/<code>.md`) fixes how the app's own
  words are said (Canvas, Space, Fireside, ledger, woodpile) and how the user is addressed; a
  mid-size model translates with it, with each text's note and with everything translated before; a
  second agent reads every language for length, placeholders and tone; `strings-check` proves that
  nothing is missing.
- A language that nobody who speaks it has read is marked "machine translated" in the picker. The
  owner reads one page for Turkish with the sixty most visible texts (`docs/i18n/tr-review.md`); the
  corrections become the glossary. The same kind of page exists for Kurmanji, should the owner wish
  to read it; unread, it stays marked like the others.
- Texts of later waves are translated in the wave that writes them (section 6, step 5), so no
  language ever falls behind.
- A language that Android itself cannot format (its plurals, its dates) still works: the app's text
  function carries the plural rules, and dates fall back to a neutral form. Each of the ten is
  checked for this on the phone.

**1.9 Settings and sound**
- Appearance: the sky (real, always night, always day), the place, the weather, the days, black
  night, wallpaper accent, haptics level, Pêt in chats.
- Sounds `WAKE`, `SLEEP`, rain, and the optional crackle loop, from CC0 sources recorded in `NOTICE`.

**Taste gates in this wave:** the fire, and the default sky.

**Two halves:** the scene, the fire, Pêt and Home first (0.3a); the tab bar with the first Library,
the Welcome and the languages second.

**Extras in this wave:** the planets and the names of stars, heat haze, wind from tilting the phone,
the crackle loop, the table of eclipses.

| Agent | Owns |
|---|---|
| HOME | `ui/home/*`, with named slots for the rows later waves add (each such row is then a file of that wave's own under `ui/home/rows/`) |
| SCENE | `ui/pixel/stage/Sky*.kt`, `Stars.kt`, `Planets.kt`, `SceneCalendar.kt`, the renderer's layers, the star table (`assets/sky/stars.bin`) and the script that derives it (`tools/art/stars.py`), their tests |
| WEATHER | `data/Weather.kt`, `data/Places.kt` (zones and cities), `ui/pixel/stage/Weather*.kt`, the debug pin, tests |
| FIRE | `ui/pixel/stage/Fire*.kt`, `service/FireFeed.kt` and the three places in `data/ChatEngine.kt` that feed it, the tilt sensor, the shader |
| PET | `ui/pixel/pet/*` (brain, reactions, peek), `ui/components/PetNote.kt`, tests |
| ART-PET | `assets/pixel/mascot.pxl` (the fifteen animations, shadow, small Pêt) |
| ART-WORLD | `assets/pixel/scene*.pxl` (with seasons and weather), `fire.pxl`, a new `illustrations.pxl` (tiles, empty states), tab icon frames in `icons.pxl`, the new glyphs of the pixel font |
| SHELL | `ui/AppRoot.kt` (bars and the tab set of section 4.1), `ui/Nav.kt` (the routes of the tabs and of the first run), `ui/components/PocketTopBar.kt`, the splash theme and resources, new `ui/library/LibraryScreen.kt` (its first two shelves) |
| ONBOARD | new `ui/onboarding/*` (with the guided chat), `runtime/Settings.kt` (one flag), `runtime/RuntimeManager.kt` (unpacking can be started before a provider is set) |
| LANG | every `values-<code>/*`, `docs/i18n/*`, the language list and the three language settings in a new `ui/settings/LanguageSection.kt` (APPEAR takes the old rows out of its own file), the line about the answer language in `data/BotPresets.kt` |
| APPEAR | `ui/settings/AppearanceSection.kt`, `ui/theme/Appearance.kt`, `voice/SoundEngine.kt`, `res/raw/*` |

**Proven when:** the robot walks the Welcome from a wiped installation to the first answer on the
practice model; pinned screenshots exist for dawn, day, dusk and night in two themes, for a winter
day, for rain, for snow and for a night of shooting stars; the star field of a pinned date and place
agrees with a planetarium picture of the same moment (three bright stars and the moon checked by
eye); with the weather switch off no request leaves the phone, and with it on exactly one does per
hour; Home scrolls within budget with the scene alive and the scene costs under 1 ms per frame; ten
minutes on Home leave no sensor registered and no measurable drain beyond the screen itself; a
reviewer has looked at every new sprite and glyph on a contact sheet; the five main screens have been
photographed in each of the ten languages with nothing missing and nothing cut; the owner has passed
the two taste gates and read the Turkish page.

### 7.2 Wave 2, Conversation: the chat

**Goal.** The screen where the time is spent becomes a pleasure: answers that read like pages, work
you can follow, hands that can swipe, files you can see, and answers that are more than text.

**2.1 The open layout and rich content** (design language, section 8.2; finding A11)
- `BubbleStyle.OPEN` as the new default (taste gate).
- Code blocks: full width, sideways scrolling instead of wrapping, a wrap switch, line numbers from
  nine lines up, "Save as file". Tables: sideways scrolling with a fixed header row. Task lists, quotes,
  links with their host shown. Pictures in Markdown: files inside the guest are shown; remote pictures
  load only after a tap.

**2.2 The work timeline** (design language, pattern 13)
- Storage stays as it is (one TOOL message per step). A pure function groups the steps between a
  question and its answer into one timeline. New optional fields on `Message`: `toolStartedAt`,
  `toolDurationMs`, `toolPreview`, `toolError`.
- Folded: "Worked 12 s · 3 steps". Live: the running step with a timer. Each step opens a sheet with its
  full label and output. Thinking is the first line of the same timeline ("Thought for 4 s").
- Steps are coloured and drawn by kind (terminal, file, web, memory, skill, code, agent, phone, browser):
  nine icons on the six signal colours of the design language.
- The model is built for the richer data of Wave 3 (result previews, sub-agents as child steps), and
  works today with what the current stream provides.

**2.3 Blocks: answers that are more than text** (finding B15)
- `StreamBlocks`: one parser that splits an answer, while it streams, into prose and typed blocks (a
  code fence whose first word is a kind the app knows, followed by `key=value` pairs). It tolerates a
  fence that has only half arrived, never shows the raw text of a block it knows, and shows a kind it
  does not know as ordinary code. A property test: however the stream is cut into pieces, the blocks
  come out the same. The bot forge's `pocket-bot` block becomes the first registered kind; later waves
  register theirs (`canvas`, `deck`, `sources`).
- **Question cards.** When the agent needs a decision it writes an `ask` block instead of a paragraph
  of questions: a title and up to four fields (one choice, several choices, a short text, a number, a
  date). The chat shows a card with real controls. The answer goes back as an ordinary message that
  also reads well as text ("Format: slides · Length: 10 · Tone: formal"), so the transcript stays
  plain. Bots learn the block from two lines in their system message (about 60 tokens, with a switch
  per bot). Hermes's own question tool is not offered to API clients.
- **Result cards.** A small family of native cards that a block or, later, a tool result turns into:
  a link with its title and host, a table, a file. The phone powers of Wave 9 add the place, the
  event, the task, the timer and the contact.
- **A block that would create or change something lasting is a proposal, not an act.** Today a
  `pocket-bot` block saves a bot the moment it is written. From this wave it shows a card, and the
  bot exists when the owner taps Add. Wave 3 puts every such block (a bot, later an app, a theme, a
  recipe) under the gate's policy with the turn's record: in a turn that has read outside content,
  the card says so.

**2.4 Actions and gestures** (design language, section 4)
- Long-press lifts the message over a dither scrim with its menu beside it. Swipe right quotes: the
  next message carries `Message.quote` (shown as an inset; sent to the model as a plain Markdown quote).
  Double-tap stars. Long-press, then more taps, selects several messages: copy, share, star, delete, or
  "ask about these".

**2.5 Versions of an answer**
- "Again" keeps what was there: the answer becomes a small stack with a pager ("2 / 3") instead of
  being replaced.
- "Again with…": another model, more or less thinking, another bot, or a one-tap instruction
  (shorter, simpler, as a table, in another language).
- **Compare:** two versions side by side, or one above the other on a narrow screen, with the
  differences marked.
- **Branch:** continue from any version in a new chat. Editing one's own earlier message makes a
  branch too, instead of destroying what followed. Both chats show where they parted.
- Versions live inside the message; only the chosen one is sent to the model as history, so a stack
  of versions costs nothing later.

**2.6 The input bar**
- One button that is a microphone when the field is empty, send when there is text, stop while the agent
  works. The field grows to six lines and can expand to a full-screen editor. Pictures can be pasted or
  inserted from the keyboard. In groups, `@` opens the members. A message sent while the agent is busy
  waits in a "queued" chip and goes out when the turn ends (Wave 3 turns this into steering).
- An attachment tray instead of three separate buttons: camera, pictures, files; later waves add the
  scanner, the sketch pad and the recorder to the same tray.
- **The clipboard chip.** When the field is empty and something new is on the clipboard, a chip offers
  "Paste and ask". The clipboard is read only when the chip is tapped.

**2.7 Files from the agent** (findings B11, B17)
- **A home for the agent's files.** A folder outside the Linux image (`files/agent/`), mounted into the
  guest as `/root/workspace` through the launcher's existing `PA_EXTRA_BIND` and set as the folder the
  agent's terminal starts in (`terminal.cwd`). What the agent writes there survives updates, the app
  reads it without going through the image, and backups can carry it. The user's attachments are
  mirrored there as well. Until Wave 4 writes a proper platform hint, one line in each bot's system
  message tells the agent where its folder is.
- `AgentFiles` finds files in an answer: `MEDIA:<path>` tags (removed from the text), and Markdown links
  or pictures that point into the guest. A guest path is turned into the real file only after a
  canonical-path check that it lies inside the workspace (or one of Hermes's own output folders, named
  in a short list), and never for a file whose name the backup filter treats as a secret (`.env`,
  `auth.json`, credential files): a card can be shared, so the agent must not be able to make a card
  out of a key.
- Cards by kind: picture (inline, with a viewer that zooms, shares and saves), text, Markdown, CSV as a
  table, JSON, HTML (shown with scripts off; Wave 5 can run it), PDF (first page), sound, anything else
  (name and size). Every card can share and save.
- **A second check before anything leaves.** A name can lie (a key copied into `notes.txt`), so
  before a file is shared, exported, shown to another app or put into a backup, its content is
  searched for the exact secrets the app's redactor knows. A file that holds one stays where it is.

**2.8 The chat list**
- Swipe actions, archive, labels with filter chips, multi-select, two densities.
- Search moves onto an index: `ChatIndex`, a full-text table in the platform's own SQLite, built in the
  background from the JSON files, which stay the truth. Results show highlighted snippets and can be
  narrowed by bot, date, "has files" and "starred". The index is rebuilt when missing and is never part
  of a backup.

**2.9 Out of the app**
- Export as Markdown (exists), HTML and PDF. And the **postcard**: a selected exchange drawn as a
  picture in a pixel frame with the fire at the bottom, ready to share.
- **A postcard carries its content.** The text of the exchange is stored inside the picture file
  itself. Opened with Agirî on another phone it becomes a chat again; for everyone else it is a
  picture. A messenger that recompresses photos strips it; sent "as a file" it survives, and the
  share sheet says so. The same trick later carries a bot (Wave 11) and a theme (Wave 6). No link,
  no cloud.
- **What comes in from a file is outside content, for good.** A chat opened from a postcard is
  marked as imported; its lines are shown as quoted material, never as the user's or the agent's own
  words; and a turn that continues it starts under rule 1 of the gate. Whatever is carried inside a
  picture is read against a strict form and a size limit.

**2.10 Spaces** (the projects of the owner's notes, here from the start)
- A space gathers what belongs to one matter: a name, instructions (how to answer here), a default
  bot and mind, its chats, and its folder in the workspace (`/root/workspace/spaces/<id>/`).
- A chat started inside a space inherits the instructions. The chat list and the Library can be
  filtered by space, a chat can be moved in and out, and Home shows the spaces as a row.
- Instructions cost tokens on every message, and the space says how many.
- Wave 7 gives spaces their own knowledge, a look, and a way to be given away.

**Taste gate in this wave:** the answer layout.

**Extras in this wave:** the marked differences in a comparison (the pager and "side by side" are
core), export as PDF, the full-screen editor, the second density of the chat list.

| Agent | Owns |
|---|---|
| MODEL | `data/Models.kt`, `data/ChatJson.kt`, `data/ChatRepository.kt`, `data/Transcript.kt` (new fields, quote, versions, archive, labels), `data/ChatEngine.kt` (the queued message, the quote in what is sent, "again with"), their tests |
| CHATVIEW | `ui/chat/view/*` (layout, Markdown rendering, code, tables), and the chat screen itself with the parts Wave 0 split out of `ChatScreen.kt` and `ChatMessages.kt`: this agent wires the others' pieces in, by the names the contract fixes |
| TIMELINE | `ui/chat/work/*`, the grouping function and its tests |
| BLOCKS | `data/StreamBlocks.kt`, `ui/chat/blocks/*` (question cards, result cards, the proposal card), `data/BotForge.kt` (moved onto the parser; nothing is saved without a tap any more), every line that bots learn in this wave in `data/BotPresets.kt` (blocks, the workspace hint), tests |
| ACTIONS | `ui/chat/actions/*` (menu, swipe, selection), `ui/chat/versions/*` (pager, compare, branch) |
| COMPOSER | `ui/chat/input/*`, taking over `ui/chat/ChatInput.kt` |
| FILES | `data/AgentFiles.kt`, `ui/chat/files/*`, tests for the path check; the workspace mount (`runtime/Paths.kt`, one variable in `runtime/RuntimeManager.kt`, `terminal.cwd` in `runtime/HermesConfig.kt` with its fixture) |
| LIST | `ui/chat/list/*`, taking over `ui/chat/ChatListScreen.kt`; `data/ChatIndex.kt`, tests |
| EXPORT | new `data/Export*.kt` (taking the export code out of `ui/chat/ChatLogic.kt`), `data/PngText.kt` (content inside a picture, read strictly), `data/SecretScan.kt` (the second check of 2.7), `ui/chat/export/*` |
| SPACES | new `data/spaces/*`, `ui/spaces/*`, the row on Home (a slot), the filter the chat list offers, the space's instructions in what is sent (through a hook MODEL provides in `data/Transcript.kt`) |

**Proven when:** the long practice stream meets the streaming budget; the timeline, a question card,
a version pager with a comparison, a file card, a quote, a multi-selection, archive, labels and
search each pass a robot scenario; a debug seeder fills 300 chats and the list still scrolls within
budget, with the index built in the background; a postcard made in one installation opens as a chat
in a wiped one, arrives marked as imported, and cannot make the agent act without an ask; a bot block
from the practice model creates nothing until Add is tapped; a file that holds a known key refuses to
be shared; a chat started in a space carries its instructions; with the real model one tool turn, one
question card and one file made by the agent look right in Turkish.

### 7.3 Wave 3, Engine: the agent shows its work and answers to you

**Goal.** Use what Hermes can really do and show it; let no answer be lost; and put one gate, one
review and one ledger between the agent and everything it can touch. Pictures, sources, prices and
the phone's powers all stand on this wave.

**3.1 Engine v2 on the Runs API** (findings B1, B22; experiment S9)
- New `api/HermesRuns.kt`: create a run (`POST /v1/runs` with `input` = the new message, `instructions`
  = the bot's system message, `conversation_history` = the transcript, `session_id` =
  `agiri-<convId>-<botId>`, plus model, provider and model options), read its events (`GET
  /v1/runs/{id}/events`, resumable with `Last-Event-ID`), `stop`, `steer`, `approval`, and status.
- `RunEvent` (decoded from the stream): text, interim text, tool started, tool completed (duration,
  error flag, result preview), reasoning, sub-agent started and completed (goal, status, time, tokens,
  cost, files), approval request, steered, completed (answer, usage with cached tokens, the provider
  and model that really served), failed, cancelled, interrupted, replay truncated.
- `ChatEngine` gets this as a second backend behind the same public API, plus `steer(convId, text)`. A
  setting keeps the classic backend selectable for one release.
- **Fewer lost answers** (finding B22). The baseline needs no luck: the text that has arrived is
  saved every few seconds, and a turn that was cut off can be continued (3.2). On top of that,
  running turns are noted in `files/chat/runs.json` (run id, session, the last event number). If the
  stream breaks while the app lives, it is joined again from that number. If the app's process dies,
  Hermes usually dies with it or is swept away at the next start. Experiment S9 tries two ways to do
  better, and the contract takes whichever holds: taking a healthy gateway back (same port, same key,
  an unchanged configuration), and keeping the service that runs Hermes in a process of its own
  (`:engine`), so that the interface can be killed without the agent noticing. Where neither holds,
  the answer is read from the session Hermes stored (`/api/sessions/{id}/messages`) if it got that
  far; otherwise the chat says "interrupted" and offers Continue and Retry.
- A known limit: the history of a run is text only, so pictures of earlier messages are referred to by
  their path in the guest (they are mirrored there already); the newest picture is sent as today.
- What the owner gets: a real **Stop** (tools included); **Steer** ("Add to this task" while it works,
  shown as a "noted" line in the timeline; otherwise the message queues); **sub-agents** as child steps
  with goal, time, tokens and cost; step durations and result previews; a note when a fallback model
  answered; cached tokens in the meta line ("11.2k in, 9.8k of it cached · 340 out").
- A run reports its usage only when it ends. What a turn costs while it is still running comes from
  the gate's meter (3.4).

**3.2 Work that survives** (the second complaint of section 2.2)
- While a turn runs, the service that keeps Hermes alive also keeps the processor awake; when the last
  turn ends it lets go. The text that has arrived is written to disk every five seconds, so that even
  a hard kill costs seconds and not the answer.
- **The outbox.** A message sent without a network waits in the chat with a small hourglass and goes
  out by itself when the network is back. The fire shows that something is waiting.
- **Continue.** A turn that was cut off for good can be continued: the partial answer goes back with
  one line that asks to go on from there, instead of starting again and paying twice.
- **The first long task explains the phone.** Android and One UI put apps to sleep. Once, when a turn
  first runs longer than two minutes, a Pêt note offers the system's own screen for unrestricted
  battery use and says what it is for. It is never asked again.
- The running notification names the step ("Reading a page · step 4") and has Stop. When the turn
  ends, the reply notification of today does the rest.

**3.3 The bridge** (new package `bridge/`; findings B7, B9, B15; experiment S11)
- **The bridge** is how the agent reaches the phone: `BridgeServer`, a minimal HTTP server on loopback,
  on a random port, alive while Hermes is. Its doors: `/mcp` (the Model Context Protocol over HTTP:
  initialise, list tools, call tool), `/gate` (below), `/hooks` (events pushed by Hermes) and `/cli`
  (for a small `agiri` command inside the guest).
- Its token is made at every start and reaches Hermes through the regenerated `.env`. The token keeps
  other apps on the phone out. It is not a defence against the agent, which can read that file: the
  defence against a misled agent is the gate, and the short list of what the bridge offers at all.
- Configuration fragments: an `mcp_servers.agiri` entry, named in `platform_toolsets.api_server` so
  that it is offered (never together with `no_mcp`), and a `hooks.outbound` entry. The hooks are how
  the app also sees work it did not start itself (a job of Hermes's own scheduler, later a conversation
  that came in through Telegram): the fire reacts to it and the log stays complete. Hermes sends them
  without waiting for an answer, so they are never used for a decision.
- `PhoneTool`: a name, one short sentence, a JSON schema, a tier, the Android permission it needs,
  whether it opens a screen, and a `call`. Only tools that are not Off are listed to the agent, so an
  ability that is off costs no prompt tokens.
- The generated configuration becomes pluggable in this wave: each feature contributes a pure,
  separately tested fragment (`ConfigFragment`), and one owner composes them. The bridge, the gate,
  the keeper and everything Wave 4 adds are fragments.
- In this wave the bridge offers three tools, enough to prove the path: `phone_status`, `show_file`,
  and `ask_user` (the question card of 2.3 as a tool: the turn waits for the answer instead of
  ending). The phone's powers come in Wave 9.
- **Contained tools.** A bridge tool can be marked as contained: it changes nothing but the folder of
  the run it was called in (a note of a research, a row in a list of sources, a cell of a notebook).
  Contained calls are not effects under rule 1 and never ask. That is what lets long work run
  unattended without a terminal (Waves 7 and 9).

**3.4 The gate** (findings B19, B21, D9, D14, D17; experiment S10)
- **The gate** (finding B19). Hermes lets a plugin look at every tool call before it runs, with the
  session it belongs to, and veto it or send it to the human-approval gate. The app writes one small
  plugin at each start, like the configuration, that asks the bridge about every call and reports
  every result. So one policy, written in Kotlin and unit-tested, sees **every** tool the agent uses:
  Hermes's own (web, browser, terminal, files, memory, skills) and the phone's.
- **It fails closed.** Hermes lets a call through when a plugin's check ends in an error (finding
  D17). So the plugin catches everything itself: an error inside it, a timeout, a bridge that does
  not answer all end as a refusal of every call with an effect.
- **The policy.** Each ability is Off, Ask or Allow, with a separate "allowed in routines". On top of
  the switches the gate keeps a short record of what each turn has touched, and applies three rules:
  1. **Outside content, then effects ask.** Once a turn has read anything the owner did not write (a
     web page, a search result, the browser, an MCP connection, a notification, a shared file, a
     message from outside), every call with an effect asks, whatever its switch says. An effect is
     anything that changes the phone, leaves the chat or lasts: the abilities of tiers 1 and 2, a
     terminal command, and the writes of rule 3. Reading is not an effect. This is the defence against
     a page that tells the agent what to do. Such a turn is called tainted.
  2. **Private data, then the ways out ask.** Once a turn has read private data (the calendar,
     contacts, the location, the clipboard, notifications, and the owner's own files: the Library,
     recordings, the knowledge of a space, a folder the owner granted), every call that can carry
     something out asks: web search, reading a page, the browser, the terminal, a connection,
     sharing, opening a link.
  3. **Lasting changes ask.** Saving or changing a skill, creating a routine, and any write to
     `SOUL.md`, to the configuration, or to the folders Hermes runs code from (`hooks/`, `plugins/`).
     In a tainted turn a memory write asks as well.
- A call the gate cannot place (no known session, or made through `/cli`) counts as tainted. A routine
  started by a notification or by something shared starts tainted too.
- **Blocks pass the same policy.** A block in an answer that would create or change something
  lasting (a bot, a small app that asks for doors, a theme, a recipe) is not a tool call, so no
  plugin sees it. The engine therefore asks the policy itself before it shows the proposal card,
  with the turn's record. In a tainted turn the card carries the warning, and no standing "always
  allow" can accept it.
- **A mark that travels.** What a tainted turn writes into the workspace keeps the mark (a small
  list beside the workspace, by path and fingerprint), and a later turn that reads such a file is
  tainted too. Whatever came in from outside the app is outside content from the start and stays
  so: an imported chat, bot or space, a scan, a file shared in from another app, a file from a
  folder the owner granted.
- **Contained work never asks.** Calls to the bridge's contained tools (3.3) are not effects.
- **Consent by task.** Some work cannot ask at every step: a research that reads fifty pages, a
  notebook that runs code, a job for the night. Such work starts from a card that states what it
  needs: which abilities, how many steps at most, a ceiling in tokens and in money, where its
  results go. Starting it grants exactly that, for that run and nothing after it. Inside the run
  everything that is not on the card still asks, and when nobody is there to answer it is held for
  the owner (9.5). A grant can never include speaking in the owner's name, spending beyond its
  ceiling, or a lasting change.
- **Sub-agents.** A child cannot ask (Hermes refuses its asks) and its session is new to the gate
  (finding D9). The plugin is told which child belongs to which parent when the child starts
  (finding D14), so a child inherits its parent's record and its parent's grant, and nothing more.
  What a child needs beyond that is refused with the reason, and the parent can turn the refusal
  into a question.
- **Nothing with a price.** Hermes's picture tool and its speech tool are refused by name whatever
  the configuration says (findings D6, D15). A tool the gate has never heard of counts as an effect
  and as a way out, so it asks until the policy gives it a place.
- **The meter.** The plugin is also told about every request to the model and about Hermes's own
  side calls. So the gate counts tokens and money per turn while the turn runs, which the run's own
  stream cannot do, and children are counted with their parent. The loop guard and the spend guard
  of 4.12 stand on this: Hermes has no pause of its own, so the gate turns the next call into a
  question ("this answer has cost 40 cents so far; go on?"). If S10 shows that a request to the
  model can be refused from a hook, a ceiling stops the turn there too.
- **Asking** uses Hermes's own approval gate: the plugin answers "ask", Hermes waits, and the app shows
  the approval card it already has, or a notification with Allow and Deny in the background, with the
  reason in plain words ("after reading a web page, Agirî wants to copy text to your clipboard"). No
  answer within two minutes is a "no".
- **The log.** Every call that reached the gate: time, tool, a redacted summary of the arguments, the
  rule that applied, the answer, the chat. Kept to the last 2,000, shown under Safety and privacy.
- **Honest limits.** The terminal is a general tool: no rule can read from a command's text everything
  it will do. That is why rule 1 makes every command ask in a tainted turn, why nothing the bridge
  offers can speak in the owner's name or spend without the owner's tap, and why private data needs Android's own
  permission first. The worst a fooled agent can do is bounded by what it is offered.
- **One more limit, said plainly.** The provider's key has to lie where the agent's Linux can read
  it, and a command could use it to buy something the app never offers (a picture, a voice). The
  gate asks before every command in a tainted turn, and the budgets and the meter catch what the app
  can count. An owner who wants a hard stop sets a spending cap at the provider (section 13).

**3.5 The keeper** (finding B18; experiment S12)
- **What the agent may keep.** A skill is an instruction that lasts, so it is the first thing a hostile
  page would try to plant. From this wave on, a skill the agent wants to save or change waits for the
  owner's OK (Hermes's own review gate, `skills.write_approval`, finding B18): the app watches the
  folder of waiting writes, shows each as a card with the difference, and applies or drops it through
  Hermes's own functions. Memory can be held back the same way by a switch (Wave 11 gives it a screen).
- The app also keeps its own snapshots of everything that lasts: memory, skills, Hermes's scheduler,
  `SOUL.md`, `hooks/` and `plugins/`. Whatever changed there without passing the gate (a terminal
  command can write anywhere) is shown as a card with Undo. Code that appeared in `hooks/` or
  `plugins/` and was not written by the app is set aside at the next start until the owner says Keep.
  The configuration is compared with what the app wrote before every message.

**3.6 Undo for files** (finding D8; experiment S13)
- **The app's own snapshots come first.** Before and after every run the app takes a snapshot of the
  workspace with the `git` that is already in the image, in a store beside the workspace (outside the
  image, so it survives an update). That covers whatever changed and however: a file tool, a script,
  a terminal command. The timeline shows "changed 3 files" as a step; its sheet lists the files with
  their differences and offers **Undo this turn** and "Undo everything since this message".
- Hermes's own checkpoints are switched on as well, where S13 shows that they work for API turns.
  They are finer (before each file tool) but narrower: one per turn, nothing for what a script
  changes, and a store that an update deletes (finding D8). Where they exist, the sheet also offers
  "Undo this step"; restoring goes through a small helper in the guest that calls Hermes's own
  functions, because the typed command is not open to an API client.
- The store has limits, and the sheet says them: the last twenty runs, 500 MB, files over 10 MB left
  out. It is not part of a backup, and a private chat takes no snapshots (Wave 11).

**3.7 The ledger: what left the phone** (finding D7)
- Under every answer, one quiet line: "Sent to Google · 2 searches · 5 pages". A tap opens the ledger
  of that turn:
  - **The model:** provider, model, tokens in and out (and money from Wave 4), and whether pictures
    or files were in the request.
  - **Searches:** the words that were searched and the service they went to.
  - **Pages:** every address that was asked for and the address it ended at, the outside service
    that read it for the agent (finding D16), when, and how much came back.
  - **Connections:** every call to an outside service, by name.
  - **The phone:** every ability used (from Wave 9), with the gate's decision.
  - **The app itself:** its own direct calls (helper tasks and pictures from Waves 4 and 6, the
    weather) and its downloads (a voice, a pack, a font, an update), each with where it came from.
  - **Hermes's own side calls,** when one happens (the titles are switched off; compressing a long
    session is listed with its tokens).
- Where it comes from: the gate sees every tool call and its result, the engine knows every request to
  the model, and the app's own calls are logged at the one place they are made. Nothing in the ledger
  is the model's own account of what it did.
- **The record of fetched pages** is what Wave 7 builds its source marks on. Per turn it keeps the
  address asked for and the address reached, the time, the service that read the page, and the whole
  text that came back (compressed, up to a limit per turn). A long page arrives as its head and its
  tail; what the agent later reads of the middle from Hermes's page cache is added to the same
  record.
- Honest limits. The ledger lists what the agent's tools and the app itself sent; it is not a
  recording of the network. A terminal command can reach the network without the gate seeing where:
  such a turn's ledger says "ran commands that may have used the network" and lists them. What an
  outside service does with an address or a search, and what a provider does with a request, is
  theirs to answer for; the ledger names them, so the owner knows whom to ask.
- The ledger is kept with the chat and deleted with it. The privacy centre of Wave 11 sums it up.

**3.8 Safety and privacy** (the one home of the rules; section 4.1)
- A screen under the Agent tab. The three rules in plain words, each with the last time it applied.
  The approval mode, which is set here and nowhere else. The gate's log. **The waiting room:** what
  the agent wants to keep (skills, and memory if that switch is on), each as a card with the
  difference. Snapshots with Undo. The grants of running tasks, each with what is left of it and a
  Stop. And one switch that is also in the running notification, "Hold everything": until it is
  turned off, every effect asks.
- Wave 11 adds the other half to the same screen: what is stored, what left, and the locks.

**Extras in this wave:** steering a running turn (a queued message is core), "Undo this step" from
Hermes's finer checkpoints, the outbox.

| Agent | Owns |
|---|---|
| RUNS | `api/HermesRuns.kt`, `api/RunEvents.kt`, `data/ChatEngine.kt` (with the policy check before a proposal card), `data/RunStore.kt`, tests with a fake backend |
| TIMELINE2 | `ui/chat/work/*` (previews, sub-agents, the steer line, the "changed files" step), `ui/chat/input/Steer*.kt` |
| ENDURE | `service/AgentService.kt` (the wake lock, the step in the notification, Stop; if S9 says so, its move into a process of its own, with this wave's manifest entries), `data/Outbox.kt`, the partial saves in `data/ChatRepository.kt`, the battery note, "Continue" |
| CONFIG | `runtime/HermesConfig.kt`, `runtime/ProviderConfig.kt`, `runtime/ConfigFragment.kt`, fixtures, `runtime/RuntimeManager.kt` (taking a living gateway back; starting and stopping the bridge with Hermes), `runtime/UserState.kt` (`pending` and `profiles` survive an update) |
| BRIDGE | `bridge/BridgeServer.kt`, `bridge/Mcp.kt`, `bridge/tools/Core*.kt` (the three first tools), the `agiri` guest command, its configuration fragments, tests |
| GATE | `bridge/Gate.kt`, `bridge/Policy.kt` (with the table that places every tool Hermes has), `bridge/TurnRecord.kt`, `bridge/TaintStore.kt` (the mark that travels), `bridge/Grant.kt` (consent by task), `bridge/Meter.kt`, `bridge/Audit.kt`, the gate plugin written into the guest (it fails closed), the reasons in plain words, a test for every rule |
| KEEPER | `hermes/PendingWrites.kt`, `hermes/StateSnapshots.kt` (memory, skills, the scheduler, `SOUL.md`, `hooks/`, `plugins/`), `hermes/FileUndo.kt` (the app's own snapshots of the workspace, and Hermes's checkpoints where S13 allows), the helpers in the guest (review, snapshots), the Keep, Remove and Undo cards |
| LEDGER | `data/Ledger.kt`, `data/FetchRecord.kt` (the whole text, the service, both addresses), the ledger line and sheet (`ui/chat/ledger/*`), `ui/safety/*` (the rules, the approval mode, the log, the waiting room, grants, snapshots, "Hold everything") |

This wave gets a second reviewer whose only task is to break the gate.

**Proven when:** on the practice model the robot stops a turn in the middle of a tool, steers a running
turn, and kills the app during a turn and after reopening finds the answer, or an honest "interrupted"
with Continue, as S9 decided; a message sent in flight mode goes out when the network returns. The
attack scenarios pass: a practice page that orders a skill, a memory entry, a scheduler job and a file
in `hooks/` meets an ask or a refusal every time; a call with no known session asks; with the bridge
stopped, effects are refused; a skill the practice model tries to save waits, and exists only after
Allow. A file the practice model overwrote is back after Undo, and so is one that a script changed
through the terminal. The ledger of a practice turn with two searches and three pages lists those
and nothing else, with the service that read each page, and a page the practice model merely names
in its answer is not in it. A gate plugin made to fail refuses instead of letting the call through.
A practice turn that reads a page and then writes a bot block shows the card with its warning. A
file written in a tainted turn taints the turn that reads it. A task started from a card runs
twenty contained steps without one ask and is asked about the first thing outside its grant. The
meter's count for a practice turn with a sub-agent equals the usage reported at its end. With the
real model one tool turn shows its steps, its ledger and a working Stop.

### 7.4 Wave 4, Thrift: strength where it is needed, and nothing wasted

**Goal.** Stop paying for what is not used, show cost in money, and open the doors to more skills,
more models and outside services.

**4.1 Abilities: a switch for every toolset** (findings B2, B3, B20)
- Agent tab, "Abilities": one card per toolset in plain words, with its state (works here, needs
  setup and what, cannot work on this phone and why), its weight in prompt tokens, and a switch.
  Presets: everything that works here (default), Coder, Researcher, Talker (no tools). This screen
  is the one home of what the agent can do (section 4.1): Wave 9 adds the phone's powers to it as a
  section, not as a screen of their own.
- `runtime/AbilityConfig.kt` renders `platform_toolsets.api_server` and `agent.disabled_toolsets`. MCP
  servers are named there one by one, so only the enabled ones are offered. With none enabled the list
  carries `no_mcp`; the two are never combined, because `no_mcp` shuts out every server.
- A switch applies to the next message without a restart (finding B20). Only connections and profiles
  need one, and that is asked for.

**4.2 Prompt weight** (finding B4; experiment S15)
- A screen that shows where the fixed cost of every message goes, as one stacked bar: tools, skills
  index, memory, your profile, guidance, soul. Hermes reports the size of each part in characters
  (`hermes prompt-size --platform api_server --json`, run in the guest and cached per configuration);
  the app turns them into tokens with a factor per model, calibrated against the usage of real turns.
- Under the bar, switches with the saving each brings: the guidance blocks, the memory blocks, how many
  tools are deferred to tool search, and a short platform hint written for this phone. The hint also
  tells the agent the truth about where it runs (a small Linux on a phone, no desktop, where files go,
  how to hand a file to the user), which today it has to guess.
- Goal: the full agent at or under 6,000 tokens per message. Before and after go into
  `docs/perf/w4-prompt.md`.

**4.3 Light minds** (finding B5; experiment S14)
- A second Hermes profile, `light`, served by the same gateway under `/p/light/` (a second profile
  switches that on by itself, finding B20): no tools, no skills index, no guidance, the same model.
  Each profile reads only its own files, so the app generates `profiles/light/.env` and `config.yaml`
  together with the main ones, plus a copy of the credential file for the two modes that use one, and
  a read-only copy of the memory files at each start, so a light bot still knows its user but cannot
  write memory.
- `Bot.mind`: Full (tools, skills, memory) or Light (conversation only), shown in the editor with the
  real cost of each per message. Rebel and Dengbêj start as Light, Hermes and Coder as Full. Light bots
  are sent to `/p/light/...`. Goal: 2,500 tokens per message or less.
- If the experiment fails, in this order: light requests by swapping the configuration for the instant
  a request starts (the gateway reads it per request, and the engine starts one request at a time);
  then one global "Light mode" switch. Features that count on a light mind say so and fall back to the
  full one: the quick question (Wave 10), the routine limit (Wave 9), the Council's default (Wave 11).

**4.4 Money** (finding B10)
- `data/PriceBook.kt`: price per million tokens (in, out, cached) per model. Sources in order: the
  owner's own entry; Hermes's cost for the session (`GET /api/sessions/{id}` carries it; the difference
  after each turn belongs to that turn); a small seeded table marked with its date.
- Usage shows money for today, seven days, all time, per model and per chat. The budget can be set in
  money. Providers list their prices in US dollars; the app shows money in any currency at a rate
  the owner types (no network lookups), and says which rate it used.
  A model can be marked "free for me": its tokens count, its cost shows zero.
- From this wave the meta line under an answer, the ledger and the woodpile speak in money. The price
  book also holds the prices of pictures (per picture, by model and size), which Wave 6 shows before
  anything is made.
- Every ceiling in this plan (a daily budget, a research, a job for the night) is kept in tokens as
  well as in money, so that a missing or a wrong price can never lift it.

**4.5 Long chats: the meter and compaction**
- Chat controls show how full the next request is against the model's window, and what one message in
  this chat costs now.
- "Compact now" replaces the older part of the chat, for the model only, by a summary. Nothing
  disappears from the screen; a divider shows where the model's detailed memory ends
  (`Conversation.summary`, `summaryUpToMessageId`). Optional automatic compaction above a cost per message.
- **"Where were we?"** On opening a chat that has rested for a week, one chip offers a three-line
  recap, made by a cheap helper only when it is tapped.

**4.6 Cheap helpers** (principle 5)
- `data/UtilityLlm.kt`: small tasks under their own daily cap and a master switch (off, titles only,
  all). Uses: a real title after the first answer, compaction summaries, the recap, one-line texts for
  routine notifications, and later translations and summaries of shared things. Each use is listed in
  the settings with its typical cost.
- A helper never rides on the full prompt. It goes straight to the provider (4.7), with a one-line
  system message and no Hermes at all: about a hundred tokens of overhead instead of eleven thousand.
- Helpers and every other direct call follow the practice switch of Wave 0: while it is on they go
  to the practice model, so a test cannot spend.

**4.7 Direct calls, and Google's token made in the app** (experiment S16)
- `api/DirectLlm.kt`: one small client for a plain request to the owner's provider, used by the
  helpers and, in Wave 6, by pictures. In the key-based modes the app already holds the key and the
  address.
- `runtime/GoogleAuth.kt`: for the two Google modes that need a sign-in exchange, the app makes the
  short-lived access token itself from the stored credential (a signed assertion for a service
  account, a refresh exchange for user credentials), with the platform's own cryptography. The token
  lives in memory only and is never written or logged.
- Every direct call is counted in Usage like any other and appears in the ledger under "The app
  itself".

**4.8 Models, chosen with open eyes**
- A catalogue per provider from what the provider's own list and Hermes's tables say: the size of the
  window, the price, whether it sees pictures, its thinking levels. Favourites on top.
- **A speed test** on tap (an extra): the time to the first word and the words per second, on this phone and
  this network (about 200 tokens).
- **A fallback chain.** When the provider fails twice, the next model in the chain is offered (or,
  with a switch, used at once), and the answer says which model really served.

**4.9 The skill shop** (finding B12)
- "More skills" under Agent > Skills: the 134 optional skills that are already inside the guest, by
  category, with what each needs and whether that exists here. Install and remove work offline through
  the `hermes skills` command. Every installed skill shows what it adds to the prompt.

**4.10 Packs and downloads** (finding D12; experiment S17)
- One screen for everything optional, with its size and what it is for: voices (today's download
  moves here), and from this wave **packs**: sets of libraries for the agent's Linux. The first is the
  data pack (numpy, pandas, matplotlib; about 43 MB to download), which Wave 7 uses.
- A pack lives outside the Linux image, like the workspace, so an update does not delete it. It is
  stamped with the version of Python it was built for and rebuilt on first use when that changes.
- The downloader that fetches the voice model today becomes one shared piece (resumable,
  cancellable, for plain files and archives). Speech models, script fonts and the updater of Wave 10
  use it too.
- **Pinned and checked.** Nothing is fetched from an address that can change under the app. Every
  download is named in a catalogue that ships inside the APK: a fixed revision of its source, a
  SHA-256 per file, its size, its licence, and what else it brings along (some voices need a
  separately licensed set of pronunciation data, and the screen says so first). Today's voice
  download has no checksum; it gets one here. A pack is installed from a lock file that names every
  file with its checksum, so nothing is resolved at install time and a changed file is refused.
- The screen shows what each download takes on the phone and removes it with one tap.

**4.11 Connections** (findings B7, D11)
- Add a server by address (the validator of the custom provider is reused), with an optional secret
  header that lives in the secret store and reaches Hermes as a variable. Test button, the list of its
  tools with switches, and its weight in the prompt. The 76 bundled descriptions appear as suggestions;
  those that need local programs are greyed with the reason.
- **Sign-in, run by the app** (finding D11). Hermes's own browser sign-in opens a browser inside the
  guest, which cannot reach Android's. So the app starts the sign-in by code (`hermes mcp login`),
  shows the code and opens the address in the phone's browser. Hermes keeps the result in
  `mcp-tokens/`, which joins the list of what survives an update and is a secret in backups; every
  profile has its own. The app shows who is connected, what the connection may read, and a
  Disconnect.
- **Four guided connections.** A calendar, cloud files, mail and notes get a guided setup instead of
  a bare address (from the bundled descriptions, where one exists), each with one sentence on what
  it can read and what it can send.
- **Connections that read messages.** Reading mail or chats is never one of the phone's own powers
  (Wave 9). A mail connection the owner adds is the owner's choice, and it is treated with care:
  what it returns is outside content and private data at once (rules 1 and 2), sending through it
  always asks, and it is off in routines unless the owner allows it there.
- **Cloud files without a connection.** Drive, OneDrive, Dropbox and the like already appear in
  Android's own file picker. Picking a file there needs no sign-in inside Agirî and gives the agent
  that one file and nothing else. The Connections screen says so before anything else.
- The gate treats everything a connection returns as outside content (rule 1).
- **Folders you grant.** Through Android's folder picker the owner can hand the agent one folder
  outside the app: a vault of notes, a folder of documents. The agent reaches it through bridge
  tools (list, read, search, and write only if that was granted), never as a mount. What it reads
  there is private data (rule 2) and outside content (rule 1).

**4.12 Guard rails** (findings B6, B13, B14)
- Settings: a cheaper model for sub-agents (`delegation.model`) and the most steps per answer
  (`agent.max_turns`). (The approval mode has its one home under Safety and privacy; the switch for
  keyless web search has been there since Wave 0.)
- A "Sessions" page under the Agent tab (list with cost, open, delete, prune).
- **A loop guard:** the same tool with the same arguments three times in one turn asks. **A spend
  guard:** a single answer that passes an amount the owner sets, in money or in tokens, asks. Both
  are built in the gate, on its meter (3.4), because Hermes has no pause of its own.
- **Rate limits, taken calmly.** When a provider answers "too many requests", the engine waits as
  long as the provider asks, lowers how many requests run at once (sub-agents first), and says so in
  the timeline ("waiting 20 s: the provider's limit"). A free key has small limits; the app says
  that instead of failing.

**Extras in this wave:** the speed test, automatic compaction, "Where were we?", the four guided
connections (adding a connection by address is core).

| Agent | Owns |
|---|---|
| ABILITIES | `runtime/AbilityConfig.kt`, `ui/hermes/Abilities*.kt`, the tab list (`ui/hermes/HermesTabs.kt`, `HermesScreen.kt`), and the composition of all fragments in `runtime/HermesConfig.kt` with its fixtures |
| LEAN | `runtime/ProfileConfig.kt`, the prompt-weight helper and screen, the platform hint, `Bot.mind` in `ui/bots/*` |
| MONEY | `data/PriceBook.kt`, `data/UsageStore.kt`, `ui/settings/UsageSection.kt`, money in the meta line and the ledger line |
| CONTEXT | `data/Compaction.kt`, `data/UtilityLlm.kt`, the meter in the chat controls, titles, the recap chip |
| DIRECT | `api/DirectLlm.kt`, `runtime/GoogleAuth.kt`, `data/ModelCatalogue.kt`, the speed test, the fallback chain, `ui/settings/ModelsSection.kt`, tests |
| SHOP | the skill shop, `ui/hermes/Sessions*.kt`, the guard-rail settings (`ui/settings/GuardSection.kt`, `runtime/GuardConfig.kt`), `bridge/Guards.kt` (the loop and spend guards, on the gate's meter), `data/RateLimit.kt` (waiting and lowered parallelism, called by the engine at the one place the contract names) |
| PACKS | `data/Downloads.kt` (the shared downloader), `assets/downloads/catalogue.json` (revisions, checksums, sizes, licences), `runtime/Packs.kt` (the mount, the stamp, the lock file, install and remove) with its one variable in `runtime/RuntimeManager.kt`, `ui/settings/DownloadsSection.kt`, `voice/SupertonicModelStore.kt` (moved onto the downloader) |
| CONNECT | `runtime/McpConfig.kt`, `ui/hermes/Connections*.kt` (catalogue, the sign-in by code, tools, weight, the guided setups), `mcp-tokens` in `runtime/UserState.kt`, `bridge/tools/Folder*.kt` (folders you grant) |

**Proven when:** `GET /v1/toolsets` reflects the switches without a restart; a full and a light "hello"
on the real model show their prompt tokens against the goals; a real turn's cost matches Hermes's own
session cost; a 60-message practice chat compacts and its next request is smaller; a title is made by
a direct call in every credential mode the twin has, costs about a hundred tokens and stands in the
ledger; a pack is installed, is still there after the Linux image was replaced, and is removed again;
with the first model of the chain made to fail, the second answers and the answer says so; a practice
MCP server in the guest is added, tested and called, and a sign-in by code against it is still there
after the Linux image was replaced; a pack with one altered file refuses to install, and so does a
download whose checksum is wrong; a practice provider that answers "too many requests" makes the
timeline say so and the turn still ends; the same tool call three times in a turn asks; a file in a
granted folder is read through the bridge and marks the turn.

### 7.5 Wave 5, Canvas: a place for what the agent makes

**Goal.** Whatever the agent makes that is more than a reply gets a place of its own: it appears
while it is being written, it has versions, it can be changed by hand and by asking, and it leaves as
a real file. This is the wave the owner's notes asked for first.

**5.1 The protocol** (experiment S18)
- An item is born when the agent writes a block:

  ````
  ```canvas kind=document id=trip-plan title="Three days in Lisbon"
  # Three days in Lisbon
  …
  ```
  ````

  Kinds in this wave: `document` (Markdown), `code`, `page` (one HTML file), `app` (a page that is
  kept as a tool), `drawing` (SVG), `diagram` (Mermaid), `chart` and `table`. Wave 6 adds `deck`,
  `sheet`, `picture` and `theme`: twelve kinds at 1.0. Later things are not new kinds: a notebook
  (Wave 7) is a `code` item made of cells, a research report and a transcript are `document` items.
- A change is a second block that names the item and carries only what changes:

  ````
  ```canvas-edit id=trip-plan
  <<<<<<< find
  Day 2: museum
  =======
  Day 2: market, then museum
  >>>>>>> replace
  ```
  ````

  A hunk must match exactly once. One that does not is refused, and the next turn tells the agent so
  that it can send the item whole. A whole new version is simply a `canvas` block with the same id.
- **Why blocks in the stream, and not a tool:** no extra model call, the panel fills while the model
  writes, it works with every provider, with the light mind and with the practice model. Things made
  by tools land in the same place: the store watches the item folders in the workspace, so a chart a
  script drew or a file a skill built becomes a version too. The watch adopts documents and data
  only. A new version of a `page` or an `app` is never taken from the folder: code that will run in
  the sandbox becomes a version only through a block in the chat or through the owner's own edit, so
  that a script cannot quietly rewrite an app the owner kept.
- **Teaching it costs about 120 tokens**, in the system message of bots that have the Canvas switched
  on (on for Hermes and Coder, a switch for every bot). The rule it teaches: use an item for anything
  the user will keep, reuse or look at as a whole. S18 measures on the cheapest model whether the
  block is used when it should be and left out when it should not.
- **Anything can be promoted by hand.** An answer, a code block or a table in a chat has "Open in
  Canvas" in its menu, so the workspace never depends on the model's judgement.
- **What a long session costs.** Later turns do not carry every item again. The transcript holds a
  short reference ("[canvas trip-plan v3, 84 lines]"); the full text travels only for items that are
  open in the panel or were touched in the last two turns, or when the user points at one with `@`.

**5.2 The store**
- `CanvasStore`: an item has an id, a kind, a title, its chat (and that chat's space, if any) and its
  versions. It lies in the workspace (`files/agent/canvas/<chat>/<item>/v<n>.<ext>` with a
  `meta.json`), where the agent sees it as `/root/workspace/canvas/…`, so the agent's own tools can
  read, run and convert it.
- A version knows who made it (the agent in which turn, or the user by hand), when, and from which
  version. The last 30 are kept in full; older ones are thinned to every fifth. A version the user
  named ("sent to the client") is never thinned.
- Items survive updates and travel in backups with the workspace. `CanvasIndex` (the platform's own
  full-text search, like the chat index) makes all of them searchable.

**5.3 The panel** (design language, section 8.5)
- On a phone an item has three states. **A card** in the chat where the block was written: the kind,
  the title, a preview, the version, and a writing line while it streams. **A half sheet** over the
  chat when the card is tapped: the chat stays visible and usable above it. **The full canvas** when
  the sheet is dragged up: the item fills the screen and the chat shrinks to one input line, "Ask
  for a change", with the agent's last sentence above it.
- **The keyboard wins.** A half sheet and a keyboard do not fit on one phone screen together with a
  chat. When the keyboard opens, the sheet drops to a peek (its bar and two lines of the item) and
  rises again when the keyboard closes.
- What runs in the sandbox (a page, an app) is shown in the card and the half sheet as a picture of
  itself, and runs live in the full canvas (5.4).
- On a wide screen (a tablet, an unfolded phone, DeX, landscape) the chat stands on the left and the
  canvas on the right, with a divider that can be dragged.
- Several items of one chat are tabs. A shared element carries the card into the panel and back.
- The panel's bar: the title (editable), the version scrubber, Edit, Share, and More (pin, duplicate,
  move, delete).
- While the agent writes, the view follows the end unless the user has scrolled away (the same rule
  as in the chat). Code is highlighted as it arrives. A page is run only when it is complete.

**5.4 The viewers**
- **Document:** a reading layout with margins, a table of contents drawn from the headings, footnotes
  and mathematics (5.5).
- **Code:** highlighting for about 25 languages, line numbers, a wrap switch, search, and a difference
  view between any two versions. **Run** for Python and shell: the code runs in the agent's Linux
  inside the item's folder, and a console strip under it shows the output, the exit code and the
  time; pictures and files the script wrote appear as cards. A run the user taps is the user's own
  act; a run the agent asks for passes the gate like any command.
- **Page and app:** a web view in a process of its own, apart from the app and (later) from the
  agent's browser. Android cannot place a view that lives in another process inside the chat, so the
  card and the half sheet show a picture of the page, and the page itself runs full screen in the
  sandbox's own activity. The sandbox is built, not assumed, and built from parts of the platform
  only: the app answers every request itself (`shouldInterceptRequest`) from the item's folder under
  a made-up address, with a strict content policy (no requests of any kind, no frames, no forms
  going out); it refuses every navigation, turns file and content access off, and puts a short
  script in front of every page it serves, which removes what could open a connection around the
  policy (peer connections, sockets, workers that fetch) before the page's own code runs. Debugging
  of the view is off in the owner's build, and the escape tests run on that build, because a test
  build switches debugging on. The owner can give one item network access; its card says so. Around
  it: a size switch (phone, tablet, desktop width), reload, "Source", and a strip with the page's
  own error messages, with **Fix it**: one tap sends those errors back to the agent.
- **An app** is a page the user kept. It gets a name, a generated pixel icon and a place in the
  Toolbox. It can be given two doors, both closed until the owner opens them and both named on its
  card: a small store of its own (its data stays on the phone and no other app can read it), and
  questions to the agent (each one is listed in the ledger and they are limited per minute). The doors
  are a message channel with a fixed form, never an object placed inside the page.
- **A door belongs to one version.** A door, and network access, are granted to the exact code the
  owner looked at (its fingerprint). When the code changes, whoever changed it, the doors close and
  the card asks again, showing what changed. A question that comes out of an app is outside content:
  the turn it starts is tainted from its first word (rule 1), and it is answered by a light mind
  without tools unless the owner chose otherwise for that app.
- **Drawing and diagram:** drawn inside the same sandbox with scripts of the page itself off, and
  handed to the card, the sheet and the document as a picture. A diagram's source is rendered by the
  bundled renderer in the plain colours of the paper it stands on, with pan and zoom, an error line
  when the source is wrong, and "Source" to edit it.
- **Chart:** drawn by the app itself from a small description (bar, stacked bar, line, area, pie,
  scatter; title, categories, series, unit), plainly, in the look of the document or deck it belongs
  to. The pixel style is one style a chart can take, and the app's own for its dashboards; it is
  never the default for the owner's content (design language, rule 4). It is the same description a
  chart has inside a slide (finding D3), so a chart moves into a deck untouched; the kinds that
  PowerPoint's writer does not know (stacked bar, area, scatter) go into an exported file as
  pictures, and the export says so. A tap on a bar shows its value; the same data can be shown as a
  table. Patterns, not only colours, tell the series apart.
- **Table:** a native grid with a fixed header row and first column, sorting, a filter field, sums and
  averages in a footer, editing by hand, new rows and columns. Up to 5,000 rows comfortably; larger
  files open for reading only and say why.
- **PDF and pictures** that the agent hands over open in the same panel as viewers, so everything has
  one home.

**5.5 The render kit** (finding D13; experiment S19)
- Two renderers ride inside the APK as plain files: Mermaid for diagrams and KaTeX for mathematics
  (about 2 MB together once compressed; S19 measures it). Pinned versions, licences in `NOTICE`. They
  are loaded only inside the sandbox process, which never has the network.
- **Mathematics everywhere:** formulas in a chat and in a document are drawn by one hidden sandbox
  view and placed as cached pictures; copying gives the formula's source. Diagrams and drawings
  reach a card the same way: the sandbox draws, the app shows a picture.
- The sandbox process starts on first use and is let go after five idle minutes (the memory budget of
  0.12).

**5.6 Versions and the scrubber** (design language, pattern 21)
- A row of ticks, one per version. Dragging along it travels through the item's history; each tick
  says who and when; named versions carry a flag.
- **Restore** makes a new version from an old one: history is never rewritten. **Compare** any two:
  marked differences for text, a wipe between two pictures for everything visual.

**5.7 Editing by hand**
- Documents, code, tables, chart data and diagram sources have a real editor (for Markdown with a bar
  for headings, bold, lists, links and tables; undo and redo). Every save is a version "by you".
- The agent learns of a hand edit in the next turn through the reference line and gets the current
  text. An edit of the agent that arrives while the user is typing becomes a suggestion (5.8); it
  never overwrites.

**5.8 Editing by asking**
- **Select and ask.** Select a passage, some lines of code or some cells: a small bar offers Improve,
  Shorter, Longer, Simpler, Fix, Translate, Explain and "Ask…". The request travels with the selection
  marked, and the answer comes back as an edit of those lines only.
- **Review changes** (a switch per item). Off: an edit applies at once as a new version, and the
  scrubber takes it back. On: the edit is shown as tracked changes (added text marked, removed text
  struck through), each hunk with Accept and Reject, and "Accept all". This is writing together, line
  by line.
- Whole-item actions on the card and in the panel: Shorter, Change the tone, Translate, Continue, and
  from Wave 6 "Turn into…" (a document into slides, a table into a chart).
- **Thrift:** an edit returns only changed lines, so changing one paragraph of a long document costs
  a fraction of writing it. Improve, Shorter and Translate on a selection go to the cheap helper of
  4.6 where it exists; "Ask…" goes to the full agent.

**5.9 Out of the Canvas**
- Export by kind. A document: Markdown, HTML, plain text, and PDF through Android's own print engine
  (paper, margins, a header and page numbers); DOCX from Wave 6. Code: the file, or a ZIP of the
  item's folder. A page: the HTML file, a picture, a PDF. Drawings, diagrams and charts: SVG and PNG
  at a chosen size. A table: CSV; XLSX from Wave 6.
- Share, "Save to…" through Android's file picker (any folder or cloud drive listed there), print,
  copy with formatting. A postcard of an item (2.9) carries the item inside.
- **Pin:** to the app's Home, to the phone's home screen (a shortcut with the generated icon that
  opens the item full screen: a kept app then behaves like an installed one), or to a space.

**5.10 The Made shelf and the Toolbox**
- "Made" is a shelf of the Library (section 4.1): everything the agent ever made, newest first, by
  kind, searchable, with multi-select to export or delete. Each item leads back to the chat where it
  was born. The Toolbox is the shelf's view of apps.
- A skill written by the app teaches the agent what makes a good small app here: one self-contained
  file, no outside requests, made for a phone, in the look of the current theme.

**Taste gate in this wave:** the Canvas panel (the card, the half sheet and the full canvas, as
pictures side by side).

**Extras in this wave:** the wide layout with its divider (it comes back in Wave 12), the two doors
of an app (keeping an app without doors is core), network access for one item, the wipe between two
pictures, Run for shell.

| Agent | Owns |
|---|---|
| PROTOCOL | `data/canvas/CanvasBlocks.kt` (the two kinds for `StreamBlocks`), `data/canvas/CanvasEdit.kt` (hunks), the teaching text and its switch in `data/BotPresets.kt`, the references in `data/Transcript.kt`, "Open in Canvas", the practice model's `CANVAS` behaviour, tests |
| STORE | `data/canvas/CanvasStore.kt`, versions and thinning, the folder watch (documents and data only), `data/canvas/CanvasIndex.kt`, tests |
| PANEL | `ui/canvas/panel/*` (card, half sheet with its keyboard peek, full canvas, the wide layout, tabs, the bar, the scrubber), the route in `ui/Nav.kt` |
| DOCVIEW | `ui/canvas/doc/*` (reading layout, editor, select and ask, tracked changes) |
| CODEVIEW | `ui/canvas/code/*` (highlighting, differences, Run and the console), `hermes/GuestRun.kt` |
| WEBBOX | `ui/canvas/web/*` (the sandbox process, its activity and its policy, the pictures it hands to cards, page, app, drawing, diagram, the two doors bound to a version, the mathematics renderer), `assets/render/*`, this wave's manifest entries, the escape tests (run on the owner's build) |
| DATAVIEW | `ui/canvas/data/*` (the table grid, charts in the plain style and the pixel style), `data/canvas/ChartSpec.kt` |
| EXPORT | `data/canvas/Export*.kt`, the print adapters, pinned shortcuts, `ui/library/made/*` (the Made shelf and the Toolbox, added through the Library's registry of shelves), the small-app skill |

**Proven when:** on the practice model a document streams into the panel within the streaming budget;
an edit changes two lines and the scrubber shows both versions; a hunk that does not match is refused
and the resend works; with "Review changes" on, one hunk is accepted and one rejected; with the
keyboard open the sheet is a peek and the chat can be used. A page with a script runs in the sandbox
on the owner's build, and its eight ways of reaching the network all fail; an app keeps a value in
its store over a restart and cannot read another app's; an app whose code was changed has lost its
doors and asks again; a file a script drops into a page's folder does not become a version; a
question from an app starts a tainted turn. A diagram and a formula render in flight
mode; a table of 3,000 rows scrolls within budget; a document leaves as a PDF with page numbers; a
pinned app opens from the phone's home screen. With the real model "write a packing list for three
days" opens a document, "make day two shorter" comes back as an edit of a few lines, and that edit
costs less than a quarter of the first answer.

### 7.6 Wave 6, Create: documents, slides, pictures

**Goal.** The things people really hand to other people: a Word file, a deck, a spreadsheet, a PDF, a
picture. Made on the phone by the same libraries a desktop uses, looked at natively, and never paid
for without a question.

**6.1 Documents that become real files** (findings D1, D2, D3; experiment S20)
- The Linux image gets the document libraries, built where Hermes's office skill looks for them: the
  side environment that the skill would otherwise download and build on first use, with both extras
  (writing documents, and reading PDFs; finding D2). Built into the image it needs no network on the
  phone, and an update cannot delete it. The app calls the skill's scripts with that environment's
  interpreter, with a fixed list of arguments and files, never through a shell. S20 measures what
  this adds to the APK; the pass mark is 8 MB.
- **Export to Word.** The app turns a document item into the plain description the skill's script
  reads (headings, paragraphs with their runs, lists, tables, pictures, page size, header and footer)
  and runs the script in the guest. No model is called: exporting costs nothing. What the script
  cannot write as Word's own (mathematics, coloured code, footnotes, a diagram) goes in as a picture
  or as plain text, and the export says which.
- **Page setup:** paper by region (A4 or Letter), margins, three type pairings, header and footer, a
  cover page, a table of contents.
- **Import.** A Word file from the phone becomes a document item through the skill's reader (text,
  headings, tables, pictures); a note says what could not be carried over. The original stays as
  version 0. A file from outside is opened with care: an office file is an archive, so there is a
  limit on its size before and after unpacking, its XML is read without outside references, and what
  comes out of it is outside content for the gate (rule 1).
- **Review a document someone sent.** The agent's suggestions are written into the Word file as real
  comments and tracked changes, with the skill's own scripts, so that the file opens in Word with the
  changes waiting to be accepted or refused.
- **Your own template:** a Word file with placeholders (a letterhead, a report frame) is filled by the
  skill's template script.

**6.2 Slides** (findings D3, C14; experiment S22; design language, section 8.6)
- The `deck` kind. The agent writes the same description Hermes's script reads: the slide size and,
  per slide, a layout, a title, bullets, notes, pictures, tables, charts and shapes. The app **draws
  that description itself**, so a deck is on the screen while it is still being written, and no
  office program is needed on the phone.
- **Eight deck themes** to start with (Hearth, in the app's own pixel look; Paper; Night; Mono;
  Mountain; Blueprint; Bold; Pastel). A theme is a set of values (colours, type sizes, where the
  title sits, the accent shape, the chart colours) that is also written into the exported file.
  Charts, tables and pictures on a slide are drawn plainly in the deck's own colours; only Hearth
  uses the pixel chart. Each theme names two typefaces: the one the app draws with on the phone, and
  the one the exported file asks for, taken from those every office program has. Hearth draws its
  titles in the app's pixel font on the phone and exports them in a bold monospace, and the export
  sheet says so. Every theme's colours pass the contrast test of the design language.
- **Three views:** a strip of small slides; a light table to drag slides into a new order, duplicate
  and delete; one slide large with its notes underneath.
- **By hand:** tap a title or a bullet and type; change a slide's layout; swap a picture; edit a
  chart's numbers in the table grid; write notes.
- **By asking:** select one slide or several: Rewrite, Fewer words, Split in two, Add a picture (6.4),
  Add speaker notes, Make a chart of this. With "Review changes" on, changes are shown slide by slide,
  before against after.
- **From anything:** "Turn into slides" on a document, a research report or a chat. A question card
  asks how many slides, for whom, in which tone, with or without pictures.
- **Present.** Full screen; a swipe or the volume keys move on; a long press is a pointer. With a
  second screen connected by cable or wirelessly, the slides go there and the phone becomes the
  presenter's view: the current slide, the next one, the notes, a timer and the clock. If S22 finds
  that the phone cannot drive a second screen this way, the phone's ordinary screen mirroring shows
  the slides and the notes stay hidden.
- **Export:** PowerPoint through the skill's script (the same description, so what was shown is what
  is written); PDF and pictures drawn by the app; a handout with three slides per page.
- **Import:** a PowerPoint file becomes a deck through the skill's reader (texts, notes, tables,
  pictures; layouts approximated, and said so).
- Honest limit: line breaks can differ slightly between the app's drawing and PowerPoint. Texts are
  kept inside safe areas so that nothing overflows in either.

**6.3 Sheets**
- The `sheet` kind: the table grid of Wave 5 with several tabs, column types (text, number, date,
  money, tick), number formats, and simple formulas the app works out itself (sum, average, smallest,
  largest, count, arithmetic on cells). Anything harder is asked of the agent, who uses real Python.
- Excel files in and out through the skill's scripts. A chart from a selection becomes a chart item.
- **"Ask about this sheet"** never pours the sheet into the prompt: the agent gets the header and a
  sample and, for real questions, runs code on the file (Wave 7's data mode goes further).

**6.4 Pictures** (findings D6, C22; experiment S21; design language, section 8.7)
- `PictureService`: the app itself asks the owner's provider (4.7): Google's picture models with an
  AI Studio key or with the Vertex credentials, and picture endpoints of OpenAI's kind where the owner
  has such a key. One interface: make (words, size, how many) and change (a picture plus words).
  Hermes's own picture tool stays off the agent's list (0.10) and is refused by name at the gate
  (3.4), so this is the only road to a picture the agent has, and it leads past the price card.
- **The price comes first.** A wish for a picture, from the user (the tray, a slide's "Add a
  picture") or from the agent (a `picture` block), becomes a card: the words (editable), the model,
  the size, "about 4 cents", Create and Cancel. The default is to ask every time. The owner can open
  an allowance ("up to five pictures a day without asking"). Pictures have a daily budget of their
  own, separate from the token budget, and it starts at zero: at zero, every picture asks.
- **A stand-in that costs nothing.** The practice provider returns a drawn placeholder with the
  request's words on it, so every flow (card, gallery, slide, editing) is built and tested at zero
  cost. A real picture is made for the first time when the owner taps a price; S21 is that tap, and it
  happens only if the owner wants it (section 13). S21 also looks, without spending, for a route that
  is free; if one exists it becomes the default.
- **Changing a picture.** With words: the picture goes back to the model with an instruction; it has
  a price, shown first. On the phone and free: crop, turn, resize, flip, light and contrast, draw and
  write on it, blur a region, change format, compress, remove the hidden location, and **cut out a
  background** where it is simple (pick the background's colour, set the tolerance, clean up with an
  eraser). A cut-out that needs a model is the priced kind, and its card says so.
- **Pixelize** (on the phone, free): any picture becomes pixel art. Choose the grid (16 to 128), the
  palette (the current theme's, classic eight-bit palettes, or one taken from the picture), the
  dithering, an outline. The result serves as a sticker, a slide picture or a keepsake, and the
  16-pixel preset writes a bot's avatar in the app's own sprite format.
- **Stickers:** a picture with a transparent background, exported for messengers.
- **The Pictures shelf** of the Library: everything made, changed, pixelized or photographed for the
  agent; by date, chat and kind; each with its words and its price ("made for 4 cents on 3 May").
  From there: use as a bot's avatar, a chat's background, a slide picture, a space's cover.
- The ledger lists what went to the provider: the words, and any picture sent for changing. A picture's
  details say that it carries the provider's invisible mark.
- Honest limit: the price card governs what the app and the agent's tools can do. It cannot govern a
  command typed into the terminal with the owner's key in reach (section 5). The gate asks before
  commands in a tainted turn, and a spending cap at the provider is the hard stop (section 13).

**6.5 PDF tools** (findings D4, C11)
- Merge, split, reorder, turn and remove pages on a grid of pages; compress; protect with a password.
  Done by the library in the guest.
- **Fill a form by talking.** The agent proposes values from what the user says or from another
  document ("fill this from my CV"), the user checks field by field, the app writes them (by the
  platform on Android 15 and later, by the guest's library before).
- **Sign:** a signature drawn once and kept on the phone is stamped onto a page. The app says plainly
  that this is a picture of a signature and not a cryptographic one.

**6.6 The theme studio**
- An extra. Today the themes are a fixed list in code, so the studio first has to turn them into
  data; that is why its agent owns the theme files in this wave.
- The `theme` kind: the palette roles of the design tokens, the scene variant and the fire's colours.
  Three ways to make one: by hand with a live preview, from a picture (the colours are taken out on
  the phone), or by asking ("a harbour in the rain at dusk").
- A contrast check refuses pairs that cannot be read. A miniature of three screens previews it;
  Apply; share it as a postcard that carries the theme.
- Honest limit: a theme changes colours, not drawings. The scene is tinted through the table of 1.2.

**6.7 Recipes** (design language, section 8.8)
- A gallery of about forty ready-made tasks. Each card: a pixel illustration, what you get, a short
  form (question cards with three or four fields), what it needs, and what it usually costs.
- The groups: **Write** (reply to this mail, cover letter, summary, rewrite in another tone, translate
  and keep the layout, minutes), **Present** (a deck from a topic, a deck from a document, a pitch, a
  lesson), **Research** (compare options, explain at three levels, find and check a fact), **Study**
  (cards from a text, quiz me, a study plan), **Plan** (a trip, a week, meals with a shopping list, a
  budget), **Data** (analyse a file, chart a table, clean a list), **Make** (a small app, a diagram of
  a process, a poster), **Phone** (from Wave 9: the morning brief, the evening note, watch a page).
- **A recipe is a file.** Any first message of a chat can be saved as a recipe; recipes can be
  edited, shared as cards, pinned to Home, run from a routine or a widget, and tied to a share action
  (a shared link goes straight to "Summarise"). A recipe that arrives from a card is shown in full
  before it is saved, and the chat it starts is marked as imported (2.9).
- They appear in the slash palette and as three suggestions in an empty chat.

**6.8 More ways in**
- **A scanner:** corners set with a magnifier (automatic detection is tried and kept only if it is
  reliable), the page straightened and cleaned, several pages into one PDF. Reading the text of a scan
  is done by the model's eyes and costs tokens, said before; S20 also tries a text recogniser that
  runs inside the guest, as a free pack.
- **A sketch pad:** a full-screen drawing surface for the finger or the pen (pen, marker, eraser,
  shapes that snap, a pixel grid). "Draw what you mean": the sketch goes to the agent as a picture to
  become a diagram, a layout or a clean drawing.
- **Drag and drop** from another app in split screen: text, pictures and files onto a chat.
- The platform's own photo picker, which needs no permission.

**Two halves:** documents, slides, sheets and PDF tools first (0.65a); pictures, recipes and the new
ways in second.

**Taste gate in this wave:** the deck themes (the eight title slides side by side).

**Extras in this wave:** the theme studio, the sketch pad, filling forms and the signature,
stickers, your own Word template, the handout.

| Agent | Owns |
|---|---|
| OFFICE | `rootfs/*` (the side environment with the document libraries in the image, the smoke test, the size check), `hermes/OfficeScripts.kt` (calling the skill's scripts by a list of arguments), `data/office/DocSpec.kt` (Markdown to the description and back, and what goes in as a picture), import with its limits, review into a Word file, page setup |
| DECK | `data/office/DeckSpec.kt`, `ui/canvas/deck/*` (the renderer, the themes with their two typefaces, the three views, editing), the practice model's `DECK` behaviour |
| PRESENT | `ui/present/*` (full screen, the second display, the presenter's view), the app-drawn exports (PDF, pictures, handout) |
| SHEET | `ui/canvas/sheet/*`, `data/office/Formula.kt`, Excel in and out |
| PDFTOOLS | `ui/pdf/*`, `hermes/PdfTools.kt`, form filling, the signature |
| PICTURES | `data/pictures/*` (the service, the providers, the stand-in, allowance and budget), the price card, the `picture` block, the gate's entry for priced calls |
| STUDIO | `ui/pictures/*` (the Pictures shelf, the tools, the cut-out, Pixelize with the avatar preset, stickers); as the extra: `ui/theme/studio/*`, `data/ThemeSpec.kt`, and the change that turns themes into data (`ui/theme/Appearance.kt`, `Palettes.kt`, `PocketTheme.kt`) |
| RECIPES | `data/recipes/*`, `assets/recipes/*.json`, `ui/recipes/*`, the palette and empty-chat suggestions |
| INPUTS | `ui/scan/*`, `ui/sketch/*`, drag and drop and the photo picker in `ui/chat/input/*` |

**Proven when:** on the practice model a deck of ten slides streams in, is reordered, has one title
changed by hand and leaves as a PowerPoint file that opens on a computer (three decks looked at by
eye); presenting on a second screen shows slides there and notes here, or the fallback of S22; a Word
file survives export and import with its text unchanged, and a review leaves two comments that Word
shows; an Excel file survives the round trip; two PDFs are merged and a practice form is filled. The
whole picture flow runs on the stand-in at zero cost: the price card, Cancel that creates nothing,
Create that fills the Pictures shelf, the ledger line; with the picture budget at zero the agent's wish
always asks. A photo is pixelized on a 64 grid with the theme's palette; a theme taken from a picture
passes the contrast check; five recipes run; a scan of three pages becomes one PDF. With the real
model "five slides about the water cycle" gives a deck that exports. A deck in each theme opens on a
computer with typefaces that computer has. An office file that unpacks beyond the limit is refused.
The document libraries add less than 8 MB to the APK, and the APK is within its allowance.

### 7.7 Wave 7, Know: research, sources, your own files

**Goal.** Finding things out, and knowing where they came from: research that takes its time in the
background, sources you can check, the Library grown up, spaces with their own knowledge, real
analysis of data, and tools for learning.

**7.1 Deep research** (findings D5, D9; experiment S23; design language, section 8.9)
- A mode of a chat (from the chat controls, the palette or a recipe) in five steps:
  1. **Clarify.** One question card: the scope, the depth, the languages of the sources, the time
     span, sites to prefer or to leave out.
  2. **Plan.** The agent proposes the sub-questions and where it will look. The card shows the plan
     with an estimate of pages, minutes and money for the chosen depth: Quick (about 8 pages, a few
     minutes), Standard (about 20, ten minutes), Deep (about 50, up to forty minutes). The owner can
     edit the plan, then Start. The estimate is a ceiling: when it is reached, the run stops and asks.
     **Start is the consent** of 3.4: the card names what this research may use (search, reading
     pages, its own notes, so many pages, a ceiling in tokens and in money, its folder) and nothing
     else is granted. A page that tries to make a reader do anything else meets an ask, and with
     nobody there, a refusal that is listed in the report.
  3. **Work, in the background.** One run that hands the reading to sub-agents (at most three at
     once unless 0.12 allows more), on the cheaper model set for them. The configuration keeps the
     children inside the turn (`delegation.gateway_mode: inline`; for a turn that comes through the
     API they would otherwise be cut loose, finding D9) and lowers their limit from Hermes's 250
     steps. Readers need no terminal: notes and sources are written through contained bridge tools
     (`research_note`, `research_source`; 3.3), which never ask. Each returns notes with quotes and
     source numbers. Everything lies in a folder of the research
     (`/root/workspace/research/<id>/`: the plan, the notes, the sources, the report). If S23 shows
     that children cannot be followed or counted inside one run, the app runs each reader as a run
     of its own and gathers the notes itself: every reader is then an ordinary run with its own
     events, its own ceiling and its own Stop.
  4. **Cross-check.** Claims that stand on a single source are marked, contradictions are listed, and
     the most important numbers are looked for in a second source.
  5. **Write.** The report opens in the Canvas as a document: a summary first, numbered sources
     throughout, and a section "What I could not find out".
- **It survives everything.** The notes are files. A research that was cut off (the app killed, the
  phone restarted, the ceiling reached) continues from its folder with "Continue research", and no
  page is read or paid for twice.
- **The expedition view** replaces the spinner. The sub-questions are camps along a mountain path;
  a scout leaves the fire for each camp while its pages are read and comes back with a note, and the
  camp lights up. Counters show pages read, sources kept, minutes and money so far. A tap on a camp
  shows its notes. The notification shows the same in one line.
- **Steer while it works:** "also look at prices in Germany" adds a camp (3.1).
- **Slow keys.** On a provider that answers "too many requests" the readers slow down and take
  turns (4.12). The estimate on the plan card is made for the limits of the key in use, and says so.
- Each research keeps its own list of sources in its folder; the skill's scripts are pointed at that
  file for each call, not through a setting that only a restart could change.
- Questions after the report are answered from the notes, not by reading the web again.
- "From my files only" runs the same steps over a space's knowledge (7.6) without the web.

**7.2 Sources you can check** (findings D5, D7, D16; experiment S25)
- Answers carry numbered marks; the app draws them as small chips. A tap opens the source card: the
  title, the site, when it was fetched, the quoted evidence, Open, and "Read here" (7.3).
- **The mark.** For every cited address the app looks into its own record of fetched pages (3.7),
  by the address a page ended at as well as the one that was asked for:
  - **Opened** (a filled mark): the app received this page's text in this work.
  - **Quote matches:** a quote is given, and its words are in the text that came back.
  - **Not opened** (a hollow mark): the model cites an address the app never received here. It may
    be right from memory; nothing vouches for it.
  - **Quote not found** (a warning): the page was opened, and the quoted words are not in it.
- **What a mark says, and what it does not.** It says the page was read (by the outside service
  that fetches pages for the agent) and that the app holds the text. It does not say the page is
  right, and it cannot speak for a part of a long page that never arrived. The source card says so
  in one line.
- The Sources sheet under an answer lists all of them with their marks, and copies them as a
  reference list in three common formats.
- It works in every chat, not only in research: bots that search learn in three lines to cite with
  numbers (about 50 tokens, a switch); without that, every link in an answer still gets its mark.
- **Check this** (in an answer's menu): a small research that takes the answer's main claims, looks
  each one up and writes the result beside it: supported, disputed, or not found, each with its
  source. It costs like a Quick research and says so first.
- The list of sources is the app's own, built from the gate's record; the skill's list is a help
  for the agent, not the truth. If the citation skill proves unreliable on a cheap model (S25), the
  app reads numbers and addresses out of the text itself. The mark never depends on the model's
  honesty.

**7.3 The reader**
- "Read here" shows the text of a fetched page as the agent read it, with the cited passage marked:
  no scripts, no trackers, working offline. Text size, listen (Wave 8), "Ask about this page", save to
  the Library, and "Open in the browser" for the original.
- **Read later:** a link shared to Agirî can be saved; it is fetched once and kept as text. A summary
  is made when it is asked for, not before.
- **Videos:** a shared video link becomes a transcript where one exists (Hermes's own skill), and a
  summary whose time marks open the video at that second.

**7.4 The Library grows up**
- The Library is the one place for every thing (section 4.1). It has had two shelves since Wave 1,
  Made since Wave 5 and Pictures since Wave 6. This wave completes Files (attachments, shared
  things, saved pages, scans, exports) and adds the reading list, the inbox and the study cards;
  Wave 8 adds recordings. By kind and by space; searchable by name and by text.
- Viewers: PDF (pages, search, and on Android 15 and later selectable text), pictures, text and code,
  tables, Word and PowerPoint files (through the import of Wave 6), sound.
- **Quick actions on any file:** Summarise, Key points, Action items, Translate, Explain, Make slides,
  Make cards, Ask… Each is a recipe with the file attached.
- **The inbox.** Whatever is shared to Agirî without choosing an action lands here untouched. Nothing
  is sent to a model until the user asks. Screenshots get a shelf of their own; their text becomes
  searchable when the user asks for it, with the cost said first.
- A view of what the Library takes on the phone, and a clean-up.

**7.5 Spaces grow** (design language, section 8.10)
- Spaces have gathered chats, instructions and a folder since Wave 2 (2.10). Now a space also holds
  files as its knowledge (7.6), its canvas items, routines, a budget of its own, and a look: a
  cover, an accent colour and its own small camp in the scene.
- Inside a space the accent and the header change, a new chat inherits the instructions and the
  knowledge, and the chat list and the Library show only what belongs there.
- **A space can be given away** as one file (instructions, recipes, and files if wanted; chats only if
  chosen), and exported whole as a ZIP of plain files: work is never locked in. A space that comes
  in from a file is outside content: its instructions are shown before anything is saved, and its
  files keep that origin for the gate.
- Starters: a subject to study, a project at work, a trip, a piece of writing, household papers, code.

**7.6 Knowledge: answers from your own files** (finding C11; experiment S24)
- Per space, and one shelf for everything: files are cut into passages and indexed **on the phone**
  with the platform's own full-text search. No model, no cost, nothing leaves the phone.
- Text comes out of PDFs through the platform on Android 15 and later and through the guest's library
  before; out of office files through the skill's readers; out of text, code and tables directly.
- A bridge tool, `knowledge_search`, returns the best passages with their file and page. The agent
  cites them like pages of the web; the source card opens the file at that page with the passage
  marked, and the mark says "from your file".
- **Small spaces skip the search:** if all files of a space fit under a limit the owner sets, they can
  be read whole on request, with the cost shown.
- Honest limit: this finds words, not meanings. The tool takes several wordings at once, and the agent
  is told to try synonyms and both languages. Search by meaning needs a model on the phone and is a
  candidate for the offline pack of Wave 13.
- For the gate, whatever `knowledge_search` returns is private data (rule 2): after it, the ways out
  ask. A file also keeps its origin: what came from outside (a saved page, a shared file, a scan,
  an import) is outside content under rule 1 as well. Only what the owner typed in the app is the
  owner's own.

**7.7 Data mode: real analysis on the phone** (finding D12)
- A CSV or Excel file, then "Analyse". The agent works in a notebook: a code item made of cells that
  run in the guest with the data pack of 4.10, the output under each cell (text, tables as grids,
  charts drawn by the app). Cells can be run again, changed, added.
- **The data does not go to the model.** The agent sees the header, the column types and a sample of
  rows, writes code, and reads what the code printed. The card says it: "the model saw 20 of 48,000
  rows".
- Results become canvas items (a chart, a table, a report), and "Explain this chart" works on each.
- **One consent per notebook.** Running code is an effect, and a notebook runs code many times.
  Opening a notebook shows a card: which file, and that code will run in the agent's Linux inside
  the notebook's folder. Start grants that for this notebook (3.4), and its cells then run without
  asking. Honest limit: a cell is real code, and code can reach the network. The app reads each cell
  before it runs and asks when it sees the common ways of doing that; it cannot see the uncommon
  ones, and the card says so. A notebook on a file that came from outside starts with "ask before
  every cell" switched on.

**7.8 Study tools** (finding D10)
- **Cards.** From a text, a file, a chat or a research report the agent writes question-and-answer
  cards (with a hint and a source). Reviewing them needs no model and no network: the schedule of
  spaced repetition is worked out on the phone. Swipe for "knew it" and "did not"; reversed cards and
  cards with a gap; a "due today" count on Home.
- **Quiz me:** choice questions or open ones from any material. Open answers are judged by a cheap
  helper; the result names the weak spots and offers to make cards of them.
- **Explain at three levels** (new to it, student, expert), as a stack of versions (2.5).
- **Highlights:** a passage marked in the reader or in a PDF is kept with its source and can be asked
  about or turned into cards.
- Card decks leave and enter as plain text files that the common card programs read.

**Two halves:** deep research, sources and the reader first (0.7a); the Library, the knowledge of
spaces, data mode and the study tools second.

**Extras in this wave:** video transcripts, reading a small space whole, quizzes with open answers,
highlights, card decks in and out as files.

| Agent | Owns |
|---|---|
| RESEARCH | `data/research/*` (the mode, the plan as a grant, depths, ceilings, the folder, continuing, readers as children or as runs of their own), `bridge/tools/Research*.kt` (the contained tools for notes and sources), the instructions for the orchestrator and the readers (a skill the app writes), the delegation settings as a configuration fragment, the practice model's `RESEARCH` behaviour |
| EXPEDITION | `ui/research/*` (the plan card, the expedition view, the notification line), its sprites in `assets/pixel/expedition.pxl` |
| SOURCES | `data/sources/*` (reading citations, the app's own list of sources, the marks from `FetchRecord` by the address reached, reference lists), `ui/chat/sources/*` (chips, card, sheet), "Check this" |
| READER | `ui/reader/*`, read later, video transcripts |
| LIBRARY | `data/library/*`, `ui/library/*` (the shelves for files, the reading list and the inbox; viewers, quick actions, storage) |
| SPACES | `data/spaces/*`, `ui/spaces/*` (knowledge, items, routines, the budget, the look and the camp), giving away and exporting, a space from a file as outside content |
| KNOWLEDGE | `data/knowledge/*` (cutting, the index, text out of files, origins), `bridge/tools/Knowledge.kt` (its results are private data for the gate), citing files |
| DATA | `ui/canvas/notebook/*`, the cell runner on `hermes/GuestRun.kt`, the notebook's grant and the reading of cells before they run, sampling |
| STUDY | `data/study/*` (cards, the schedule, quizzes), `ui/study/*`, highlights, the `cards` block |

**Proven when:** on the practice model a Standard research with three practice readers works through
practice pages served inside the guest, is killed in the middle and continues without fetching a page
twice; the camps light up; the report opens in the Canvas with its chips. The same research runs
from Start to the report with the phone locked and nobody answering: not one ask appears, and a
practice page that orders a reader to save a skill is refused and listed. A cited page that was
opened has the filled mark and its quote matches; a page that redirected is still matched; an
invented address has the hollow mark; a wrong quote has the warning; "Check this" annotates a
practice answer. A turn that used `knowledge_search` and then wants the web asks. A saved page reads
offline. A PDF
added to a space is found by `knowledge_search` and cited with its page. A file of 48,000 rows is
analysed while the ledger shows that the model saw only the sample. Thirty cards are made and a day's
review works in flight mode. A space shows the cost of its instructions and survives being given away
to a wiped installation. With the real model one Quick research on a harmless question stays inside
its estimate with every source marked, and the memory peak is written down (S23).

### 7.8 Wave 8, Voice: talking, listening, and sound that stays on the phone

**Goal.** A conversation without hands and eyes that feels like sitting by a fire, not like dictation.
And around it everything a voice can do for its owner: interpret, take the minutes, read aloud,
explain a report on the way home, coach a talk.

**8.1 The conversation loop** (`voice/talk/TalkSession.kt`)
- A state machine: listening, thinking, speaking, listening again. Version 1 is half-duplex: the
  microphone is closed while Agirî speaks, and a tap interrupts.
- **Speech starts before the answer is finished.** Sentences are cut from the stream as they complete
  and queued to the voice engine (`VoiceEngine.enqueue`, new), so the first words are heard while the
  rest is still being written. Code and tables are not read aloud ("there is code on the screen").
- In talk mode one line is added to the system message: you are heard, not read; two or three short
  sentences unless asked for more; no lists, no Markdown. Better to listen to, and cheaper.
- Budget: from the end of your sentence to the first sound, under 2.5 seconds with the cheapest model.
  Every turn logs its own timeline (recognition, first token, first sentence, synthesis).

**8.2 The stage, "Fireside"** (design language, section 8.3)
- The full-screen scene: the flame's height follows the speaking voice, a ring of embers follows your
  own, Pêt listens and talks, captions can be shown. The bot can be changed on the stage (voice and
  character change with it).
- A talk is an ordinary chat that appears in the list with a mark, so nothing said is lost.
- Audio focus, wired and Bluetooth headsets, and the headset's button (talk and stop). The phone must
  be unlocked: an agent with hands is not offered on the lock screen.

**8.3 Listening on the device** (findings C7, C8, C18; experiment S26)
- One interface, two engines: the phone's recogniser (default, no download) and sherpa-onnx (microphone,
  Silero voice detection bundled at well under 1 MB, then a streaming model, or Whisper on the detected
  segment).
- A model store like the one for the voice. English comes first: a streaming model of the Zipformer
  family, which the embedded library's own examples use. Then multilingual Whisper tiny (about
  100 MB) or small (about 358 MB), streaming models for further languages where they exist (French;
  Turkish `seda-v0.1`, 73 MB), and a recogniser for 1,600 languages (365 MB) for the languages the
  others do not cover (finding C10). All of them are pinned and checksummed through the catalogue of
  4.10, resumable and deletable.
- The experiment records twenty sentences in English and twenty in Turkish once, and compares the
  engines on this phone. The settings then recommend from real numbers. A language that is not good
  enough on the device ships marked "experimental", and the phone's recogniser stays its default.
- Voice messages in ordinary chats: hold the microphone, release to turn speech into text.
- The embedded library can also tell which language is being spoken and put the punctuation back.
  Both are used wherever speech becomes text.

**8.4 Voices for many languages** (finding C10; experiment S28)
- **A catalogue of voices.** Besides the installed model (31 languages, ten voices) the app offers
  voices of the Piper family by language, and the small Kokoro and Kitten voices, each a download on
  request (20 to 80 MB) through the downloader of 4.10, each with its size, its checksum, its
  licence and a "remove". Some families speak through a separately licensed set of pronunciation
  data; it comes with the voice as a download, never inside the APK, and the screen names its
  licence too. No new library: the embedded one runs them all.
- A voice can be set per language ("for Spanish, use this one"), and the language of a text is
  recognised from its letters and its commonest words, so a Spanish answer is read in Spanish and the
  next Turkish one in Turkish. Every bot keeps its own voice and mood as today.
- Voices under a non-commercial licence (several community voices are) show that licence before the
  download and are never packed into the APK.
- Nobody has heard these voices on this phone yet. In S28 the owner listens to the candidates for the
  languages they speak; those become "recommended", the others are marked "community voice".

**8.5 Interrupting by voice** (finding C9; experiment S27, a stretch goal)
- Only if this phone's echo canceller keeps the app's own voice out of the microphone. Otherwise the
  tap stays, and interrupting by voice is offered with headphones only (no echo to cancel there).

**8.6 The interpreter** (design language, section 8.11)
- Two people, two languages, one phone on the table. The screen is split in two halves, the far half
  turned around for the person opposite. Each sentence is heard on the phone, translated, shown large
  and spoken in the other language.
- The phone tells by itself which of the two languages was spoken. In a loud place each side has a
  hold-to-talk button and a keyboard.
- Listening and speaking stay on the phone. Only the sentence's text goes to the model for the
  translation (a direct call of 4.7, about a hundred tokens a sentence), and the ledger says so.
- The exchange is kept as a chat. Phrases can be pinned to a small phrasebook that works offline.
- Budget: under three seconds from the end of a sentence to the first sound.

**8.7 The recorder** (finding C16; experiment S30; design language, section 8.12)
- Records a meeting, a lecture or a note to oneself, with the screen off, from a notification that
  shows the time and Stop. The sound never leaves the phone.
- **It writes while it listens** (live captions, optional), and after the end a second, more careful
  pass with the larger model writes the final text, on the charger if the recording was long.
- Speakers are told apart ("Speaker 1", "Speaker 2", renamed with a tap) and punctuation is restored.
- The transcript is a document linked to the sound: a tap on a sentence plays it; it can be searched,
  highlighted and corrected.
- **Then, on request and with the cost shown** (text only): a summary, the decisions, the action
  items, the open questions. Action items become tasks (Wave 9).
- A sound file from the phone can be brought in the same way.
- Keeping: the sound is stored compressed; a setting deletes it once the text exists.
- Every start shows one line: tell people that you are recording.
- S30 measures an hour of speech: the time to write it, the battery, the heat.

**8.8 The player, reading aloud, and audio overviews**
- **One player** for everything longer than an answer: a small bar in the app, controls on the lock
  screen and the headset through the platform's own media session (`android.media.session`, no
  library), speed, fifteen seconds back, a
  sleep timer, a queue. Sound started from the background fails silently on Android 17 (finding C4),
  so playback lives in the foreground service.
- **Read this to me:** any document, saved page, report or PDF is spoken by the phone's own voice,
  made ahead in pieces and kept as a sound file, so it also plays offline.
- **Audio overview.** From a research report, a file or a whole space the agent writes a script for
  two voices: one asks, one explains, for three, seven or fifteen minutes. The script is a document
  the user can read and change before a word is spoken. Then the phone speaks it with two different
  voices and keeps it as one sound file with chapters, which can be shared. The model is paid for the
  script only; the voices cost nothing.
- **Ask in the middle:** pause, ask about what was just said, hear the answer, go on.

**8.9 Narrated slides as a video** (an extra; needs Wave 6)
- A deck with speaker notes becomes a video file: the notes (or a narration the agent writes per
  slide) are spoken by the phone's voice, the slides are drawn by the deck renderer, and the
  platform's own encoder writes an MP4. A notification shows the progress. With notes already there,
  no model is needed at all.

**8.10 Rehearse** (an extra; needs Wave 6)
- Present mode with the microphone on. The phone writes down what is said per slide and afterwards
  shows a coach's card: the time per slide against the plan, the pace in words per minute, filler
  words counted, slides that were read out word for word, slides that were rushed.
- "Ask for advice" sends the text and the notes to the agent, with the cost shown. Until then nothing
  of the rehearsal leaves the phone.

**8.11 Story mode**
- "Tell me a story", with whichever bot tells stories (the storyteller among the starter bots, if
  none was chosen): longer answers, the crackle loop, and a sleep timer (15, 30, 60
  minutes) that fades the voice and lets the fire go out. The next night the story goes on where it
  stopped, because the chat remembers.

**8.12 Doors and reading aloud**
- The microphone on Home, a "Talk" quick-settings tile, a launcher shortcut, a link.
- In ordinary chats reading aloud uses the same queue: it starts while the answer streams, the spoken
  sentence is highlighted, a tap on a sentence jumps to it.

**8.13 Live look** (finding C12; experiment S29)
- **The camera.** "Look at this" on the Fireside stage opens the phone's own camera for one photo,
  which travels with the next question: one picture per question, never a video stream, and the
  stage says "1 picture sent". There is no viewfinder inside the app: it would need the camera
  permission, and declaring that permission would break the photo the app takes without one today.
- **The screen.** "Look at my screen": Android asks for consent, one frame is taken and goes with the
  question. S29 tries whether one consent may cover a short session of several looks under a visible
  "Agirî can see your screen · Stop" notification; if not, every look asks.
- The answer is spoken, or arrives as a notification when another app is in front.
- For the gate a frame of the screen is outside content and may hold private data: such a turn asks
  before every effect and before every way out (rules 1 and 2). The first use explains that the
  picture goes to the model's provider; the ledger lists every frame.

**8.14 Live voice through the provider** (an extra; experiment S45)
- For those who want the quick back-and-forth of the big apps' voice modes and accept what it costs:
  the talk can run through the provider's own live interface, where one model hears and speaks. The
  sound then leaves the phone and the provider bills it. Both are said on a card before the first
  use; it is off by default and never the only way to talk.
- It sits behind the same stage and is a socket client written with the platform's own parts (no new
  library). The ledger and the budget count it like any other direct call (4.7); the agent's tools
  are not offered in it.
- S45 tries it against a practice server first and, only if the owner agrees to a price shown
  beforehand, for one minute against the provider.

**Two halves:** the talk, the stage, listening, the voices, story mode and the doors first (0.75a);
the interpreter, the recorder, the player with overviews and the look second.

**Extras in this wave:** narrated slides as a video, Rehearse, live voice through the provider,
interrupting by voice, the look at the screen (the photo is core).

| Agent | Owns |
|---|---|
| TALK | `voice/talk/*` (the state machine, the spoken style, the per-turn timeline), tests; as the extra `voice/live/*` (the provider's live interface and its socket client) |
| STAGEUI | `ui/voice/*` (the stage, captions, the look button) |
| LISTEN | `voice/listen/*` (both engines, voice detection, language identification, punctuation, a file-fed engine for tests), `voice/SpeechModelStore.kt`, its settings |
| AUDIO | `voice/VoiceEngine.kt` (queue, loudness envelope), audio focus, `voice/player/*` (the player and the media session), the new cues |
| VOICES | `voice/catalogue/*` (the voice families, the catalogue, a voice per language), `voice/TtsTextCleaner.kt` (language recognition), `ui/settings/VoiceSoundSection.kt` |
| INTERPRET | `voice/interpret/*`, `ui/interpret/*` |
| RECORDER | `voice/record/*` (the service, the file, both passes, speakers), `ui/record/*`, the transcript item |
| OVERVIEW | `voice/overview/*` (read to me, the script for two voices, synthesis, chapters), `media/VideoWriter.kt` (narrated slides), `ui/present/Rehearse*.kt` (the coach) |
| STORY | story mode, the sleep timer, the highlight while reading aloud; the tile, the shortcut and the link (`res/xml/shortcuts.xml`, `AppLinks.kt`); every manifest entry of this wave, from the contract's list (`AndroidManifest.xml`); `voice/look/*` (the photo through the phone's camera, the screen frame and its consent) |

**Proven when:** the robot runs a whole talk on the practice model with a recorded sentence fed in as
the microphone; the owner talks for two minutes and the logged latencies meet the budget
(`docs/perf/w8-voice.md`). A practice interpreter session between recorded English and Turkish
sentences speaks both ways. A ten-minute sound file fed in as the microphone becomes a transcript with
two speakers and its action items, and the ledger shows that only text left the phone; an hour's
recording with the screen off survives, and its numbers are written down (S30). An audio overview of
a practice report is spoken by two voices and plays from the lock screen in flight mode. A deck of
five slides becomes a video that plays; a rehearsal of three slides produces the coach's card. A look
through the camera sends exactly one picture; a look at the screen asks as S29 decided. Three voices
of the catalogue are installed, heard and removed. Starting and ending any of it leaves no microphone
open and no audio focus held.

### 7.9 Wave 9, Hands: the agent uses the phone

**Goal.** The step from "an agent on a phone" to "an agent that can use the phone", without ever
surprising its owner. The door and the gate have existed since Wave 3; this wave puts the phone's
powers behind them, and gives the agent work to do while its owner sleeps.

**9.1 The abilities** (finding B7)

| Tier | Default | Abilities |
|---|---|---|
| 0: stays inside the app | Allow; never asks | `phone_status` (battery, charging, network, storage, time), `show_file`, `ask_user` (these three since Wave 3), `vibrate`, `flashlight`, `theme_set`, `task_list` |
| 1: shows or says something to the owner | Allow; asks under rule 1 | `notify`, `speak`, `clipboard_write`, `task_add`, `task_done` |
| 2: opens or changes something | Ask | `open_url`, `open_app`, `share`, `set_alarm`, `set_timer`, `reminder_set`, `calendar_add`, `compose_email`, `compose_message`, `navigate_to`, `take_photo`, `pick_file`, `routine_create`, `routine_update`, `routine_delete`, `bot_create` |
| 3: reads private data | Ask, and Android's own permission on first use | `calendar_read`, `contacts_find`, `location`, `clipboard_read` (works only while the app is in front) |
| Has a price | Ask, unless inside an allowance the owner set | `picture_create` (Wave 6) |
| Never offered | | Calls, SMS, payments, sending anything in the owner's name, reading messages or notifications, changing system settings, operating other apps |

- Eleven abilities of tier 2 open a screen (`open_url`, `open_app`, `share`, `set_alarm`, `set_timer`,
  `calendar_add`, `compose_email`, `compose_message`, `navigate_to`, `take_photo`, `pick_file`). That
  works directly while the app is in front. From the background Android does not let an app open a
  screen, so the ability posts a notification ("Tap to set the timer") and tells the agent that it is
  waiting for the owner.
- **Drafts, never sends.** `compose_email`, `compose_message`, `calendar_add` and `navigate_to` open
  Android's own app with everything filled in. The owner presses the button. Nothing is ever sent by
  the agent.
- Permissions this wave adds, each requested only when its ability is first switched on: reading the
  calendar, reading contacts, the location. `calendar_add` goes through the calendar app's own editor
  and needs none.
- **Thrift.** Abilities that are rarely used are left to Hermes's tool search instead of being spelled
  out in every prompt; the Hands section shows what the enabled abilities cost per message.
- **Result cards.** What an ability returns is shown as one of the native cards of 2.3: a place, an
  event, a task, a timer, a contact.

**9.2 Hands, a section of Abilities**
- The phone's powers do not get a screen of their own. They are a section of Abilities (section
  4.1), under the toolsets Hermes brings. Per ability: what it does, its switch (Off, Ask, Allow,
  and "allowed in routines"), the state of the Android permission with a button, the last use, and
  what it adds to the prompt.
- One master switch, and "Hands off" in the running notification that turns all of it off at once.
  The rules, the log and the waiting room stay under Safety and privacy, one tap away.

**9.3 Tasks and reminders**
- A list of the app's own: a task has a text, perhaps a time, a repeat, a note, and the chat or space
  it came from. "Remind me to call the dentist at five" becomes a reminder after one Allow (with
  "always allow" on the card). Reminders fire as notifications with Done and "In an hour", also when
  the app is dead.
- Tasks also come from elsewhere: the action items of a recording (Wave 8), the open questions of a
  research, something shared with "remind me of this".
- "Due today" on Home, and later as a widget.
- **The focus log.** A log put on the fire burns for twenty-five minutes (or as long as chosen). While
  it burns, Agirî's own notifications wait; when it has burnt down, Pêt says so. No model, no cost.

**9.4 Routines that start on events** (finding C6; experiment S31)
- `RoutineTrigger`: time (as today), charging started, charging ended, a chosen Bluetooth device
  connected, Wi-Fi joined, a notification from chosen apps, "share to this routine" in the share sheet,
  a link with a per-routine key (for automation apps, NFC tags, QR codes), and from Wave 10 a
  widget button.
- "Charging ended" has no signal of its own that reaches a sleeping app. It is built from one that
  does: when charging starts, the app arms a small foreground service that waits for the cable to be
  pulled. While the phone is on the charger that costs no battery.
- Speaking from the background: Android 17 lets sound start from the background only in some cases
  (finding C4), so a routine that speaks is part of S31. If Android refuses, the routine posts its
  result with a Play button instead.
- A routine started by a notification or by something shared begins tainted (rule 1): whatever it
  wants to do beyond answering, it asks. The notification trigger is not an ability of the agent: the
  owner picks the apps, the app reads their notifications, and the agent cannot ask for others. On
  Android 13 and later a sideloaded app must be allowed "restricted settings" before it can be given
  that access; the first use explains the two steps, and S31 tries them on this phone.
- Limits per routine: at most once per N minutes, quiet hours, only while charging, light mind, which
  abilities it may use (they must be on Allow), one retry. A history of the last twenty runs with
  status and cost.
- New ready-made routines: the morning brief on unplugging (9.6), "in the car"
  (the car's Bluetooth connects, today's agenda is read aloud), the evening note, the battery guard,
  "keep what I share".
- The agent can propose a routine itself (`routine_create`): a card shows it in plain words with Allow,
  Edit and Deny.
- **Watch a page.** A routine of its own kind: the app itself fetches a page or a feed on a schedule
  and compares it with the last time **without any model**. The fetch is narrow on purpose: http and
  https only, a size limit, and no address inside the phone or a private network, checked after the
  name is looked up and again at every redirect, so that a watch cannot be turned into a probe of
  the owner's home network. Only when the page changed in the watched way (anything at all, a number
  passing a line, a phrase appearing such as "in stock") is the agent woken to write one line and
  notify, in a turn that starts tainted, because what woke it came from outside. While nothing
  changes it costs nothing. At most twenty watches, none more often than every half hour.
- **A routine can run a recipe** (6.7), and its result can go to a chat, a notification, a canvas
  item or a spoken note.
- **Doors for other apps.** The link with a per-routine key works from an automation app (Tasker, or
  the one a phone maker ships: on this phone, Samsung's Modes and Routines), from an NFC tag and
  from a QR code. Such a key can start a routine; it can never fetch an answer. A second door
  answers with text ("ask and return"). It can be reached only as a screen started for a result, so
  that Android tells the app who is calling, and the caller's signing certificate is pinned when the
  owner approves it. What the caller sends is outside content, the answer is a way out (rule 2), and
  it is given by a light mind without tools unless the owner chose otherwise.

**9.5 Night shift** (finding C19; design language, section 6.6)
- "Do it tonight" on any long task: a Deep research, a large document, a batch of summaries, the
  weekly review. The jobs wait in a queue the owner can see and reorder.
- The queue runs when the phone is charging, inside the night's hours (01:00 to 06:00 unless changed),
  on Wi-Fi if wanted, in a foreground service (not a background job, which Android may cut short),
  one job after the other, under a ceiling of money for the night.
- **Nobody is woken, and nothing is left hanging.** A job says what it will need at the moment it is
  queued (the consent of 3.4), so its ordinary work runs without a question. What would still ask
  at three in the morning cannot simply wait, because Hermes gives up on an unanswered question: the
  gate refuses it at once with "held for the morning" and writes down the tool and its arguments as
  a proposal. In the morning a night report says what was done, what waits for an OK, and what it
  cost; an OK runs the proposal then.
- If the night ends before the queue does, the rest stays for the next night.

**9.6 The morning brief**
- A card (spoken if wanted) when the phone leaves the charger in the morning, or at a set time: the
  weather (1.3), today's calendar (if that ability is allowed), the tasks due, what the night shift
  brought, what the watches found.
- It is **built without a model**, from what the app already knows. One sentence by a cheap helper can
  be added (a switch, off at first). So the brief costs nothing.

**9.7 A browser for the agent** (finding B8; experiment S32)
- The agent's browser is one WebView in a process of its own (`:browser`) with its own data folder, so
  its cookies are separate from everything else in the app, and whatever is switched on for it is on
  in that process only. The owner can open it as a screen ("Agent browser"), watch it live as a small
  picture in the chat, and sign in to a site by hand.
- Plan A: the app opens that WebView's DevTools socket and relays it to a loopback port.
  `BROWSER_CDP_URL` points there and the browser toolset is switched back on. Hermes's own browser
  tool then drives a real Chromium that needs no download.
- With plan A the tool talks to the page directly and passes no rule on its way, so the rules sit in
  the WebView itself: every page load passes one check in the app before a byte is fetched. The first
  visit to a site in a turn asks, and every load taints the turn.
- Plan B, if Hermes's tool cannot drive a WebView: five bridge tools on the same WebView (`web_open`,
  `web_read`, `web_click`, `web_type`, `web_screenshot`), which pass the gate like any other ability.
- Either way downloads land in the workspace (`/root/workspace/downloads/`).

**If it must ship in two halves:** the abilities, the Hands section, tasks and the triggers first;
watching pages, the night shift, the brief and the browser second.

**Extras in this wave:** driving the browser through Hermes's own tool (plan A; reading and clicking
through the bridge is core), the focus log, "ask and return" for other apps, the Bluetooth and Wi-Fi
triggers, speaking from the background.

| Agent | Owns |
|---|---|
| TOOLS | `bridge/tools/*` (all tiers), the permission requests, the notification for abilities that open a screen, the drafts |
| HANDSUI | the Hands section of Abilities (`ui/hermes/Hands*.kt`), the ability cards in `ui/chat/blocks/cards/*` |
| TASKS | `data/tasks/*`, `ui/tasks/*`, reminders and their alarms, the focus log |
| TRIGGERS | `routines/triggers/*`, the receivers, the jobs and the waiting service, the two doors for other apps (the keyed link, and the screen that answers a pinned caller), every manifest entry of this wave (`AndroidManifest.xml`) |
| ROUTINES2 | `routines/*` except `routines/triggers/` (limits, history, templates, recipes in routines), `routines/watch/*` (the app's own narrow fetch, the comparison), `ui/routines/*` |
| NIGHT | `service/NightShift.kt`, `data/night/*` (the queue, the ceiling, each job's grant, the proposals held for the morning), `ui/night/*` (the queue and the report) |
| BRIEF | `data/brief/*`, `ui/brief/*`, its trigger |
| BROWSER | `bridge/browser/*`, `ui/browser/*` (the `:browser` process) |

**Proven when:** on the practice model the robot sees a notification made by the agent, an ask card
answered with Allow and with Deny, the log entries, and a "tap to open" notification from the
background. A practice page that orders a notification and a clipboard write meets an ask every time;
a turn that read the calendar asks before a web search. A link trigger, a charging trigger and an
unplugging trigger start their routines with the app dead. A watch on a practice page stays silent
while nothing changes and notifies when a number passes its line, with no model call before that. A
night queue of two practice jobs runs with the app dead while the phone charges, holds one effect for
the morning and writes its report. The brief is built in flight mode. A reminder set by the practice
model fires with the app dead. The agent browser opens a page and reads its title. A watch aimed at
an address inside the home network is refused, also when a redirect leads there; "ask and return"
from an app that was not approved gets nothing; the effect the night held runs after the morning's
OK and not before. With the real model
"set a timer for ten minutes" asks once and the timer exists.

### 7.10 Wave 10, Everywhere: at home on the phone

**Goal.** Agirî is where the owner already looks: on the home screen, behind it, in the notification
shade, in the share sheet, in the text menu of every app, in the phone's file picker, and one gesture
away over anything.

**10.1 Widgets** (finding C5; experiment S33)
- Classic widgets (`RemoteViews`, no new library), drawn by the same `SceneRenderer` as the app, so they show the
  app's own art in the app's own theme:
  - **Hearth** (2 by 2, stretches to 4 by 2): the sky of this moment, the fire as it is (asleep, lit,
    working), Pêt (holding a scroll when a routine's result waits unread), the woodpile. A tap opens
    the app; a tap on the fire lights it or puts it out. The flame flickers through four prepared
    frames that the launcher cycles by itself (a `ViewFlipper`), without waking the app.
  - **Ask** (4 by 1): "Ask Agirî…", a microphone, the bot used last. Opens the quick question (10.2)
    or the talk of Wave 8.
  - **Results** (4 by 2): the latest routine results and answers as a list, unread ones marked; a tap
    opens that chat.
  - **Bots** (4 by 1): the avatars; a tap starts or continues the chat with that bot.
  - **Routine button** (1 by 1): runs one routine and shows idle, running, done.
  - **Pêt** (1 by 1): only the newt, living on the home screen: asleep at night, busy when the agent
    works. A tap makes Pêt say something.
- Pictures are drawn at a whole multiple of the pixel grid for the widget's real size, so they stay
  sharp. They are redrawn when the state changes, and every thirty minutes for the sky.
- A debug-only "widget gallery" screen shows every widget inside the app (the same remote views,
  applied locally), so the robot can photograph and tap them without a launcher.
- Four more, each on a feature of an earlier wave: **Tasks** (due today, ticked off in place),
  **Brief** (the morning brief of 9.6), **Card of the day** (one study card that is due; a tap turns
  it), and **Pinned** (a picture of any canvas item, a chart or a table for instance, redrawn when the
  item changes).

**10.2 The quick question**
- A small translucent activity that rises as a sheet over whatever app is open: the field with the
  keyboard up, the microphone, a bot switch. The answer streams into the sheet in the open layout, with
  Copy, Speak and "Continue in Agirî".
- Doors: the Ask widget, an "Ask" quick-settings tile, a launcher shortcut, the text-selection menu
  (which today opens the whole app), a link, and the assistant gesture or the side button (10.7).
- It is an ordinary chat marked `quick`, kept under a "Quick questions" label and archived after seven
  days unless it was continued. It uses a light mind by default (fast and cheap; needs Wave 4) with a
  switch to the full agent.
- If Hermes sleeps, the sheet shows the fire being lit and delivers the question, as wake-on-send does
  today.

**10.3 Writing tools in every app** (finding C13; experiment S39)
- Text selected in any app gets Agirî in its menu. On text that can be edited: **Rewrite**, which
  opens a small sheet with the choices (Improve, Shorter, Friendlier, More formal, Fix the spelling,
  Translate), shows the result with its differences, and on "Replace" puts it back where the
  selection was. On text that can only be read: Explain, Summarise, Translate, and "Save to the
  Library".
- One or two entries in the menu and the choices inside the sheet, because One UI may hide a long
  list (S39 finds out how many it shows).
- The work is done by the cheap helper of 4.6: about a hundred tokens more than the text itself. Every
  use stands in the ledger.
- Putting text back needs a screen that Android started for a result. Today's entry in the text menu
  opens the whole app, which is a single instance and cannot hand anything back. So the menu gets a
  small screen of its own (`ProcessTextActivity`), and today's entry is retired.

**10.4 Share actions**
- Sharing anything to Agirî (text, a link, pictures, files) opens a compact sheet with what was shared
  and the actions as chips: Ask…, Summarise, Translate, Save to the Library, Add to a space, Read
  later, Remind me, Run a recipe. The action used last for that kind of thing comes first.
- "Open with Agirî" for text, Markdown, PDF, office files and pictures; several files at once.

**10.5 Live progress** (finding C3; experiment S34)
- While a turn or a routine works and the app is not in front, the running notification shows the
  current step from the timeline ("Reading a page", "2 of 4"), the time, and Stop. If S34 passes it is
  a Live Update: a chip in the status bar and, on phones that have one, the maker's bar on the lock
  screen (Samsung's Now Bar on this phone). Otherwise it is an
  ordinary progress notification, updated at most once a second.
- It ends by becoming the reply notification that exists today.

**10.6 Chats as conversations** (experiment S35)
- Reply notifications become conversation notifications: the bot as a person with its pixel avatar,
  tied to a long-lived shortcut per chat. That alone gives the "Conversations" area of the shade,
  priority settings per chat, and **Direct Share**: the chats used most appear in the share sheet of
  every app, so a link goes straight to the chat with Coder.
- Bubbles (a chat floating over other apps), if S35 passes.
- "Add to home screen" for a bot, a chat or a routine (and a space): a pinned shortcut with
  its pixel icon.

**10.7 The assistant gesture** (finding C3; experiment S36)
- If Android accepts Agirî as the phone's digital assistant, the corner swipe or a long press of the
  side button opens the quick question (or the talk; the owner chooses). "Ask about this screen" then
  becomes possible: Android hands an assistant the text on screen and a screenshot, but only if the
  owner allowed that in the system settings. The sheet shows a chip for each, and nothing is sent
  until Send is pressed.
- A fallback that needs nothing from Android: an optional second launcher entry, "Ask Agirî", which
  a phone's own button settings can open (on this phone, a double press of the side button).

**10.8 The living wallpaper and the bedside clock** (experiment S38; design language, section 6.7)
- **A live wallpaper:** the scene behind the phone's home screen, drawn by the same renderer as the
  app. The real sky of the hour, the weather, the fire as it is (asleep, lit, working), Pêt. It shifts
  a little as the home pages are swiped. It moves for a few seconds after unlocking and then holds
  still, redrawing once a minute; it draws nothing at all while it is hidden.
- **A bedside clock:** Android's screen saver while the phone charges. The fire low, the real stars
  turning slowly, a pixel clock, the next alarm or task, and one quiet line when the night shift is
  at work. Everything drifts by a few pixels so that nothing burns into the screen.
- If One UI does not let a third-party wallpaper or screen saver be chosen (S38): the scene of the
  hour is set as an ordinary wallpaper picture and refreshed by the app, and the clock becomes a
  "bedside mode" screen inside the app that stays on while charging.

**10.9 The workspace in the phone's file picker**
- "Agirî" appears as a place in Android's Files app and in every app's open and save dialogs (a
  `DocumentsProvider`). It
  shows the workspace: what the agent made, the research folders, the Library. A file the agent made
  can be attached to a mail from the mail app; a file from another app can be saved straight into a
  space.
- Only the workspace is shown. Hermes's own folder, the configuration and anything the backup filter
  treats as a secret can never be reached this way (the same path check as in 2.7, with a test for
  every way around it). Other apps may read by default; "let other apps save here" is a switch.
- Three things hold whatever that switch says. The folder of canvas items can be read from outside
  but never written, so that no other app can change the code of a page or an app. A file whose
  content holds one of the app's known secrets is not handed out (the second check of 2.7). And what
  another app saves into the workspace is outside content for the gate.

**10.10 Updates from inside the app** (finding C1; experiment S37)
- About > "Check for updates" reads the public release list of the repository the build was made
  from: one anonymous request with nothing about the user in it. The address is a value of the
  build, not text in the code, so a copy of the project updates from its own releases (and which
  address the owner's builds name is one of the decisions of section 13). Only releases that carry
  Agirî's own tag pattern count (the repository also holds releases of other projects). A weekly
  automatic check is a switch, off by default.
- The app shows what is new, downloads with the shared downloader, and offers the file only if all of
  this holds: the SHA-256 published with the release matches, the package is the app's own, the version
  is newer than the installed one, and the file is signed with the same key as the installed app. That
  last check means something because Wave 0 moved the owner's build to a key that is not public.
- It needs the permission to request installs and, once, the owner's "allow from this source" in
  Android's settings; the first check explains both.
- Before installing it makes a local backup. Installing always shows Android's own confirmation (C1).
  After the restart, the "What's new" note.

**10.11 Backups, and moving to a new phone**
- A scheduled local backup: weekly, into a folder the owner picks once, the last four kept. Format 2
  adds what the waves added (section 9). No cloud.
- **A backup can be locked** with a passphrase, with the platform's own cryptography: the key is
  stretched from the passphrase (PBKDF2 with SHA-256, 600,000 rounds or more, a random salt), the
  file is sealed piece by piece with AES-256-GCM, and its header is part of what is sealed, so a
  changed file is refused instead of half restored. Only a locked backup may carry the credentials,
  and only if the owner ticks it. There is no way to recover a forgotten passphrase, and the screen
  says so.
- **Moving to a new phone,** with nobody in between. Both phones on the same Wi-Fi; the new one
  finds the old one on the network. Each makes a key pair for this one move, and both screens then
  show the same short code, worked out from the fingerprints of the two keys. The owner compares
  them: that is what keeps a stranger on the same network out. Only after that does anything travel,
  encrypted from phone to phone: chats, memory, the workspace, the Library, voices, and the
  credentials if ticked. An interrupted move goes on where it stopped. Where the two phones cannot
  see each other, a locked backup file carried over by cable does the same.

**Two halves:** widgets, the quick question, writing tools, share actions, live progress and
conversations first (0.85a); the assistant gesture, the file picker, updates and backups second.

**Extras in this wave:** the living wallpaper and the bedside clock, the move from phone to phone
(the locked backup file is core), bubbles, the four later widgets (Tasks, Brief, Card of the day,
Pinned).

| Agent | Owns |
|---|---|
| WIDGETS | new `widget/*` (providers, drawing, the configuration screen, the debug gallery), the app's first XML layouts (`res/layout/widget_*`, `res/xml/widget_*`) |
| QUICK | new `ui/quick/*`, `QuickAskActivity.kt` |
| WRITE | new `ui/process/*` (the entries of the text menu, the rewrite sheet, putting text back), `ProcessTextActivity.kt` |
| SURFACE | `AndroidManifest.xml` (every entry of this wave, by the names the contract fixes), `AppLinks.kt`, `res/xml/shortcuts.xml`, the share sheet and its actions (`ui/share/*`), pinned shortcuts, the Ask tile, open-with and multi-share intake |
| LIVE | `service/LiveProgress.kt`, `service/ReplyNotifier.kt`, `service/NotificationGroups.kt` (conversation style, bubbles) |
| ASSIST | new `assist/*` (the assistant services, or the fallback entry) |
| AMBIENT | new `ambient/*` (the wallpaper service, the screen saver, bedside mode), their settings |
| DOCS | new `provider/WorkspaceProvider.kt` (read-only canvas folder, the secret check, the mark on what comes in), its path checks and their tests |
| UPDATE | new `update/*` (check, download, verify, install), the release address as a value of the build (`app/build.gradle.kts`), the About section |
| BACKUP | `data/BackupManager.kt` (format 2, the schedule, the lock and its sealing), new `move/*` (finding, the two keys and the code to compare, the transfer), `ui/settings/BackupSection.kt` |

**Proven when:** the robot photographs every widget in the gallery at dawn and at night; the owner
places the Hearth widget once and it changes within five seconds when the fire is lit from the tile;
the quick question is opened over another app from the tile, answered by the practice model and
continued in the app; a paragraph selected in another app is rewritten and replaced in place (or
handed back as S39 decided); a shared link is summarised from the share sheet; a shared link reaches a
chat through Direct Share; the results of S34 to S36 are written down with the path taken. The
wallpaper shows the sky of a pinned hour and draws no frame while another app is in front; the clock
runs an hour on the charger and its parts have moved. A file the agent made is opened from another app
through the phone's file picker, and the picker cannot be led to the configuration. Build N updates
itself to build N+1 with all data intact; a scheduled backup appears in the chosen folder while the
app's process is dead; a locked backup refuses a wrong passphrase and a file with one changed byte;
a move from the twin into a wiped installation shows the same code on both sides, carries nothing
before it is confirmed, and then carries chats, the workspace and the ticked credentials (two real
phones remain a check for the owner). Another app cannot write into a page's folder through the
picker, and a file that holds a known key is not handed out.

### 7.11 Wave 11, Memory: what it knows, who it is, and what stays private

**Goal.** The app remembers and keeps order, and its owner stays in charge of both: memory that can
be seen and corrected, everything findable, several minds on one question, bots worth collecting, a
past worth looking back on, and privacy that is more than a promise.

**11.1 Memory you can see** (finding B18)
- The memory screen becomes a list of entries instead of a text file: each with its size against
  Hermes's limit, when and in which chat it was written, edit and delete. Every change the app sees is
  snapshotted (the last fifty), so every change has a "what changed" view and an Undo.
- "Ask before remembering" (`memory.write_approval`): a write then waits as a card, "Agirî wants to
  remember: …", with Keep and Drop, in the chat and under Memory.
- "Tidy up": the model proposes merges and deletions of stale or double entries, the owner accepts
  them one by one, and the cost is shown before it runs. Never automatic.
- "What do you know about me?": the user profile as one readable card.
- **Forget this chat:** removes what was learnt in one chat (the app knows which entry was written
  where). **Bring your memory along:** a text exported from another assistant is turned into proposed
  entries that the owner keeps or drops one by one.
- The same history and Undo cover skills, whose review has been in place since Wave 3.

**11.2 Search everywhere**
- One field (pull down on Home, or the Chats tab) over chats (the index of Wave 2), canvas items,
  the Library and its files' text, sources that were read, memory entries, skills, tasks, recordings,
  study cards, routines, bots, recipes and settings.
- Results are grouped by kind and each jumps to its place with the shared-element motion. Several
  results can be selected and asked about in a new chat.

**11.3 The Council: several minds on one question**
- Group chats get ways of talking, chosen at the start or from the chat's controls:
  - **Round table** (today): each answers in turn.
  - **Debate:** two argue for a set number of rounds, a third sums up.
  - **Compare:** the same question to up to four bots, or to four **models**, side by side; the owner
    picks the answer the chat goes on with.
  - **Relay:** one drafts, one criticises, the first revises.
- Before it starts the cost is shown ("about 6 answers, about 14,000 tokens"), and light minds are the
  default for every member that needs no tools.
- On screen the members sit around a small fire in the top bar; the speaker's seat is lit.
- Two more ways of talking: **Consensus** (all answer, then one writes what they agree on and lists
  where they do not), and **Second opinion** on any single answer: one tap asks another model to
  check it, and its verdict is attached as a version (2.5).

**11.4 Bots 2.0**
- Per bot: a greeting, starter questions (chips in an empty chat), knowledge files (as in spaces, with
  their cost), a default set of abilities, a default model and effort.
- A gallery of templates that can be tried before they are added, and figures per bot (messages,
  tokens, favourite hour).
- The gallery grows to about two dozen characters in groups (work, study, making, play, coaches), and
  **language partners**: a bot that talks in the language you are learning, corrects gently, and
  speaks with that language's voice (8.4).
- **A bot travels as a card:** a postcard with its avatar that carries the whole bot (2.9). A bot
  that arrives from a card is outside content: its prompt is shown in full before anything is saved,
  it arrives with every ability off and a light mind, and its chats say where it came from.

**11.5 The avatar forge** (an extra; experiment S40)
- A pixel editor for 16 by 16 avatars (the theme's palette, mirror, undo): the owner can draw a face.
- "Draw it for me": the model writes the sprite as text (a `pocket-avatar` block in the `.pxl` format,
  which is plain text), a validator checks size and palette, and the editor opens with the result. If
  the experiment shows that cheap models cannot draw, the forge builds faces from parts instead (shape,
  eyes, colours, one accessory), seeded by the bot's name.

**11.6 Collections**
- The Saved shelf of the Library grows into collections: starred messages, clipped passages, canvas
  items and files, sorted into named collections, each item with the way back to where it came from.
  A collection can be shared as one page.
- Two more tools on the bridge, `collection_add` and `collection_find`, so the agent can put things
  in and look things up.

**11.7 The journal and the recap** (the journal is an extra)
- The journal is optional and off at first. An evening routine writes a short page about the day from
  the titles of the day's chats and one line the owner may add by voice. Pages are plain files in a
  space of their own, shown in a calendar with the moon of each night, and "on this day" a year
  later. One light call per day, under a fixed ceiling.
- **The monthly recap** needs no model: what was made, read and learnt, the hours by the fire, the
  cost, built from the app's own counters. It can be shared as a postcard with the numbers the owner
  chooses.

**11.8 Keepsakes** (an extra; design language, section 6.8)
- Small pixel objects that appear in the scene for things that really happened, never for streaks:
  the first document, the first research, a study deck learnt to the end, a year together. A lantern
  at a camp, a book on a stone, a flag on the ridge. A tap says what it stands for and when; a shelf
  lists them all. A pixelized picture of one's own can be placed as a keepsake too.
- **Postcards that move:** the scene of the moment as a short looping picture, written by a small
  encoder of the app's own.

**11.9 Private chats and the lock** (finding C15)
- **A private chat** leaves nothing behind on the phone, and that has to be true store by store. It
  lives in memory only: no partial saves, no outbox, no entry in the record of running turns. It is
  not indexed and gets no title from a model. The gate's log keeps its decisions without their
  arguments; its ledger and its record of fetched pages die with it. No snapshot of files is taken.
  When it closes, its session in Hermes is deleted and Hermes's page cache is emptied. The screen
  cannot be captured, so the list of recent apps shows a blank card, and no notification ever shows
  its text. Memory and skills can be read in it but not written (the keeper refuses every lasting
  change). The fire burns in another colour while one is open.
- Honest limits, said once in the chat: the model's provider still receives the messages, and what
  it keeps is the provider's policy; pages read for it pass through the same outside services as
  always; a file the agent writes into the workspace stays until the owner deletes it; and Hermes's
  own log may hold a line per tool call until it rotates.
- **The lock:** the phone's own fingerprint or face dialog (the framework's `BiometricPrompt`, no
  library), on opening the app or only for chosen chats and spaces, again after a number of minutes
  away. The list of recent apps shows a blank card. A locked chat's notification says "New message"
  and nothing more.

**11.10 The privacy centre**
- The other half of Safety and privacy (3.8; section 4.1), in the same place. It answers two
  questions: what does this app know, and what did it send?
- **What is here:** a map of everything stored on the phone, with sizes: chats, memory, the Library,
  recordings, pictures, the ledger, logs. Each can be opened, exported or deleted.
- **What left:** the ledgers of Wave 3 summed by day and week: which providers got how many tokens,
  which search services, which sites, which connections, how many pictures.
- **Who can reach in:** the abilities and their switches, the connections, the doors.
- **Masking.** A list the owner fills (a name, an address, a number) and patterns the app recognises
  by itself (mail addresses, phone numbers, numbers shaped like cards or bank accounts). With masking
  on, each is replaced by a stand-in before a message leaves the phone and put back in the answer on
  the phone. A preview shows what will be sent. Honest limits: it masks what it recognises; it does
  not understand meaning; it does not look into pictures or files; and it covers what the app itself
  sends (the message, and the helper and direct calls). What Hermes adds to a request on its own
  (memory, the results of tools) is outside its reach unless S10 finds a hook at the last step
  before a request leaves. Until then the preview says so.
- **Export everything** (one ZIP of plain files), and **Erase everything** (asked twice).

**11.11 About you**
- A short form instead of a text file: what to call you, what you do, how you like answers (length,
  tone, language). It is saved into the profile Hermes already reads, shows what it adds to every
  message in tokens, and can be different per space.

**Extras in this wave:** the avatar forge, the journal, keepsakes and postcards that move, and of
the Council everything beyond Compare, Debate and Second opinion.

| Agent | Owns |
|---|---|
| MEMORY | `hermes/MemoryLog.kt` (who wrote what, and when), the memory screens under `ui/hermes/`, tidying, forgetting a chat, bringing memory along, the "About you" form |
| SEARCH | `data/SearchIndex.kt` (over the indexes of Waves 2, 5 and 7), new `ui/search/*` |
| COUNCIL | `data/Council.kt` (orders of speaking, the cost preview, the second opinion), new `ui/chat/council/*` |
| BOTS2 | `ui/bots/*`, `data/BotPresets.kt`, the new `Bot` fields, the gallery, bot cards |
| AVATAR | new `ui/pixel/editor/*`, `data/AvatarForge.kt` |
| KEEP | `data/Collections.kt`, the Saved shelf with its collections (`ui/library/saved/*`), the two bridge tools, the journal and its calendar, the recap, `data/Keepsakes.kt` and their sprites, `data/GifWriter.kt` |
| PRIVATE | private chats (`data/ChatRepository.kt`, `data/ChatEngine.kt`, and the switch every store of the list in 11.9 reads), the keeper's refusal, new `security/*` (the lock), locked notifications |
| PRIVACY | new `ui/safety/privacy/*` (the centre, as the second half of Safety and privacy), `data/Masking.kt` with its tests, export and erase of everything |

**Proven when:** on the practice model one search finds a word in a chat, in a memory entry, in a
canvas item and in a file of the Library; one waiting memory write is kept, one dropped, one undone;
a debate of two rounds runs and its real usage is within 20 % of the preview; a consensus of three
practice bots lists a disagreement; a drawn avatar survives a restart and a backup. A private chat
leaves no file, no index entry, no run record, no snapshot, no ledger and no session behind, and a
word said in it is found nowhere under the app's folder afterwards; the lock holds, and a locked
chat's notification shows no text. A bot that arrives from a card comes with its abilities off and
shows its prompt first. With masking on, a practice message with a mail address and a private word
reaches the
practice model with stand-ins, and the answer on screen shows the originals. The privacy centre's
week equals the ledgers it was summed from; "Export everything" gives a ZIP whose chats open as plain
text. A keepsake appears after the first practice deck. With the real model, two models are compared
on one question.

### 7.12 Wave 12, World: every script, every screen, everyone

**Goal.** An app for the world: languages written from right to left, languages without spaces
between words, a way for any user to add their own language, a scene and a calendar that fit where
they live, screens of every size, and people of every ability.

**12.1 Scripts** (experiment S41; design language, section 13)
- **Right to left** (Arabic, Persian, Hebrew). The layouts mirror (Wave 0 prepared it and every wave
  kept its own screens right; this wave does what needs new machinery). Mixed text is handled
  paragraph by paragraph: an answer may hold a
  right-to-left paragraph, a left-to-right code block and a table. Documents and slides get a
  direction of their own.
- **East Asian** (Chinese, Japanese, Korean): the keyboard's composing text is never disturbed by the
  input bar or the editors, lines break correctly, and the search indexes (chats, knowledge) cut text
  that has no spaces into overlapping pieces instead of words.
- **Indic scripts and Thai** are shaped by the system's own text engine. Space between letters is
  never added to a script whose letters join or stack.
- **Search in every script.** Every index folds what a reader takes for the same letter: accents
  ("e" finds "é"), the dotted and dotless i, the forms an Arabic letter takes, full-width and
  half-width forms.
- **Type in three tiers,** so that titles and labels keep their character wherever they can. The
  app's own pixel font, drawn by hand: Latin with every accent, Cyrillic, and now Greek. Open pixel
  fonts as script packs on request: Ark Pixel at 12 pixels for Chinese, Japanese and Korean (a
  download per language, under an open font licence), and Handjet for Arabic and Hebrew if its
  letters join correctly on Android (S41). The system's font, a little heavier and inside the same
  panels, for everything else. Body text has always been set in the system's font, so reading is the
  same in every language.

**12.2 Eight more languages**
- Arabic, Persian, Hebrew, Hindi, Simplified and Traditional Chinese, Japanese and Korean, by the
  process of 1.8: a glossary, a translation, a second reading, the automatic check, and the mark
  "machine translated" until a speaker has read it. That makes eighteen.

**12.3 The language workshop**
- **Improve a translation.** In translation mode a long press on any label shows its English source
  and a field. The correction applies at once and is kept in a pack of the user's own.
- **Make a language.** Pick any language: the app translates its own texts in batches with the cheap
  helper, glossary first, and says what it will cost before it starts. The result is a pack file: in
  use at once, marked "made by you", checked on the phone by the same rules as the built-in languages
  (placeholders, lengths), and shareable as a file.
- A pack from someone else is imported from a file. There is no server; packs travel like any file.
  A pack can never replace the protected texts of 0.11 (the words a decision hangs on); it is
  checked by the same rules, carries its language's plural rules, and is shown in the picker as
  "from a file".

**12.4 Your region**
- **Scene packs:** the same world in other places. Besides the mountain: a sea coast, a steppe, a
  desert, a forest, a city at night. Each works with every theme, every season and the real sky.
- **Days by region and culture:** lists the user can switch on (the turn of the year in its several
  calendars, spring festivals, festivals of light, national days), each with a small touch in the
  scene (lanterns, lights, distant fireworks). None is on by default; the Welcome proposes the lists
  that fit the phone's region, and one tap accepts or declines.
- Units, dates, the first day of the week, paper size and currency follow the region.

**12.5 For everyone**
- The basics have been kept in every wave (principle 13); this is the deep pass.
- A TalkBack walk through every screen (names, headings, streamed answers announced by the sentence),
  and the scene described in words ("evening, light rain, the fire is lit"). Touch targets of 48 dp,
  text up to 200 %, contrast in every theme, a high-contrast theme, a still form of every animation,
  no information carried by colour alone, captions for the sounds, and a reading font on request.

**12.6 Sideways, big screens, keyboard and pen**
- Nothing breaks when the phone is turned; from 840 dp of width the chat list and the chat sit side
  by side, and the Canvas takes the third column. On a folding phone the fold is the divider.
- Desktop modes (DeX on this phone) and tablets: windows that can be resized, hover states, a menu
  on right click, drag and drop.
- A keyboard: Enter sends, Escape goes back, and shortcuts for search, a new chat and the Canvas,
  listed in the system's own shortcut help.
- The pen: pressure in the sketch pad, the pen's button erases, hovering previews.

**12.7 Battery, heat, storage, data**
- A 24-hour measurement with the fire on (`docs/perf/w12-battery.md`); the scene and the voice slow
  down when the phone reports heat; a Storage screen (the Linux, packs, voices, speech models, chats,
  the workspace, the browser's data, logs) with a clean-up for each.
- **Data saver:** on a metered connection nothing is downloaded by itself and the weather waits. With
  the phone's power saving on, the scene holds still.
- **Other phones.** The app is built on one phone. A checklist for others (memory pages of 16 KB,
  older Android versions down to 8, a small screen, a tablet) is written and run on whatever second
  device can be borrowed; what cannot be tried is listed as untried.

**Extras in this wave:** the scene packs, "Make a language" (improving a translation and importing a
pack are core), the pen, the reading font.

Three of this wave's agents work across the same screens, so they run one after another and not
side by side: LAYOUT first, then SCRIPTS, then A11Y. The others run in parallel.

| Agent | Owns |
|---|---|
| SCRIPTS (after LAYOUT) | What mirroring still needs, mixed-direction text, the composing-text rules, the indexes' cutting and folding, across `ui/*` and `data/*Index.kt` (one pass, by the list of flaws) |
| TYPE | The Greek glyphs of the pixel font, `ui/text/Fonts.kt` (script packs, the three tiers), their place in the Downloads screen |
| LANG2 | Eight `values-<code>/*`, their glossaries in `docs/i18n/*` |
| WORKSHOP | `ui/text/workshop/*` (translation mode, making a language), the pack format in `ui/text/LanguagePack.kt` (with plural rules and the refusal of protected texts), the check on the phone |
| REGION | The scene packs (`assets/pixel/scene_*.pxl`), the day lists (`assets/days/*.json`) and their touches in the scene, regional formats |
| A11Y (after SCRIPTS) | Descriptions, headings, live regions, targets, font scaling and the high-contrast theme across `ui/*` (one pass, by a list of screens) |
| LAYOUT (first) | Landscape and wide layouts in `ui/AppRoot.kt`, the chat, list and canvas screens; keyboard, mouse and pen |
| CARE | `docs/perf/w12-battery.md`, the heat response, data saver, the Storage screen (`ui/settings/StorageSection.kt`), the checklist for other phones |

**Proven when:** every screen has been photographed in Arabic and in Japanese with nothing cut,
nothing unmirrored that should mirror and nothing mirrored that should not; a chat in Arabic with a
code block and a table reads correctly; Japanese typed with a composing keyboard arrives intact, and a
Japanese word is found by search; a language made in the workshop on the practice model passes the
check and is used by the app; a TalkBack user can start the app, ask a question and hear the answer
without sight; the Canvas stands beside the chat in DeX; the 24-hour battery figure is written down
next to the figure of today's build.

### 7.13 Wave 13, One: 1.0

**Goal.** Thirteen waves become one finished thing, and the app may reach for a star.

**Part one: what makes it 1.0** (all of it required)

- **13.1 Hardening.** An adversarial review by the strong model of everything with an edge: the
  bridge, the gate and its rules (tested with practice pages that try to give orders), the file path
  checks, the web sandboxes and the two doors of small apps, the price card (nothing the app or the
  agent's tools offer can be made around it; what a terminal command could do with the owner's key
  is tried and written down as the limit it is), blocks and imports, grants and what they cannot
  hold, the file-picker provider, exported components, backups and their lock, the move
  between phones, masking, private chats, updates, and the wall between the owner's app and the test
  twin (nothing signed with the public test key may install over the owner's build or read its data).
- **13.2 The regression run.** Every scenario of every wave (about 300 by then) green on the practice
  model, and one hour of random valid taps without a crash.
- **13.3 Hermes itself.** The pinned Hermes (v0.23.3) is compared with the newest release. It is
  upgraded only if the CI smoke test and the whole regression run pass against the new one; otherwise
  the upgrade is written down for after 1.0.
- **13.4 The numbers again.** Every measurement of Wave 0 repeated on 1.0 and laid beside the first:
  frames, start time, the weight of the prompt, memory at the heaviest moment, the size of the APK.
- **13.5 The guide.** A guide inside the app, in every built-in language, searchable, with Pêt as
  the guide, and "Show me" on every page: the feature is walked through on the practice model, at no
  cost. The README with pictures and a thirty-second film. `docs/APP_OVERVIEW.md` rewritten for 1.0.
- **13.6 The launch edition.** The scene at its best: the first time 1.0 starts, dawn rises once over
  the mountain while the fire is lit, with every keepsake earned so far in its place. A seventh
  launcher icon and a keepsake for 1.0. Planned for the June solstice, 21 June 2027.
- **13.7 The extras that fell out.** The list kept since Wave 1 is gone through with the owner. Each
  extra is built now (by the agent that owns its files, in a short round of its own), written down
  for a version after 1.0, or struck.

**Part two: the stars** (the owner picks; none of them blocks 1.0)

- **A door from outside** (finding B16; experiment S42). Either Telegram, through Hermes's own gateway
  in the same process (a free bot token, only the owner's account allowed; it needs the Telegram
  library added to the Linux image, the second and last change to the image in this plan; Telegram's servers carry the
  messages, which makes it the one feature here that passes through someone else's cloud). Or the
  **Wi-Fi door**, which involves nobody else: the app serves one small chat page to the home network
  over an encrypted connection, with a certificate the phone makes for itself (the laptop's browser
  warns once, and the phone shows the fingerprint to compare) and a pairing code shown on the phone.
  It is off by default and turns itself off after an hour or when the network changes.
- Through either door the agent has no hands: no phone powers, and by default no terminal. Whatever
  arrives that way counts as outside content for the gate.
- **"Agirî" as a wake word** (finding C7; experiment S43). The keyword spotter is already inside the
  speech library. Only while charging or while the app is in front, with Android's microphone
  indicator visible, and measured for false alarms before it ships.
- **An offline mind** (finding E9; experiment S44). A small open model that runs inside the agent's
  own Linux and is offered to the app through a configuration of its own, like the practice model's
  (0.4), so that the owner's custom endpoint stays free. Its server is built from pinned source in
  CI and comes as a pack, never as a ready-made program from somewhere: no new library in the app,
  and a download of two to three gigabytes on request. It would serve helper tasks
  at no cost, light bots without a network, and later the search by meaning that 7.6 lacks. S44
  measures speed, heat and memory on this phone before anything is promised.

| Agent | Owns |
|---|---|
| HARDEN | The fixes from the review, across the files it names |
| REGRESS | The full scenario run, the random-tap run, the list of what failed and why |
| HERMESUP | The comparison with the newest Hermes, the upgrade if it passes (`rootfs/*`, fixtures) |
| NUMBERS | `docs/perf/w13.md` (every measurement repeated), fixes for whatever fell behind its budget |
| BOOK | `assets/guide/*`, `ui/guide/*` (with "Show me"), `README.md`, `docs/APP_OVERVIEW.md`, the film |
| LAUNCH | The opening, the icon, the keepsake (`assets/pixel/*`, `ui/pixel/stage/Launch*.kt`) |
| STAR (optional) | The star the owner picked, as a small wave of its own with its experiment first |

**Proven when:** the regression run is green; the review's findings are fixed or accepted in writing by
the owner; the numbers of 13.4 meet their budgets or say why not; the guide's "Show me" works on every
page; an installation of today's build updates to 1.0 and keeps everything.

## 8. The experiments register

Everything the plan needs that is not documented, or that only this phone can answer. Each is tried by
the lead agent on the phone **before** the contract of its wave is written, takes at most an hour, and
has a fallback, so none can block a wave. Results go into `AGENTS.md` under "Hard-won facts".

| # | Wave | The question | Tried by | Passes when | Otherwise |
|---|---|---|---|---|---|
| S1 | 0 | Does a back swipe report its progress to an app that targets API 28? | `android:enableOnBackInvokedCallback="true"` and a `PredictiveBackHandler` on a throwaway screen | Progress values arrive while the finger moves | The same animation plays after the gesture ends |
| S2 | 0 | Does the optimised build still find everything that is looked up by name at run time? | A release build with R8: start Hermes, speak a sentence with the neural voice, back up and restore, switch the launcher icon | All four work, with no `NoSuchMethodError`, `ClassNotFoundException` or native abort in logcat | Shrinking stays off; the build is still not debuggable and compiled ahead of time, which is most of the gain |
| S3 | 0 | Do shared elements work with the hand-written navigator? | An avatar travelling from a chat row to the chat's top bar and back, also under the back gesture | No flicker and no frame over 8.3 ms | Fade and slide only (pattern 7) |
| S4 | 0 | Does ahead-of-time compilation stay on a sideloaded build? | `cmd package compile -m speed -f`, then `dumpsys package` after a reboot and after a day | The compile filter still reads `speed` | The build script repeats the command at every install, and Diagnostics says when the app finds itself uncompiled |
| S5 | 0 | Can the practice model run inside the guest next to Hermes? | One launch that starts the mock and then Hermes (the launcher sweeps guest processes at every start and stop) | "hello" is answered by the mock, also after a stop and a start | The practice model runs inside the app's own process on loopback |
| S6 | 0 | Does a new signing key install over the old one? | A build signed with a new key and a proof of rotation (`apksigner rotate`): first with a throwaway package name (old key, then the new key with the proof), then over the owner's app after a backup. A refused install changes nothing | It installs and the data is intact | The owner's build keeps today's key for now; the updater of Wave 10 then calls its signature check what it is, a check against damage and not against forgery |
| S7 | 0 | How much room is there, and what does Android 17's memory limit count? | The app, Hermes with three idle children, a web view in its own process and a speech model, all loaded together; `dumpsys meminfo` and `adb shell am memory-limiter status`; then one of them made to grow until something is killed | The heaviest planned moment fits with a quarter to spare, and it is known what Android counts as "the app" | The caps are set from what was measured: fewer sub-agents at once, the speech model unloaded before a web view opens |
| S8 | 1 | Can the system's splash hand its icon over at target 28? | `windowSplashScreenAnimatedIcon` in `values-v31` and an exit listener | The flame is shown and the listener receives the icon's view | A plain fade into the scene |
| S9 | 3 | Does the Runs API behave as its code reads, and what survives the app's death? | A script against the practice model, then one real turn: (a) the history sent with the run is the one used, (b) a picture in the input arrives, (c) Stop ends a running tool, (d) a steer is taken in, (e) joining again with `Last-Event-ID` repeats nothing, (f) how long a finished run can still be read, (g) an approval through the run, (h) after the app's process is killed mid-turn: is Hermes still alive, does it finish, and can a restarted app take it back instead of sweeping it? (i) the same kill with the service that runs Hermes in a process of its own (`:engine`) | (a) to (e) hold; (h) and (i) decide which of the two ways "fewer lost answers" is built on | The classic backend stays the default: the app times the steps itself, steer becomes "queue", Stop stays what it is today. Partial saves and Continue remain in every case |
| S10 | 3 | Does the gate work as its code reads? | A plugin written by the app and listed under `plugins.enabled`: it vetoes one tool, sends another to the approval gate (does the approval reach an API client as it does for a flagged command?), and reports results; the time it adds per call; what happens when the bridge is down, and when the plugin itself raises an error; the hooks around a request to the model and around side calls (do the counts add up, can a request be refused, can it be changed at the last step?); a sub-agent's start (does it name its parent?); a task with a grant and the phone locked: twenty contained calls and one call outside the grant | A veto and an ask both arrive as designed; a call costs under 20 ms more; a silent bridge and a failing plugin both mean refusal; the meter's count equals the usage reported at the end; the task runs without one ask, and its one outside call is refused | The same gate as a shell hook from the configuration (slower: a process per call); failing that, bots with private abilities get no web tools at all, and every ability asks. Without the hooks around requests, ceilings are checked at tool calls only and masking stays with what the app itself sends |
| S11 | 3 | What is the least a hand-written MCP server must speak for Hermes's client? | A server with one tool, added under `mcp_servers` | The practice model calls `mcp__agiri__phone_status` and gets the answer | The `agiri` command in the guest and a skill that teaches it; the agent calls it with its terminal tool |
| S12 | 3 | Can a waiting write be applied from outside? | The gate on; the practice model saves a memory entry and a skill; one waiting file is applied through Hermes's own functions (a helper script), one is dropped | The applied one is in place exactly as Hermes would have written it | The gate stays off; the app's snapshots and Undo are the protection |
| S13 | 3 | Can whatever a turn changed in the workspace be undone? | The app's own snapshot (the `git` in the image, a store beside the workspace) before and after a run in which the practice model overwrites one file with the file tool and another through a script in the terminal; then `checkpoints.enabled` and a helper that lists, compares and restores through Hermes's own functions | Both files are back byte for byte after "Undo this turn", and a snapshot of a 50 MB workspace takes under two seconds; whether Hermes's finer checkpoints exist for API turns is written down | Snapshots only up to a workspace size the phone handles, which the sheet says; without Hermes's checkpoints there is no "Undo this step" |
| S14 | 4 | Do two profiles work in one gateway under proot? | A `light` profile with its own `.env`, and a request to `/p/light/v1/chat/completions`, in each of the three Google credential modes; then the default profile's terminal, to see that its environment is unchanged | It answers with its own tools and a small prompt, the default profile is unchanged, memory grows by less than 60 MB | Light requests by swapping the configuration as a request starts; then one global "Light mode" switch |
| S15 | 4 | Can the prompt be weighed without spending tokens, and do switches apply without a restart? | `hermes prompt-size --platform api_server --json` in the guest (it counts characters), compared with the usage of one real "hello" to get the factor; then each switch of 4.2 once, and a tool list changed between two messages | It runs in under ten seconds, the factor holds within 10 % across three prompts, and the changed list shows in the next message | The practice model reports the size of every prompt it receives (in characters, free); a restart for switches |
| S16 | 4 | Can the app make Google's access token itself? | A token made in Kotlin from each of the two credential kinds in the twin (a signed assertion; a refresh exchange), then one direct "hello" to the cheapest model | Both tokens are accepted and the answer arrives; nothing of the credential or the token is in any log | Helpers and pictures work in the key-based modes only, and say so in the other two |
| S17 | 4 | Can libraries be added to the agent's Python from outside the image, and checked? | The data pack installed into a mounted folder with the image's own package tool, from a lock file with a checksum per file; the same with one file altered on the way; imported by the agent's Python; the image replaced; imported again | It imports before and after, the altered file is refused, the install takes under three minutes on Wi-Fi, and removing the folder removes everything | Packs are baked into the image at build time (the APK grows), or the data mode is left out |
| S18 | 5 | Does the cheapest model follow the canvas protocol? | Ten requests that should make an item and ten that should not; five edits of an existing item | At least eight of each ten are right; four of five edits match their place exactly once | The app promotes by a rule of its own (long code, long text) and by hand; an edit that fails is asked again as a whole item |
| S19 | 5 | Do the renderers work offline inside the sandbox, and does the sandbox hold? | On the owner's build (debugging off): a diagram and a formula with the network off, each handed to the app's process as a picture; a page that tries every way out it can (requests, frames, forms, sockets, peer connections, workers, navigation, the two doors used wrongly); the keyboard and the back gesture in the sandbox's own screen; the size the renderers add to the APK | Both render and arrive as pictures within half a second; no attempt gets out; under 2.5 MB added | A diagram is shown as its source with a note, formulas as text, pages with scripts off |
| S20 | 6 | Do the document libraries work in the image, and what do they weigh? | The side environment the office skill looks for, built into the image with both extras; the skill's scripts, called with that environment's interpreter, make a Word, a PowerPoint and an Excel file from fixtures, in the CI smoke test and on the phone with the network off; the growth of the APK; a text recogniser tried in the guest as a pack | The three files open on a computer, nothing is downloaded on first use, and the APK grows by under 8 MB | The libraries become a pack (S17) installed on first use; without either, export to those formats is left out and the native views remain |
| S21 | 6 | Is there a picture that costs nothing, and does the paid path work? | Without spending: what the owner's providers list for pictures, and at what price. Only with the owner's explicit yes: one picture at the smallest size through the app's own call | The price shown is the price charged, and the picture arrives | Pictures ship on the stand-in until the owner wants the tap; the flows are complete either way |
| S22 | 6 | Can the phone drive a second screen with its own content? | A `Presentation` on a display connected by cable and by wireless display; the same with DeX on | The slide is on the big screen while the notes are on the phone | The phone's ordinary mirroring shows the slides; the notes stay out of sight |
| S23 | 7 | Does a research with parallel readers fit the phone, and can it run unattended? | A Standard research on the practice model with three readers kept inside the turn (`delegation.gateway_mode: inline`): are the children's events in the run's stream, are they counted by the meter, do they inherit the grant, does a child's ask end as a refusal; memory, time, the phone's temperature; the whole run once more with the phone locked; then one Quick research on the real model against its estimate | Within the room found in S7, no process killed, children seen and counted, not one ask with the phone locked, and the real cost within a quarter of the estimate | The app runs each reader as a run of its own, one at a time if memory demands it; the estimate is corrected from the measured run |
| S24 | 7 | Can the platform read the text of a PDF, and can the index cut text that has no spaces? | Three PDFs (plain, two columns, scanned) through the platform's page text and through the guest's library; a search for a Japanese word in the platform's full-text search | The platform's text is as complete as the library's for the first two; the word is found | The guest's library for all PDFs; text without spaces is cut into overlapping pairs by the app before indexing |
| S25 | 7 | Does the citation skill's list agree with what the gate saw? | A practice research and one real one: the skill's list of sources beside the gate's record of fetched pages, with a page that redirects, a long page whose middle is read later from the cache, and a page that comes out of the twenty-minute cache | Every source the skill calls verified is in the gate's record under the address it ended at, and a quote from the middle of the long page is found | The app reads numbers and addresses out of the text itself; the marks never depended on the skill |
| S26 | 8 | Which way of listening is best on this phone? | Twenty sentences recorded once by the owner (in the languages the owner speaks), given to the phone's recogniser, `seda`, Whisper tiny and small, and the 1,600-language model | An on-device engine is within five points of the phone's recogniser in word errors and faster than real time | On-device listening ships marked "experimental"; the phone's recogniser stays the default |
| S27 | 8 | Can the phone listen while it speaks? | The voice on the loudspeaker, the microphone open with echo cancelling, two minutes | The voice detector never takes the app's own voice for the user | Tap to interrupt; by voice only with headphones |
| S28 | 8 | Which voices are good enough? | For each language the owner speaks: the installed model's voice beside two from the catalogue, ten sentences each, heard by the owner; the speed measured | The owner would listen to it, and it speaks at least twice as fast as real time | A voice that fails is offered marked "community voice", or not at all |
| S29 | 8 | What does a look at the screen need at target 28? | The capture consent and one frame; then a second frame under the same consent with a visible notification; what the frame holds when a protected app is in front | One frame per consent works; whether a session works is written down | Every look asks; or the look is left to the assistant gesture of Wave 10, where Android itself hands over the screen |
| S30 | 8 | Does an hour of recording with the screen off survive, and what does writing it down cost? | An hour of radio recorded from the notification; the live pass and the careful pass; battery, heat and time; a talk between two people for the speakers | The file is whole, the careful pass takes under twenty minutes on the charger, two speakers are told apart most of the time | Shorter recordings with a stated limit; speakers left out; the careful pass only on the charger |
| S31 | 9 | Which events really wake a dead app on One UI 9, and may it then speak? | Each trigger with the process killed (`am kill`): Bluetooth connected, charging started, the cable pulled (through the waiting service), Wi-Fi joined, a notification (with the two steps of "restricted settings" that a sideloaded app needs before it is given that access); and a routine that reads its answer aloud | The routine starts within a minute; the voice is heard | A trigger that fails is not offered; a routine that may not speak posts its result with a Play button |
| S32 | 9 | Can Hermes's browser tools drive a WebView? | WebView debugging on, its DevTools socket relayed to a loopback port, `BROWSER_CDP_URL` pointed there | Navigate, snapshot and click work on a simple page | Five bridge tools on the same WebView (9.7, plan B) |
| S33 | 10 | How good can a classic widget be? | The Hearth widget on the home screen: size limits, sharpness, the four-frame loop, how quickly One UI lets it change | Sharp pixels, a looping flame, a change shown within five seconds | A still picture that changes with the state |
| S34 | 10 | Are Live Updates given to an app that targets API 28? | A promoted progress notification during a long practice turn | The chip and the Now Bar appear | An ordinary progress notification |
| S35 | 10 | Conversations and bubbles at target 28? | A conversation notification with a long-lived shortcut and bubble data | It sits under "Conversations" and the bubble opens the chat | Conversation style without bubbles (Direct Share needs only the shortcut) |
| S36 | 10 | Can Agirî be the digital assistant? | The services Android asks for, then the system setting | Agirî is offered in the list and the gesture opens the quick question | The second launcher entry for the side button |
| S37 | 10 | Can the app install its own update? | A newer build downloaded by the app and given to the package installer | One confirmation, the data intact, the app back by itself | The file is handed to the system's installer screen |
| S38 | 10 | Does One UI let a sideloaded app be the live wallpaper and the screen saver? | The two services installed; the system's pickers; an hour of each with the battery watched | Both can be chosen, and the wallpaper costs under one per cent of battery an hour with the screen on | A still wallpaper picture that the app refreshes; a bedside mode inside the app |
| S39 | 10 | Can the text menu put text back, and how many entries does One UI show? | Two entries; a rewrite in three apps (notes, mail, a browser's text field) | The replacement lands in the field in at least two of the three | The result is copied, and a notice says "copied: paste to replace" |
| S40 | 11 | Can a cheap model draw a face in 16 by 16 pixels? | Ten descriptions, the cheapest model, the validator, then the lead's eyes | Seven of ten are valid and look like what was asked | Faces built from parts; the editor stays |
| S41 | 12 | Do other scripts work in the app's pixel look? | The Ark Pixel font for Japanese at 12 pixels in a title; Handjet for an Arabic sentence (do the letters join?); an answer with both directions; a composing keyboard in the input bar | Sharp titles, joined letters, composition intact | The system's font for that script |
| S42 | 13 | Can the phone be reached from outside? | Telegram: thirty minutes of long polling under proot with only the owner allowed. Wi-Fi door: the page opened from a laptop on the same network over the encrypted connection, paired by the code | The owner's message is answered and a stranger's is ignored | The other door, or none |
| S43 | 13 | Does a wake word work? | The keyword spotter with "Agirî" spelled in the model's sound units; thirty minutes of talk radio | Nine of ten at one metre, at most one false alarm an hour | Dropped |
| S44 | 13 | Is a model on the phone good enough? | A small open model served inside the guest and reached through the custom-endpoint mode; the candidates of that day, measured on the phone | Ten tokens a second, under 2 GB of memory, twenty usable titles out of twenty, no heat warning in ten minutes | Dropped |
| S45 | 8 | Can the app hold a live voice session with the provider, and is it worth its price? | A socket client written by hand against a practice server in the guest that speaks the same protocol; then, only with the owner's yes and the price shown first, one minute against the provider | Sound in and sound out, under a second to the first sound, an interruption stops the voice, and the minute cost what the card said | The feature is dropped; the talk on the phone stays the only one |

The numbers follow the waves, except S45 (Wave 8), which was added after the review.

**What the experiments cost.** About an hour each and at most two real-model turns each: roughly five
working days in total, spread over fourteen waves. Two of them may cost money, each only with the
owner's yes: S21 (one picture, a few cents) and S45 (one minute of live voice). Four of them (S1,
S34, S35, S36) answer questions that Android's
documentation leaves open for an app like this one; their answers are worth writing down for anyone
else who sideloads.

## 9. What changes under the hood

For the contract writers. Nothing here is a contract; it is the map that keeps fourteen contracts
consistent.

### 9.1 Data

Every new field is optional. Old files load unchanged, because the reader already ignores what it does
not know and fills what is missing (`data/ChatJson.kt`).

| Type | New | Wave |
|---|---|---|
| `Message` | `toolStartedAt`, `toolDurationMs`, `toolPreview`, `toolError`, `quote`, `variants` (the versions of an answer and which one is chosen), typed blocks | 2 |
| `Message` | `runId`, `servedModel`, `cachedTokens`, the cost of the turn, the parent step of a sub-agent's step, a pointer to the turn's ledger | 3, 4 |
| `Message` | references to canvas items; sources with their marks | 5, 7 |
| `Conversation` | `archived`, `labels`, the chat it branched from | 2 |
| `Conversation` | `summary`, `summaryUpToMessageId` | 4 |
| `Conversation` | `spaceId`, `research` (a research in progress) | 7 |
| `Conversation` | `talk` (a spoken chat), `quick` (a quick question), `private`, `locked`, the Council's way of talking | 8, 10, 11 |
| `Bot` | `mind`, the switches for question cards, the Canvas and numbered sources | 2, 4, 5, 7 |
| `Bot` | `greeting`, `starters`, `knowledge`, default abilities, default model and effort, a voice per language | 8, 11 |
| `Routine` | `trigger`, limits, allowed abilities, a recipe to run, a watch; the run history in its own file | 9 |

Rules:
- Adding a field never raises the file version. The version rises only when an older build would
  misread a file.
- There are no downgrades: Android refuses an older build over a newer one, and the restore screen
  already refuses a backup made by a newer format (`data/BackupManager.kt:588`).
- Before a build first starts with a newer file version, it copies `files/chat` aside and keeps the
  copy for seven days.

### 9.2 New stores

| Store | Where | Wave | In a backup |
|---|---|---|---|
| Language packs | `files/lang/` | 0, 12 | Yes |
| Weather and place | preferences, a small cache | 1 | The setting yes, the cache no |
| The agent's workspace (with canvas items, research folders, the Library's files) | `files/agent/`, seen by the guest as `/root/workspace` | 2 | Yes, as an option, with its size shown and a plain warning: it is copied as it is, whatever the agent wrote there |
| `ChatIndex` (full text) | `files/index/` (SQLite) | 2 | No, it is rebuilt |
| Spaces | `files/chat/spaces.json`, and a folder each in the workspace | 2 | Yes |
| `RunStore` (turns in flight), the outbox | `files/chat/runs.json`, `outbox.json` | 3 | No |
| The gate's policy | preferences | 3 | Yes |
| The gate's log | `files/bridge/audit.jsonl` | 3 | No |
| The gate's record of each turn | memory only | 3 | No |
| The mark that travels (which files a tainted turn wrote, what came in from outside) | `files/bridge/taint.json` | 3 | Yes, with the workspace |
| Grants of tasks (consent by task) | memory; `files/bridge/grants.json` for queued jobs | 3, 9 | No |
| The ledger and the record of fetched pages | `files/ledger/`, one file per chat | 3 | Yes, with the chats |
| Snapshots of what lasts (memory, skills, the scheduler, `SOUL.md`, `hooks/`, `plugins/`) | `files/history/` | 3 | No |
| File snapshots for Undo | `files/agent-history/` (the app's own); Hermes's own store where S13 allows | 3 | No |
| `PriceBook`, the model catalogue | `files/chat/prices.json`, `models.json` | 4 | Yes |
| Ability switches, light minds, guard rails | preferences | 4 | Yes |
| Packs for the guest | `files/packs/`, mounted into the guest | 4 | No, they are downloads |
| Sign-ins of connections | `mcp-tokens/` in Hermes's folder, kept across updates | 4 | Only in a locked backup |
| Folders the owner granted | Android's own lasting permission, and a list in preferences | 4 | No: a grant does not travel |
| `CanvasIndex` | `files/index/` | 5 | No, it is rebuilt |
| Small apps' own stores | `files/apps/<id>/` | 5 | Yes |
| Pictures and the list of the Pictures shelf | the workspace, `files/chat/pictures.json` | 6 | Yes, with the workspace |
| Recipes, themes made by the user | `files/recipes/`, `files/themes/` | 6 | Yes |
| The signature | `files/private/signature.png` | 6 | Only in a locked backup |
| The knowledge index, study cards and their schedule, highlights, the reading list | `files/index/`, `files/study/`, the workspace | 7 | Yes (the index is rebuilt) |
| Speech models and voices | `files/stt/`, `files/tts/` | 8 | No, they are downloads |
| Recordings and made sound files | the workspace (`audio/`) | 8 | As an option, with the size shown |
| Tasks, the night queue, routine history, watches' last state | `files/tasks/`, `files/night/`, `files/routines/` | 9 | Tasks yes, the rest no |
| The agent browser's data (cookies) | the web view's own folder | 9 | Never |
| Collections, keepsakes, the journal, the list of private words | `files/chat/`, `files/private/` | 11 | Yes; the private words only in a locked backup |
| Script fonts | `files/fonts/` | 12 | No, they are downloads |

### 9.3 The generated configuration

From Wave 3 one composer builds `.env` and `config.yaml` from **fragments**: each a pure function from
settings to a piece of configuration, each with its own golden fixture. The composite is the fixture
the CI smoke test boots, so a combination that stops Hermes from starting cannot be shipped.

| Wave | Keys the app writes (each one read in Hermes's source) |
|---|---|
| 0 | `platform_toolsets.api_server` (without the browser set, the scheduler tool, the picture tool and the speech tool), the `auxiliary` models pinned, `auxiliary.title_generation.model_upgrade_enabled: false`, `security.allow_lazy_installs: false`, `web.keyless_fallback` (the visible switch) |
| 2 | `terminal.cwd` |
| 3 | `mcp_servers.agiri` (named in the tool list), `plugins.enabled` (the gate), `hooks.outbound`, `skills.write_approval`; `checkpoints.enabled` and its limits where S13 allows |
| 4 | `platform_toolsets.api_server` from the switches, `agent.disabled_toolsets`; `agent.task_completion_guidance`, `agent.parallel_tool_call_guidance`, `agent.tool_use_enforcement`, `agent.execution_guidance`, `agent.environment_probe`; `memory.memory_enabled`, `memory.user_profile_enabled`; `platform_hints.api_server`; `tools.tool_search`; the `light` profile's own `config.yaml` and `.env` (serving it switches on by itself); `delegation.model`, `agent.max_turns`; `mcp_servers.<name>`, each named in the tool list |
| 7 | `delegation.gateway_mode: inline`, `delegation.max_iterations` (lowered from 250), `delegation.max_concurrent_children` (the cap found in S7) |
| 9 | `browser.cdp_url` (with the browser set back) |
| 11 | `memory.write_approval` |
| 13 (star) | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USERS`; or the offline mind's own configuration, like the practice model's |

### 9.4 The guest

- Files the app writes into the guest at each start, next to `.env` and `config.yaml`: the practice
  model (Wave 0); the gate plugin, the `agiri` command, the review helper and the snapshot helper
  (Wave 3); the prompt-weight helper (Wave 4); the small-app skill (Wave 5); the research skill
  (Wave 7). All are regenerated, none is user state.
- The workspace and the packs are mounts, not part of the image (`PA_EXTRA_BIND`). The CI smoke test
  runs without them, so the launcher stays the one launcher and stays unchanged.
- The image changes once for certain: the side environment with the document libraries (Wave 6),
  which the smoke test then exercises with three fixtures. A second change (the Telegram library)
  happens only if that star is picked.
- The second profile lives under Hermes's own `profiles/` folder. The list of what survives an image
  replacement (`runtime/UserState.kt`) gains `profiles` and `pending` in Wave 3 and `mcp-tokens` in
  Wave 4.

### 9.5 Secrets

New secrets (headers and sign-ins of connections, the bridge's token, a passphrase's derived key while a backup
runs, a Telegram token) follow the rule that exists: they live in the app's secret store, reach Hermes
only through the regenerated `.env`, never appear in an unlocked backup, and the redactor learns each
new shape in the same change that introduces it. Google's access token of 4.7 lives in memory only.
The bridge's token is made at every start. Like Hermes's own API key it sits in the regenerated
`.env`, which the agent can read, so nothing relies on it against the agent itself (feature 3.3).

### 9.6 Packages at 1.0

New, next to today's: `bridge/` (with `tools/` and `browser/`), `data/canvas/`, `data/office/`,
`data/pictures/`, `data/recipes/`, `data/research/`, `data/sources/`, `data/library/`, `data/spaces/`,
`data/knowledge/`, `data/study/`, `data/tasks/`, `data/night/`, `data/brief/`, `voice/talk/`,
`voice/listen/`, `voice/catalogue/`, `voice/player/`, `voice/interpret/`, `voice/record/`,
`voice/overview/`, `voice/look/`, `voice/live/` (if that extra is built), `routines/triggers/`,
`routines/watch/`, `widget/`, `ambient/`, `provider/`, `assist/`, `update/`, `move/`, `security/`,
`media/`; and under `ui/`: `text/`, `pixel/stage/`, `pixel/pet/`, `pixel/editor/`, `home/rows/`,
`chat/view/`, `chat/work/`, `chat/blocks/`, `chat/actions/`, `chat/versions/`, `chat/input/`,
`chat/files/`, `chat/list/`, `chat/export/`, `chat/ledger/`, `chat/sources/`, `chat/council/`,
`library/` (with `made/` and `saved/`), `safety/` (with `privacy/`), `canvas/` (panel, doc, code,
web, data, deck, sheet, notebook), `present/`, `pdf/`, `pictures/`, `recipes/`, `scan/`, `sketch/`,
`research/`, `reader/`, `spaces/`, `study/`, `voice/`, `interpret/`, `record/`, `tasks/`, `night/`,
`brief/`, `browser/`, `quick/`, `process/`, `share/`, `search/`, `onboarding/`, `guide/`.

### 9.7 What the app uses of Hermes at 1.0

| Use | Through |
|---|---|
| A turn | `POST /v1/runs`, `GET /v1/runs/{id}/events`, and `/stop`, `/steer`, `/approval` under the same run |
| Helper tasks, the classic backend | `POST /v1/chat/completions` (also under `/p/light/`) |
| What exists | `/health`, `/v1/models`, `/v1/capabilities`, `/v1/toolsets`, `/v1/skills` |
| Sessions and their cost | `/api/sessions`, `/api/sessions/{id}` |
| A private chat's session removed | `DELETE /api/sessions/{id}` |
| The gate | A plugin under `plugins/`, listed in `plugins.enabled`, using `pre_tool_call` and `post_tool_call`; for the meter the hooks around requests to the model and around side calls (`pre_api_request`, `post_api_request`, `pre_auxiliary_call`, `post_auxiliary_call`); for sub-agents `subagent_start` and `subagent_stop` |
| Commands in the guest | `hermes prompt-size`, `hermes skills install` and `uninstall`, `hermes mcp test`, `hermes status` |
| Files | `SOUL.md`, `memories/`, `skills/`, `pending/`, `profiles/light/`, `config.yaml`, `.env` |
| Undo for files | The app's own snapshots with the `git` in the image; `checkpoints.enabled` and Hermes's own checkpoint functions through a helper in the guest, where S13 allows |
| Office files and PDFs | The scripts of the skills `microsoft-office` and `pdf`, run in the guest with the interpreter of the side environment `documents` |
| Sources | The skill `grounded-citations`, its list pointed at the research's folder with every call (`--ledger`); the gate's own record beside it, which is the one the marks use |
| Research | `delegate_task` with `delegation.gateway_mode: inline`, `delegation.max_concurrent_children`, `delegation.max_iterations` and `delegation.model`; or one run per reader |
| Connections | `mcp_servers.<name>`, `hermes mcp login` for a sign-in by code, `mcp-tokens/`, `hermes mcp test` |

## 10. How things get proven

| Layer | What it is | Since | Costs |
|---|---|---|---|
| Unit tests | Everything that is a pure function is one, and is tested as one: the sky clock and the star field, Pêt's brain, the suggestions, the smoothing of streamed text, incremental Markdown and the block parser (random pieces against the whole), the grouping of work steps, the file path checks, prices, each configuration fragment, the gate's policy with its rules, the MCP messages, canvas edits, the deck and document descriptions, the marks on sources, the card schedule, masking, triggers and their limits, compaction | Today (about 410), growing with every wave | Nothing |
| The practice model | A scripted model inside the guest: tools, approvals, long streams, failures, files, memory, and from each wave its own behaviours (question cards, canvas items, decks, sources, a research with readers) | Wave 0 | Nothing |
| The phone robot | Scenarios as files, by test tag, run on the test twin: about 20 after Wave 0, about 300 at 1.0 | Wave 0 | Nothing |
| Smoothness and memory numbers | `tools/phone-perf.ps1` against the budgets of the design language, the memory peak of each wave, kept per build in `docs/perf/` | Wave 0 | Nothing |
| Text checks | Every key in every language, placeholders, lengths; one screenshot per screen in the stretched test language, one mirrored and one at double text size; a spoken label on every control | Wave 0 | Nothing |
| Looks | Screenshots of every changed screen, dark and light, judged by a reviewer that looks at pictures; contact sheets for sprites; pinned-clock and pinned-weather pictures for the sky | Wave 0 | Nothing |
| Stand-ins for what costs money | The picture provider that draws a placeholder; practice pages served inside the guest for research and sources | Waves 6, 7 | Nothing |
| Real-model checks | The few things only a real model shows: at most thirty turns per wave, on the model the owner chose for testing, in the twin | Always | Stays inside the owner's allowance: the twin's daily budget is set to block |
| The owner's eyes and ears | Taste gates, the Turkish page, the voices, and living with each build for a day | Always | The owner's time |

The CI smoke test stays what it is (Hermes boots in the real image against the mock model) and gains
the composite configuration of section 9.3 and, from Wave 6, three office files made by the skill's
scripts.

## 11. Risks

| Risk | What is done about it |
|---|---|
| The optimised build breaks something found by name (the speech library's native code has done that once already) | S2 before anything else; no renaming of classes; keep rules; the twin remains as a second file in every release |
| An experiment or a test damages the owner's chats, memory or credentials | The test twin: agents and the robot never touch the owner's app, which changes only when a wave ships |
| Pixel art made by agents looks worse than what exists | The owner is the art director (the taste gates); contact sheets; a reviewer that looks at pictures; the flame's core is keyed by hand; and one rule: new art replaces old art only where it is better |
| The Runs API differs from what its code suggests | S9; the classic backend stays selectable for a whole release |
| Android gives an API 28 app less than hoped (back gesture, Live Updates, bubbles, the assistant role) | S1, S34, S35, S36; every one has a fallback that is already designed |
| The agent is talked into something by a web page, a file or a notification | One gate before every tool, with three rules (S10), and a plugin that refuses when anything in it fails; blocks in an answer and everything imported pass the same policy; a mark that travels with files; lasting writes wait for the owner; snapshots, quarantine and Undo for what slips past; a log of every call; nothing that speaks in the owner's name is offered at all, and whatever has a price asks first; a reviewer whose only task is to break the gate in Wave 3, and again in Wave 13. What remains: no rule can fully read a terminal command, which is why commands ask in a tainted turn |
| The plan is too big | The five largest waves ship in two halves; every wave names its extras, which are dropped first; every wave ships a complete app; a wave is never stretched; the owner can stop after any wave and still own something finished |
| Tests burn tokens | The practice model and the robot; thirty real turns per wave at most, in the twin, under a daily budget set to block |
| Battery and heat (a living scene, listening, widgets) | Budgets and measurements from Wave 0; the animation level; sensors and the microphone only while their screen is in front; the 24-hour figure in Wave 12 |
| The APK grows | Art is text, sounds are small, models are downloads; CI fails a build that grows more than the allowance of section 5 |
| An update loses data | Optional fields only; the copy made before a format change; the local backup before every self-update; the scheduled backup of Wave 10 |
| Parallel agents collide | One owner per file, a contract that names every shared symbol, one integration agent: the method that worked in Phase 3 |
| One UI stops background work | What Phase 3 learned (alarms, the foreground service, notification groups) plus S31 for the new triggers |
| The plan is wide, and a wide app can be thin everywhere | Rule 8: a wave must match the best in its area and carry something of its own. The taste gates. The regression run, which keeps old features working while new ones arrive. And the order itself: the base that everything shares (motion, text, the gate, the Canvas) is built before the features that stand on it |
| Something with a price is made without the owner knowing | One price card that every picture passes; a picture budget that starts at zero; Hermes's own picture and speech tools taken off the agent's list and refused by name at the gate; a meter that counts while a turn runs; the whole flow built and tested on a stand-in that costs nothing; the hardening review of Wave 13 tries to get around the card. What remains: the provider's key lies where a terminal command can reach it, so the hard stop is a spending cap at the provider (section 13) |
| Cheap models ignore or misuse the canvas protocol | S18 before the contract; anything can be promoted by hand; a rule of the app's own for long code and long text; an edit that fails is asked again whole |
| An exported file looks different in the office program | Texts kept inside safe areas; the skill's own scripts write the file from the same description the app drew; three decks and three documents looked at on a computer in the wave's proof; the limit is said in the app |
| The phone runs out of memory (Hermes, a web view, a speech model and sub-agents at once) | S7 measures the room before anything is built on it; caps on parallel readers; the web view and the speech models are let go when idle; every wave writes down its peak |
| A research costs more than it said | The estimate is a ceiling, kept in tokens as well as in money, that stops and asks; S23 corrects the estimates from a measured run; a cheaper model for the readers and a cap on their steps; a limit per day |
| A bundled renderer has a flaw | It runs only inside the sandbox process, which has no network, no files and no door into the app; versions are pinned and looked at again at every wave |
| A translation embarrasses the app in a language nobody here reads | The mark "machine translated"; a second reading by another agent; automatic checks of length and placeholders; English behind every missing text; the workshop lets a speaker fix it on the spot |
| A recording or a look at the screen captures other people | One plain line at every start; a visible notification while it runs; nothing leaves the phone unless the owner asks; a look is one frame, never a stream |
| A font, a voice or a table of data comes with a licence that does not fit | Only permissive licences are bundled, each listed in `NOTICE`; anything non-commercial or copyleft can only be a download the user asks for; the star table's licence asks for sharing, which a public repository does |
| The weather service changes its terms | The weather is optional and behind one small interface; the sky needs nothing from the network |
| The app is built and tested on one phone | A checklist for other phones in Wave 12; what could not be tried is listed as untried, in the app's own "About" |
| A later Hermes changes what the app relies on | The version is pinned; section 9.7 is the list to check; the smoke test and the regression run decide about an upgrade (13.3) |
| Unattended work is stopped by its own safety rules, or the rules are loosened to let it run | Consent by task: a card states what a job needs and grants exactly that, for that run; contained tools that can touch nothing but the job's folder; what falls outside is held for the owner. S10 and S23 prove a research from Start to report with the phone locked |
| A free key meets its limits in the middle of long work | The engine waits as long as the provider asks, runs fewer requests at once and says so in the timeline; estimates are made for the key in use; the Welcome says plainly what a free key is |
| A download is swapped for something else on the way | A catalogue inside the APK with a fixed revision and a checksum per file; packs installed from a lock file; nothing resolved at install time |

## 12. What is not in this plan, and why

- **The Play Store.** The app targets API 28 on purpose (it must run programs from its own data
  folder); the store does not accept that. It stays a sideloaded app with its own updater.
- **iPhone.** The whole idea rests on running a Linux inside the app.
- **Accounts, cloud sync, a server of ours, links to a shared chat.** What leaves the phone is what is
  sent to the model's provider, what the agent looks up (through outside services that search and
  read pages, which Wave 0 names and lets the owner switch off), what a connection the owner added
  receives, the downloads the owner asks for (voices, packs, fonts, each from a pinned address), the
  weather request if it is switched on, and the anonymous update check. The ledger lists all of
  them. Sharing is done with files and postcards; moving and backing up, from phone to phone and
  into files.
- **Video and music.** Every route to them costs real money per piece, and none of the owner's
  providers gives them away. The picture service is built so that a video provider can be added
  later. What the plan does offer is narrated slides as a video file, which costs nothing.
- **Pictures made on the phone itself.** That needs a new native library and a model of several
  gigabytes; pixelizing and editing on the phone are in the plan instead.
- **Calls, SMS, payments, reading notifications, and control of other apps through the accessibility
  service.** Too easy to abuse through a hostile page, and the intents of Wave 9 cover the honest uses.
- **Statistics about the user.** None, ever. The update check is one anonymous request.
- **Replacing proot, or changing Hermes's code.** Both work; the fallback (an embedded Python) stays a
  fallback.
- **A catalogue of community plugins**, the Hermes dashboard in a WebView, and a second agent platform:
  possible later, of little use to one owner today.
- **The car's screen and the watch.** Both need libraries and a store distribution this app does not
  have. A talk works over the car's Bluetooth as it is.
- **Health data.** Sensitive, and Android's rules for reading it from an app like this one are
  unclear. Not without a reason and an experiment of its own.
- **Copying a voice, and listening all the time.** The first invites abuse. The second costs battery
  and trust; the wake word is offered only as a star, only while charging or in front.
- **A tablet-first design.** Wave 12 makes big screens correct and comfortable, not special.

## 13. Decisions that are the owner's

Few on purpose. Each has a recommendation, so "do as you suggest" is a complete answer.

| # | Decision | Recommendation | When |
|---|---|---|---|
| 1 | The order of the waves | As numbered. If the workspace matters more than the look right now: 0, 2, 3, 4, 5, 6, 7, then 1 and the rest | Before Wave 0 |
| 2 | From Wave 0 the owner's app becomes an optimised build signed with a private key, and agents test on a twin app ("Agirî Test", about 1.4 GB more on the phone) instead of the owner's app. Agreed? | Yes | Wave 0 |
| 3 | The first ten languages (1.8), and reading one page of Turkish (and one of Kurmanji, if wished) | The list as written; a page takes ten minutes | Wave 1 |
| 4 | Taste gate: the fire | Shown as pictures side by side | Wave 1 |
| 5 | Taste gate: does the sky follow the real day, or stay night as today? | Follow the day | Wave 1 |
| 6 | The weather: offer it at all, and which contact address its requests name (the service requires one, and the repository is public) | Offer it, switched off at first; an address made for the project that says nothing about its owner | Wave 1 |
| 7 | Taste gate: answers as open pages, or in boxes as today? | Open pages | Wave 2 |
| 8 | The gate: how strict? | As written for Wave 3 (features 3.4 and 3.5): harmless things are allowed, anything with an effect asks once the agent has read outside content, and whatever lasts waits for review | Wave 3 |
| 9 | The model for every day, now that cost is visible in money and the prompt is lighter (the open decision in `AGENTS.md`) | Decide with the figures of Wave 4 on the screen | After Wave 4 |
| 10 | Taste gates: the Canvas panel, and the deck themes | Shown as pictures side by side | Waves 5, 6 |
| 11 | The Linux image grows by the document libraries (about 8 MB of APK) | Yes | Wave 6 |
| 12 | Pictures: may one real picture be made to prove the paid path (a few cents), and what is the daily picture budget? | No picture until the owner wants one; everything is proven on the stand-in; the budget starts at zero | Wave 6 |
| 13 | The voices: which are good enough to recommend? | The owner's ear is final; ten sentences per candidate | Wave 8 |
| 14 | Phone powers: the defaults per tier | As in 9.1 | Wave 9 |
| 15 | Updates: look for them automatically once a week? | Off until the owner turns it on | Wave 10 |
| 16 | Which lists of regional and cultural days exist in the first set | A broad first set from every region; none switched on by itself | Wave 12 |
| 17 | The star of Wave 13 | The offline mind first (it makes helper tasks free), then the Wi-Fi door | Wave 13 |
| 18 | 1.0 on the June solstice, 21 June 2027? | Yes, with the stars optional and the extras that fell out moved to later versions; late summer if every extra has to be inside 1.0 | Now |
| 19 | Core and extras: may a wave ship without its extras when it runs long? | Yes; the list is gone through again in Wave 13 | Before Wave 0 |
| 20 | A spending cap set at the provider for the credential the twin uses (the hard stop behind the app's own budgets) | Yes, as low as the provider allows | Wave 0 |
| 21 | Where the app looks for updates: the address is a value of the build, and the repository it names is public. Keep today's, or move the project to a neutral name first? | Keep today's for now; it is one line to change | Wave 10 |
| 22 | Live voice through the provider (an extra with a price) | Not before the talk on the phone has been lived with; then one priced minute to judge | Wave 8 |

## 14. Appendix: facts this plan stands on

Checked on 2026-11-24, and again for the review on 2026-11-25. Hermes facts were read in the source of v0.23.3 as shipped in the app's image
(paths relative to `/root/.hermes/hermes-agent/`); section 3 gives file and line for the ones that
shaped the plan.

**Hermes, the Runs API** (`gateway/platforms/api_server_runs.py`)
- `POST /v1/runs` takes `input`, `instructions`, `conversation_history` (the content of each entry is
  turned into plain text), `session_id`, `model`, `provider`, `model_options`.
- `GET /v1/runs/{id}/events` is a server-sent stream in which every event has a number. It can be
  joined again with `Last-Event-ID` or `?last_seq=`. Closing the stream does not end the run.
- Events: `message.delta`, `message.interim`, `tool.started`, `tool.completed` (duration, error flag, a
  preview of at most 500 characters), `reasoning.available`, `subagent.start`, `subagent.complete`
  (goal, status, tokens, cost, files read and written), `approval.request`, `approval.responded`,
  `run.steered`, and one of `run.completed`, `run.failed`, `run.cancelled`, `run.interrupted`.
- Usage: `input_tokens`, `output_tokens`, `total_tokens`, `cache_read_tokens`, `cache_write_tokens`;
  the provider and model that really served.
- `/stop`, `/steer` (the text under `input`, `message` or `text`), `/approval`, `/clarify`.

**Hermes, tools and prompt**
- 24 tools are offered to an API client by default. Toolsets are chosen only by
  `platform_toolsets.api_server`; `no_mcp` keeps other MCP servers out; `agent.disabled_toolsets`
  removes sets everywhere.
- The parts of the fixed prompt and their switches are listed in section 9.3. `hermes prompt-size`
  reports them in characters, not tokens.
- The gateway reads `config.yaml` again for every request (`_load_gateway_config` in the request path),
  so tool lists change without a restart.
- Before every tool call Hermes asks its plugins (`pre_tool_call`: the tool, its arguments, the session,
  the call's id). A plugin may answer `block` (its message becomes the tool's result), `approve` (the
  call goes to the human-approval gate) or `modify`. `post_tool_call` follows every call. User plugins
  live in `plugins/<name>/` with a `plugin.yaml` and a `register(ctx)`, and load only when listed under
  `plugins.enabled`. The same gate exists for shell commands named in the configuration (`hooks:`,
  with `fail_closed`), at the price of one process per call.
- Among the default tools is `cronjob_manage` (Hermes's own scheduler). Folders named `hooks/<name>/`
  with a `handler.py` are run by the gateway on its events.
- MCP servers: `mcp_servers.<name>` with `url`, `headers` (values may be `${VAR}` from `.env`), and
  include and exclude lists. Their tools are named `mcp__<server>__<tool>`.
- The browser tools attach to a running browser through `browser.cdp_url` or `BROWSER_CDP_URL`.
- A text part of a message is cut at 65,536 characters. Audio and file parts are refused. Slash
  commands do not pass through the API.
- Writes to memory and skills can be held for review (`memory.write_approval`, `skills.write_approval`);
  for an API client they wait as files under `pending/` in Hermes's folder. The plain file tools are
  not covered by that gate.
- Profiles live under `profiles/<name>/` in Hermes's folder, each with its own configuration and its
  own `.env`, and are served under `/p/<name>/`. Serving them is on by default once a second profile
  exists, and from then on a profile reads only its own secrets.
- 134 optional skills and 76 MCP descriptions ship in the image. Sessions are kept in SQLite with
  full-text search and cost columns.

**Hermes, making things and finding things out** (read 2026-11-24)
- `skills/productivity/microsoft-office/scripts/`: `docx_create.py`, `docx_edit.py`, `docx_read.py`,
  `docx_comments.py`, `docx_revisions.py`, `docx_template.py`, `docx_validate.py`, `pptx_create.py`,
  `pptx_edit.py`, `pptx_from_template.py`, `pptx_read.py`, `pptx_render.py` (needs an office suite and
  falls back to an outline), `xlsx_create.py`, `xlsx_edit.py`, `xlsx_read.py`, `xlsx_recalc.py`,
  `xlsx_restructure.py`, `csv_to_xlsx.py`, `xlsx_to_csv.py`.
- A deck description: `slide_size` ("16:9" or "4:3") and `slides[]`, each with `layout` (title,
  title_content, section, two_content, title_only, blank), `title`, `subtitle`, `bullets` (text, or
  objects with text, level, bold, size, colour, font, italic, link), `background`, `footer`,
  `slide_number`, `notes`, `images`, `tables`, `shapes`, and `charts` (bar, bar_h, line, pie, with
  categories and series). Positions are in inches, colours as RRGGBB.
- A document description: `page` (size and margins in millimetres), `header`, `footer`, `styles[]`,
  and `blocks[]` (heading with level, paragraph with runs, bullet and numbered lists, table, image).
- The libraries: the extra `documents` (python-docx 1.2.0, openpyxl 3.1.5, python-pptx 1.0.2,
  reportlab 5.1.0, pypdf 6.21.4) is not part of the set the app's image installs; `documents-pdf` adds
  pdfplumber and pypdfium2. `hermes pm environment documents` builds a side environment.
- `skills/research/grounded-citations`: `scripts/sources.py` owns the mapping from an address to its
  number, keeps quotes, marks what is unverified; its file can be set with `--ledger` or
  `HERMES_CITATION_LEDGER`.
- Picture providers under `plugins/image_gen/`: deepinfra, fal, krea, meta-ai, openai, openai-codex,
  openrouter, xai. None talks to Google directly; OpenRouter resells Google's picture models. The
  tool `image_generate` is one of the default tools and becomes active as soon as
  `image_gen.provider` is set.
- The speech tool `text_to_speech` is a default tool too; its default provider is a public cloud
  voice (`edge`). Session titles are made by a call to the model unless
  `auxiliary.title_generation.model_upgrade_enabled` is false.
- Plugin hooks beyond the two around a tool call: `pre_llm_call`, `post_llm_call`,
  `pre_api_request`, `post_api_request`, `api_request_error`, `pre_auxiliary_call`,
  `post_auxiliary_call`, `subagent_start`, `subagent_stop`, `transform_tool_result`,
  `transform_terminal_output`, `transform_llm_output`. Whether a request can be refused or changed
  from one of them has not been tried (S10). A plugin's check that raises an error lets the call
  through.
- A run reports its usage only at its end.
- Reading a page without a key goes through outside services (Exa, Parallel, Firecrawl, Keenable).
  Results are cached for twenty minutes; a long page is cut to its head and tail at 15,000
  characters, with the whole text left in a cache file; a redirect is followed and reported under
  the address it ended at.
- The office skill looks for an interpreter that has the libraries and otherwise builds the side
  environment with both extras. `pptx_create.py` knows four kinds of chart (bar, bar_h, line, pie);
  `docx_create.py` writes flat lists and has no links, code blocks, footnotes or mathematics.
- `DELETE /api/sessions/{id}` removes a session.
- `post_tool_call` receives the tool's name, its arguments, the result, the ids of the session and
  the call, the duration and the status.
- `checkpoints`: off by default; one hidden git store under `checkpoints/` in Hermes's folder, which
  is not on the list of what survives an update; 20 snapshots per folder, 500 MB in all, files over
  10 MB skipped; taken once per turn, before the file tools (`write_file`, `patch`) and before
  terminal commands that look destructive, so what a script changes is not covered; restored by the
  typed command `/rollback`, which the API does not carry, though the functions behind it (list,
  difference, restore) can be called.
- `delegation.max_concurrent_children` is 10 and `delegation.max_spawn_depth` is 1 by default; a
  child may take 250 steps. `delegation.gateway_mode` decides whether the children of a gateway turn
  stay inside it (`inline`) or are cut loose, which is what happens to a turn from the API by
  default. A child's requests for approval are refused. The `subagent_start` hook carries the ids
  of parent and child.
- Hermes holds the sign-in of MCP servers itself (`tools/mcp_oauth*.py`), in `mcp-tokens/` in its
  folder. Its browser flow opens a browser inside the guest; the flow by code is `hermes mcp login`.
- The image has `git`, `ripgrep`, `curl` and `jq`; it has no office suite, no Node and no ffmpeg.

**Android, for an app that targets API 28 and runs on Android 17**
- Documented and not limited by the target: shared elements (Compose 1.10 and later), classic widgets,
  sharing shortcuts and Direct Share, quick-settings tiles, shaders (Android 13 and later), the haptic
  constants by Android version, job-scheduler triggers, the notification listener, the package installer
  (always with the user's confirmation).
- Not documented for this case, so tried first: the back gesture with progress, Live Updates and
  Samsung's Now Bar, bubbles, the assistant role (section 8).
- For every app on Android 17: sound started from the background fails silently; memory per app is limited.
- An app in the background cannot open another app's screen, whatever its target.
- Read on Android's reference pages on 2026-11-24: a PDF page's text and form fields are platform
  calls from API 35; one consent per screen capture is valid whatever the target; an entry of the
  text-selection menu returns text through its result; `Presentation` shows content on a second
  display since API 17; the platform's `BiometricPrompt` exists from API 28 and takes the phone's own
  PIN from API 30; a microphone service must be started while the app is visible; recognising speech
  on the device through the platform exists from API 31; Android 16 gives background jobs a quota.
- Not documented, so tried first: what the memory limit of Android 17 counts (S7), a second display
  under DeX (S22), a capture session at target 28 (S29), third-party wallpapers and screen savers on
  One UI (S38), the text menu on One UI (S39).

**Speech**
- The embedded sherpa-onnx 2.0.3 contains streaming and offline recognition, voice detection (Silero,
  TEN) and keyword spotting: no new library for voice.
- Turkish on the device: `seda-v0.1` (streaming, 73 MB, Apache-2.0, about 15 % word errors by its
  author's figures); Whisper tiny about 100 MB, small about 358 MB (about 12 % on a public Turkish test).
- Open-vocabulary keyword models exist for English and Chinese sound units only.
- Voices: the installed Supertonic model speaks 31 languages. The `tts-models` release of sherpa-onnx
  holds ready-converted Piper voices for dozens of languages (among them the Kurmanji voice
  `vits-piper-ku_TR-berfin_renas-medium`, 24 to 80 MB, CC-BY-NC-4.0), and the embedded library also
  contains the Kokoro, Kitten, Matcha and other voice families, language identification, punctuation,
  speaker separation and noise removal. The 1,600-language recogniser is 365 MB (Apache-2.0).

**Services and data** (read 2026-11-24 by a research agent; to be read again before each is used)
- Weather: `api.met.no` (the Norwegian Meteorological Institute) needs no key, allows any use with
  credit, asks for an identifying request header and for caching. Open-Meteo's free service is for
  non-commercial use only.
- Stars: the HYG database (CC BY-SA 4.0) holds about 120,000 stars; 513 are brighter than magnitude
  4.0 and about 1,600 brighter than 5.0. The licence of the Yale Bright Star Catalogue is unclear.
- Pictures: Google's picture models have no free allowance; a picture costs roughly 3 to 15 US cents
  by model and size; they are called through the ordinary message call with a picture as the answer.
- Libraries for the guest's Python 3.14 on arm64 exist for the whole office and data set (about 13 MB
  and 43 MB to download). PyMuPDF (AGPL) and pandoc (GPL) are left out.
- Renderers: Mermaid 12 (one file of 5.4 MB, MIT), KaTeX 0.19 (about 0.5 MB with fonts, MIT).
- Fonts: Ark Pixel (SIL OFL 1.1; 10 and 12 pixels; Chinese, Japanese, Korean; all variants together
  37 MB), Handjet (SIL OFL; Arabic, Hebrew, Greek, Cyrillic, Armenian, Latin; 0.3 MB; whether Arabic
  joins is not verified). No open pixel font was found for Indic scripts or Thai. Zpix is not open.

**The market** (pages read in November 2026)
- The vendors' own release notes and announcements: Claude (support.claude.com, release notes),
  ChatGPT (help.openai.com, release notes; openai.com on Pulse), Gemini (blog.google: the Gemini app
  and Gemini Live, the February 2026 drop), NotebookLM (blog.google, 2025), Pixel (blog.google, the
  Pixel 10 features), Apple (apple.com/newsroom, June 2026), Microsoft 365 Copilot
  (support.microsoft.com), Mistral (mistral.ai, the Le Chat launch).
- Press: Android Authority (NotebookLM video overviews on phones; the end of Pixel Studio), MacRumors
  (Comet on the iPhone), 9to5Mac (Meta AI), SamMobile (One UI 9).
- Open projects: Hermes's own Termux guide (hermes-agent.nousresearch.com), Google AI Edge Gallery and
  PocketPal AI (GitHub), the F-Droid page of `com.mobilefork.hermesagent`.
- Not opened, and so not relied on: the official pages of Grok, DeepSeek, Kimi, Doubao and Qwen.

**This repository**
- `tools/local-build.ps1` builds and installs in under a minute; CI needs about ten.
- The launcher accepts extra mounts (`PA_EXTRA_BIND`) and is shared with the CI smoke test.
- Only a fixed list of names in Hermes's folder survives the replacement of the Linux image
  (`runtime/UserState.kt`).
- Chat files are read leniently (`data/ChatJson.kt`), backups carry a format number
  (`data/BackupManager.kt`).

## 15. Words used in this plan

| Word | Meaning |
|---|---|
| Wave | A block of work, at least the size of Phase 3, that ends in a build proven on the phone; the five largest ship in two halves |
| Core, extra | What a wave is for and always ships, and what it ships without when it runs long |
| Contract | The document written at the start of a wave: exact names, one owner per file (`docs/waves/`) |
| Experiment (S1 to S45) | A question tried on the phone for at most an hour, before a contract depends on the answer |
| Test twin | A second installation of the app under its own name ("Agirî Test") where agents and the robot work, so the owner's app is never touched by a test |
| Taste gate | A choice of looks that only the owner makes, from pictures side by side |
| Practice model | A scripted stand-in for the language model; it runs on the phone and costs nothing |
| Stand-in | The same idea for things that cost money: a picture provider that draws a placeholder |
| Phone robot | The script that drives the app on the phone and checks what it shows |
| The stage, the scene | The picture of mountains, sky, fire and Pêt, and the code that draws it everywhere (app, widget, wallpaper, clock) |
| Fire feed | One number, "how hard is the agent working", that every fire in the app follows |
| Woodpile | The picture of what is left of today's budget |
| Work timeline | The steps the agent took for one answer, with time and result |
| Block | A typed piece of an answer that the app shows as something better than text: a question card, a canvas item, a deck |
| Run, stop, steer | One turn of the agent through Hermes's Runs API; ending it; adding an instruction while it works |
| Full mind, light mind | A bot with tools, skills and memory, or one that only talks and costs a fraction per message |
| Prompt weight | The tokens every message carries before the conversation itself |
| Helper | A small task (a title, a summary, a translation) sent straight to the provider without Hermes's prompt |
| Ability | A group of Hermes's tools, or one phone power, that has its own switch |
| The bridge | The small server inside the app through which the agent reaches the app and the phone |
| The gate | The one check in the app that every tool call of the agent passes before it runs, with its three rules |
| Tainted turn | A turn in which the agent has read anything the owner did not write (a page, a search result, a notification, a shared or imported file); from then on everything with an effect asks |
| Proposal card | What a block becomes when it would create or change something lasting (a bot, an app with doors, a theme): nothing exists until the owner taps |
| Consent by task, grant | A card that states what a long job needs (abilities, steps, a ceiling, a folder); starting it allows exactly that, for that run |
| Contained tool | A bridge tool that can change nothing but the folder of the run that called it, and therefore never asks |
| The meter | The gate's running count of what a turn has cost, kept while the turn is still working |
| Protected texts | The words a decision hangs on (Allow, Deny, a price, a warning); no language pack from a file can replace them |
| The keeper | The part that holds back what the agent wants to keep (skills, memory), snapshots everything lasting, and offers Undo |
| The ledger | The list, kept by the app, of what left the phone for one answer |
| Workspace | The agent's own folder, outside the Linux image, which survives updates |
| Pack | A set of libraries for the agent's Linux, downloaded on request and kept outside the image. Voices, speech models and fonts are downloads of their own; all of them come through one downloader and one catalogue with checksums |
| Canvas | The panel beside the chat where what the agent makes is shown, versioned, edited and exported |
| Item, version | One thing in the Canvas (a document, a deck, an app), and one state of it in its history |
| Small app | A page with a program in it that the agent wrote and the app runs in a sandbox (the first edition called it a pocket tool) |
| Recipe | A ready-made task with a short form; a file that can be edited and shared |
| Deep research | A long run in the background that plans, reads many pages with helpers, checks and writes a report with sources |
| Mark | What the app itself knows about a cited source: opened, not opened, quote matches, quote not found. It says the page was read, not that the page is right |
| Space | A place for one matter: its instructions, chats and folder from Wave 2, its knowledge and look from Wave 7 (what other apps call a project). The only way to group things |
| Library, shelf | The one place for every thing, and its sections: Made, Files, Pictures, Saved (with collections), the reading list, the inbox, recordings, study cards |
| Made, Toolbox | The shelf of everything the agent made, and its view of small apps |
| Agent tab | The tab that was called Hermes: the agent's mind, Abilities, skills and packs, connections, Safety and privacy, the terminal |
| Abilities, Hands | The one screen of switches for what the agent can do, and its section for the phone's powers |
| Safety and privacy | The one home of the rules, the approval mode, the log, the waiting room, and from Wave 11 what is stored and what left |
| Expedition | The view of a research at work: camps along a path, one per sub-question |
| Fireside | The hands-free talk on the scene |
| Night shift | Queued work that runs while the phone charges at night |
| Quick question | The small sheet that answers over whatever app is open |
| Council | A group chat with a way of talking: round table, debate, compare, relay, consensus |
| Keepsake | A small object in the scene that stands for something that really happened |
| Private chat | A chat that is not stored and cannot change memory or skills |
| Welcome | The first-run wizard |

