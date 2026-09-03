# Refactor Progress & Remaining Backlog

Last updated: 2026-09-02

This branch completed the correctness, architecture, performance and tooling work catalogued in the
previous version of this document. What follows is what changed, how it was verified, and what is
deliberately still open.

---

## 1. Verification

| Check | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | passes, no warnings except two documented legacy-migration deprecations |
| `./gradlew :app:testDebugUnitTest` | **41 tests, 0 failures** (was 5) |
| `./gradlew :app:ktfmtCheck` | clean — formatting is now enforced by the build |
| `./gradlew :app:assembleRelease` / `bundleRelease` | signed AAB + APK produced |
| Runtime, emulator + physical device | launches clean; full sync verified against a live server |

Live sync, observed in logcat against a real account (165 passwords):

```
GET /index.php/apps/passwords/api/1.0/password/list -> 200
GET /index.php/apps/passwords/api/1.0/folder/list   -> 200
GET /index.php/apps/passwords/api/1.0/share/list    -> 200
Loaded 165 passwords in 128 ms
```

Two runtime unknowns carried by the old document are now settled: the **add-password crash could not
be reproduced** and the **folder list loads correctly**. Both paths were rewritten anyway.

---

## 2. Correctness bugs fixed

Each of these was a real defect, not a style preference.

1. **`stopFaviconPull()` crashed when nothing had downloaded yet.** `lateinit var executor` was only
   assigned inside a worker thread, so *Clear cache* and *Logout* could throw
   `UninitializedPropertyAccessException`. The executor is gone; downloads are coroutines.
2. **One failed favicon aborted the whole batch.** A non-2xx response called `executor.shutdownNow()`
   from inside a worker, cancelling every queued download. Failures now skip that one password.
3. **`FoldersFragment.refresh()` leaked a collector per navigation.** Every folder open, breadcrumb
   tap and back press started another `repeatOnLifecycle` collector and cancelled none. Folder
   navigation is now a `StateFlow` feeding one long-lived collector.
4. **Five `NetworkManager` methods never called `disconnect()`.** All requests now go through
   `PasswordsApiClient`, which disconnects in a `finally`.
5. **`try { ioScope.launch { … } } catch` caught nothing** in three methods — the body ran
   asynchronously. Replaced by `suspend` functions returning `ApiResult`.
6. **Silent `catch` blocks hid failures.** A parse error, a dead network and "server returned zero
   folders" were indistinguishable. Every failure now carries a `FailureReason` mapped to a message.
7. **`StorageManager` threw from its constructor** on a corrupt data file, producing an unrecoverable
   crash loop. It now recovers to empty state and logs.
8. **Folder moves could create a cycle.** The picker excluded only the folder itself, not its
   descendants. `FolderTree.selfAndDescendants` now excludes the whole subtree.
9. **`expandBottomSheet` was ignored on the Passwords tab** — the two copies of the create-password
   dialog had drifted. There is now one copy.
10. **`MyAutofillService` kept per-request state in service fields**, so concurrent fill requests
    could interleave. Parsing returns a per-request `ParsedFields`.
11. **Favicon lookup mishandled multi-part TLDs.** `host.split(".").takeLast(2)` produced `co.uk`,
    giving every UK site the same icon. Now shared, tested `Domains` logic.
12. **Mutable internal collections escaped `StorageManager`**, risking `ConcurrentModificationException`
    and letting callers mutate state without publishing. Only immutable snapshots are exposed.
13. **`LoginActivity` created an unscoped `CoroutineScope`** that outlived the activity.
14. **The login callback parser crashed on malformed input** — `substring(0, -1)` when a segment had
    no colon. Extracted to `LoginCallbackParser` and covered by tests.
15. **Back navigation was broken across all tabs.** All three fragments registered back callbacks that
    stayed enabled while hidden, so whichever registered last handled every press — back closed the
    app from any tab. `OverviewActivity` now owns one callback and asks only the visible tab.
16. **The favicon cache directory collided with the legacy blob file of the same name**, failing
    migration with `ENOTDIR`. *(Introduced and caught during this work, by running the app.)*

