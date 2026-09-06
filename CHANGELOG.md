# Changelog

Notable changes per release. Versions follow [semantic versioning](https://semver.org); a
pre-release tag means the line is still settling and belongs on a testing track.

## 1.0.1

A round of autofill work, most of it prompted by fill requests that named the wrong field or none at
all.

### Added

- **Form level field detection.** Fields are now judged against the rest of the form rather than one
  view at a time, and a username and password box are paired by where they sit on screen when
  nothing else identifies them. Independently written, but the approach is owed to Keepass2Android.
- **Three ways to use a suggestion.** Every entry offers filling both fields, only the username, or
  only the password, so a box this app read wrongly can still be filled correctly. Each row wears the
  entry's favicon with a small person or key badge, the same pair the password list uses.
- **Remembered choices.** Picking an entry from "Search all passwords" files it against that site or
  app, together with what you typed to find it: the entry is offered first next time, ahead of every
  guess, and the picker opens with the same search. A new settings screen lists everything this has
  happened for and clears any of it.
- **A Chrome walkthrough.** Chrome 121 and later ignore a third party autofill service until the user
  turns it on in Chrome's own settings, silently. Settings now says so, with the steps, and stops
  offering the walkthrough once Chrome has actually asked.

### Changed

- Suggestion rows identify themselves by username rather than by naming their action, so two accounts
  on one site can be told apart. What each row does moved to its badge and to the text a screen
  reader announces.
- The picker no longer decodes every stored favicon when it opens; it decodes the rows it shows, off
  the main thread.
- A dialog's body now starts where its title starts.

### Fixed

- Passwords shared with you were listed as shared by you.
- The share list was cut off in the password detail sheet.
- The single field rows appeared on one field and were silently missing from every other one.
- Deprecated status and navigation bar colour calls that Android 15 warns about.

## 1.0.0

The first version under semantic versioning. It follows the "Preview 10" series, and carries a large
architecture and interface rebuild.

### Upgrading

Updating from Preview 10 signs you out once. The stored document now carries the layout it was
written with, and anything written before that tag is cleared rather than reinterpreted - the shapes
happen to line up this time, but only by luck, and checking that by hand every release is the kind of
task that is fine until the once it is not. Nothing is deleted from your Nextcloud; sign in again and
everything syncs back. The app explains this on the login screen rather than just appearing signed
out.

### Added

- **Material 3 theming from a single seed colour.** Every colour in the app is generated from one
  seed: the colour the Nextcloud admin set in the Theming app, a colour picked by hand, or the
  system wallpaper. Fetched from `ocs/v2.php/cloud/capabilities` on each sync.
- **Light, dark and AMOLED modes.** AMOLED forces dark and takes the window to true black while
  keeping the container tones above it, so cards and sheets still read as separate objects.
- **A colour picker** with hue, saturation and brightness sliders, a hex field, and a reset that goes
  back to whatever the server says.
- **Neutral body text by default**, with a setting to let text carry the seed's hue as well.
- Password creation now disables the form while saving, closes on success, and scrolls to the new
  entry and briefly highlights it.
- Clearing the offline storage asks for confirmation and explains what it does; returning to an
  empty password list then fetches automatically.
- A progress bar during any sync that has nothing cached to show — a first login, or after the
  offline copy has been cleared.
- Editable word lists for autofill field detection, and a custom look-alike character list for the
  password generator, both resettable.
- Clipboard clearing is now configurable: how long a copied password stays, and whether it is
  taken back at all. Clearing is safer, but it also removes something the user asked for, so the
  trade is theirs.

### Changed

- The release build is optimised and shrunk with R8, which takes the download from 15.5 MB to 5.6 MB.
  It had been disabled after an earlier attempt crashed; the cause turned out to be a stripped type
  attribute rather than anything fundamental.

- Every dialog is one shared implementation with a consistent size, button row and destructive
  colour, replacing `AlertDialog`.
- The app draws edge to edge, and the system bar icons follow the colour actually behind each bar.
- Settings rows toggle from anywhere along the row, not only from the switch.
- Defaults for new installs: inline keyboard suggestions off, expanded bottom sheet on, screenshots
  allowed. Existing installs keep whatever was chosen.
- The offline store and its favicon cache are excluded from backup and device-to-device transfer,
  because the key that decrypts them never leaves the device.

### Security

- An `nc://login/...` callback carrying credentials is now only acted on when it answers a login this
  app started, against the server the user entered. The scheme is a custom one with nothing proving
  who may use it, so previously a link on any page could have pointed the app at another Nextcloud:
  the password list would have been replaced by that server's on the next sync, autofill would have
  offered its entries, and anything created afterwards would have been created there. Nothing was
  ever uploaded to such a server - syncing only reads - and no release carried this.

### Fixed

- A crash when certain search terms were typed, caused by stale layout positions being used to order
  favicon decoding.
- Search results now scroll back to the top so the best match is visible.
- Text on filled containers was at the WCAG minimum and looked washed out; it is now 13:1 in light
  and 8.9:1 in dark, asserted across every seed.
- The fast scrollbar ignored the chosen colour.
- The server's colour is applied on the first sync after login instead of on the next start.
- The server-URL screen reports problems as toasts rather than through an unreadable popup.
- A request that cannot reach the server now says so, instead of "something went wrong". Every
  failing action shares one reporting path, so this covers creating, editing, sharing, folders and
  syncing alike.
