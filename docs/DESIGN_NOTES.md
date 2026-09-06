# Design notes

Decisions and traps that are expensive to rediscover. Each entry is here because the obvious thing
was tried first and did not work, or because the code cannot say it in a comment where it belongs.

---

## Theming

### The theme is static; the resource *values* move

A theme attribute cannot be handed an arbitrary colour at runtime. So `Theme.NextcloudPasswords`
maps every Material 3 role at a `@color/npac_*` resource once, at build time, and `ThemeApplier`
replaces the **values** of those resources through a `ResourcesLoader`. Everything that already
resolves `?attr/colorSurface` follows the seed with no further work.

`DynamicColors.setContentBasedSource(seed)` is not usable: it early-returns unless
`isDynamicColorAvailable()`, which needs API 31 plus an OEM allowlist. It remains only as an
implementation option for the wallpaper source.

On Android 10 there is no loader, so the static defaults in `values/theme_colors.xml` are what the
app paints with. They are generated from the same default seed by the same generator, so that build
is a coherent palette — it just cannot be re-seeded.

### Overriding both id sets is not belt and braces, it is required

`ColorResourcesLoaderCreator` — the class that builds the in-memory ARSC — is **package-private**, so
the only way in is `ColorResourcesOverride.applyIfPossible`. That method also force-applies
`ThemeOverlay.Material3.PersonalizedColors`, which re-points every M3 role at Material's own
`material_personalized_color_*` resources. Left alone it would undo the mapping our theme declares
and hand the app baseline purple.

The fix is to override **both** id sets with the same values, which is what
`ThemeApplier.materialTwinsOf` is for. Material's resources are private, but private only blocks a
compile-time `R` reference — they are merged into our package, so a runtime lookup by name works.

### `AlertDialogStyle` must be a `ThemeOverlay`

Its parent used to be `Theme.Material3.DayNight.Dialog.Alert`, a **full theme**. A full theme
re-declares its own colour set pointing at Material's private `m3_sys_color_*` resources, which the
loader does not touch — so every dialog reverted to baseline purple while the rest of the app was
seeded. An overlay inherits the activity's colours and adds only what it names.

### Elevation overlays are off

They composite `colorPrimary` over a surface to fake a raised tone, which is the pre-M3 approach.
With a saturated seed the toolbar came out visibly redder than the window behind it, reading as a
seam under the status bar. Material 3 separates surfaces with the `surfaceContainer` ramp, which this
app maps in full, so the overlay has nothing left to do.

### On-container text is re-toned, and so is `secondaryContainer`

`SchemeContent` aims its on-container colours at roughly 4.5:1 — the WCAG floor and no more. The
grouped lists put labels straight onto `secondaryContainer`, and at 4.5:1 they read as washed out.

Two steps, because moving only the text is not enough: a container that lands mid-tone cannot carry
readable text at all (a pure red seed produced one where even black-or-white reached only 5.4:1). So
the on-colour is chosen by measuring contrast, and `secondaryContainer` itself is put back on
Material's tones — 90 in light, 30 in dark.

### Body text is neutral by default

`SchemeContent` carries the seed's chroma through the neutral palette, which is the point for
surfaces but gives pink paragraphs from a red seed. The on-surface text roles are re-emitted at
chroma 0 at the same HCT tone, so contrast is unchanged — there is a test asserting exactly that.
`tintedText` turns it back on.

### Regenerating the static defaults

`values/theme_colors.xml` and its night twin are generated, not hand-written. `PaletteGeneratorTest`
fails if they drift. To regenerate, remove the `@Ignore` from `PaletteXmlDumpTest` for one run.

### The theme cache is a file, not `SharedPreferences`

The theme must be read synchronously in `onCreate`, but `StorageManager` publishes asynchronously
after decrypting. `AutofillPickerActivity` runs in `:autofill_process`, where preferences cache per
process and would serve a stale theme for as long as that process lived. A file is re-read each
time. None of the three cached values is a secret.

### Where the palette is applied

`BaseActivity.onCreate`, immediately after `super.onCreate()`. That is the only safe window:
`OverviewActivity` calls `installSplashScreen()` *before* `super.onCreate()` (which swaps the theme
to `postSplashScreenTheme`) and inflates its binding *after*. All four activities extend
`BaseActivity`, so `:autofill_process` is covered too.

---

## Cross-process colours must stay literal

Anything inflated in another app's process cannot resolve our attributes:

- `layout/autofill_dataset_presentation.xml` and `drawable/autofill_presentation_key_24.xml` — a
  RemoteViews tree inflated by the app requesting the fill. They use
  `@color/autofill_presentation_*`.
- `drawable/icon_vpn_key_24.xml` — handed to SystemUI and the keyboard as an `Icon`.
- The launcher icon drawables.

A `?attr/` reference in any of these resolves against a stranger's theme, or not at all.

---

## Gotchas that cost real time

**Non-transitive R classes are the default.** Each library's `R` carries only what it declares.
`colorPrimary`, `colorControlNormal` and friends are **AppCompat's**; `colorSurface`,
`colorOnSurface` and the container ramp are **Material's**. Using the wrong `R` fails to resolve.

