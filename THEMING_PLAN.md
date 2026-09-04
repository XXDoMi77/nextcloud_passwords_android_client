# Material You theming from a Nextcloud seed colour — implementation plan

Working plan for the next chunk of work. Delete this file once the work has landed.

## Goal

Every colour in the app comes from a Material 3 palette generated from a seed colour — the colour the
Nextcloud admin set in the Theming app, a colour the user picks, or the system wallpaper. Deliberate
signal colours stay literal. An AMOLED mode is added alongside.

Today the app paints itself from 20 hand-written colour resources with a `values-night` twin, so every
colour is fixed at build time. We already fetch and store the server's colour
(`PasswordRepository.pullServerTheme()` → `Settings.serverThemeColor`) and let the user override it, but
that accent reaches only the two sync sweeps.

## Decisions taken with the user

| Question | Decision |
| --- | --- |
| Surface tinting | Fully tinted — true Material You; surfaces stop being pure white/black |
| AMOLED | A toggle that **forces dark mode**, with pure black surfaces |
| Wallpaper colours | Kept as a third seed option |
| Stay literal | Password status green/yellow/red, favourite star, destructive red |
| Android 10 (API 29) | Keep `minSdk 29`; API 29 falls back to the static palette |
| Palette maths | Material's bundled HCT classes, with a scoped lint suppression |

## Verified facts (do not re-derive)

1. **`DynamicColors` + `setContentBasedSource(seed)` cannot be used.** It early-returns unless
   `isDynamicColorAvailable()` — API 31 plus an OEM allowlist on 31–32. It remains only as an option for
   the wallpaper mode.
2. **The palette maths runs anywhere.** `com.google.android.material.color.utilities` (`Hct`,
   `SchemeContent`, `DynamicScheme`) is pure Java with no `Build.VERSION` checks.
   **Spike confirmed this on the plain JVM test classpath** (`PaletteSpikeTest`), producing from
   `#0082C9`: primary `#006097`, onPrimary `#FFFFFF`, primaryContainer `#007ABD`,
   secondaryContainer `#BEDDFE`, surface `#F7F9FF`, onSurface `#181C20`,
   surfaceContainerHigh `#E5E8EF`, onSurfaceVariant `#404851`, outline `#707882`. A zero-chroma grey seed
   also produces a usable scheme.
3. **Every class in that package is `@RestrictTo(LIBRARY_GROUP)`**, and **CI runs `lintDebug`** with
   `abortOnError`, so `RestrictedApi` would fail the build. Confine the imports to one file with
   `@SuppressLint("RestrictedApi")`.
4. **Material's `material_personalized_color_*` resources are private** (absent from the AAR's
   `public.txt`). Do not reuse them — define our own slots.
5. **Overriding our own colour resources at runtime is supported on API 30+.** Material's public,
   unrestricted `HarmonizedColors.applyToContextIfAvailable(context, options.setColorResourceIds(ourIds))`
   drives the same `ColorResourcesOverride` → `ResourcesLoader` chain over app-owned ids, which is the
   proof the mechanism is not limited to Material's own resources.

**So: compute the palette anywhere; deliver it by overriding our own colour resources with a
`ResourcesLoader` on API 30+, falling back to static defaults on API 29.** The theme style stays static —
only resource *values* change at runtime.

## Design

### Palette layer (new, `ui/theme/`)

- `PaletteGenerator.kt` — `@SuppressLint("RestrictedApi")`. **Must not import anything from `android.*`**
  so it stays JVM-testable: takes a seed `Int` + `isDark` + `isAmoled`, returns `Map<String, Int>`.
- `ColorResourceOverride.kt` — the only `ColorResourcesOverride` call. `SDK_INT >= 30`, a **fresh**
  `ResourcesLoader` per call (never cache one), wrapped in `runCatching` so a failure degrades to the
  static defaults rather than crashing `onCreate`.
- `ThemeApplier.kt` — all `Context` handling.
- `ThemeCache.kt` — see "synchronous read" below.

### Colour slots and theme wiring

Declare ~40 app-owned `npac_*` colour resources in `values/theme_colors.xml` + `values-night/`, each with
a static default generated offline (this doubles as the API 29 palette and the first-frame colour).
`Theme.NextcloudPasswords` maps the **full** M3 role set at them — accents, error, the whole surface
container ramp, outlines, and the AppCompat bridge (`colorControlNormal`, `colorControlActivated`,
`colorControlHighlight`, `android:textColorPrimary/Secondary/Hint`). A role left unmapped falls through to
baseline purple inside some widget's internal style.

