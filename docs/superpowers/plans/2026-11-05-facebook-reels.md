# Facebook Reels Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scrollscope counts Facebook Reels and Iron Hoot limits them, the same as Instagram Reels and YouTube Shorts.

**Architecture:** First replace the Instagram/YouTube `if/else` branches spread across the code with one registry, `SupportedApps`. Settings, Iron rules, the detector and every screen loop over it. Then Facebook becomes one registry entry plus its player matchers, which are found on a real phone with the existing "Dump screen tree" tool.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose, DataStore Preferences, JUnit 4, Gradle 8.9 (CI).

**Spec:** None written. This plan comes from the chat on 2026-11-05 ("I wanted to add Facebook to this one as well"; the app is for the user and people they know, not the Play Store). The decisions below are Claude's calls. The user should veto any before execution.

**Depends on:** Task 0 of `2026-11-05-iron-timer-bubble.md` (JUnit + tests in CI). If you run this plan first, do that task first.

## Decisions (Claude's calls, veto before execution)

1. **Only the main Facebook app** (`com.facebook.katana`). Facebook Lite (`com.facebook.lite`) draws its own screens and exposes almost nothing to accessibility services, so it's out of scope.
2. **Facebook gets its own daily limit** in Iron Hoot (default 60 min, same 15–120 min range). It's covered by the night lock too.
3. **Names:** app label "Facebook", feed label "Facebook Reels" (so "5 min of Facebook Reels left" is clear).
4. **Existing users keep their settings:** Instagram and YouTube keep their current DataStore keys, so there's no migration.
5. **Matching:** if Facebook's Reels player has no stable view IDs, which is likely, the detector also matches by accessibility description text.

## Global Constraints

- `Apps.FACEBOOK = "com.facebook.katana"`.
- Existing DataStore keys are unchanged: `track_instagram`, `track_youtube`, `iron_reels_min`, `iron_shorts_min`. New: `track_facebook`, `iron_facebook_min`.
- `SupportedApps.kt` must not import `android.*`/`androidx.*`. Icons and colors live in the UI layer.
- After this plan, no file outside `data/SupportedApps.kt` and `data/Entities.kt` refers to `Apps.INSTAGRAM`/`Apps.YOUTUBE`/`Apps.FACEBOOK` (check with grep in Task 4).
- Keep `minSdk 26`/`targetSdk 34`; no new runtime dependencies.

## Review Focus

1. **Upgrading an existing install:** a user who set Reels to 30 min and turned Shorts tracking off must see exactly that after the update. Test: `legacyKeysStillDriveInstagramAndYoutube` (Task 2).
2. **Facebook home feed with an autoplaying video** (not the Reels viewer): must **not** count as Reels. Manual check in Task 3.
3. **Iron Hoot already holding on, then the update adds Facebook at 60 min:** "settings can only get stricter" must still hold for the new row. The ➕ is disabled while locked, the same as the other rows. Manual check in Task 4.
4. **Switching from Facebook Reels straight to Instagram Reels:** a new session starts, and each app's time goes to its own budget. `SessionTracker` already splits on package change. Manual check in Task 3.
5. **Facebook not installed:** the app works normally. The Facebook row still shows in Settings and Iron, and the count stays at 0.

---

### Task 1: Find Facebook's Reels player on a real phone (manual)

**Files:**
- Modify: `app/src/main/java/com/scrollscope/app/service/ReelDetector.kt` (comment only: record findings)

- [ ] **Step 1:** Install the current APK with the latest Facebook app. In Debug, tap "Dump in 5s", open a Facebook Reel and wait. Repeat on: the Facebook home feed with a video playing, the Watch tab, and a Reel opened from a friend's profile.
- [ ] **Step 2:** From the dumps, record:
  - (a) any `com.facebook.katana:id/...` IDs that appear **only** in the Reels viewer dumps;
  - (b) failing that, a `contentDescription` or text that appears only there (e.g. a "Reels" header or a "Like"/"Comment"/"Share" rail shown only on full-screen reels).
- [ ] **Step 3:** Write the findings as a comment block above `PLAYER_IDS` with the Facebook version (Settings › Apps › Facebook). These values feed `SupportedApp.playerIds` / `playerDescriptions` in Task 3.
- [ ] **Step 4: Commit** — `git commit -m "docs(detector): record Facebook Reels matchers from dumps"`

---

### Task 2: `SupportedApps` registry and per-app settings

