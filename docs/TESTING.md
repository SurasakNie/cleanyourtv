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

**Check the TV's IP address first, every time (owner's rule, 6 Nov).** The address comes from the router and changes
when the TV restarts: it was `192.168.1.3` until 6 Nov and `192.168.1.19` after the restart that night, and another
device then answered at `.3`. Read it on the TV (Settings › Network & Internet, or About › Status), or confirm a
candidate from the PC — the TV answers on the adb port and on Google Cast's two ports:

```
Test-NetConnection 192.168.1.19 -Port 5555
Test-NetConnection 192.168.1.19 -Port 8009
```

Both `TcpTestSucceeded : True` means it is the TV. A new address also asks for **Allow USB debugging** on the TV
again: allow it only when the dialog is on the Sony itself. A DHCP reservation in the router would keep the address
fixed.

```
.\adb.exe connect 192.168.1.19:5555
.\adb.exe shell ip -4 addr show eth0
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

- [x] **1.** Arrive on Home with the focus on the **Home pill** → the HDMI 2 picture is already showing, with no app name — **6 Nov: pass**
- [x] **2.** Down onto Your apps → the picture stays, the app name and dots appear under it, **nothing jumps** — **6 Nov: pass**
- [x] **3.** Up to the Home pill again → the picture stays; name and dots go — **6 Nov: pass**
- [ ] **4.** Right to the **gear** → the picture stays — **6 Nov: fails as written.** Right from the Home pill passes the Apps and Inputs tabs first; the window goes when the Apps tab takes the focus and returns on Home, no flicker. The gear cannot be reached with the picture up, so this item needs another path or a layout decision
- [x] **5.** **Apps** tab and **Inputs** tab → the picture goes — **6 Nov: pass**
- [x] **6.** Back to Home → the picture returns; HDMI from the Inputs tab still shows the TV's own full-screen picture with sound — **6 Nov: pass**
- [x] **7.** OK on an app, then Home → the app opens normally; on return the picture comes back — **6 Nov: pass** on the second try; the first Youku open was a black cold start that the evidence does not tie to the live window (HANDOFF § 3)
- [ ] **8.** Rows = **1, 2, 3** → still no window and no black flash — **owner, 31 Oct: no window at 1, 2, 3; decided: the live window works at 0 rows only**
- [x] **9.** Banner = Name + glow, then Glass panel, then Off: unchanged from 2.32 (needs a selected app; Off shows nothing) — **6 Nov: pass**, all three
- [x] **10.** Launcher settings › About shows **2.33** — **6 Nov: shows 2.36**, the installed build
- [x] **11.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **6 Nov: pass** (13,045 lines, none)

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

- [x] **1.** Home › Your apps › move to the **+** › OK → the **Apps tab** opens, a toast says to choose an app, the focus is on an app that is **not** in Your apps — **6 Nov: pass** (toast "Choose an app to add to Your apps. Back cancels.", focus on Aberto Sonorus)
- [ ] **2.** Apps already in Your apps look **dimmed** (also when focused); OK on one → toast "… is already in Your apps", you stay in the Apps tab — **6 Nov: toast and stay pass; dimming fails** (follows the tile, not the app: see HANDOFF *Known bugs*)
- [x] **3.** OK on a free app → back on **Home**, the app is the **last tile** of Your apps and has the focus; toast "added" — **6 Nov: pass** twice (AIS PLAY, Crunchyroll)
- [ ] **4.** Repeat ten times with different apps → the right app is added every time (this is the open "wrong app from +" fault) — **6 Nov: 2 of 10, both right; stopped** because Down from the Home pill does not reach the row after an add (HANDOFF *Known bugs*)
- [x] **5.** + › OK, then **Back** → Home, focus on the **+**; nothing added — **6 Nov: pass** (and Up › Down still reaches the row afterwards)
- [x] **6.** + › OK, then Up to a tab and the **Home** or **Inputs** tab → the choosing ends; go back to Apps → no dimming — **6 Nov: pass** (via Inputs)
- [x] **7.** + › OK, hold OK on an app → **no menu** opens — **6 Nov: pass** (owner's remote; after leaving through the Home tab, hold OK in Apps opens the menu again, as item 9 expects)
- [ ] **8.** When every app is already in Your apps → + › OK shows "Every app is already in Your apps" and stays on Home
- [x] **9.** Hold OK on an app in Home or the Apps tab (not choosing) → the menu still opens as before — **6 Nov: pass**, both places (owner's remote; closed with Back, Your apps unchanged)
- [x] **10.** Launcher settings › Focus style: the page has a **preview** above five rows (Zoom, Lift, Glass halo, Underline, Lift + bar); moving the highlight changes the drawing; it looks like the real style — **6 Nov: pass** (screenshots over adb; Lift + bar shows the bar inside the tile's bottom edge)
- [x] **11.** Choose **Lift + bar** → on Home the selected tile rises a little with a bar along its bottom edge; no zoom; the white ring does not return — **6 Nov: pass** (YouTube: raised, same size, red bar inside the bottom edge, no ring); set back to Zoom
- [x] **12.** Launcher settings › Banner: the page has a preview above four rows (Name + glow, Glass panel, Off, Live input) that follows the highlight — **6 Nov: pass**; in the Live input drawing the "App name" text overlaps the bottom of the window
- [x] **13.** The other four Focus styles still look as in 2.33 — **6 Nov: pass** by eye (Lift raised, Glass halo red glow, Underline red line, Zoom scaled); no 2.33 screenshots to compare side by side
- [x] **14.** Launcher settings › About shows **2.34**
- [x] **15.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **6 Nov: pass** (one log, 23:18–00:08 across the 2.33–2.36 run: 0 `FATAL EXCEPTION`, 0 `E AndroidRuntime`, no launcher warning beyond the known noise)

Not checked here: how the drawings look on the TV (they were never rendered off-device), and the Hebrew texts.

---

## 2.35 — a preview in the Glow page, and previews that stay in view

**What changed.** The **Glow** choice page (ten styles) now shows a small 16:9 screen above the rows with the highlighted
style drawn on it, placed with the launcher's own `GlowStyle` positions; styles that move (Random, Random ×2/×3, Slow
drift) show where the glow can go as faint marks; Random shape uses ellipses; Ribbon is a diagonal band. Also, the
drawing of **every** previewed page (Focus style, Banner, Glow) now sits in a fixed slot **above** the scrolling rows,
so it stays in view while the longer lists scroll. Nothing else changed.

Setup: `.\adb.exe logcat -c` first.

- [x] **1.** Launcher settings › Glow (turn **Ambient glow** on first if the row is greyed): the page shows a **preview** above ten rows — **6 Nov: pass**
- [x] **2.** Move down all ten rows → the drawing **changes with each row** and stays at the top while the list scrolls — **6 Nov: pass**
- [x] **3.** Classic, Per app, Follow, Two glows, Ribbon: each looks like what the real Home shows for that style (a spot top-right, a fixed place, above the tile, two spots, a diagonal band) — **6 Nov: pass** by eye from screenshots
- [ ] **4.** Random / Random ×2 / Random ×3 / Random shape / Slow drift: the faint marks read as "moves around"; nothing flickers — **6 Nov: no flicker (owner); the marks do not read as movement** — the owner was not sure they saw any, and the screenshots show none, so the marks are too faint on this TV
- [ ] **5.** Pick a style, back out, open Home → the picked glow is the one in use (the choice itself is unchanged) — **6 Nov: not confirmed.** After picking Ribbon, Home with an app focused showed a broad, still glow in the app's colour running diagonally from the top centre down to the left; the preview's band runs from top left to bottom right. Needs the owner's eye. Set back to Random position and shape
- [x] **6.** Focus style and Banner pages: preview still shows, now fixed at the top; the Back hint and the title scroll under it — **6 Nov: pass** (seen scrolling under it on the Glow page; Focus style and Banner lists are too short to scroll)
- [x] **7.** A page without a preview (Font, Wallpaper, Banner title size, Live input source) → no empty gap at the top — **6 Nov: pass** for Banner title size and Live input source (title at the top, y = 60; Font and Wallpaper have previews since 2.36). Banner title size can only be focused when Banner is not Off, and Live input source is only listed with Banner = Live input
- [x] **8.** Launcher settings › About shows **2.35**
- [x] **9.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **6 Nov: pass** (one log, 23:18–00:08 across the 2.33–2.36 run: 0 `FATAL EXCEPTION`, 0 `E AndroidRuntime`, no launcher warning beyond the known noise)

Not checked here: how any drawing looks on the TV; they were never rendered off-device.

---

## 2.36 — previews in the Font and Wallpaper pages

**What changed.** The two choice pages that had no drawing now have one, in the same fixed slot above the rows as the
Focus style, Banner and Glow pages. **Font**: the banner's lines ("App name", "Last opened…", the tab names) set in the
highlighted font, Outfit or CMU Sans. **Wallpaper**: the highlighted picture, centre-cropped to a 16:9 screen with a
faint copy of Home's tiles over it; decoded small, off the main thread. The "None" row and the "Theme default" row have no
picture, so they say what they mean in a line of text. Nothing else changed.

Setup: `.\adb.exe logcat -c` first. Wallpaper needs at least one picture in the theme folder (`wallpapers/` or `wallpaper.jpg`).

- [x] **1.** Launcher settings › Font: the page shows a **preview** above two rows; moving between Outfit and CMU Sans changes the lettering — **6 Nov: pass**
- [x] **2.** Choose CMU Sans → the panel redraws in it (as before); open the page again → the preview matches the font in use — **6 Nov: pass**; set back to Outfit
- [ ] **3.** Launcher settings › Wallpaper: the page shows a preview; moving the highlight over each picture shows **that picture**, cropped to a 16:9 frame
- [ ] **4.** A large picture: moving onto it never freezes the remote (the picture may appear a moment later)
- [x] **5.** "None" and "Theme default": a line of text instead of a picture — **6 Nov: pass** (the theme has no pictures, so items 3, 4 and 6 could not be tried)
- [ ] **6.** Pick a wallpaper → Home shows the same picture as the preview
- [x] **7.** All five previewed pages (Focus style, Banner, Glow, Font, Wallpaper) still show their drawing fixed at the top while the rows scroll — **6 Nov: pass**
- [x] **8.** Launcher settings › About shows **2.36** — **6 Nov: pass**
- [x] **9.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **6 Nov: pass** (one log, 23:18–00:08 across the 2.33–2.36 run: 0 `FATAL EXCEPTION`, 0 `E AndroidRuntime`, no launcher warning beyond the known noise)

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
