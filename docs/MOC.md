# Map of Content

Every part of this repository, what it is for, and where to start. The companion to
[`HANDOFF.md`](HANDOFF.md), which covers the current state of the work instead of the structure.

| If you want to… | Go to |
|---|---|
| Understand the project | [`README.md`](../README.md) |
| Know what is built and what is next | [`HANDOFF.md`](HANDOFF.md) |
| Debloat a TV | [`tools/`](#tools) + [`examples/`](#examples) |
| Change the launcher | [`launcher/`](#launcher) |
| Change how Home looks without rebuilding | [`themes/`](#themes) |
| Fix something that went wrong | [`docs/TROUBLESHOOTING.md`](TROUBLESHOOTING.md) |
| Speed up a TV that has gone sluggish | [`HANDOFF.md` § Keeping the TV healthy](HANDOFF.md#3-keeping-the-tv-healthy) |
| See the plan for the new UI | [`docs/proposals/google-tv-ui/`](proposals/google-tv-ui/README.md) |

---

## Root

| File | What it is |
|---|---|
| [`README.md`](../README.md) | The main document: what the project does, quick start, the TV table, the launcher's own section (settings, remote keys, versioning), restoring a TV, troubleshooting, FAQ |
| [`README.zh-CN.md`](../README.zh-CN.md) | Simplified Chinese translation of the README |
| [`SHOWCASE.md`](../SHOWCASE.md) | Screenshots and press coverage |
| [`PROMPT.md`](../PROMPT.md) | The brief this project was built from |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md) | How to send a TV list or a code change |
| [`SECURITY.md`](../SECURITY.md), [`CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md), [`LICENSE`](../LICENSE) | Policies |

---

## launcher

The Android TV launcher, `com.gathin.tvhome`: plain Java with AndroidX RecyclerView, no network, no
background services, built for sets with 1 GB of RAM. `minSdk` 26.

| Path | What it is |
|---|---|
| [`launcher/app/build.gradle`](../launcher/app/build.gradle) | `versionCode` / `versionName` (`A.BB`), `minSdk`, signing |
| `launcher/app/src/main/res/values/config.xml` | **Start here for behaviour**: dock order, hidden apps, Live TV package, columns, rows under the dock, and every feature switch, each one commented |
| `launcher/app/src/main/res/values/strings.xml` | English strings. Every one needs a Hebrew twin in `values-iw/` — `tests/test_hebrew_resources.py` enforces it |
| `launcher/app/src/main/res/layout/` | The Apple TV layout in XML (`activity_main.xml`) and the settings screen. The Google TV screen is built in code, not XML |
| `launcher/build-and-install.sh` / `.ps1` | Build and push to a TV in one command |

### The two layouts

| Class | What it is |
|---|---|
| `MainActivity.java` | The **Apple TV layout**, and the component Android calls Home. When the Google TV layout is chosen it starts `GtvActivity` and finishes immediately; every lifecycle method then returns early (the `redirected` flag) |
| `GtvActivity.java` | The **Google TV layout**: tabs, the glass banner, the Home grid (Your apps + Favorite rows), the Apps and Inputs tabs, the menus, and Move |

### Supporting classes, by job

| Job | Classes |
|---|---|
| Settings and themes | `Config` (config.xml + theme JSON + on-TV choices), `LauncherSettingsActivity`, `ThemeFiles`, `ThemeProvider`, `QuickSettingsActivity`, `HomeSettingsActivity` |
| App lists and order | `AppAdapter`, `Order` (the shared dock, `order.txt`), `Favorites` (Google TV favorites, `gtv_favorites.txt`), `LibraryApps`, `AppLibraryDialog`, `DockSlots`, `DockDecoration`, `GridNavigation`, `LastOpened` |
| Inputs and remote keys | `InputPreview`, `RemoteKeys`, `InputKeyService`, `HomeKeyService` |
| Look | `Fonts`, `GlowStyle`, `GlassView`, `HeroArt`, `WallpaperPlayer`, `WallpaperRotation`, `WallpaperSize`, `Sky`, `SkyView`, `Sun` |
| Screensaver | `ClockDream`, `ClockActivity`, `ClockScene` |
| Module cards | `Cards`, `CardFeature` (contract: [`docs/specs/card.md`](specs/card.md), guide: [`docs/modules.md`](modules.md)) |
| Festive skin | `Festival`, `DiyaDrawable`, `MarigoldDrawable`, `BellDrawable`, `PetalShowerView`, `Shamsi`, `RaceWeekend` |

---

## tools

Python and shell that talk to the TV over ADB. Nothing here runs on the TV itself.

| Path | What it is |
|---|---|
| [`tools/tv.py`](../tools/tv.py) | The main helper: probe a TV (model, Android version, firmware), run commands, push files, take screenshots |
| `tools/disable.sh` / `.ps1` | Apply a debloat list |
| `tools/restore.sh` / `.ps1` | Undo it and put the stock launcher back |
| `tools/theme.sh` / `.ps1` | Push or reset a theme, no rebuild |
| `tools/check.py` | What a firmware update undid |
| `tools/recovery.py` | The recovery record |
| `tools/b64push.py`, `tools/relay.py` (+ [`relay.md`](../tools/relay.md)) | Pushing files and the relay helper |
| `tools/readme_card.py`, `readme_featured.py`, `readme_stats.py` | Render the README card images |
| `tools/scenes_wallpapers.py`, `race_calendar.py` | Theme wallpapers and the race calendar |
| `tools/requirements.txt`, `tools/venv/` | Python dependencies (`tools/venv/bin/python -I` is how the scripts are run) |

---

## themes

Five themes. A theme is a `config.json` plus a wallpaper, pushed to the TV with no rebuild.
[`themes/README.md`](../themes/README.md) documents every key a theme may set.

`default` · `aurora` · `dussehra` · `scenes` · `six-columns`

---

## examples

One folder per TV model (160 of them), each with a `debloat.txt`: the packages that are safe to disable on
that firmware, grouped and commented. [`examples/README.md`](../examples/README.md) is the index table.

- Folder names are `brand-model`, so the flat list already sorts by brand (`tcl-…` 34, `sony-…` 20, `xiaomi-…` 19).
- `tests/test_examples.py` checks that the folders, the table in `examples/README.md` and the TV table in
  `README.md` all agree. A new folder without table rows fails CI.
- A handful of TVs have a longer write-up in [`docs/devices/`](devices/) instead of, or beside, their list.

**Decided (October 2025): this folder stays flat.** Grouping it into brand subfolders was considered and turned
down: the names already sort by brand, the move would rewrite all 160 rows of the index table plus the TV table in
`README.md`, the Chinese README, `SHOWCASE.md`, the issue template and the script examples, and — because this is a
public repository — it would permanently break every outside link to `examples/<model>` in issues, pull requests,
articles and bookmarks. The cost is real and the gain is cosmetic, so please do not revisit it without a new reason.

---

## docs

| Path | What it is |
|---|---|
| [`HANDOFF.md`](HANDOFF.md) | Current state, open work, how the owner tests |
| `MOC.md` | This map |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | When something goes wrong on a TV |
| [`REMOTE.md`](REMOTE.md) | Remote keys and the accessibility services |
| [`app-library.md`](app-library.md) | The optional app library |
| [`modules.md`](modules.md) + [`specs/card.md`](specs/card.md) | Module cards: the guide and the contract |
| [`sky.md`](sky.md) | The drawn sky backdrop |
| [`PRESS.md`](PRESS.md) + `press/` | Coverage and its logos |
| [`devices/`](devices/) | Per-TV write-ups that are too long for an examples row |
| [`proposals/google-tv-ui/`](proposals/google-tv-ui/README.md) | The full UI proposal: layout, settings redesign, effects, wallpapers, ideas from other launchers, and the owner's answers. **Read §6.7 and §8 before proposing UI work** |
| `images/` | Every screenshot and rendered image the documents use |

---

## tests

Python tests, run by [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) on every push.
**No Android tests run in CI** — the launcher's own Gradle tests and lint need Maven, which the build
container cannot reach.

```bash
python3 -m unittest discover -s tests </dev/null    # the </dev/null matters: it hangs without it
```

| Test | What it guards |
|---|---|
| `test_examples.py` | The example folders and both index tables agree |
| `test_hebrew_resources.py` | Every English string has a Hebrew twin, with the same format arguments, and the Hebrew layout has the required views |
| `test_gtv_favorites.py` | The Google TV favorites file: format, cap, atomic write, and its menu strings |
| `test_launcher_handover.py`, `test_launcher_min_sdk.py` | Home handover and the SDK floor |
| `test_home_guard.py`, `test_setupwraith_guard.py`, `test_tvinput_guard.py` | The debloat scripts never leave a TV without a Home, a Wi-Fi owner or its inputs |
| `test_adb_*.py`, `test_b64push_atomic.py`, `test_relay.py` | The ADB helpers |
| `test_drift_check.py`, `test_recovery_record.py` | Firmware-update drift and the recovery record |
| `test_*_note.py`, `test_issue577_report.py` | Specific device notes stay accurate |

---

## Conventions

- **Versions**: `A.BB`; a fix raises BB, a major change raises A; `versionCode` = A×100 + BB and only ever rises.
- **Strings**: English and Hebrew together, in the same commit.
- **Images**: in `docs/images/`, referenced by relative path.
- **New TV**: a folder in `examples/`, plus a row in both index tables, or CI fails.
- **The signing key** is never committed. See [`HANDOFF.md`](HANDOFF.md).
