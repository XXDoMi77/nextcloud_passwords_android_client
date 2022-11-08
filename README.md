# Native Android Client for Nextcloud Passwords
## This is a WIP native Client for Nextcloud Passwords written in Kotlin.



<img src="https://user-images.githubusercontent.com/27887974/200460244-f5f599bc-1b08-4df6-9949-5afb10a2abf7.jpg" width=200px>

https://user-images.githubusercontent.com/27887974/200460258-19c0f5c6-2114-4a68-99aa-29c9aa606ea8.mp4

https://user-images.githubusercontent.com/27887974/200460270-ea640df0-8e68-4363-9410-ab0b4a3bc38c.mp4

https://user-images.githubusercontent.com/27887974/200460287-6c0522c3-5134-4b2b-bea1-16a21d77f342.mp4



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
