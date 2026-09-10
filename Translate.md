I'll start by exploring the project structure to understand the codebase, then dig into the language/locale handling to find why Turkish appears.

Only a single `values` folder — so Turkish strings are likely hardcoded in code or a locale switch is happening programmatically. Let me search deeper.

Found it — there's a custom `core\Language.kt` and `core\Strings.kt`. Let me read those plus the app entry points.

Root cause found already: `LanguageManager.getLanguage()` **defaults to Turkish** when no preference is stored. Let me see how strings are consumed across the app.

The picture is clear: the app has a hand-rolled i18n system (`Strings.kt` with TR/EN sets) that **defaults to Turkish** on first launch. Now let me find every place that uses `LanguageManager`, `AppLanguage`, or `Strings` so I can remove Turkish end-to-end.

Now I have the full usage map. Let me read the affected screens (Splash, MainActivity, AppList language toggle) and also sweep for any hardcoded Turkish text elsewhere (Toasts, labels) that bypasses the `Strings` system.

Now let me check the language toggle in `AppListScreen` and sweep for any hardcoded Turkish strings anywhere else in the codebase.

This is bigger than a default-value bug: besides the language system that **defaults to Turkish**, about 20 screens have Turkish **hardcoded directly in Kotlin** (the newer v4 modules never went through the `Strings` system) — that's why English never sticks. Let me check the remaining user-facing surfaces (resources, manifest, services, navigation) before fixing everything.

Resources and manifest are clean English. The work is now clear — let me set up a plan and work through every affected file.

## Step 1 — Remove the Turkish language system

The toggle in AppList was also broken (it never triggered recomposition — another reason the app "stayed Turkish"). I'll delete the language system entirely and keep `Strings` as a single English source.

Now the app-level wiring — Application class, MainActivity, and the splash screen (which becomes a branding-only English splash):

Now remove the broken language toggle from the Apps screen header:

## Step 2 — Translate the hardcoded Turkish screens

Now the bulk of the work. Reading the monitor package screens first:

Monitor package done. Next batch — ActivityLauncher, ADB, AppDetail, AutoAdb:

Now AppDetailScreen and AutoAdbScreen:

Batch done. Next: BroadcastFuzzer, DexAnalyzer, FileProvider, FridaGenerator:

Now FridaGenerator:

Next batch — ManifestViewer, Payload Engine/Screen, SharedPrefs, ContentProvider:

Final translation batch — SslBypass, Terminal, WebTester, plus checking the remaining files:

Now checking the remaining files — About, IntentFuzzer, common components, NavGraph, services, and the rest of AppList:

AboutScreen is clean. Fixing the NavGraph comment, then checking the last files:

IntentFuzzer is clean. Checking the remaining files — AppList rest, common components, Screen.kt, services, accessibility config:

Now the services (notification text is user-visible) and accessibility config:

Services and accessibility config are clean. Checking themes + Screen.kt, then running a comprehensive final sweep (Turkish diacritics AND common Turkish words without diacritics):

Both clean. Now the comprehensive final sweep:

Sweep results: the diacritic hits are false positives (✓ ✗ ▸ ▲ ▼ symbols — findstr quirk), but there's one real leftover: a `"Temizle"` contentDescription in TerminalScreen. Fixing it:

## Step 3 — Build to verify everything compiles

The build failed at **plugin resolution** (couldn't download the Android Gradle Plugin) — that's before any compilation, so it doesn't reflect on the code. Let me check the local Gradle cache and retry:

No AGP in cache, but a previously-built APK exists. Let me look at the repo configuration and retry:

The `local.properties` points to a Linux path (`/workspaces/android-sdk`) — this project was built in a container before. Let me find the SDK on this Windows machine and fix it:

No Android SDK exists on this Windows machine (and the project's `local.properties` points to a Linux container path — this project was previously built in a codespace), so a full Gradle build can't run here. I'll instead verify correctness statically — checking for any dangling references to the removed language system and structural integrity of the edited files:

Zero dangling references to the removed language system. Now a proper brace-balance check across all Kotlin files (accounting for braces inside string templates):

All files balanced. One last check for Toasts or other user-visible strings I might have missed:

All titles are English and the app uses no Toasts — every user-visible string is now English.

The language problem is fixed — the app is now 100% English everywhere, on first launch and forever after, regardless of device language.

## Why it kept showing Turkish

There were three separate causes stacking on top of each other:

1. **Turkish was the default.** `core/Language.kt` had `getLanguage()` returning `TURKISH` whenever no preference was saved (first launch, cleared data, or any read failure).
2. **The English toggle was broken.** The 🇹🇷/🇬🇧 button in the Apps screen saved the choice to SharedPreferences but never told Compose to recompose — so picking English often didn't even take effect until an app restart, and combined with cause #3 it looked random.
3. **Most screens had Turkish hardcoded.** About 20 of the newer modules (Auto ADB, Broadcast Fuzzer, File Provider, Frida Generator, SSL Bypass, Shared Prefs, Dex Analyzer, Payload Engine, etc.) never used the `Strings` system at all — their Turkish text was baked into the Kotlin code, so no language setting could ever change them.

## What I changed

- **Deleted the language system entirely** — `core/Language.kt` is gone (no more Turkish/English enum or prefs), `Strings.kt` now contains only the English string set, and the broken 🇹🇷/🇬🇧 toggle was removed from the Apps screen.
- **Splash screen** no longer shows "Select Language / Dil Seçin" — it's now a short English-only branding animation (logo + "ANDROHUNTER / Android Security Toolkit v2.0") that auto-continues into the app.
- **Translated ~150 hardcoded Turkish strings to English** across every screen: labels, buttons ("[ BAŞLAT ]" → "[ LAUNCH ]"), result messages, error text (`HATA:` → `ERROR:`), quick-command descriptions, Frida script descriptions, SSL bypass step-by-step instructions, accessibility/broadcast monitor panels, Web Tester, Terminal, and more.
- **Verified by sweep** that no Turkish remains anywhere — diacritics (ş/ğ/ı/ç/ö/ü), a common-word scan, all screen titles, services, manifest, and XML resources are clean. All braces balance and no code references the deleted language classes, so nothing dangles.

One honest caveat on verification: I could not run the Gradle build on this machine — it has no Android SDK (`local.properties` points to a Linux container path, `/workspaces/android-sdk`, and no SDK exists locally), and the first attempt also couldn't download the Android Gradle Plugin from Google's Maven. The failures were environmental, before any of my code was compiled. Since I can't compile here, please rebuild in your usual build environment (where you produced `AndroHunter-v.apk`) — the changes are string-level and structural removals, all cross-checked statically.

Also, you mentioned features you want implemented — the message only contained the language fix, so send the feature list and I'll get to work on it next.