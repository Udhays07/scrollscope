# ScrollScope 🦉

Counts the Instagram Reels and YouTube Shorts you watch, with Hoot the owl.
No backend, no account — everything stays on the phone.

## Get the APK (no Android Studio needed)

1. Create a **private** GitHub repo and upload everything in this folder
   (including the hidden `.github` folder).
2. Open the **Actions** tab → the "Build APK" run starts on every push to `main`.
3. When it's green, open **Releases → latest** on your phone and install `ScrollScope.apk`.

Every build is signed with the same key (`app/scrollscope.keystore`), so new
builds install over old ones and keep your data. Keep the repo private.

## First run

1. Allow **Accessibility** (Android 13+: if the toggle is greyed out,
   App info → ⋮ → *Allow restricted settings*, then try again).
2. Allow **Battery: Unrestricted**. On Xiaomi / Oppo / Vivo / Realme also enable *Autostart*.
3. Allow **Notifications** for the nightly summary.

No "Display over other apps" permission is needed: the accessibility service
draws Hoot's bubble itself (`TYPE_ACCESSIBILITY_OVERLAY`).

## If reels aren't being counted

Instagram/YouTube change their internal view IDs between versions.

1. Open a reel, tap Hoot → **Dump screen tree**
   (or Settings → Debug log → **Dump in 5s**, then switch to the reel).
2. In Debug log, share the dump file.
3. Find the ID of the full-screen vertical pager and put it at the top of the
   list in `app/src/main/java/com/scrollscope/app/service/ReelDetector.kt`.

## Project map

| Folder | What's inside |
|---|---|
| `service/` | Accessibility service, Reels/Shorts detection, swipe debounce, sessions, tree dump |
| `overlay/` | Hoot's floating bubble, long-press menu, mindful-pause dialog |
| `data/` | Room tables (sessions, reel events), DataStore settings, stats queries |
| `ui/` | Material 3 screens: onboarding, Today, Trends, Insights, Settings, Debug, share card |
| `work/` | Nightly summary notification + data cleanup |

Tuning knobs: `DEBOUNCE_MS`, `ENTER_GRACE_MS` in `ReelAccessibilityService`,
`GRACE_MS` (session end) in `SessionTracker`.
