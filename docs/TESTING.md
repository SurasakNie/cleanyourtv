# Testing the launcher on the TV

Five builds are installed-but-unchecked (2.22 to 2.26). This is the list to work through, newest-first in the
repository but **oldest-first here on purpose**: each build leans on the one before it, so a fault in 2.23 explains
faults in 2.24 and later.

Mark each line as you go. Anything that fails: stop on that build and say which line — it is more useful than a
list of everything that happened afterwards.

## Before you start

```
.\adb.exe connect 192.168.1.3:5555
.\adb.exe shell dumpsys package com.gathin.tvhome | findstr versionName
```

The version on the TV should be the build you mean to test. To install a build you have been sent:

```
.\adb.exe install -r tvhome-2.26.apk
```

**The one rule that applies to every build:** nothing on Home may overlap. The banner, the performance card, the
rows and the ⌄⌄ arrow each step aside rather than sit on top of one another. Check it at **Rows below dock 0** and
again at **3** — that is where it breaks if it is going to.

## 2.22 — the clock screen is out of the APK

A removal, so the test is that nothing changed.

- [ ] HOME brings up the launcher as before
- [ ] The **clock in the top-right corner still ticks** (this is not the screensaver and must stay)
- [ ] The TV's own screensaver still starts when the TV is left alone
- [ ] Launcher settings has **no Clock row**

## 2.23 — Your apps is a scrolling dock

The biggest change of the five. Set **Apps per row = 6** first.

- [ ] Six tiles are on screen
- [ ] Holding **Right** brings the next app in, and the selected tile stays in the **middle of the screen**
- [ ] The **first** and **last** tiles rest at the edges instead of being dragged to the middle
- [ ] Right past the last app reaches the **+** tile; it opens a list of apps not in the row
- [ ] Picking one adds it to the end and leaves the selection on it
- [ ] **Hold OK** on an app → **Remove from Your apps** takes it out
- [ ] The **last** app cannot be removed (a message says so)
- [ ] **Up** from the row lands on the **Home tab**
- [ ] **Down** reaches the Favorite rows, or the Apps tab when there are none
- [ ] Rows below dock **0** and **3**: nothing overlaps
- [ ] The dots under the banner match the row; with more than 12 apps they disappear altogether

## 2.24 — the Inputs row, Banner style, Glass bars

- [ ] Launcher settings → Layout → **Inputs on Home** is **Off** to begin with; switching it on adds an Inputs row
      under the Favorite rows
- [ ] An input on that row opens full screen
- [ ] With the row on: Rows below dock **0** and **3**, nothing overlaps
- [ ] **Banner**: *Name + glow* (as before), *Glass panel* (a dark panel with a hairline), *Off* (no banner)
- [ ] With Banner off, **Banner title size** is greyed out
- [ ] **Glass bars** off: the frosted strip behind the tabs and the clock goes, the pills stay

## 2.25 — the live input banner

**This is the one that may simply not work on this TV.** A non-system app is not allowed to show a passthrough
input on every set. If it is refused, the window says "Preview unavailable" — that is an answer, not a bug.

- [ ] Banner → **Live input**, then **Live input source** → HDMI 2 (the NVR)
- [ ] The window shows the picture, **or** a clear message
- [ ] Open an app, press HOME: Home comes back and the window starts again
- [ ] **The important one:** with the live banner running, open HDMI from the Inputs tab, or Live TV on the
      remote. The picture must be the TV's own — **not a black screen.** If it is black, say so and stop: that
      means the launcher is holding the input it should have given back

## 2.26 — effects, part 1

- [ ] Launcher settings → Effects → **Glow** now has an effect. Worth trying: *Corner* (the old look), *Follow*
      (the glow sits above the selected tile), *Fixed per app*, *Slow drift*, *Ribbon* (a diagonal band)
- [ ] **Slow drift** moves while Home is on screen and stops when you open an app or another tab
- [ ] **Animations** off: the focus zoom and the dots jump instead of sliding
- [ ] **Advanced effects** on: moving between apps **fades** the glow's colour instead of cutting
- [ ] **Focus style**: *Zoom* (today's), *Lift*, *Glass halo* (the shadow takes the app's colour), *Underline*
      (a coloured bar, no zoom)
- [ ] Glow, Animations and Advanced effects are back in the panel; **Cinematic** and **App names** are still gone
      (deliberately — they mean nothing in this layout)

## After each build

```
.\adb.exe logcat -d | findstr /i "FATAL AndroidRuntime gathin"
```

Nothing from `com.gathin.tvhome` is a pass. Crashes from WeTV (`com.tencent.qqlivei18n`) and the permission
controller are other apps' and are recorded in the handoff — ignore them.

## If Home will not start at all

The recovery screen is the safety net added in 2.19: it says "Home could not start" with **Try again** and **TV
system settings**. To reach the TV's own home picker without it:

```
.\adb.exe shell am start -a android.settings.HOME_SETTINGS
```

And to go back to a build that worked:

```
.\adb.exe install -r tvhome-2.22.apk
```