---

## 3. Architecture

- **`PasswordRepository`** is the single entry point for password data, owning the "call the server,
  then update the local store" sequencing that was spread across `NetworkManager`.
- **`ApiResult` / `FailureReason`** replace `func: () -> Unit` callbacks that fired identically on
  success and failure.
- **`PasswordsApiClient`** issues every API call in one place — connection handling, session-token
  capture and status-code mapping are no longer repeated per endpoint.
- **ViewModels** now hold screen state: `PasswordsViewModel`, `FoldersViewModel`,
  `SettingsViewModel`, `PasswordActionsViewModel`, `OverviewViewModel`. Fragments render and forward
  events. State survives configuration changes.
- **One concurrency model.** The `Thread { }`, `Executors.newFixedThreadPool(4)` and ad-hoc
  `CoroutineScope` mix is gone; everything is coroutines on the appropriate dispatcher.
- **Duplication removed:** one `PasswordEditorSheet` (was ~240 duplicated lines across two
  fragments), one `SecureClipboard`, one `PasswordStatus`, one `Domains`, one `FolderTree`.
- **`Password` is immutable.** Edits build copies instead of mutating shared state before the server
  has accepted the change.
- **`findEditorAction` is gone.** It walked up the view hierarchy and threw when it ran out of
  parents; replaced by an `EditText.actionButton` extension.

---

## 4. Performance

- **Favicon storage was O(n²).** Every download re-encoded the entire cache to base64 and rewrote one
  JSON blob. Now one encrypted file per favicon: O(1) per write.
- **Favicons are decoded lazily, per visible row.** An intermediate version decoded the whole cache
  at start-up, which cost roughly 700 ms on a 165-password account and was visible as a startup
  delay. `FaviconBinder` decodes only what scrolls into view, into a memory-bounded `LruCache`.
  Cold start went from ~1.88 s back to ~1.23 s.
- **`notifyDataSetChanged()` per favicon is gone.** Both lists are `ListAdapter` + `DiffUtil`, and an
  arriving favicon rebinds exactly one row.
- **`StorageManager` no longer does blocking I/O in its constructor.** Loading is explicit and off
  the main thread; the splash screen waits only on the small data document (128 ms).
- **libsodium loads lazily** — constructing it loaded the native binary at start-up for every user,
  including the majority who never use client-side encryption.

---

## 5. UI and behaviour changes

- **Back navigation:** the visible tab gets first refusal, then back returns to the password list,
  then a confirmation dialog before leaving the app.
- **Settings regrouped** into Autofill / Password generation / Appearance / Security / Data & account.
  Autofill options were previously scattered under "General" alongside cache and sheet settings.
- **New setting: "Animate search results."** Search re-ranks on every keystroke; the row-reordering
  animation is now optional.
- **Settings observe state** instead of reading once in `onViewCreated`, so a change made elsewhere
  (an E2E unlock storing the passphrase) is reflected immediately.
- **Accessibility:** content descriptions added across icon buttons.
- **Status colours are theme-aware resources** rather than hardcoded constants — deliberately kept
  vivid (`#00FF00` / `#FFFF00` / `#FF0000`) in both themes.

---

## 6. Build, tooling and release

- **ktfmt runs in Gradle**, not only in the IDE. `.idea/ktfmt.xml` alone meant nothing checked
  formatting in a build or a PR, and the codebase had already drifted.
- **`QUERY_ALL_PACKAGES` removed.** This Play-restricted permission existed only to populate the
  autofill blocklist; a `<queries>` element gives the same visibility with no declaration form.
- **Java 17** (from 11).
- **Dependencies dropped:** all three `navigation-*` artifacts, `lifecycle-livedata-core-ktx`, and
  every Compose entry in the version catalogue — all had zero usages.
- **CI added** (`.github/workflows/ci.yml`): formatting, unit tests, debug build, lint.
- **`.gitignore` hardened** — keys, keystores, `.env`, build outputs, and machine-local `.idea` state.
  Sixteen tracked IDE files and a stale `app/release/output-metadata.json` were untracked.
