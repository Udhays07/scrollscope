# Iron Hoot Timer Bubble Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Iron Hoot is on, the floating Hoot bubble shows a live countdown of today's Reels/Shorts time left (instead of the reel count). The bubble is readable with TalkBack and large fonts, and the mascot reacts to how much time is left.

**Architecture:** A new Android-free `IronTimer` turns (time left, limit, pass) into a `TimerReading` (clock text, fraction left, stage, spoken text). The accessibility service pushes a reading to the overlay every second, replacing the old `IronBadge`. `BubbleContent` draws a depleting ring, a tabular-digit timer, and an Iron Owl pose chosen by stage.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.02), JUnit 4 for JVM unit tests, Gradle 8.9 (CI).

**Spec:** None written. This plan comes from the chat on 2026-11-05 ("in the Iron Hoot mode, the floating Hoot should show a running timer instead of the reels count; think about design, make it accessible, better mascot design elements"). The decisions below are Claude's calls unless marked otherwise. The user should veto any they disagree with before execution.

## Decisions (Claude's calls, veto before execution)

1. **Countdown, not count-up.** The timer shows time **left** today for the app in front, because the limit is what Iron Hoot enforces.
2. **Always on in Iron mode.** The timer shows whenever Iron Hoot is enabled and you're in the Reels/Shorts player. Today the badge only appears in the last 10 minutes.
3. **Seconds tick.** Format is `m:ss`, or `h:mm:ss` from 1 hour up (limits go to 120 min).
4. **The bubble shows in Iron mode even if counting is off** for that app in Settings.
5. **During the 5-minute pass,** the timer counts the pass down instead (this already happens today).
6. **Outside Iron mode, nothing changes:** reel count, "+1" pop, amber/red ring.
7. **Mascot stages:** Calm (more than 10 min left) shows a new `Watch` pose (visor half up, amber eyes, relaxed). Low (10 min or less) shows `Guard`. Critical (5 min or less) shows `Guard`, plus one steam puff on entering the stage. Pass shows `Granted`.
8. **TalkBack hears minutes, not seconds:** "42 minutes of Reels left". The description changes at most once a minute, so TalkBack isn't spammed every second.

## Global Constraints

- Keep `minSdk = 26`, `compileSdk`/`targetSdk = 34`. Add no new runtime dependencies (only `testImplementation("junit:junit:4.13.2")`).
- `IronTimer.kt` must not import any `android.*` or `androidx.*` class, so it runs as a plain JVM unit test.
- Every animation added respects `rememberMotionEnabled()`: with reduced motion there's no ring smoothing, no pulse, and no steam.
- Badge colors (text on fill) are exactly: Calm `IronColors.I950` on `IronColors.I300`; Low `HootColors.OnAmber` on `HootColors.Amber`; Critical `IronColors.I950` on `IronColors.Ember`; Pass `IronColors.I950` on `IronColors.EmberHot`. All are ≥ 6:1 contrast.
- Badge timer text uses tabular digits (`fontFeatureSettings = "tnum"`) so it doesn't jiggle each second.
- The bubble's touch target stays ≥ 48 dp (the owl circle is 64 dp).

## Review Focus

1. **Limit already used up but a pass is active:** the reading must be the pass countdown (stage Pass), not `0:00`. Test: `passOverridesExhaustedLimit` (Task 1).
2. **Time left at an exact boundary** (exactly 10:00 or 5:00): the stage must be Low or Critical (inclusive), so the color and the 5-minute banner agree. Tests: `stageBoundariesAreInclusive` (Task 1).
3. **Large system font (200%):** the badge must not clip `1:59:59`. Cap badge scaling at 1.3× and let the badge widen. Manual check in Task 3.
4. **Limit changed mid-day to below time watched:** time left goes negative. It must show `0:00` with fraction 0, not a negative clock. Test: `negativeLeftClampsToZero` (Task 1).
5. **TalkBack:** focusing the bubble reads the minutes left and offers "Open Hoot menu"; it must not read "Hoot the owl" separately. Manual check in Task 3.

---

### Task 0: Unit-test plumbing

