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
.\adb.exe install -r tvhome-2.38.apk
```

**The rule that applies to every build:** nothing on Home may overlap. The banner, the performance card, the rows
and the ⌄⌄ arrow each step aside rather than sit on top of one another. Check at **Rows below dock 0** and again at
**3** — that is where it breaks if it is going to.

---

## 2.33 — the live window stays on Home when no app is selected

**What changed.** With **Banner › Live input**, the window vanished as soon as the focus left the app tiles for the
Home tab, because the banner showed only while an app was selected. Now the live style shows whenever
Home is in front and the banner fits; the app **name and the page dots hide** when no app is selected (they keep their
room, so the window does not move). The other banner styles are unchanged: they still need a selected app. Apps and
Inputs tabs still hide the window and hand the input back. Rows 1–3 still show no window (option A). **The gear is
reached only through the Apps and Inputs tabs**, which switch the page as they take the focus, so the window goes on the
way to the gear — accepted by the owner on 7 Nov (option A; no code change).

Setup: Banner = **Live input**, source = **HDMI 2**, Favorite apps rows = **0**. Run `.\adb.exe logcat -c` first.

- [x] **1.** Arrive on Home with the focus on the **Home pill** → the HDMI 2 picture is already showing, with no app name — **6 Nov: pass**
- [x] **2.** Down onto Your apps → the picture stays, the app name and dots appear under it, **nothing jumps** — **6 Nov: pass**
- [x] **3.** Up to the Home pill again → the picture stays; name and dots go — **6 Nov: pass**
- [ ] **4.** Right from the Home pill towards the **gear** → the window goes when the focus reaches the **Apps tab**, and comes back on Home, no flicker (reworded 7 Nov: owner's option A) — **6 Nov, under the old wording:** Right from the Home pill passes the Apps and Inputs tabs first; the window goes when the Apps tab takes the focus and returns on Home, no flicker. The gear cannot be reached with the picture up, so this item needs another path or a layout decision
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
- [x] **4.** Repeat ten times with different apps → the right app is added every time (this is the open "wrong app from +" fault) — **7 Nov: pass, 10 of 10** (AIS PLAY, Crunchyroll on 6 Nov; FLIXER, GeForce NOW, MangoTV/MGTV, Music, Spotify, Timers & Clock, Viki, VLC on 7 Nov, each the last tile, focused, with its own toast). The last eight went Right from the new tile to the + without the Home pill, which avoids the Down-after-add fault
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
- [x] **5.** Pick a style, back out, open Home → the picked glow is the one in use (the choice itself is unchanged) — **7 Nov: pass** (owner: Ribbon shows a diagonal band in the selected app's colour and follows the app); set back to Random position and shape
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
- [x] **3.** Launcher settings › Wallpaper: the page shows a preview; moving the highlight over each picture shows **that picture**, cropped to a 16:9 frame — **7 Nov: pass** with three test pictures pushed to `wallpapers/` (1920×1080, 1080×1920 portrait shown as its centre 16:9 band, 7680×4320)
- [x] **4.** A large picture: moving onto it never freezes the remote (the picture may appear a moment later) — **7 Nov: pass** for 7680×4320: the next Down moved the focus as fast as on the small pictures; `gfxinfo` 0 janky frames, 0 slow UI thread
- [x] **5.** "None" and "Theme default": a line of text instead of a picture — **6 Nov: pass** (the theme has no pictures, so items 3, 4 and 6 could not be tried)
- [x] **6.** Pick a wallpaper → Home shows the same picture as the preview — **7 Nov: pass**; set back to Theme default (Home plain again)
- [x] **7.** All five previewed pages (Focus style, Banner, Glow, Font, Wallpaper) still show their drawing fixed at the top while the rows scroll — **6 Nov: pass**
- [x] **8.** Launcher settings › About shows **2.36** — **6 Nov: pass**
- [x] **9.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **6 Nov: pass** (one log, 23:18–00:08 across the 2.33–2.36 run: 0 `FATAL EXCEPTION`, 0 `E AndroidRuntime`, no launcher warning beyond the known noise)

Not checked here: how any drawing looks on the TV; and no picture was available to try the Wallpaper decoding.

---

## 2.37 — the fix batch from the 6–7 Nov run

**What changed.** (1) **Down from the Home pill after an add**: the row was left scrolled to its end, and with more
apps than fit, its first tile had no view to focus, so Down did nothing; now it scrolls back and focuses the first app.
(2) **The + choosing mode dims by app**: the dim read the tile's previous app; it now reads the app on every bind.
(3) **Underline**: the bar is **just below** the tile, touching it, at full tile width, in the app's colour — white
when that colour is too dark to see on Home's background (contrast under 3:1, e.g. HBO Max). (4) **Lift + bar**: the
tile rises and the same bar stays below, so the lift shows as a **gap**. (5) The **Focus style** preview draws both that
way. (6) The **Banner** preview's Live input row puts "App name" **under** the window. (7) The **Glow** preview's moving
styles show one glow plus **clear white outlines** where it can go (rings, a dotted loop for Slow drift, ellipses for
Random shape); nothing animates. (8) § 2.33 item 4 is reworded (no code).

Setup: check the TV's IP first (Before you start), install `tvhome-2.37.apk`, then `.\adb.exe logcat -c`.

- [x] **1.** Your apps holds more apps than fit on screen. Add one through the **+** → Home, the new app focused. Press **Home**, then **Down** → the focus lands on the **first** app of Your apps (the row scrolls back to its start) — **7 Nov: pass** (13 apps at the start; Home › Down lands on YOUKU, the first app) (local session over adb, owner by remote)
- [x] **2.** Repeat item 1 for § 2.34 #3–4: add apps one by one, with **Home › Down** between adds → Down works every time, and each add is the app picked — **7 Nov: pass** (Crunchyroll and Viki added, each the app picked; Down worked after both)
- [x] **3.** + › OK: apps already in Your apps are dimmed, **only** those, and the dimming stays right after moving along the rows and in a second choosing session (§ 2.34 #2) — **7 Nov: pass** (exactly the 15 apps in Your apps dimmed, the same after moving along the rows and in a second session)
- [x] **4.** Focus style **Underline**: a bar just **below** the focused tile, touching it, as wide as the tile, in the app's colour; on iQIYI, Prime Video, bilibili the bar is clearly visible; on HBO Max it is **white** — **7 Nov: pass** (clear on iQIYI, Prime Video, bilibili; HBO Max's bar reads light grey — its own colour, which passes the 3:1 rule, so not switched to white)
- [x] **5.** Focus style **Lift + bar**: the tile rises; the bar stays below with a visible **gap**; no zoom — **7 Nov: pass**
- [x] **6.** Both bar styles: the bar follows the focus through Your apps, the Favorite rows and the Apps tab, and leaves nothing behind when the focus moves on or the row scrolls; Move (hold OK › Move) shows no bar *(2.37's rule — **from 2.38 the carried tile shows the bar too**, see § 2.38 item 1)* — **7 Nov: pass in Your apps and the Apps tab; not run:** the Favorite rows (set to 0) and Move (needs hold-OK on the remote). **Later the same evening: Favorite rows pass** (rows set to 1 with Lift + bar: the bar followed Google Play › FLIXER › up to YouTube and left nothing behind), **Move passes as written** (owner's remote: no bar while moving, the bar back on the focused tile after; YouTube put back in place). Rows set back to 0, Focus style back to Zoom. **The owner wants this changed:** while moving, the tile should show the chosen Focus style's effect — see HANDOFF *For the next fix batch*
- [x] **7.** Zoom, Lift and Glass halo look as in 2.36 — **7 Nov: pass**
- [x] **8.** Settings › Focus style preview: Underline and Lift + bar drawn with the bar below the tile (and the gap for Lift + bar) — **7 Nov: pass**
- [x] **9.** Settings › Banner preview, Live input row: "App name" **under** the window, not over it, and above the tiles — **7 Nov: pass**
- [x] **10.** Settings › Glow preview: Random position, Random ×2, Random ×3, Random position and shape and Slow drift each show **clearly visible** white marks; nothing flickers — **7 Nov: pass** (rings, ellipses and the Slow drift dotted loop all clear)
- [x] **11.** Launcher settings › About shows **2.37** — **7 Nov: pass** (2.37, 237)
- [x] **12.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **7 Nov: pass** (531 lines: no FATAL, no AndroidRuntime; known noise only — Duplicate finish request 22, toast 6, BufferQueueProducer 1)

Not checked here: anything on screen. The bar colour rule was checked as arithmetic against Home's background colour
(`0xFF10131A`); over a bright wallpaper or glow the bar may read differently.

**7 Nov result:** items 1–12 pass, no crash, no new fault. 28 screenshots are on the owner's PC in `platform-tools`
(not attached to the cloud session; the readings above are the local session's). Still to do with the remote: item 6 in
the Favorite rows and during Move. **Crunchyroll and Viki were added to Your apps by this test** — the owner removes them
(hold OK › Remove).

---

## 2.38 — Move shows the focus style, the Glow preview moves, rows wrap

**What changed.** (1) **Move** (hold OK › Move): the carried tile now looks like any focused tile in the chosen Focus
style — Zoom, Lift, Glass halo, Underline (bar below) or Lift + bar (lift, gap, bar) — instead of 2.37's fixed 1.15 zoom,
see-through look and no lift, halo or bar. What marks it as held is that **every other tile dims**. This replaces 2.37
item 6's "Move shows no bar". (2) **Glow page preview**: the white rings, ellipses and dotted loop of 2.37 are gone. With
Random position, Random ×2, Random ×3 or Random position and shape highlighted, the glow **jumps to a new place every
1.6 s** (×2 and ×3 with their extra glows), as Home does on each new app; with Slow drift highlighted it **drifts** at
Home's own pace. It runs only while the page is open on that row and stops on leaving. (3) **Left/Right wrap** in the
**Apps tab** and Home's **Favorite rows**: Left on a row's first app → the **last app of the row above**; Right on a
row's last app → the **first app of the row below**. Mid-row Left/Right is unchanged; Left on the very first app and
Right on the very last app still stop; Your apps (one row) and the Inputs rows still stop at their ends. This
deliberately reverses the 2.30 stop for those two grids.

Setup: check the TV's IP first (Before you start), install `tvhome-2.38.apk`, then `.\adb.exe logcat -c`.
**Notes for the local session:** adb cannot hold OK on this TV — **Move and the hold-OK menu need the owner's remote**
(items 1–3). Since 2.37 the focused tile is kept in the middle of Your apps, so **find the + by what is focused**
(screenshot), not by counting presses. Favorite apps rows is normally **0**: set it to **1** for items 9–10 and back to
0 afterwards; restore Focus style and Glow to what they were at the start.

- [x] **1.** (remote) Focus style **Zoom**: hold OK on an app in Your apps › Move → the carried tile is zoomed like a normal selection, **not** see-through; every other tile is dimmed; Left/Right moves it; OK puts it down and the dimming ends — **7 Nov: pass** (owner's remote)
- [x] **2.** (remote) Repeat item 1 with **Lift**, **Glass halo**, **Underline** and **Lift + bar** → the carried tile shows that style (lift; coloured halo; bar just below; lift with gap and bar) while it moves, and the bar follows it — **7 Nov: pass**, all four styles (owner's remote)
- [x] **3.** (remote) With Favorite apps rows = 1, Move a Favorite-row app: the same look; it still cannot cross into Your apps (and a Your apps tile cannot cross into the Favorite rows) — **7 Nov: pass** (owner's remote; rows set back to 0)
- [x] **4.** Settings › Glow, highlight **Random position** (do not pick) → the glow jumps to a new place about every 1.6 s; **no white rings**; no flicker between jumps — **7 Nov: pass**: six frames 0.7 s apart all differ (mean change 2.4–9.3 in the preview box); no white rings. Flicker judged by the owner on screen, not from stills
- [x] **5.** Same for **Random ×2** and **Random ×3** → the main glow and its 1 / 2 extra glows in shifted colours jump together — **7 Nov: pass** (×2 change 2.9–7.9, ×3 2.4–6.8; two and three glows in shifted colours)
- [x] **6.** **Random position and shape** → the glow jumps and changes shape (wide or tall) — **7 Nov: pass** (change 0.9–7.7; round top right, then wide on the left)
- [x] **7.** **Slow drift** → the glow moves slowly and smoothly, no jumps, no flicker — **7 Nov: pass** (frames 0.4 s apart change 0.4–0.9: small, steady steps)
- [x] **8.** Highlight a still style (Classic, Per app, Follow, Two glows, Ribbon) → the drawing stops moving at once; Back out of the page → nothing keeps running (no lag on Home) — **7 Nov: pass**: Classic and Ribbon frames identical (change 0); after Back, Home drew **0 frames in 6 s** (`dumpsys gfxinfo`)
- [x] **9.** **Apps tab**: on the **last app of a row**, Right → the **first app of the next row**; on the **first app of a row**, Left → the **last app of the row above**; mid-row Left/Right unchanged; Left on the very first app and Right on the very last app do nothing; a target row off screen scrolls into view — **7 Nov: pass**: (0,7) Right › (1,0); (1,0) Left › (0,7); mid-row normal; (0,0) Left and (3,7) Right stay. All four rows fit on screen, so the scroll case did not arise
- [x] **10.** Favorite apps rows = **1** (then 2 if more Favorites exist): the same wrap between Favorite rows; Left on the first Favorite does **not** jump into Your apps; set rows back to 0 — **7 Nov: pass** with rows = 2 (8 + 8 Favorites): row 1 last Right › row 2 first, and back with Left; Left on the first Favorite stays (does not enter Your apps); Right on the very last stays. Rows set back to 0
- [x] **11.** Your apps: Left on the first tile and Right on the + still do nothing; Inputs tab: the row still stops at both ends — **7 Nov: pass, with one unexplained case**: Left on the first tile stays; Inputs HDMI 1 Left and HDMI 4 Right stay; Right on the + stayed in 4 of 5 tries, but **once (the first try) the focus went to the Home pill** — not reproduced in four retries, two with the same key sequence; the focus read just before was blank, so a transition may have been caught. **The owner then pressed Right on the + more than 10 times on the remote: it stayed every time**, so this is taken as a reading artefact, not a fault
- [x] **12.** Launcher settings › About shows **2.38** — **7 Nov: pass** (Version 2.38 (238))
- [x] **13.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime — **7 Nov: pass**: 754 lines 22:11–22:43 covering the whole run including Move: 0 `FATAL`, 0 `E AndroidRuntime`, no launcher restart; known noise only (23 `Duplicate finish request`, 6 toast, 5 `BufferQueueProducer`)

Not checked here: anything on screen; the glow ticker's cost was judged from the code (one redraw per 120 ms tick for
Slow drift, one per 1.6 s for the Random styles), not measured on the TV.

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
