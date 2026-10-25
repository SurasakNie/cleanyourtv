# Handoff — CleanYourTV, 25 October 2025

Where the work stands, what is still open, and where everything lives. Read the three sections in order:
**state** (what is on the TV now), **open work** (what to pick up), **keeping the TV healthy**, **map**
(where things are in the repo).

---

## 1. State

| | |
|---|---|
| TV | Sony Bravia KD-49X8500H (UR3), Android TV 9, ADB over the network at `192.168.1.3:5555` |
| Launcher on the TV | **2.19** (`versionCode` 219), the Google TV layout as Home — now the only layout. 2.17 was confirmed apart from the page-indicator bug 2.18 fixes; 2.18 and 2.19 are not tested yet |
| Branch | `claude/zen-euler-0mir9r`. PR #3 merged 2.05–2.12; PR #4 carries 2.13–2.14 |
| Merged so far | PR #1 (merge 0c5f3e3): the Apple TV layout and the debloat work. PR #2 (merge daad5b4): the Google TV layout at 2.04, this handoff and the repo map |
| Network | **wired Ethernet** (`eth0`, 192.168.1.3). No Wi-Fi address at all, which is why the card's Wi-Fi rows stay blank |
| Signing key | debug keystore, SHA-256 `a215696a…436e`. The owner holds `cleanyourtv-debug.keystore` (alias `androiddebugkey`, password `android`). **Never commit it.** Every build must keep this key, or the TV refuses the update. |
| Screensaver | the TV's own (Google Backdrop). The launcher never changed it; its Clock screensaver stays off. |

### Version numbers
`A.BB` in [`launcher/app/build.gradle`](../launcher/app/build.gradle): a fix or minor change raises BB
(2.19 → 2.20), a major change raises A (→ 3.00). `versionCode` = A×100 + BB and must always rise.

### What each build after the merge added
Newest first; PR #2 merged at 2.04.