Two theme bugs to fix here:

- **`AlertDialogStyle`'s parent is `Theme.Material3.DayNight.Dialog.Alert` — a full theme, not an
  overlay.** `ContextThemeWrapper` re-applies its entire colour set pointing at Material's own
  `m3_sys_color_*` resources, which we do not override, so every dialog would revert to baseline purple.
  Change the parent to **`ThemeOverlay.Material3.Dialog.Alert`** and delete its five hardcoded colour items.
- `AppDialogButton.Destructive` uses `?attr/colorErrorContainer`; per the decision to keep destructive red
  literal, give it a literal container/on-container pair.

### What the loader cannot reach

- **The autofill RemoteViews.** `layout/autofill_dataset_presentation.xml` and
  `drawable/autofill_presentation_key_24.xml` are inflated in the *requesting app's* process. Split their
  colours into dedicated literals (`autofill_presentation_text`, `…_shaded`, with `values-night` twins).
  They must never become `?attr/` — the host app would resolve it against its own theme. Same reasoning
  that already forces `icon_vpn_key_24.xml` to hardcode `#FFFFFF`.
- **The system splash.** `windowSplashScreenBackground` is read before the process starts, so it can never
  be seeded. Give it a static `@color/splash_background` chosen to sit near the default seed's surface.

### Seed, mode and recreate

Model it as one enum — `ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }` — rather than a flag plus a mode, so
"AMOLED forces dark" is a value rather than a rule that can desync.
`SYSTEM → MODE_NIGHT_FOLLOW_SYSTEM`, `LIGHT → MODE_NIGHT_NO`, `DARK`/`AMOLED → MODE_NIGHT_YES`.

**AMOLED must not black out the whole ramp.** Window/surface go `#000000`; the container ramp keeps
near-black *tinted* tones (roughly tones 4–17) or cards, dialogs and sheets stop reading as separate
objects. Raise `on_surface` to tone ≥ 95 for contrast against black.

**Synchronous read.** The theme is needed synchronously in `onCreate`, but `StorageManager` publishes
asynchronously after decrypting on `Dispatchers.IO`. Mirror the three theme fields (seed, source, mode)
into a tiny plain file in `filesDir`, written atomically, read fresh per activity start. **A file, not
`SharedPreferences`** — `AutofillPickerActivity` runs in `:autofill_process`, where prefs cache per
process and would serve a stale theme indefinitely. None of the three values is a secret.

**Apply site: `BaseActivity.onCreate`, immediately after `super.onCreate()`.** Verified as the only safe
window — `OverviewActivity` calls `installSplashScreen()` *before* `super.onCreate()` (which swaps the
theme to `postSplashScreenTheme`) and inflates its binding *after*. All four activities extend
`BaseActivity`, so this covers `:autofill_process` too.

**Recreate rules** (the tabs are show/hide and never recreated, so a theme change needs `recreate()`):

1. `setDefaultNightMode` is called in exactly two places: `MyApplication.onCreate` (from the cache, before
   any activity exists) and the settings handler.
2. Never recreate from a `collect`/observer — `SettingsFragment.render()` runs on every settings emission
   and would loop forever.
3. In the settings handler, avoid a double recreate:
   `if (getDefaultNightMode() != new) setDefaultNightMode(new) else requireActivity().recreate()`.
4. `BaseActivity.onCreate` only ever *applies* — it never compares and never recreates.

A server-side colour change arriving mid-session should **not** auto-recreate; write the cache and apply
on next start.

### Loose ends that would break a tinted look

- `layout/fragment_folders.xml:13` `#FF000000` → `?attr/colorSurfaceContainer`
- `drawable/folder_breadcrumb_background.xml:4,7` → `?attr/colorSurfaceContainerHigh` / `?attr/colorOutlineVariant`
- `layout/activity_enter_server_url.xml:5` — delete `android:theme="@style/Theme.AppCompat.Light.DarkActionBar"`;
  nothing under it can be M3-themed
