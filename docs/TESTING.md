# Testing the launcher on the TV

Each build says **what changed and why** first, then what to check. Work through them **oldest-first**: every build
leans on the one before it, so a fault in 2.23 explains faults in 2.24 and later.

Anything that fails: stop on that build and say which line — more useful than a list of everything that happened
afterwards.

**A name that changes.** The setting these checklists call **Rows below dock** is renamed **Favorite apps rows** in
2.30. Until then the TV still shows the old label.

**What is here.** Only the builds still waiting for the TV (2.33–2.36) and how to test. The checklists for 2.18–2.32,
with their results, are kept word for word in [`TESTING-history.md`](TESTING-history.md).

## Before you start

```
.\adb.exe connect 192.168.1.3:5555
.\adb.exe shell dumpsys package com.gathin.tvhome | findstr versionName
.\adb.exe install -r tvhome-2.36.apk
```

**The rule that applies to every build:** nothing on Home may overlap. The banner, the performance card, the rows
and the ⌄⌄ arrow each step aside rather than sit on top of one another. Check at **Rows below dock 0** and again at
**3** — that is where it breaks if it is going to.

---

## 2.33 — the live window stays on Home when no app is selected

**What changed.** With **Banner › Live input**, the window vanished as soon as the focus left the app tiles for the
Home tab or the gear, because the banner showed only while an app was selected. Now the live style shows whenever
Home is in front and the banner fits; the app **name and the page dots hide** when no app is selected (they keep their
room, so the window does not move). The other banner styles are unchanged: they still need a selected app. Apps and
Inputs tabs still hide the window and hand the input back. Rows 1–3 still show no window (option A).

Setup: Banner = **Live input**, source = **HDMI 2**, Favorite apps rows = **0**. Run `.\adb.exe logcat -c` first.

- [ ] **1.** Arrive on Home with the focus on the **Home pill** → the HDMI 2 picture is already showing, with no app name
- [ ] **2.** Down onto Your apps → the picture stays, the app name and dots appear under it, **nothing jumps**
- [ ] **3.** Up to the Home pill again → the picture stays; name and dots go
- [ ] **4.** Right to the **gear** → the picture stays
- [ ] **5.** **Apps** tab and **Inputs** tab → the picture goes
- [ ] **6.** Back to Home → the picture returns; HDMI from the Inputs tab still shows the TV's own full-screen picture with sound
- [ ] **7.** OK on an app, then Home → the app opens normally; on return the picture comes back
- [ ] **8.** Rows = **1, 2, 3** → still no window and no black flash — **owner, 31 Oct: no window at 1, 2, 3; decided: the live window works at 0 rows only**
- [ ] **9.** Banner = Name + glow, then Glass panel, then Off: unchanged from 2.32 (needs a selected app; Off shows nothing)
- [ ] **10.** Launcher settings › About shows **2.33**
- [ ] **11.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime

If the picture flickers while moving between the pill, the tiles and the gear, send `.\adb.exe logcat -d | Out-File -Encoding utf8 live.txt`.

---

## 2.34 — the + picks from the Apps tab, Lift + bar, and previews in two choice pages

**What changed.** (1) **The + in Your apps** no longer opens a list: it opens the **Apps tab in a choosing mode**, on the
first app that is not in Your apps yet. Apps already in the row stay on screen, **dimmed**. OK on a free app adds it
and returns to Home with that app focused; OK on a dimmed one says it is already there; **Back** (or any other tab)
cancels and returns to the +. No hold-OK menu while choosing. (2) **Focus style › Lift + bar**: the tile rises a little
(no zoom) and the app's colour shows as a bar along the tile's **bottom edge** — inside the tile, not below it (the tile
clips its children). (3) **Focus style** and **Banner** choice pages now show a **small drawing** above the rows that
follows the highlighted row. Drawn in code; no image added.

Setup: `.\adb.exe logcat -c` first. Favorite apps rows = 0 is fine for all of it.

