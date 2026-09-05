# Changelog

Notable changes per release. Versions follow [semantic versioning](https://semver.org); a
pre-release tag means the line is still settling and belongs on a testing track.

## 1.0.0-beta01

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

### Changed

- Every dialog is one shared implementation with a consistent size, button row and destructive
  colour, replacing `AlertDialog`.
- The app draws edge to edge, and the system bar icons follow the colour actually behind each bar.
- Settings rows toggle from anywhere along the row, not only from the switch.
- Defaults for new installs: inline keyboard suggestions off, expanded bottom sheet on, screenshots
  allowed. Existing installs keep whatever was chosen.
- The offline store and its favicon cache are excluded from backup and device-to-device transfer,
  because the key that decrypts them never leaves the device.

### Fixed

- A crash when certain search terms were typed, caused by stale layout positions being used to order
  favicon decoding.
- Search results now scroll back to the top so the best match is visible.
- Text on filled containers was at the WCAG minimum and looked washed out; it is now 13:1 in light
  and 8.9:1 in dark, asserted across every seed.
- The fast scrollbar ignored the chosen colour.
- The server's colour is applied on the first sync after login instead of on the next start.
- The server-URL screen reports problems as toasts rather than through an unreadable popup.