- **Logging is debug-only.** `GlobalFunctions.println` logged request URLs unconditionally, including
  in release builds of a password manager.
- **Release script**: `Build-Release.ps1`, kept beside the keystore outside this repo. It verifies
  formatting and tests, builds a signed AAB + APK, records versions, commit and SHA-256 hashes, and
  zips the result for the Play Console.

**R8 stays disabled.** Enabling it crashes the app at startup in Gson deserialisation. This is
deliberate and now commented in `app/build.gradle.kts`.

**No native debug symbols are produced, and that is expected.** `debugSymbolLevel` only extracts
symbols from native code the project builds; `libsodium` and `libjnidispatch` arrive pre-stripped
from their AARs.

---

## 7. Testing

41 unit tests, all pure JVM:

| Suite | Covers |
|---|---|
| `FolderTreeTest` (9) | subtree exclusion for moves, path building, cyclic-parent termination |
| `DomainsTest` (8) | registrable domain, multi-part TLDs, IP addresses, primary label |
| `LoginCallbackParserTest` (7) | callback forms, percent/plus decoding, malformed segments |
| `PasswordGeneratorTest` (7) | length, symbol clamping, look-alike exclusion |
| `PasswordSearchTest` (5) | field matching, case-insensitivity, prefix ranking |
| `AutofillCredentialMatcherTest` (3) | host ranking, substring rejection, app-name fallback |
| `AutofillSearchQueryTest` (2) | domain-label extraction |

The old `androidTest/LoginRedirectTest` was deleted. It asserted only `!activity.isFinishing` and its
own comment conceded it proved nothing; the logic it nominally covered is now genuinely tested by
`LoginCallbackParserTest`.

---

## 8. Still open

Ordered by value, not urgency.

1. **`CseCryptoManager` has no tests.** It is the most security-sensitive code in the app and the
   least covered. Lazysodium needs an Android runtime, so this needs Robolectric or an instrumented
   test. Use the `jane` / `erika` accounts on `test.passwordsapp.org` to exercise it end to end.
2. **`AutofillFieldAnalyzer` has no tests** — the component most likely to regress silently. Needs
   either fake `ViewNode`s or a pure classifier extracted from the `ViewNode` reading.
3. **E2E write paths are unverified against a live server.** Decryption on download is exercised;
   creating and editing a password on an E2E account has not been run end to end.
4. **Passwords API token / 2FA is unsupported.** Only the `PWDv1r1` challenge is implemented;
   accounts requiring a token get `UNSUPPORTED_CHALLENGE`.
5. **Custom fields are a raw JSON string** on `Password`, neither parsed nor editable.
6. **The autofill service runs in its own process** (`:autofill_process`) and therefore has its own
   `StorageManager`, re-reading state on each fill. Workable, but worth an explicit decision.
7. **AGP 9 transitional flags** remain in `gradle.properties` (`android.newDsl=false`,
   `android.builtInKotlin=false`, and others). Each is an opt-out from an AGP 9 default and should be
   removed one at a time with a build check.
8. **`EncryptedFileManager` still carries the AndroidX `EncryptedFile` migration reader.** It can be
   deleted once a release window has passed; it is the only remaining deprecation warning.
9. **No baseline profile**, which would measurably improve cold start.

---

## 9. Reference

**Restore point:** `restore/pre-cleanup-2026-09-02` (`96c4159`) — a signed snapshot of the full
working tree from before this work began.

**Test accounts and the release script** live in
`…\Keys\Nextcloud Passwords Android App Keys\` (`TEST_ACCOUNTS.md`, `Build-Release.ps1`),
deliberately outside this repository.

```powershell
.\gradlew.bat :app:ktfmtCheck :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:installDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 logcat -s "Nextcloud Passwords" AndroidRuntime
```

`FLAG_SECURE` is on by default, so `adb screencap` and `uiautomator dump` return nothing for this
app. Enable *Allow screenshots* in Settings when you need either. Never paste logcat output
containing decrypted password fields into an issue or commit.