There is no Gradle, JDK or Android SDK on this Mac, and the repo has no tests. CI is the test runner.

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block)
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1:** Add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts`.
- [ ] **Step 2:** In `build.yml`, add `pull_request:` and `push: branches: [ main, 'feature/**' ]` triggers. Add a step `gradle :app:testDebugUnitTest --stacktrace` before "Build signed APK". Make the "Publish to the latest release" step run only on `main` (`if: github.ref == 'refs/heads/main'`).
- [ ] **Step 3:** Create `app/src/test/java/com/scrollscope/app/SmokeTest.kt` with one test asserting `1 + 1 == 2`.
- [ ] **Step 4:** Commit and push the branch. Run `gh run watch`. Expected: the "testDebugUnitTest" step passes. Then delete `SmokeTest.kt` in Task 1's commit.
- [ ] **Step 5: Commit** — `git commit -m "build: run JVM unit tests in CI"`

> **Verification for all later tasks:** "Run tests" means push and `gh run watch` (expected: green). If the user has installed JDK 17, Gradle 8.9 and the Android SDK locally, `gradle :app:testDebugUnitTest` works the same way.

---

### Task 1: `IronTimer` — the pure timer model

**Files:**
- Create: `app/src/main/java/com/scrollscope/app/iron/IronTimer.kt`
- Test: `app/src/test/java/com/scrollscope/app/iron/IronTimerTest.kt`

**Interfaces:**
- Consumes: `IronRules.IRON_BUBBLE_MS` (10 min), `IronRules.WARN_MS` (5 min), `IronRules.PASS_MS` (5 min).
- Produces:
  ```kotlin
  enum class TimerStage { Calm, Low, Critical, Pass }
  data class TimerReading(val text: String, val fractionLeft: Float, val stage: TimerStage, val spoken: String)
  object IronTimer {
      fun clock(ms: Long): String
      fun reading(leftMs: Long, limitMs: Long, passLeftMs: Long?, feedLabel: String): TimerReading
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
class IronTimerTest {
    @Test fun clockUnderAnHour() {
        assertEquals("42:07", IronTimer.clock(42 * 60_000L + 7_000))
        assertEquals("0:09", IronTimer.clock(8_001))          // rounds partial seconds up
    }
    @Test fun clockOverAnHour() = assertEquals("1:05:09", IronTimer.clock(3_909_000))
    @Test fun negativeLeftClampsToZero() {
        val r = IronTimer.reading(-5_000, 3_600_000, null, "Reels")
        assertEquals("0:00", r.text); assertEquals(0f, r.fractionLeft); assertEquals(TimerStage.Critical, r.stage)
    }
    @Test fun stageBoundariesAreInclusive() {
        assertEquals(TimerStage.Calm, IronTimer.reading(600_001, 3_600_000, null, "Reels").stage)
        assertEquals(TimerStage.Low, IronTimer.reading(600_000, 3_600_000, null, "Reels").stage)
        assertEquals(TimerStage.Critical, IronTimer.reading(300_000, 3_600_000, null, "Reels").stage)
    }
    @Test fun fractionLeft() = assertEquals(0.25f, IronTimer.reading(900_000, 3_600_000, null, "Reels").fractionLeft, 1e-4f)
    @Test fun passOverridesExhaustedLimit() {
        val r = IronTimer.reading(0, 3_600_000, 150_000, "Reels")
        assertEquals(TimerStage.Pass, r.stage); assertEquals("2:30", r.text); assertEquals(0.5f, r.fractionLeft, 1e-4f)
        assertEquals("Pass: 3 minutes left", r.spoken)
    }
    @Test fun spokenUsesMinutesRoundedUp() {
        assertEquals("42 minutes of Reels left", IronTimer.reading(41 * 60_000L + 1, 3_600_000, null, "Reels").spoken)
        assertEquals("1 minute of Shorts left", IronTimer.reading(20_000, 3_600_000, null, "Shorts").spoken)
        assertEquals("No Reels time left", IronTimer.reading(0, 3_600_000, null, "Reels").spoken)
    }
}
```

- [ ] **Step 2:** Run the tests. Expected: compile FAIL, "Unresolved reference: IronTimer".
- [ ] **Step 3:** Implement `IronTimer.kt` with the interfaces above. Seconds round up (same as `Format.mmss`). Stage rules: Pass if `passLeftMs != null && passLeftMs > 0`; else Critical if left ≤ `WARN_MS`; Low if left ≤ `IRON_BUBBLE_MS`; else Calm. Fraction is `left/limit` (or `passLeft/PASS_MS`), clamped to 0..1, and 0 when the limit is ≤ 0. Spoken minutes = ceil(ms / 60 000), with "minute" singular at 1. Spoken text is `"Pass: N minutes left"` for Pass, `"No <feed> time left"` when left ≤ 0, and `"N minutes of <feed> left"` otherwise.
- [ ] **Step 4:** Run the tests. Expected: all 7 pass.
- [ ] **Step 5: Commit** — `git commit -m "feat(iron): add IronTimer countdown model"`

---

### Task 2: Feed the timer to the bubble every second

**Files:**
- Modify: `app/src/main/java/com/scrollscope/app/overlay/OverlayManager.kt` (`IronBadge`, `BubbleState`, `setIron`)
- Modify: `app/src/main/java/com/scrollscope/app/service/ReelAccessibilityService.kt` (`ironCheck` ~L183-191, `refreshPlayer` ~L208-226)

**Interfaces:**
- Consumes: `IronTimer.reading(...)`, `TimerReading` (Task 1).
- Produces: `data class BubbleState(val count: Int = 0, val limit: Int = 200, val iron: TimerReading? = null)` and `fun setIron(reading: TimerReading?)` on `OverlayManager`. Delete `IronBadge`.

- [ ] **Step 1:** Replace `IronBadge` with `TimerReading` in `BubbleState` and `setIron`. In `ironCheck`, replace the `overlay.setIron(when { ... })` block with `overlay.setIron(IronTimer.reading(limit - watched, limit, (snap.passUntil - wall).takeIf { passActive }, IronRules.feedLabel(pkg)))`. Leave the banners and lock logic unchanged.
- [ ] **Step 2:** In `refreshPlayer`, show the bubble when the player is found if `track || app.settingsState.value.ironEnabled` (Decision 4). Keep `tracker.onEnterPlayer()` / `tracker.onLeave()` gated on `track` only.
- [ ] **Step 3:** In `BubbleContent` (`OverlayContent.kt`), make it compile against the new type for now: `iron.text`, `hot = iron.stage == Critical || iron.stage == Pass`, `granted = iron.stage == Pass`. Task 3 redesigns it.
- [ ] **Step 4:** Run tests (the build must compile). Expected: green.
- [ ] **Step 5: Commit** — `git commit -m "feat(iron): bubble shows live time left in Iron mode"`

---

### Task 3: Timer bubble design, accessibility and mascot

**Files:**
- Modify: `app/src/main/java/com/scrollscope/app/overlay/OverlayContent.kt` (`BubbleContent`)
- Modify: `app/src/main/java/com/scrollscope/app/ui/components/IronOwl.kt` (`IronPose`; `granted` mapping at ~L92 and ~L102)
- Create: `app/src/main/java/com/scrollscope/app/overlay/TimerRing.kt`

**Interfaces:**
- Consumes: `BubbleState.iron: TimerReading?` (Task 2).
- Produces: `enum class IronPose { Guard, Granted, Night, Watch }`. `@Composable fun TimerRing(fraction: Float, color: Color, modifier: Modifier)` draws a 4 dp arc. It starts at 12 o'clock and sweeps clockwise for `360 * fraction`, over a full track circle of `IronColors.I700`.

- [ ] **Step 1:** Add `IronPose.Watch` to `IronOwl.kt`. It draws as `granted = 0.5f` in both the static and animated paths: the visor sits halfway, the eyes are amber, there's no scan sweep. Every other pose stays exactly as it is.
- [ ] **Step 2:** Create `TimerRing`. With motion on, animate the sweep using `animateFloatAsState(tween(900))`. With reduced motion, draw the exact value.
- [ ] **Step 3: Redesign the Iron branch of `BubbleContent`** (when `s.iron != null`):
  - Replace the 3 dp `border` with `TimerRing` around the 64 dp circle, colored by stage (Global Constraints fills). Draw the pulse only for Critical.
  - Choose the pose: Calm→`Watch`, Low/Critical→`Guard`, Pass→`Granted`. On entering Critical, bump `fumeKey` once (guarded by `remember` of the previous stage).
  - Badge shows `reading.text` with `fontFeatureSettings = "tnum"` and `maxLines = 1`. Font size is 13 sp × min(fontScale, 1.3) ÷ fontScale. Use `widthIn(min = 56.dp)` and let it grow. Change the outer `Box` width from fixed `80.dp` to `widthIn(min = 80.dp)`.
  - Skip the "+1" float and the count pop in Iron mode.
- [ ] **Step 4: Accessibility on the outer `Box`:** `clearAndSetSemantics { contentDescription = iron?.spoken ?: "$count reels today"; onClick(label = "Open Hoot menu") { onTap(); true } }`. This lets TalkBack double-tap open the menu and stops it from reading the inner "Hoot the owl".
- [ ] **Step 5:** Run tests. Expected: green.
- [ ] **Step 6: Manual check on a phone** (install the APK from the CI artifact):
  - (a) Iron on, open Reels: the timer ticks, the ring shrinks, Hoot is in the Watch pose.
  - (b) Set the limit so less than 10 min is left: amber, Guard pose.
  - (c) At 5 min: ember, one steam puff, the banner appears.
  - (d) Use the pass: Granted pose, pass countdown.
  - (e) Settings › Display size and Font size at maximum: `1:59:59` isn't clipped.
  - (f) TalkBack on: focusing the bubble reads "N minutes of Reels left", and double-tap opens the menu.
  - (g) Settings › Accessibility › Remove animations: no ring animation or pulse.
  - (h) Iron off: the reel count and "+1" behave as before.
- [ ] **Step 7: Commit** — `git commit -m "feat(iron): timer ring, accessible bubble, Watch pose"`