**Files:**
- Create: `app/src/main/java/com/scrollscope/app/data/SupportedApps.kt`
- Modify: `app/src/main/java/com/scrollscope/app/data/Entities.kt` (`Apps.FACEBOOK`)
- Modify: `app/src/main/java/com/scrollscope/app/data/SettingsStore.kt`
- Modify: `app/src/main/java/com/scrollscope/app/iron/IronRules.kt`, `iron/IronStore.kt` (L38)
- Test: `app/src/test/java/com/scrollscope/app/data/SupportedAppsTest.kt`, `app/src/test/java/com/scrollscope/app/iron/IronRulesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class SupportedApp(
      val pkg: String, val appLabel: String, val feedLabel: String,
      val trackKey: String, val limitKey: String,
      val playerIds: List<String>, val playerDescriptions: List<String>,
  )
  object SupportedApps {
      val ALL: List<SupportedApp>          // order: Instagram, YouTube, Facebook
      val PKGS: Set<String>
      fun byPkg(pkg: String): SupportedApp?
  }
  // AppSettings: trackInstagram/trackYoutube/reelsLimitMin/shortsLimitMin are replaced by
  val tracked: Map<String, Boolean> = emptyMap()     // pkg -> on; missing = true
  val limitMin: Map<String, Int> = emptyMap()        // pkg -> minutes; missing = 60
  fun isTracked(pkg: String): Boolean
  fun limitFor(pkg: String): Int
  fun withTracked(pkg: String, on: Boolean): AppSettings
  fun withLimit(pkg: String, min: Int): AppSettings
  ```
- `IronRules.APPS` becomes `SupportedApps.PKGS`. `limitMs(pkg, s)` = `s.limitFor(pkg) * 60_000L`. `appLabel`/`feedLabel` read from `byPkg(pkg)`, falling back to the package name.
- Registry values: Instagram (`track_instagram`, `iron_reels_min`, "Instagram", "Reels"); YouTube (`track_youtube`, `iron_shorts_min`, "YouTube", "Shorts"); Facebook (`track_facebook`, `iron_facebook_min`, "Facebook", "Facebook Reels"). Copy the Instagram and YouTube `playerIds` from `ReelDetector.PLAYER_IDS`. Facebook's come from Task 1 (leave them empty in this task).

- [ ] **Step 1: Write the failing tests**

```kotlin
class SupportedAppsTest {
    @Test fun facebookIsRegistered() {
        val fb = SupportedApps.byPkg("com.facebook.katana")!!
        assertEquals("Facebook", fb.appLabel); assertEquals("Facebook Reels", fb.feedLabel)
        assertEquals(listOf(Apps.INSTAGRAM, Apps.YOUTUBE, Apps.FACEBOOK), SupportedApps.ALL.map { it.pkg })
    }
    @Test fun legacyKeysStillDriveInstagramAndYoutube() {
        assertEquals("track_instagram", SupportedApps.byPkg(Apps.INSTAGRAM)!!.trackKey)
        assertEquals("iron_reels_min", SupportedApps.byPkg(Apps.INSTAGRAM)!!.limitKey)
        assertEquals("track_youtube", SupportedApps.byPkg(Apps.YOUTUBE)!!.trackKey)
        assertEquals("iron_shorts_min", SupportedApps.byPkg(Apps.YOUTUBE)!!.limitKey)
    }
    @Test fun settingsDefaults() {
        val s = AppSettings()
        assertTrue(s.isTracked(Apps.FACEBOOK)); assertEquals(60, s.limitFor(Apps.FACEBOOK))
        assertFalse(s.withTracked(Apps.FACEBOOK, false).isTracked(Apps.FACEBOOK))
        assertEquals(30, s.withLimit(Apps.YOUTUBE, 30).limitFor(Apps.YOUTUBE))
    }
}
class IronRulesTest {
    private val on = AppSettings(ironEnabled = true, openHour = 10, closeHour = 23).withLimit(Apps.FACEBOOK, 15)
    @Test fun facebookLocksAtItsOwnLimit() {
        assertNull(IronRules.lockReason(Apps.FACEBOOK, 12 * 60, 14 * 60_000L, false, on))
        assertEquals(LockReason.Limit, IronRules.lockReason(Apps.FACEBOOK, 12 * 60, 15 * 60_000L, false, on))
    }
    @Test fun facebookNightLock() = assertEquals(LockReason.Night, IronRules.lockReason(Apps.FACEBOOK, 23 * 60, 0, false, on))
    @Test fun unknownAppNeverLocks() = assertNull(IronRules.lockReason("com.whatsapp", 23 * 60, 0, false, on))
}
```

- [ ] **Step 2:** Run the tests. Expected: compile FAIL (unresolved `SupportedApps`, `withLimit`).
- [ ] **Step 3:** Implement the registry and the `AppSettings` changes. In `SettingsStore.read`/`write`, loop over `SupportedApps.ALL` using `booleanPreferencesKey(app.trackKey)` / `intPreferencesKey(app.limitKey)`, and delete the four old `K.*` keys. Update `IronRules` and `IronStore.read` as described in Interfaces.
- [ ] **Step 4:** Run the tests. Expected: the 6 new tests pass. The build fails only at the UI/service call sites, which Task 4 fixes; to keep the build green, do Step 5 of this task.
- [ ] **Step 5:** Mechanically fix the compile errors in the call sites so the build stays green: `s.trackInstagram` → `s.isTracked(Apps.INSTAGRAM)`, `it.copy(reelsLimitMin = x)` → `it.withLimit(Apps.INSTAGRAM, x)`, and so on. Task 4 replaces them with loops. Run tests again. Expected: green.
- [ ] **Step 6: Commit** — `git commit -m "refactor: SupportedApps registry; per-app track/limit settings"`

