# Native Android Client for Nextcloud Passwords
## This is a WIP native Client for Nextcloud Passwords written in Kotlin.

### Currently supported features:
- Login
- Logout
- Pulling and showing passwords from server
- Pulling and showing favicons from Nextcloud server (Multithreaded and nonblocking)
- Editing label, username, password, url (only works with Internet connection)
- Offline caching for passwords and favicons
- A very early version of Autofill, this will be changed for sure

### Not yet implemented, but planned:
- Editing/ Viewing favorites
- Viewing tags
- Viewing notes
- Viewing folders
- Custom fields
- Editing shares and options for editing, resharing and setting expiration
- Locking app and unlocking via biometrics or pin
- E2E encryption
- More settings...
- Better looking settings screen...
- Theming based on Nextcloud color

### Should be fixed:
- Url in Login activity isn't checked whether it has a Nextcloud server running
- Orientation should be fixed (not rotatable) maybe
- If request, responds with "not authorized" logout and request new login

### Not yet implemented and not planned for now:
- Using multiple accounts
- Sorting passwords by security
- Viewing revisions of passwords

### IDE
Just open the project in Android Studio.