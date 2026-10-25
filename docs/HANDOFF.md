# Handoff — CleanYourTV, October 2025

Where the work stands, what is still open, and where everything lives. Read the three sections in order:
**state** (what is on the TV now), **open work** (what to pick up), **map** (where things are in the repo).

---

## 1. State

| | |
|---|---|
| TV | Sony Bravia KD-49X8500H (UR3), Android TV 9, ADB over the network at `192.168.1.3:5555` |
| Launcher on the TV | **2.04** (`versionCode` 204), the Google TV layout as Home |
| Branch | `claude/zen-euler-0mir9r` (commit 47b7086), ahead of `main` |
| Merged so far | PR #1 (merge 0c5f3e3): the Apple TV layout and the debloat work |
| Signing key | debug keystore, SHA-256 `a215696a…436e`. The owner holds `cleanyourtv-debug.keystore` (alias `androiddebugkey`, password `android`). **Never commit it.** Every build must keep this key, or the TV refuses the update. |
| Screensaver | the TV's own (Google Backdrop). The launcher never changed it; its Clock screensaver stays off. |

### Version numbers
`A.BB` in [`launcher/app/build.gradle`](../launcher/app/build.gradle): a fix or minor change raises BB
(2.04 → 2.05), a major change raises A (→ 3.00). `versionCode` = A×100 + BB and must always rise.

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
| Bug | Where | Note |
|---|---|---|
| The hold-OK menu for apps in the **bottom row** opens past the bottom edge of the screen | `GtvActivity.showMenu()` | Owner saw it in 2.04 and chose to fix it later. The Apple layout already solves this in `MainActivity.onTileLongPressed()`: it measures the panel and opens it **above** the tile when it would not fit below. Copy that approach. |

### Open: the APK size budget
The release APK is **about 880 KB**, and **some 830 KB of that is the two CMU Sans font files**
(`res/font/cmu_sans.ttf` and `cmu_sans_bold.ttf`, added in PR #1). CI's size gate was 440 KB, which this APK
cannot meet; on the owner's instruction the gate in `.github/workflows/ci.yml` was **raised to 900 KB** rather than
the fonts being trimmed. `main` had never run CI, so nothing flagged the growth when the fonts landed.

If the budget ever needs to come back down, trimming is measured and ready: the files carry 1,911 mapped
characters each (Greek, Cyrillic, maths and TeX tables), and keeping only what the launcher shows takes them from
830 KB to 227 KB, bringing the APK to roughly 280 KB. Thai, Hebrew and Chinese are unaffected — CMU never had
those glyphs, so they already fall back to the TV's own fonts.
```bash
pip install fonttools
python3 -m fontTools.subset launcher/app/src/main/res/font/cmu_sans.ttf \
  --unicodes=U+0020-007E,U+00A0-024F,U+0370-03FF,U+0400-04FF,U+2000-206F,U+20A0-20BF,U+2122,U+2190-21FF,U+2500-257F \
  --layout-features='*' --no-hinting --desubroutinize --output-file=cmu_sans.ttf
```
Also stale because of this: the README card image (`docs/images/card.jpg`) still reads "429 KB launcher". The
README text is corrected; the image needs regenerating with `tools/readme_card.py`, and its alt text matched to it.

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
but worth trimming: load each tile's artwork when it comes on screen and release it afterwards, the normal
RecyclerView pattern. Queued as a small 2.05 change, not urgent.

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
6. **Merge**: open a PR for `claude/zen-euler-0mir9r` and merge. The owner is the only person with access to this
   repository, so nobody else will review it.

### Decisions already made (do not re-ask)
- Google TV layout is the default (`default_layout` in `config.xml`).
- Your apps is the **same dock** as the Apple TV layout, kept in `order.txt`.
- Favorite apps are the owner's own list (`gtv_favorites.txt`), **Google TV layout only**; the Apple layout keeps
  showing all apps under the dock.
- Rows below dock sets the number of Favorite rows; Home sits on the bottom edge; ⌄⌄ marks the lowest row and
  Down from it opens the Apps tab.
- Both Google TV popups use the tab style; the Apple layout keeps its own.
- Continuous effects stay off by default; the screensaver stays the TV's own.

---

## 3. Map of the repository

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
| `MainActivity.java` | The **Apple TV layout** and the HOME component. When the Google TV layout is on it hands over to `GtvActivity` in `onCreate` and finishes; every lifecycle method returns early in that case (`redirected`) — that was the 2.01 crash. |
| `GtvActivity.java` | The **Google TV layout**: tabs, banner, the Home grid (Your apps + Favorites), Apps, Inputs, menus, Move. |
| `Config.java` | Reads `config.xml` plus theme JSON plus the owner's on-TV choices into one object. |
| `LauncherSettingsActivity.java` | On-TV Launcher settings (the screen the redesign replaces). |
| `Order.java` | The shared dock order (`order.txt`), used by both layouts. |
| `Favorites.java` | The Google TV Favorite apps list (`gtv_favorites.txt`). |
| `LibraryApps.java` | Home shortcut choices from the app library; `Favorites.java` follows its file pattern. |
| `InputPreview.java` | HDMI ports and the input preview. |
| `Fonts.java`, `GlowStyle.java` | The font and glow choices. |
| `ClockDream.java` | The launcher's own Clock screensaver (not selected on this TV). |

### Finding things quickly
- A string on screen → `grep -rn "the text" launcher/app/src/main/res/values/strings.xml`, then grep its name in `java/`.
- A setting → `config.xml` first (every key is commented), then `Config.java`, then where the field is read.
- Why a layout looks the way it does → `GtvActivity.build()` for Google TV, `res/layout/activity_main.xml` for Apple.
- What the owner already decided → `docs/proposals/google-tv-ui/README.md` §6.7 and §8.

---

## 4. Working with the owner

They are a lead mechanical engineer and the project manager: technical, short on time, and they test every build on
the real TV. What works best:
- One APK per round, named `tvhome-<version>.apk`, sent in the chat.
- A short numbered list of what to test, in the order they would press the buttons.
- Their feedback comes as quoted replies under each test item. "Good" means that item is done.
- They often add new items at the end of a round; treat those as the next round, not this one.
- When they say "just note for now", write it down (here, or in the proposal) and do not build it.
