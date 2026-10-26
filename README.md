# plugin251428

An Android game-automation app for the request in
[mmunki/opencode#82](https://github.com/mmunki/opencode/issues/82).

- **Double volume-up** starts the bot, **double volume-down** stops it.
- Works with **root** (`su`, `screencap`, `input`) and/or an
  **AccessibilityService** (key filtering, UI reading, gestures, screenshots).
- Finds visible **skip / continue / next / claim / close / play** buttons and
  taps them, which covers the post-goal replay and reward screens.
- Has a **soccer vision engine**: it finds the ball, finds coloured player
  blobs, scores open lanes to the goal, and emits short passes / shots.
- An **auto-skip only** mode does nothing except tap those buttons.

There is no network code and no analytics. Everything runs on the device.

## What "100%" means here, honestly

No app can guarantee that it beats every human player or that it works after
every game update. That would require the game's internal state and is exactly
what server-side anti-cheat is designed to detect. This app is a fast,
configurable input + vision loop. It is honest about what it can see: pixels
and the accessibility tree. If the game changes its colours or UI, the
settings need to be adjusted. Using automation in an online game can also break
that game's terms of service and lead to a ban. Use it on your own account and
at your own risk.

## Install

1. Download `app-release.apk` from the release and install it.
2. Open the app.
3. Either grant root when the app asks (Magisk / KernelSU / SuperSU), or enable
   **Plugin251428 game trigger and UI reader** in Android accessibility
   settings. Accessibility alone is enough on Android 11+ because it can take
   screenshots and dispatch gestures. Root is the fallback for older Android
   or games whose surface refuses accessibility screenshots.
4. Set the **game package** (for example `com.example.game`). Soccer vision
   only runs when a package is set; with an empty package only the safe
   auto-skip mode acts, in any app.
5. Tap **Arm bot**. The foreground notification stays while armed so Android
   does not kill the trigger.
6. Switch to the game and double-press volume-up.

Double volume-down stops the loop but keeps the service armed, so another
double volume-up starts it again. The notification's **Stop** action and the
**Disarm** button shut the service down completely.

## Settings

| Setting | Meaning |
| --- | --- |
| Game package | Only act when this package is in front. Required for soccer vision; empty = auto-skip only. |
| Mode | `Soccer vision` or `Auto-skip only`. |
| Goal side | `Auto` looks for the brightest goal band; otherwise force left/right/top/bottom. |
| Reaction | Delay between frames, 150-1050 ms. Lower is faster but uses more CPU. |
| Aggression | Higher = shoot from farther away and push the lane harder. |

The goal-side setting is the most important one for the soccer mode. A
top-down game usually wants `Auto` or `Top`; a side-view game wants `Left` or
`Right`.

## How it works

```
volume keys
  ├─ AccessibilityService.onKeyEvent ── double press ─┐
  └─ root `getevent -l` fallback ────── double press ─┤
                                                      v
                                            GameBotService (foreground)
                                              ├─ find skip/continue node
                                              ├─ screenshot (accessibility, else screencap)
                                              ├─ SoccerVision (ball, kit blobs, lane score)
                                              └─ input tap/swipe (root, else dispatchGesture)
```

- `SoccerVision` downsamples the frame, flood-fills bright compact blobs for
  the ball and saturated blobs for players, then scores five aim points along
  the goal line by how much of the pass lane they leave open. `SoccerBrain`
  turns the best point into a swipe that starts on the ball.
- Skip detection walks up to 700 accessibility nodes and matches visible text,
  content description and resource id against a priority list (skip, continue,
  next, claim, watch, close, OK, play). It taps the clickable ancestor.
- Every command is bounded by a timeout. A missing root shell, a screenshot
  failure, or a disappearing view degrades to the other path instead of
  blocking the loop.

## Build

Requirements: JDK 17, Android SDK with build-tools and `android-36` (or set
`PLATFORM`).

```bash
./scripts/build.sh
```

The script uses `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` directly. It
never needs Gradle or the network. Output: `dist/app-release.apk`.

Signing: the script creates `~/.android/plugin251428.keystore` on first use.
Set `KEYSTORE_PATH` to use a stable key of your own. The published APK is
signed with that local key so it can be installed directly; it is not a Play
Store upload key.

Unit tests cover the double-press timing, ball detection, goal-band detection,
blocked-lane aim selection, and the generated swipe. They run as part of
`scripts/build.sh`.

## Test matrix

The app has no native code. One dex serves all ABIs.

| Item | Result |
| --- | --- |
| Compile SDK / target SDK | API 36 |
| Min SDK | 26 |
| Java | 8 language level, desugared by d8, min-api 26 |
| ABI | architecture independent (no `.so` files) |
| Core logic tests | 20 assertions: double press, ball, goal band, blocked lane, plan, non-field rejection - pass |
| APK verification | `apksigner verify` v1/v2 pass |
| Android 14 API 34 emulator, x86_64, no root | install, launch, accessibility binding, hardware double volume-up start, double volume-down stop - pass |
| Same emulator, skip + vision | synthetic field with a Skip button: button tapped, then 1080x2340 frames produced ball-on-(270,1718) swipes toward the right goal - pass |
| Safety check | white/non-green screen produces no gesture - pass |
| Root path | implemented with `su`, `screencap -p` and `input tap/swipe`; not exercised because the emulator has no `su` binary. The accessibility path was used for the runtime tests. |

## Source layout

```
src/com/mmunki/plugin251428/
  MainActivity.java            settings and live log
  GameBotService.java          foreground loop, trigger fallback, wake lock
  BotAccessibilityService.java key filter, skip finder, gestures, screenshots
  ConfigStore.java             SharedPreferences
  core/
    DoublePressDetector.java   timing (pure)
    SoccerVision.java          pixel analysis (pure)
    SoccerBrain.java           action selection (pure)
    GamePlan.java              action value (pure)
    GameConfig.java            settings value (pure)
    VisionFrame.java           ARGB frame (pure)
    RootShell.java             su wrapper
    ScreenSource.java          accessibility/root capture
    InputController.java       root/accessibility input
    BotLog.java                in-process log ring (pure)
tests/CoreTests.java           dependency-free assertions
```