---

### Task 3: Detector and service use the registry; Facebook goes live

**Files:**
- Modify: `app/src/main/java/com/scrollscope/app/service/ReelDetector.kt`
- Modify: `app/src/main/java/com/scrollscope/app/service/ReelAccessibilityService.kt` (L89-90)
- Modify: `app/src/main/java/com/scrollscope/app/data/SupportedApps.kt` (Facebook matchers from Task 1)

**Interfaces:**
- Consumes: `SupportedApps.byPkg`, `SupportedApp.playerIds/playerDescriptions`, `AppSettings.isTracked` (Task 2).
- Produces: `ReelDetector.isReelPlayer(root, pkg)` keeps its signature; `PLAYER_IDS` is deleted.

- [ ] **Step 1:** `ReelDetector.isReelPlayer`: look up the app. First try each `playerIds` entry (current logic). If none match, try each `playerDescriptions` entry with `root.findAccessibilityNodeInfosByText(desc)`, keeping only nodes where `isVisibleToUser` and `contentDescription` or `text` equals `desc` exactly (case-insensitive). Set `lastMatch` to `"id:<x>"` or `"desc:<x>"`.
- [ ] **Step 2:** In the service, set `tracked = SupportedApps.byPkg(pkg) != null && s.isTracked(pkg)`; `ironApp = pkg in IronRules.APPS`.
- [ ] **Step 3:** Fill in Facebook's `playerIds`/`playerDescriptions` from Task 1's findings.
- [ ] **Step 4:** Run tests. Expected: green.
- [ ] **Step 5: Manual check on a phone:**
  - (a) Facebook Reels: the bubble appears and swipes count; the Debug log shows `player ✓ id:`/`desc:`.
  - (b) Facebook home feed with a video playing: no bubble, no count.
  - (c) Facebook Reels → Instagram Reels: the Debug log shows two `SESSION … start` lines.
  - (d) Instagram and YouTube still detected as before.
- [ ] **Step 6: Commit** — `git commit -m "feat: detect Facebook Reels"`

---

### Task 4: Screens loop over the registry

**Files:**
- Create: `app/src/main/java/com/scrollscope/app/ui/components/AppVisuals.kt`
- Modify: `ui/screens/TodayScreen.kt` (L111-112, L167), `ui/screens/SettingsScreen.kt` (L120-126), `ui/screens/IronCards.kt` (L148-149, L261-276, L384-385), `ui/morph/IronMorph.kt` (L227, L452-456), `ui/screens/DebugScreen.kt` (L128-129)

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun SupportedApp.accent(): Color       // IG cs.primary, YT cs.tertiary, FB cs.secondary
  @Composable fun SupportedApp.accentContainer(): Color
  fun SupportedApp.icon(): ImageVector               // IG Rounded.Movie, YT Rounded.SmartDisplay, FB Rounded.OndemandVideo
  ```

- [ ] **Step 1:** Create `AppVisuals.kt` with the mapping above, keyed by `pkg`.
- [ ] **Step 2:** Replace each listed call site with a loop over `SupportedApps.ALL`:
  - **Today:** three `AppCard`s in the row; session dot color from `byPkg(s.app)?.accent()`.
  - **Settings:** one tracking switch per app labeled with `feedLabel`.
  - **IronCards:** one `StepRow` per app, then the open/close rows, with `stagger` indexes shifted. The "used up" snackbar joins labels with ", " and " and " for the last one. One `BudgetLine` per app.
  - **IronMorph L227:** sum minutes over all apps: "Today's N min of short video already count."
  - **IronMorph L452:** if all limits are equal, "Reels, Shorts and Facebook Reels lock after X min a day"; otherwise list each "<feedLabel> after X min".
  - **Debug:** use `accent()`.
- [ ] **Step 3:** Run `grep -rnE "Apps\.(INSTAGRAM|YOUTUBE|FACEBOOK)" app/src/main/java | grep -vE "data/(SupportedApps|Entities)\.kt|ui/components/AppVisuals\.kt"`. Expected: no output.
- [ ] **Step 4:** Run tests. Expected: green.
- [ ] **Step 5: Manual check:**
  - (a) Today shows three cards and fits at 360 dp width with the largest font.
  - (b) Iron card shows three limit rows; while locked, ➕ is disabled on all three including Facebook.
  - (c) Upgrade install over the old APK: earlier Reels/Shorts limits and tracking switches are kept.
  - (d) TalkBack reads each switch as "Facebook Reels, switch, on".
- [ ] **Step 6: Commit** — `git commit -m "feat(ui): show Facebook Reels across screens"`