| | |
|---|---|
| **2.19** | **Apple TV layout removal, stage 1.** `MainActivity` is now a 141-line shim: it keeps the HOME and LEANBACK intent filters (the TV's default-home setting names it, so that must not move) and opens `GtvActivity`. The Apple layout was also the fallback when Home failed to build, so it is replaced by a **recovery screen** in the shim — "Home could not start", with **Try again** and **TV settings** — drawn from nothing but that class, since the configuration, theme or fonts may be what failed. The `layout` preference, `GtvActivity.enabled()` and `fallBack()` are gone; a failure is recorded in `gtv_failed` and cleared when Home next draws. R8 drops the now unreferenced Apple code: the APK falls **598 KB → 440 KB**. Stage 2 (deleting the Apple-only classes, resources, strings, config keys and documents) waits on the owner's test of this one |
| **2.18** | **Page-indicator bug fix.** Holding left or right left a row of half-open dashes instead of one pill: `showDot()` animated only the new dot and the one before it, each from its resting width, so every dot further back was stranded part-way open when key repeat cut the 140 ms animation short. It now starts from the widths the dots actually have and moves all of them, so any interrupted run heals itself. Stepping one app at a time always worked, which is why 2.16 and 2.17 passed |
| **2.17** | The banner sits **18 dp lower**, its own 16 dp of padding now the only clearance it keeps from "Your apps" — that is as low as it can go without crossing the rows, so the next lever would be moving the rows down; the page-indicator dots moved **above** the "Last opened" line, which stops them jumping when an app has never been opened; the Home/Apps/Inputs capsules are frosted glass **with no outline**; and both CMU Sans fonts are **subset**, taking the APK from 893 KB to **598 KB** — 302 KB clear of the 900 KB gate |
| **2.16** | The banner sits just above Your apps instead of floating near the top, and a **page indicator** (an expanding dot) under it shows which dock app is selected. The dots cover **Your apps only** — in the Favorite rows, the Apps tab and Inputs the indicator hides rather than showing a position it does not describe |
| **2.15** | The banner is plain text over the glow — no fill, no outline (**the glass fill is owed back as an effects switch**); settings gains an **About this UI** section (version, layout, TV, Android, and what this is); the **Layout row is gone**, so Google TV is the only layout you can pick — the first part of removing the Apple TV layout |
| **2.14** | The settings panel is wider (420dp) with slightly smaller text, so more rows fit; the banner lost its outline, keeping the glass fill |
| **2.13** | Launcher settings is now a **panel down the right-hand side over Home**, in the TV's own style: Home stays visible and dimmed, the rows are grouped under section headings, and the clock and gear step aside while it is open. Every setting from the one-list version is kept. Choice pages come next |
| **2.12** | The selection frame is gone: a selected tile is simply zoomed and lifted. The owner tried the ring (2.09, 2.11) and dimming the others (2.10) and settled on the zoom alone |
| **2.11** | Selection is the white ring again (2.10's "dim everything else" was tried and turned down for ordinary selection); the carried tile keeps 2.10's lifted, ghosted look with the others faded; the banner shows **only while an app is selected**, so arriving at Home names nothing until Down picks one |
| **2.10** | The selected tile is now told apart by **dimming the others** (a white ring was invisible on white artwork such as Netflix or the Play Store); a tile being moved is lifted and ghosted instead of framed; and Home from another tab no longer leaves the banner naming whatever was selected over there |
| **2.09** | **Reverted 2.08's artwork change** (see below); the carried tile's frame now covers the artwork's edges; the hold-OK menu panel is solid, because tiles read straight through the sheer one over a bright wallpaper |
| **2.08** | The hold-OK menu opens above a tile when there is no room below (bottom-row apps were cut off); a load-average figure stands in where the TV blocks the processor reading; **and an artwork change that was measured worse and reverted in 2.09** |
| **2.07** | The card shrinks before it hides: every figure where there is room, then the essential few (processor, memory used, storage used, frames), then nothing. Before this it vanished entirely at 2 or more Favorite rows |
| **2.06** | Memory and storage also shown as used / total. The owner chose to **keep both** the free and used rows |
| **2.05** | The performance card (Launcher settings → Performance card, off by default) and its `Stats` reader; pressing Home now lands on the Home tab rather than the first app |

### Build, test, audit
```bash
cd launcher
ANDROID_HOME=/root/android-sdk ./gradlew assembleRelease \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease    # lint needs Maven (blocked here)
cd .. && python3 -m unittest discover -s tests </dev/null                      # the </dev/null matters: it hangs without it
apksigner verify --print-certs launcher/app/build/outputs/apk/release/app-release.apk | grep SHA-256
aapt2 dump badging  launcher/app/build/outputs/apk/release/app-release.apk | grep -E '^package|uses-permission'
```
Check the APK exists before copying it anywhere: a failed build leaves the previous one in place, and an old APK
has been sent to the TV that way before. There is no emulator in the cloud container (no KVM), so **layout
changes are proved by the owner's photos**, not by running the app here.

### Checking the TV for crashes
After installing and testing a build, from `C:\Users\<user>\platform-tools`:
```powershell
.\adb.exe logcat -c                                              # clear, then use the TV with the remote
.\adb.exe logcat -d | findstr /i "FATAL.*gathin AndroidRuntime"  # empty = no crash
```
`findstr` treats the two space-separated terms as a plain OR (`.*` is only a wildcard with `/R`), so an empty
result means not even an `AndroidRuntime` line was printed: the app did not crash at all. **2.04 came back empty
after a full test round**, as did 2.03.

Two things in the output are normal, not faults: lines from `uid 2000` with `com.android.commands.input`
(your own `adb shell input` commands) and `BufferQueueProducer` / `SurfaceFlinger` lines when a screen closes.
Clear the log *before* testing, not after — checking straight after `logcat -c` always looks clean.

### House rules this project follows
- Risk levels: RSK-0 irreversible → ask first; RSK-1 costly to undo → do it and explain; RSK-2 → just do it.
- No guessing: state assumptions; never invent infrastructure or settings.
- Verify before saying done: build output, test output, APK audit.
- Say the blast radius, the assumptions and the rollback before a big change.
- Only what was asked. Surface anything else instead of quietly building it.
- **Standing layout rule:** before sending any APK, check both layouts for text or panels overlapping the top bar
  or the rows, at Rows below dock 0–3 and 5–8 apps per row. Ask the owner for 4 photos
  (Apple and Google TV × rows 0 and 3).
- Every reply to the owner ends with a status block (🟢 READY / 🟡 INPUT NEEDED / 🔴 BLOCKED).

---

## 2. Open work

### Known bugs
None open. The bottom-row menu was fixed in 2.08 (`GtvActivity.showPanel()` now measures the panel and opens it
above the tile when it will not fit below, the same rule `MainActivity.onTileLongPressed()` uses).

### The APK size budget — resolved in 2.17
**2.19 is 440 KB against the 900 KB gate — 460 KB clear.** The squeeze is over: the two CMU Sans font files were
subset in 2.17, from 830 KB down to 227 KB, with no visible change (811 and 809 mapped characters, every glyph the
launcher draws present, all layout features kept). Thai, Hebrew and Chinese are unaffected — CMU never had those
glyphs, so they already fall back to the TV's own fonts.

History, because it explains the gate: the fonts arrived in PR #1 and took the APK to about 893 KB. CI's size gate
was 440 KB, which that APK could not meet; on the owner's instruction the gate in `.github/workflows/ci.yml` was
**raised to 900 KB**. `main` had never run CI, so nothing flagged the growth when the fonts landed. By 2.16 the
margin was 7 KB, and 2.17 trimmed the fonts rather than raising the gate a second time. The recipe, if the fonts
are ever regenerated:
```bash
pip install fonttools
python3 -m fontTools.subset launcher/app/src/main/res/font/cmu_sans.ttf \
  --unicodes=U+0020-007E,U+00A0-024F,U+0370-03FF,U+0400-04FF,U+2000-206F,U+20A0-20BF,U+2122,U+2190-21FF,U+2500-257F \
  --layout-features='*' --no-hinting --desubroutinize --output-file=cmu_sans.ttf
```
Also stale because of this: the README card image (`docs/images/card.jpg`) still reads **"429 KB launcher"**. The
README text, its alt text and `tools/readme_stats.py` now all say 880 KB, so a re-render will be correct — but the
picture itself could not be rebuilt here: `tools/readme_stats.py` and `readme_card.py` need `Fredoka.ttf`, which is
not in the repo, and this container's network policy refuses `fonts.google.com` and `cdn.jsdelivr.net`. To finish
it: get Fredoka (the variable TTF from Google Fonts), then
`tools/venv/bin/python -I tools/readme_stats.py Fredoka.ttf docs/images/stats.png`, the same for
`readme_featured.py`, then `readme_card.py Fredoka.ttf docs/images/card.png` — and note the README points at
`card.jpg`, so the PNG still has to be converted. Leave the TV-model count alone: `tests/test_examples.py` checks
it against the folders.

### Diagnosed: a streaming app lags (not a launcher bug)
**Symptom:** iQIYI / WeTV / Youku showed a black screen on a fresh start, then slow browsing and stuttering.

**Cause, measured over ADB:** the TV has 2.36 GB of RAM and reported **`status critical`, 301 MB free and 0 KB
cached**. Zero cached means no app can stay warm, so every launch starts from nothing and evicts something else.
**iQIYI alone held 308–425 MB**, five times any other app. The ad-blocking-DNS theory was ruled out (no resolver
set on the TV) and `/data` was 84% full.

**What fixed it (October 2025):**
```powershell
.\adb.exe shell am force-stop com.iqiyi.i18n.tv
.\adb.exe shell pm trim-caches 2G
.\adb.exe shell pm disable-user --user 0 com.google.android.tvrecommendations   # reverse: pm enable <pkg>
```
plus clearing iQIYI's cache on the TV (Settings → Apps → iQIYI → Clear cache; never "Clear data", it logs you out).
Result: **status normal, 741 MB free, 309 MB cached.**

**It comes back.** iQIYI grows again the longer it runs, so the force-stop is the repeatable reset. Nothing here
touches the app, the Play Store or Play services, so updates keep working — that was the owner's condition.

**Our own share, measured honestly:** the launcher is ~39 MB (peaks near 56 MB with the Apps grid open), of which
about **22 MB is app artwork held in the native heap** — bitmaps for every installed app, loaded up front in
`GtvActivity.loadApps()`. Its Java heap is 2.3 MB, so there is no leak. It is a minor contributor next to iQIYI,
**Tried in 2.08 and reverted in 2.09 — measured worse, not better.** Holding no artwork and loading it per tile in
`onBindViewHolder` meant every tile scrolling into view decoded a fresh copy, and the selection reloaded a banner
just to sample the glow colour. The native heap went **21.8 MB → 33.9 MB → 38.0 MB and was still climbing**; the
visible behaviour was fine (no blank tiles), the memory was not. Trading memory for repeated decoding is the wrong
trade on this TV.

**Not a leak — measured and cleared (2.09).** A clean run (`am force-stop`, then Home, then two or three minutes of
Home and the Apps tab with no settings visits) reports **`Activities: 1`, `Views: 59` and a steady native heap of
19.9 MB**. The 38–50 MB seen earlier came from the testing pattern itself: every settings toggle and every APK
install makes Home rebuild, and the discarded screens sit in memory until Android collects them. An hour of about
ten installs and a dozen toggles piles them up; ordinary use does not. Read `Activities` only after a clean start —
right after a visit to Launcher settings, two or three screens legitimately exist.

Worth improving one day, though not a bug: `GtvActivity` calls `recreate()` for **any** settings change
(`onResume`, `onActivityResult`, and the package receiver), which re-decodes every app's artwork. Most settings
could be re-applied without rebuilding.

**If this 18–22 MB is worth reclaiming, the approach to try is downsampling, not on-demand loading:** decode each
banner at roughly tile size (about 207×116 px at 8 per row) instead of its full resolution, keeping one copy per
app as now. That reduces the bytes rather than moving the cost around. Measure
`dumpsys meminfo com.gathin.tvhome` before and after, and do not claim a win without it — 2.08 is the cautionary
tale.

### The performance card: four figures this TV will not give
The card (Launcher settings → Performance card, added in 2.05) shows memory, storage, frames, network and uptime
on the KD-49X8500H. Four of its figures stay blank here, each for a known reason:

| Figure | Why | Possible follow-up |
|---|---|---|
| **Processor** | Android 9 blocks apps from `/proc/stat`, and this set also refuses the `cpuidle` fallback, so both sources in `Stats.cpuPercent()` are dead. The Apple layout's stats line has the same gap | **Done in 2.08:** `Stats.loadAverage()` reads `/proc/loadavg` instead and the card shows **Load (1 min)**. A load figure, not a percentage: 1.00 means about one core's worth of work. If that file is closed too, the row stays out |
| **Temperature** | The thermal sensors are not readable by apps on this set (`Stats.temperatureC()` probes zones 0–11, then gives up). No public API on Android 9 replaces it | None |
| **Wi‑Fi signal**, **Link speed** | **Confirmed: the TV is wired.** `ip -o addr` shows an address on `eth0` and nothing on `wlan0`, so there is no Wi-Fi to report and leaving the rows out is correct, not a fault | A **Network type** row (Wi‑Fi / Ethernet / offline) was offered and declined as clutter: it would never change on a wired set. The detection already exists in `MainActivity` (`NetworkCapabilities.TRANSPORT_ETHERNET`, which drives the top-bar pill and its `ic_ethernet` icon) |

Nothing is built for these: the owner has not asked for them, and two of the three have no fix.

### In flight: removing the Apple TV layout
The owner decided to remove the Apple TV layout **including its code**, not just the setting.

**Stage 1 is done in 2.19** and waits on the owner's test:
- `MainActivity` is a shim that keeps the HOME intent filter — the TV's default-home setting names
  `com.gathin.tvhome/.MainActivity`, and moving HOME to `GtvActivity` would make Android treat this as a different
  launcher, leaving Home to be chosen again with the stock launcher likely to win in between.
- The Apple layout was the fallback that stopped the TV being left with no Home. It is replaced by the shim's
  **recovery screen**: "Home could not start", with Try again and TV settings. It is drawn in code from nothing
  but that class, because the configuration, a pushed theme or the fonts may be exactly what failed.
- The `layout` preference is gone, with `GtvActivity.enabled()` and `fallBack()`. A failure now sets
  `gtv_failed` in the launcher's preferences (or is inferred from a dead `gtv_starting_pid`), and a successful
  draw clears both.

**What to test before stage 2:** Home still comes up on HOME; the TV's default-home setting still points here
after installing; "Launcher settings" from the remote's Settings button still opens; and the recovery screen can
be reached on purpose —
`adb shell am force-stop com.gathin.tvhome; adb shell "am start -n com.gathin.tvhome/.MainActivity -a com.gathin.tvhome.action.RECOVERY"`
— with Try again returning to Home and TV settings opening the TV's own settings.

**Stage 2, after that test:** delete the Apple-only classes (`AppAdapter` beyond `loadBanner`/`vividColor`,
`AppLibraryDialog`, `Cards`, `CardFeature`, `GlassView`, `HeroArt`, `DockDecoration`, `DockSlots`,
`GridNavigation`, `VpnBadge`, the festive set, `Sky`/`SkyView`/`Sun`, `Shamsi`, `RaceWeekend`), their layouts and
drawables, the dead strings in both languages (`layout_apple`, `hint_rows`, the Apple-only labels), the dead
`config.xml` keys, and the parts of the README, the specs and the themes that describe the layout. R8 already
drops the code from the APK, so stage 2 is about the source tree, not the size.

**Off limits in stage 2, on the owner's instruction:** the remote's Settings button path stays exactly as it is.
`QuickSettingsActivity`, the `ACTION_LAUNCHER_SETTINGS` string and the shim's handling of it were not changed by
stage 1 (that code is 2.18's redirect branch line for line) and are not to be changed by stage 2 either.

**This is a fork of `Gathin23/cleanyourtv`,** where that layout is the project's main feature, so the removal
diverges permanently and makes merging from upstream painful — the owner chose this knowingly.

### Next phases (from the proposal, [`docs/proposals/google-tv-ui/README.md`](proposals/google-tv-ui/README.md))
1. **Settings redesign** (§3, the largest item). The owner picked a **right-side panel in the TV's own style**:
   dark panel sliding in from the right over Home, title on top, one list, Back closes it, choices open as the
   next page in the panel. Two owner notes belong here: a **banner title size** setting and a **banner border
   on/off** toggle. The owner wants to revisit the look when this starts.
2. **Phase 2 layout**: Inputs row on Home, Live-input banner, side-rail navigation option, glass bars.
3. **Effects** (§4): the focus and background effects chosen in §6.7.
4. **Wallpapers** (§5): new sets, the 4K quality fix, online wallpapers (opt-in). **Check the licences first**
   for the sources listed in §8 Q7 (Wikimedia, Earth View, NASA, NatGeo, Apple) before any of them ship.
5. **Hebrew and polish**: right-to-left for the new screens, a Hebrew strings pass, final tidy-up.
6. **Merging**: done per set of builds — PR #1 (the Apple layout and the debloat), PR #2 (2.04), PR #3 (2.05–2.12),
   PR #4 (2.13–2.14). The owner is the only person with access, so nobody else reviews; merge once CI is green.

### Decisions already made (do not re-ask)
- Google TV layout is the default (`default_layout` in `config.xml`).
- Your apps is the **same dock** as the Apple TV layout, kept in `order.txt`.
- Favorite apps are the owner's own list (`gtv_favorites.txt`), **Google TV layout only**; the Apple layout keeps
  showing all apps under the dock.
- Rows below dock sets the number of Favorite rows; Home sits on the bottom edge; ⌄⌄ marks the lowest row and
  Down from it opens the Apps tab.
- Both Google TV popups use the tab style; the Apple layout keeps its own.
- The performance card keeps **both** the free and the used rows for memory and storage, and shrinks to the
  essential figures before hiding itself (2.07). No Network type row.
- Pressing Home leaves the selection on the **Home tab**, not on the first app, and the banner stays hidden until
  an app is selected.
- **A selected tile is told apart by the zoom and lift alone — no frame, no dimming.** Three styles were tried on
  the TV before this one settled: a white ring (2.09, 2.11), which says nothing on white artwork such as Netflix or
  the Play Store, and dimming every other tile (2.10), which the owner turned down. Dimming survives only while a
  tile is being carried. Please do not "improve" any of it back.
- Continuous effects stay off by default; the screensaver stays the TV's own.

---

## 3. Keeping the TV healthy

The TV is a 2.36 GB set running heavy streaming apps, so memory and storage fill up again over weeks of use. When
it starts feeling slow, run these from `platform-tools`. Both are safe to repeat, and **neither affects an app's
ability to update** — that was the owner's condition.

```powershell
.\adb.exe shell am force-stop com.iqiyi.i18n.tv        # the biggest single consumer: 308-425 MB
.\adb.exe shell pm trim-caches 2G                       # freed about 700 MB the first time
```

Check whether it helped — look for `status normal` and a non-zero cached figure:
```powershell
.\adb.exe shell "dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM'"
.\adb.exe shell dumpsys diskstats | head -5
```

Healthy numbers as of October 2025, after the clean-up: **status normal, 741 MB free, 309 MB cached, 27% of
`/data` free.** Before it: critical, 301 MB free, **0 KB cached**, 16% free.

Notes for whoever picks this up:
- **Zero cached is the red flag**, not low free memory. With nothing cached, every app launch is a cold start,
  which is what made Youku show a black screen on first open (and only the first — the second was warm and fine).
- Apps still hold about **1 GB of cache** that `trim-caches` will not take. The TV's own **Settings → Apps** lists
  apps by size and clears each one's cache; that is more reliable than anything queryable over ADB, because
  `adb shell` cannot read inside `/data/data`.
- `com.google.android.tvrecommendations` is **disabled** (it only fed rows on Sony's stock home). Reverse with
  `pm enable com.google.android.tvrecommendations`.
- Sony's `com.sony.dtv.tvx` holds ~64 MB and was left alone: on some Sony sets it is tied to Live TV or the
  inputs, so check what depends on it before touching it.
- **Never** disable a streaming app, the Play Store or Play services to save memory. That breaks updates.
- Clearing an app's **cache** is safe; clearing its **data** logs you out.

## 4. Map of the repository

A short version. The full one, with every launcher class grouped by job and every test explained, is
[`MOC.md`](MOC.md).

```
README.md                  What the project is, quick start, the launcher's own section (settings, keys, versioning)
PROMPT.md                  The brief this project was built from
CONTRIBUTING.md            How changes are made
SECURITY.md                How to report a problem
SHOWCASE.md                Screenshots of the result

launcher/                  The Android TV launcher (com.gathin.tvhome), plain Java, no network, no services
  app/build.gradle           versionCode / versionName (A.BB), minSdk 26, signing
  app/src/main/
    java/com/gathin/tvhome/  See the file guide below
    res/values/config.xml    Dock order, hidden apps, columns, rows, feature switches — read this first
    res/values/strings.xml   English strings; every one needs a Hebrew twin in res/values-iw/ (a test enforces it)
    res/layout/              Apple TV layout XML (the Google TV screen is built in code)
  build-and-install.sh/.ps1  Build and push to the TV

tools/                     Python and shell for the TV itself
  tv.py                      ADB helper: probe the TV, run commands, push files
  disable.*/restore.*        The debloat and its undo
  theme.*                    Push or reset a theme
  check.py, recovery.py      Checks and the recovery record

themes/                    Five themes (config.json + wallpaper); themes/README.md lists every key
examples/                  Per-device debloat records (one folder per TV model, hundreds of them)
docs/                      This handoff, troubleshooting, the remote-key guide, module and card specs
  MOC.md                     Map of Content: every folder and file, and where to start for a given task
  images/                    Every screenshot and rendered image the documents use
  proposals/google-tv-ui/    The full UI proposal: layout, settings, effects, wallpapers, ideas, owner answers
tests/                     Python tests; CI runs them (.github/workflows/ci.yml). No Android tests run in CI.
```

### Launcher files that matter most
| File | What it does |
|---|---|
| `MainActivity.java` | The **HOME component**, and since 2.19 nothing else: it opens `GtvActivity` and finishes. It also holds the **recovery screen** shown when Home cannot be built. It was the Apple TV layout until 2.19. |
| `GtvActivity.java` | The **Google TV layout**: tabs, banner, the Home grid (Your apps + Favorites), Apps, Inputs, menus, Move. |
| `Config.java` | Reads `config.xml` plus theme JSON plus the owner's on-TV choices into one object. |
| `LauncherSettingsActivity.java` | On-TV Launcher settings (the screen the redesign replaces). |
| `Order.java` | The dock order (`order.txt`), shared with the removed Apple layout and so still a file of its own. |
| `Favorites.java` | The Google TV Favorite apps list (`gtv_favorites.txt`). |
| `LibraryApps.java` | Home shortcut choices from the app library; `Favorites.java` follows its file pattern. |
| `InputPreview.java` | HDMI ports and the input preview. |
| `Fonts.java`, `GlowStyle.java` | The font and glow choices. |
| `ClockDream.java` | The launcher's own Clock screensaver (not selected on this TV). |

### Finding things quickly
- A string on screen → `grep -rn "the text" launcher/app/src/main/res/values/strings.xml`, then grep its name in `java/`.
- A setting → `config.xml` first (every key is commented), then `Config.java`, then where the field is read.
- Why Home looks the way it does → `GtvActivity.build()`. (`res/layout/activity_main.xml` is the removed Apple layout, still in the tree until stage 2.)
- What the owner already decided → `docs/proposals/google-tv-ui/README.md` §6.7 and §8.

---

## 5. Working with the owner

They are a lead mechanical engineer and the project manager: technical, short on time, and they test every build on
the real TV. What works best:
- One APK per round, named `tvhome-<version>.apk`, sent in the chat.
- A short numbered list of what to test, in the order they would press the buttons.
- Their feedback comes as quoted replies under each test item. "Good" means that item is done.
- They often add new items at the end of a round; treat those as the next round, not this one.
- When they say "just note for now", write it down (here, or in the proposal) and do not build it.
