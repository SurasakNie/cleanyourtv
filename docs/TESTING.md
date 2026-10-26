# Testing the launcher on the TV

Each build says **what changed and why** first, then what to check. Work through them **oldest-first**: every build
leans on the one before it, so a fault in 2.23 explains faults in 2.24 and later.

Anything that fails: stop on that build and say which line — more useful than a list of everything that happened
afterwards.

## Before you start

```
.\adb.exe connect 192.168.1.3:5555
.\adb.exe shell dumpsys package com.gathin.tvhome | findstr versionName
.\adb.exe install -r tvhome-2.29.apk
```

**The rule that applies to every build:** nothing on Home may overlap. The banner, the performance card, the rows
and the ⌄⌄ arrow each step aside rather than sit on top of one another. Check at **Rows below dock 0** and again at
**3** — that is where it breaks if it is going to.

---

## 2.18 — the page indicator (**passed on the TV, 26 Oct**)

**What changed.** `GtvActivity.showDot()` animated only two dots — the new one and the one before it — and started
each from its *resting* width. Key repeat moves focus faster than the 140 ms animation, so every dot further back
was stranded part-way open: a row of half-open dashes instead of one pill. It now reads the widths the dots
actually have and moves all of them, so an interrupted run heals itself.

- [x] **Hold** Left and Right across Your apps for a few seconds, then let go: one pill, every other dot small
- [x] A short hold that ends mid-animation leaves no half-open dash

---

## 2.22 — the clock screen is out of the APK (**passed on the TV, 26 Oct**)