**The fast scrollbar reads the framework attributes.** `useMd2Style()` tints from
`android:colorControlActivated` / `android:colorControlNormal`, not the AppCompat forms. Both are
mapped now, and `usePaletteStyle` also tints the thumb and track explicitly so the result does not
depend on which the library happens to read.

**A theme-level `android:minHeight` leaks into every `TextView`.** `AlertDialogStyle` once set 46dp,
and because a view resolves any attribute it does not set itself from the theme, every label
inflated with that theme became 46dp tall — which is what made the blocked-app rows 108dp. Window
size belongs to the dialog, not to an attribute every widget also reads.

**`setBackgroundResource` replaces padding and the ripple.** Setting a row's background drops both.
Prefer `foreground`, or a dedicated overlay view — the row highlight after creating a password uses
the latter for this reason.

**`lintDebug` treats `MissingTranslation` as fatal**, and `lintVitalRelease` runs during
`assembleRelease`. A new string without its German twin fails the release build even though debug
lint only warns.

**`FLAG_SECURE` blanks `adb screencap`.** Screenshots come out pure black while "Allow screenshots"
is off. `uiautomator dump` still works.

**Edge-to-edge is enforced at `targetSdk 36`.** `android:statusBarColor` and `navigationBarColor` are
no-ops, and the opt-out attribute is disabled on Android 16. A bar's colour is whatever the app
paints behind it, so each screen takes the insets and pads itself, and `SystemBars.kt` picks the icon
appearance from the luminance underneath.

---

## The login foreground service earns its keep

`LoginPollingService` looks removable. Login Flow v2 ends on "Account connected - you can close this
window" and never redirects, so the obvious simplification is to poll from `LoginActivity` while it is
on screen and let the user close the tab. That was tried, and it breaks **two** things:

**The app stops returning by itself.** Polling on the login screen cannot notice the login finishing,
because while the browser is in front that screen is stopped. Only something running behind the
browser can see it complete and bring the app forward - which is a background activity start, and the
opt-in for one is available to a foreground service and not to a backgrounded app.

**The first sync stops happening.** `ForegroundSyncCoordinator` syncs on `ProcessLifecycleOwner`
foreground transitions and returns early when `loggedIn` is false. With the service, the login
completes while the app is backgrounded and *then* the app is brought forward, so the transition
happens after `loggedIn` becomes true and the sync runs. Polling on the login screen inverts that
order: closing the tab foregrounds the process first, the coordinator finds `loggedIn` still false and
gives up, and the credentials arrive a moment later with nothing left to trigger a fetch. The symptom
is an empty list after a successful login, which does not look related to the service at all.

The cost of keeping it is a Play **Foreground service permissions** declaration under Policy -> App
content, which wants a description, a justification and a demonstration video. That is a one-time form.
Removing the service would cost two behaviours the app is better for having.

---

## Build

**R8 is on, and the crash that once justified turning it off was a missing attribute.** An early
attempt crashed at startup inside Gson, and the build was left unminified with a comment saying so.
The cause was never `-keep`: `proguard-rules.pro` already kept every class and member in the app's
package. R8 strips the `Signature` attribute unless told otherwise, and without it the element type
of `List<Password>` erases - Gson then builds `LinkedTreeMap` rather than the data class. Keeping
`Signature`, plus the reflective surfaces of Gson, JNA and lazysodium, is the whole fix. The release
went from 15.5 MB to 5.6 MB.

That blanket keep is gone too. Obfuscation buys an open-source app nothing, but it was also blocking
*shrinking* of the app's own code, which is where the size actually was.

A `mapping.txt` now exists at `app/build/outputs/mapping/release/`. Upload it with each Play release,
or crash reports arrive obfuscated.

**Native debug symbols need an NDK, and fail silently without one.** `debugSymbolLevel = "FULL"`
separates the symbols out of libsodium and libjnidispatch, but the strip tool that does it ships with
the NDK. Where no NDK is installed, `stripReleaseDebugSymbols` runs, reports success and produces
nothing: the build logs `Unable to strip library ... due to missing strip tool`, packages the
libraries unstripped, and `extractReleaseNativeDebugMetadata` has nothing to extract. The bundle then
carries no symbols and Play asks for them separately.

The CI image has an NDK, so bundles built there do carry `.dbg` files for all four ABIs under
`BUNDLE-METADATA/com.android.tools.build.debugsymbols/`. Measured across two versions with R8 both off
and on, that is the only variable - it is not R8, and the libraries are not pre-stripped in their AARs
as an earlier version of this note claimed. It is a reason to ship the artifact the release workflow
produced rather than one built by hand.

**R8's map rides inside the bundle** at `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`,
so Play deobfuscates crash reports with no separate upload. The `mapping-<version>.txt.gz` attached to
each GitHub release is for reading crashes from a sideloaded APK, where nothing else has the map.

**Signing material lives outside the repository.** The build reads the keystore path from the
`NPAC_KEYSTORE_PROPERTIES` environment variable or the `releaseKeystoreProperties` Gradle property,
and falls back to an unsigned release build when neither is set.