- `drawable/outline_colors_24.xml` `#FFFFFF` → `?attr/colorControlNormal` (in-process only)
- `res/color/switch_*.xml` → theme attrs, and delete the `color-night/` twins (attrs are already day/night aware)
- Delete `values-v31/colors.xml` and `values-night-v31/colors.xml`
- Remove the now-redundant `accentColor` plumbing from both list view models and fragments — painting one
  progress bar with the raw seed while everything else uses derived `primary` would clash

## Work breakdown

Steps 0-8 landed together; what follows is the record of how it turned out, not a to-do list.

0. ~~Spike: confirm the utilities run on the JVM~~ **done** - `PaletteSpikeTest`, now deleted.
1. ~~`PaletteGenerator` + tests~~ **done**. 37 slots including a translucent `npac_ripple`.
2. ~~`theme_colors.xml` slots, `res/color` selectors, full M3 mapping, `AlertDialogStyle` parent fix~~ **done**.
3. ~~The XML migration~~ **done**: 122 references across 23 layouts, 4 drawables and 3 `res/color` selectors.
4. ~~`ThemeApplier` + the `BaseActivity` hook~~ **done**.
5. ~~`ThemeMode` in `Settings`, `ThemeCache`, `setDefaultNightMode` in `MyApplication`~~ **done**.
6. ~~AMOLED branch + tests~~ **done** - generated, asserted, not yet looked at on a device.
7. ~~Wallpaper option~~ **done** - `WallpaperManager.getWallpaperColors`, also unverified on a device.
8. ~~Settings UI~~ **done** - one Theme row opening `AppearanceDialog` (mode + colour source).
9. `AccentColor.contrastingTextOn` is tested but unused - use it or delete it. **Still open.**

### Two corrections the implementation forced

- **`ColorResourcesLoaderCreator` is package-private**, so the plan's "drive the loader ourselves and skip
  Material's theme overlay" is not available. `ColorResourcesOverride.applyIfPossible` is the only way in,
  and it force-applies `ThemeOverlay.Material3.PersonalizedColors` - which re-points every M3 role at
  Material's own private resources and would have handed the app baseline purple. The fix in
  `ThemeApplier.materialTwinsOf` is to override **both** id sets with the same values, so it no longer
  matters which mapping wins. Material's resources being private only blocks a compile-time `R`
  reference; they are merged into our package, so a runtime lookup by name works.
- **`colorSurfaceTint` is not a Material attribute.** `elevationOverlayColor` is the one that exists.

### What has not been checked on a device

AMOLED mode, wallpaper mode, and the API 29 static-palette fallback (still no Android 10 AVD). Verified on
the emulator: the default blue seed, a red custom seed re-tinting the whole app after recreate, and the
dialogs under the new `ThemeOverlay` parent.

## Verification

- `./gradlew :app:ktfmtCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (CI runs the first
  three; the lint suppression must be scoped or CI fails).
- **Tests.** Move the WCAG maths in `ui/ColorContrastTest.kt` onto the generator: seeds × {light, dark,
  amoled}, including `#0082C9`, pure red/yellow/green, near-black, near-white and mid-grey (zero chroma).
  Assert ≥ 4.5:1 for the on/container pairs this app actually shows, ≥ 3:1 for outline/surface, plus
  determinism, opacity, and **that the generator's key set equals the `npac_*` names in the XML** — that
  catches a slot added in one place and forgotten in the other. Keep a smaller static test for the
  literal colours.
  **Caveat:** `password_status_secure` `#00FF00` is 1.37:1 on white — it already fails today. Decide the
  presentation (e.g. a filled chip with an outline) before writing an assertion you would have to ignore.
- **On device:** server colour applied after sync; custom colour re-tints after recreate; reset returns to
  the server colour; AMOLED forces dark with black surfaces; wallpaper mode.
- **Screenshot sweep in light and dark** — extend the existing dialog capture script to the main screens.
  This is also an outstanding request from the previous round.
- API 29 fallback needs an AVD created; the installed ones are all modern.

## Risks

- **Restricted API** pins us to Material 1.13.0's internals — contained to one file, behind `runCatching`,
  version pinned in `libs.versions.toml`; re-test on upgrade.
- **Large visual diff** — backgrounds stop being pure black/white on every screen.
- **`shaded_text_color` loses its `#99` alpha** when it becomes `colorOnSurfaceVariant`. Contrast improves,
  but the look shifts across 24 references.
- **Signal colours will clash** with some seeds; they already do today.