- [ ] **1.** Home › Your apps › move to the **+** › OK → the **Apps tab** opens, a toast says to choose an app, the focus is on an app that is **not** in Your apps
- [ ] **2.** Apps already in Your apps look **dimmed** (also when focused); OK on one → toast "… is already in Your apps", you stay in the Apps tab
- [ ] **3.** OK on a free app → back on **Home**, the app is the **last tile** of Your apps and has the focus; toast "added"
- [ ] **4.** Repeat ten times with different apps → the right app is added every time (this is the open "wrong app from +" fault)
- [ ] **5.** + › OK, then **Back** → Home, focus on the **+**; nothing added
- [ ] **6.** + › OK, then Up to a tab and the **Home** or **Inputs** tab → the choosing ends; go back to Apps → no dimming
- [ ] **7.** + › OK, hold OK on an app → **no menu** opens
- [ ] **8.** When every app is already in Your apps → + › OK shows "Every app is already in Your apps" and stays on Home
- [ ] **9.** Hold OK on an app in Home or the Apps tab (not choosing) → the menu still opens as before
- [ ] **10.** Launcher settings › Focus style: the page has a **preview** above five rows (Zoom, Lift, Glass halo, Underline, Lift + bar); moving the highlight changes the drawing; it looks like the real style
- [ ] **11.** Choose **Lift + bar** → on Home the selected tile rises a little with a bar along its bottom edge; no zoom; the white ring does not return
- [ ] **12.** Launcher settings › Banner: the page has a preview above four rows (Name + glow, Glass panel, Off, Live input) that follows the highlight
- [ ] **13.** The other four Focus styles still look as in 2.33
- [ ] **14.** Launcher settings › About shows **2.34**
- [ ] **15.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime

Not checked here: how the drawings look on the TV (they were never rendered off-device), and the Hebrew texts.

---

## 2.35 — a preview in the Glow page, and previews that stay in view

**What changed.** The **Glow** choice page (ten styles) now shows a small 16:9 screen above the rows with the highlighted
style drawn on it, placed with the launcher's own `GlowStyle` positions; styles that move (Random, Random ×2/×3, Slow
drift) show where the glow can go as faint marks; Random shape uses ellipses; Ribbon is a diagonal band. Also, the
drawing of **every** previewed page (Focus style, Banner, Glow) now sits in a fixed slot **above** the scrolling rows,
so it stays in view while the longer lists scroll. Nothing else changed.

Setup: `.\adb.exe logcat -c` first.

- [ ] **1.** Launcher settings › Glow (turn **Ambient glow** on first if the row is greyed): the page shows a **preview** above ten rows
- [ ] **2.** Move down all ten rows → the drawing **changes with each row** and stays at the top while the list scrolls
- [ ] **3.** Classic, Per app, Follow, Two glows, Ribbon: each looks like what the real Home shows for that style (a spot top-right, a fixed place, above the tile, two spots, a diagonal band)
- [ ] **4.** Random / Random ×2 / Random ×3 / Random shape / Slow drift: the faint marks read as "moves around"; nothing flickers
- [ ] **5.** Pick a style, back out, open Home → the picked glow is the one in use (the choice itself is unchanged)
- [ ] **6.** Focus style and Banner pages: preview still shows, now fixed at the top; the Back hint and the title scroll under it
- [ ] **7.** A page without a preview (Font, Wallpaper, Banner title size, Live input source) → no empty gap at the top
- [ ] **8.** Launcher settings › About shows **2.35**
- [ ] **9.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime

Not checked here: how any drawing looks on the TV; they were never rendered off-device.

---

## 2.36 — previews in the Font and Wallpaper pages

**What changed.** The two choice pages that had no drawing now have one, in the same fixed slot above the rows as the
Focus style, Banner and Glow pages. **Font**: the banner's lines ("App name", "Last opened…", the tab names) set in the
highlighted font, Outfit or CMU Sans. **Wallpaper**: the highlighted picture, centre-cropped to a 16:9 screen with a
faint copy of Home's tiles over it; decoded small, off the main thread. The "None" row and the "Theme default" row have no
picture, so they say what they mean in a line of text. Nothing else changed.

Setup: `.\adb.exe logcat -c` first. Wallpaper needs at least one picture in the theme folder (`wallpapers/` or `wallpaper.jpg`).

- [ ] **1.** Launcher settings › Font: the page shows a **preview** above two rows; moving between Outfit and CMU Sans changes the lettering
- [ ] **2.** Choose CMU Sans → the panel redraws in it (as before); open the page again → the preview matches the font in use
- [ ] **3.** Launcher settings › Wallpaper: the page shows a preview; moving the highlight over each picture shows **that picture**, cropped to a 16:9 frame
- [ ] **4.** A large picture: moving onto it never freezes the remote (the picture may appear a moment later)
- [ ] **5.** "None" and "Theme default": a line of text instead of a picture
- [ ] **6.** Pick a wallpaper → Home shows the same picture as the preview
- [ ] **7.** All five previewed pages (Focus style, Banner, Glow, Font, Wallpaper) still show their drawing fixed at the top while the rows scroll
- [ ] **8.** Launcher settings › About shows **2.36**
- [ ] **9.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime

Not checked here: how any drawing looks on the TV; and no picture was available to try the Wallpaper decoding.

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