**What changed.** The launcher had two clock screens: `ClockActivity` (Home's idle clock) and `ClockDream` (a
screensaver you could pick in the TV's settings). You keep the TV's own screensaver, so both are **undeclared in
the manifest** and the idle timer 2.21 added to `GtvActivity` is gone. R8 then drops the classes and
`dream_clock.xml` from the APK — verified: no clock component in the packaged manifest, no class names in the dex.
The `.java` files stay in the repository. The **Clock** row in Launcher settings is hidden.

**Not touched:** the clock in Home's top-right corner. That is a `TextView` in the top bar on a minute ticker —
a different thing entirely.

- [x] HOME brings up the launcher as before
- [x] The **top-right clock still ticks**
- [x] The TV's own screensaver still starts when the TV is left alone
- [x] Launcher settings has **no Clock row**

---

## 2.23 — Your apps is a scrolling dock

**What changed — the biggest of the five.** Your apps used to be "the first *n* apps of the sorted list", where *n*
was Apps per row. It could not hold more than fitted on screen, and there was no way to choose what was in it.

- It is now its own **horizontally scrolling row** (`dockRow`), outside the grid that holds the Favorite rows.
  *Apps per row* means **how many are on screen**, not how many exist.
- A new `CentreManager` answers RecyclerView's `onRequestChildFocus` itself and scrolls the focused tile to the
  **middle of the screen**; the row clamps at its own ends, so the first and last tiles rest at the edges. It also
  lays out a tile's width either side, so focus cannot fall out of the row at its edges.
- The **last tile is a +** that lists every app not in the row.
- **Hold OK** on an app offers **Remove from Your apps**. The last app cannot be removed.
- The row is the owner's own list in `gtv_dock.txt` (new `DockApps`, built like `Favorites`), **seeded from
  `dock_apps`** the first time, so the row you had carried over.
- Two deliberate consequences: **Move no longer crosses** between Your apps and the Favorite rows (it reorders
  within each), because membership is now changed on purpose with + and hold-OK; and the page indicator **hides
  above 12 apps** rather than drawing a dot for each.
- Home's height arithmetic was reworked, since the row brings its own padding.

Set **Apps per row = 6** before testing.

- [ ] Six tiles are on screen
- [ ] Holding **Right** brings the next app in, and the selected tile stays in the **middle of the screen**
- [ ] The **first** and **last** tiles rest at the edges instead of being dragged to the middle
- [ ] Right past the last app reaches the **+**; it opens a list of apps not in the row
- [ ] Picking one adds it to the end and leaves the selection on it
- [ ] **Hold OK** → **Remove from Your apps** takes an app out
- [ ] The **last** app cannot be removed (a message says so)
- [ ] **Up** from the row lands on the **Home tab**
- [ ] **Down** reaches the Favorite rows, or the Apps tab when there are none
- [ ] Rows below dock **0** and **3**: nothing overlaps
- [ ] Hold OK → **Move** still reorders within the row, and within the Favorite rows
- [ ] More than 12 apps in the row: the dots disappear

---

## 2.24 — the Inputs row, Banner style, Glass bars

**What changed.**
- **Inputs row on Home**, under the Favorite rows, **off by default** (Layout › Inputs on Home). It is built from
  the same tiles as the Inputs tab — `fillInputs(...)` is now shared by both — so HDMI, AV and Live TV look
  identical in either place. Focusing an input clears the banner's selection, since an input is not an app.
- Down now walks **Your apps → Favorites → Inputs → the Apps tab**.
- **Banner** became one choice page: *Name + glow* (what it was), *Glass panel* (the dark panel with a hairline
  that 2.15 removed), *Off*. It replaces 2.21's two switches, whose values migrate — either one set meant Glass
  panel. With Banner off, **Banner title size** is greyed out.
- **Glass bars: On / Off** for the frosted strip behind the tabs and the clock.
- The height arithmetic counts **sections** now. With three sections on a short screen tiles would shrink past
  reading, so the **Inputs row stands down** below 56 dp — like the banner and the performance card, which
  already step aside. On this TV (density 1.0) that never triggers.

- [ ] **Inputs on Home** starts **Off**; switching it on adds the row
- [ ] An input on that row opens full screen
- [ ] With the row on: Rows below dock **0** and **3**, nothing overlaps
- [ ] Each **Banner** style: Name + glow, Glass panel, Off
- [ ] With Banner off, **Banner title size** is greyed out
- [ ] **Glass bars** off: the strip behind the tabs and the clock goes, the pills stay
- [ ] Down from Your apps reaches Favorites, then Inputs, then the Apps tab

---

## 2.25 — the live input banner

**What changed.** Banner gained a fourth style, **Live input**: a 260 dp 16:9 window of one HDMI port above the
app's name, with **Live input source** to pick the port. It is driven by `InputPreview`, the class that already
tunes, mutes (`setStreamVolume(0f)`) and reports "connecting", "no signal" and "unavailable". Offline through
`TvView` — **no new permission**, which is the point of doing it this way rather than with camera streams.

The window runs **only** while Home is in front with the banner on screen; `onPause`, `open()` and `openInput()`
each stop it first, so the input goes back to the TV before anything else asks for it. The "Last opened" line
steps aside while the window is up, so the banner stays near its old height.

**Worth knowing:** this is the first time anything in the Google TV layout has drawn `InputPreview`'s window — the
instance belonged to the Apple layout and its XML, so the old **Input preview** settings row had been storing a
value nothing read. The banner style owns that switch now and the row is hidden.

**This is the one item that may simply not work on this TV.** A non-system app is not allowed to show a
passthrough input on every set, and it has never been tried here. A clear message instead of a picture is an
answer about the TV, not a bug.

- [ ] Banner → **Live input**, then **Live input source** → HDMI 2 (the NVR)
- [ ] The window shows the picture, **or** a clear message
- [ ] Open an app, press HOME: Home returns and the window starts again
- [ ] **The important one:** with the live banner running, open HDMI from the Inputs tab, or Live TV on the
      remote. The picture must be the TV's own — **not a black screen.** Black means the launcher is holding an
      input it should have handed back: say so and stop

---

## 2.26 — effects, part 1

**What changed.** `GtvActivity` reads `GlowStyle` **at last**. Until now the glow was one fixed radial in the top
corner, and the nine styles in the settings changed nothing at all. Now Corner, Random, Fixed per app, Slow drift
(a ticker that runs only while Home is in front), Follow (above the focused tile), Random shape, Two glows and
Random ×2/×3 (extra glow views in a hue-shifted colour) all do what they say, plus a new **Ribbon** — a diagonal
band instead of a spot.

- **Animations** and **Advanced effects** are honoured too: off means the focus zoom and the page indicator reach
  their places at once instead of sliding; advanced fades the glow's colour between apps rather than cutting.
  `reduced_motion` counts as animations off.
- New **Focus style**: *Zoom* (what every build so far did), *Lift*, *Glass halo* (the tile's own shadow painted
  in the app's colour, Android 9 and up) and *Underline* (a bar in the app's colour, with no zoom). The white
  ring is **deliberately not offered** — it was tried in 2.09 and 2.11 and turned down both times.
- Glow, Animations and Advanced effects are **visible in the panel again**. **Cinematic** and **App names** stay
  hidden: they described the Apple layout's shelf and its always-on tile names and mean nothing here.

- [ ] **Glow** now has an effect. Worth trying: *Corner* (the old look), *Follow*, *Fixed per app*, *Slow drift*,
      *Ribbon*
- [ ] **Slow drift** moves while Home is on screen and stops when you open an app or another tab
- [ ] **Animations** off: the focus zoom and the dots jump instead of sliding
- [ ] **Advanced effects** on: moving between apps **fades** the glow's colour
- [ ] Each **Focus style**: Zoom, Lift, Glass halo, Underline
- [ ] Glow, Animations and Advanced effects are back in the panel; **Cinematic** and **App names** are still gone

---

## 2.27 — the removed layout's config keys leave the APK

**What changed. Nothing you can see; this is cleanup.** 17 keys that only the Apple TV layout ever read —
`app_banners`, `fill_dock`, the `banner_*` and `shelf_*` group, `tile_names` and the rest — were still being
parsed by `Config` and still shipped as string resources, months after the layout itself left the APK in 2.19.

- `Config` no longer reads any of them. The **fields stay as constants** with their old defaults, so the Apple
  sources still in the tree go on compiling — the removal is from the APK, never from the repository.
- In `res/values/config.xml` the 17 entries and their descriptions became **one XML comment**. Comments are not
  compiled, so the names, the defaults and the notes stay readable in the repo and none of it reaches the package.
- `res/raw/keep.xml` is **deleted**. It existed to protect drawables named at run time; with the Apple layout gone
  its only live effect was keeping `banner_tv` in the APK, so resource shrinking now drops that too. The
  `banner_tv` drawable file itself stays in the tree.
- `themes/README.md` lists the keys a theme may still name and says plainly that they are now ignored.

**Verified before the build left here:** `aapt2 dump resources` finds **0** of the 17 keys and no `banner_tv`;
219 tests pass; signer `a215696a…436e`; 3 permissions; **460,147 bytes** against the 921,600-byte gate.

- [ ] Home looks and behaves exactly as 2.26 did — this build should be indistinguishable
- [ ] Launcher settings opens and every row still works
- [ ] **Follow theme** / a theme push still applies: a theme naming one of the 17 old keys must be **accepted and
      the key ignored**, not rejected
- [ ] Nothing from `com.gathin.tvhome` in logcat

---

## 2.28 — the Inputs tab no longer kills the launcher

**What changed.** A real crash, found on the TV and reproduced from the log. With **Focus style = Glass halo**,
pressing **Down** on the Inputs tab threw `ClassCastException` and the launcher's process **died** — which is why
the screen went black and Home came back: Android restarted it.

`haloColour` asked `dockRow` and then `homeGrid` for a view's adapter position without checking the view belonged
to either. `RecyclerView.getChildAdapterPosition` casts the view's layout parameters to RecyclerView's own without
checking them, so an **input tile** — which lives in a `LinearLayout`, on Home and in the Inputs tab alike — threw.
Down from the Inputs pill or the gear was enough on its own, because `focusContent()` hands the focus to the first
input tile. It now reads the view's parent and asks only the row that owns it. Inputs have no app colour, so their
halo is plain white.

**Worth knowing:** the crash made Android clear the launcher's preferred-activity registration. It stayed the
default home this time, but a home app that keeps crashing can lose that and hand the TV back to Sony's launcher.

- [ ] Set **Focus style = Glass halo**. On the **Inputs tab**, press **Down** — the launcher must **not** blink and
      return to Home. Before 2.28 this crashed every time
- [ ] Same with the **gear** focused: Down onto the inputs row, no crash
- [ ] Switch **Inputs on Home** on, still on Glass halo: move Down onto that row — no crash there either
- [ ] The halo still shows the **app's own colour** on Your apps and the Favorite rows
- [ ] An input tile's halo is white rather than coloured — correct, an input has no app colour
- [ ] The other three focus styles (Zoom, Lift, Underline) are unchanged

---

## 2.29 — Your apps keeps the focus at its right edge

**What changed.** Found by the owner while testing 2.18, which is not something the checklist was looking for:
holding **Right** to the end of Your apps dropped the focus **down into the Favorite rows**, onto their right-hand
end. Holding Left to the other end was fine. It only shows when there are Favorite rows below to fall into.

The cause is that ordinary navigation had **nothing handling Left or Right at all**. `handleEdgeKey` takes Up and
Down; the Left/Right branches in `dispatchKeyEvent` belong to *carrying* a tile during Move. Past the row's last
item the platform's own focus search picked whatever focusable it could reach, and that was a tile in the rows
below — which is also why it was lopsided: a heuristic, not our code treating the ends differently. A new branch
swallows the key at the row's two ends, so the row stops at its first tile and at the **+**, and Down is the only
way out.

**Set Rows below dock to 1 or more first** — with none, there is nothing to fall into and the old behaviour looks
identical.

- [ ] Hold **Right** to the end of Your apps: the focus stops on the **+** tile and **stays in the row**
- [ ] Keep holding Right: nothing happens — no jump into the Favorite rows, no wrap-around
- [ ] Hold **Left** to the other end: it stops on the first tile and stays there
- [ ] **Down** from anywhere in the row still reaches the Favorite rows — the route this must not break
- [ ] **Up** from the row still lands on the Home tab
- [ ] Hold OK → **Move** still carries a tile left and right along the row, and still reorders
- [ ] The page-indicator dots still follow the selection (the 2.18 fix, unchanged)

---

## After each build

```
.\adb.exe logcat -d | findstr /i "FATAL AndroidRuntime gathin"
```

Nothing from `com.gathin.tvhome` is a pass. Crashes from WeTV (`com.tencent.qqlivei18n`) and the permission
controller are other apps' and are recorded in the handoff — ignore them.

## If Home will not start at all

The recovery screen added in 2.19 says "Home could not start", with **Try again** and **TV system settings**. To
reach the TV's own home picker without it, or to go back to a build that worked:

```
.\adb.exe shell am start -a android.settings.HOME_SETTINGS
.\adb.exe install -r tvhome-2.22.apk
```
