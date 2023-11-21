# Android Client for Nextcloud Passwords
This project aims to create a fully featured Password Manager client app on Android for Nextcloud Passwords application.

Right now it is in a "Preview" state, most functions you can access, should work. Having said that, use this app at your own risk!
 - Passwords can be shown, edited, created, deleted
 - There is random password generation with custom parameters
 - And most importantly there is Autofill for apps and browsers
 - As of now, the Autofill function works pretty well, but there are still many improvements that can ba made
 - It should work in both Chrome and Firefox, as well as any browser that provides some sort of a hint for Autofill

For more complete feature list have a look at the list below.

## Screenshots

<p float="left">
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/a9effc1c-f16e-49f8-81a2-e282d786514d" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/09c76bee-7e5c-4d5b-9b37-98175f7bbd41" width="150" /> 
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/bae4fcae-c3f2-4a77-9ade-7610a8ed46b1" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/4f42bf07-dcb4-4764-9a00-954ffa37a1ed" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/05864d22-d4c5-402a-a851-779ee012d7fc" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/6bf66b1b-0588-4d58-8f0a-e0496eeecaa0" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/40d322ac-62be-4234-9bc6-993fd634b609" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/ab66dddb-ff85-4a3c-9a38-23cf9f674182" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/811de717-801a-4688-ae63-6bc79b14022a" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/aa4ff5d3-9a02-4b79-92a8-f27de88dcb3b" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/51c41675-92fe-4702-bcac-69d5c68394a6" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/f09be2ef-7460-469e-9f86-b1b260b13edf" width="150" />
  <img src="https://github.com/XXDoMi77/nextcloud_passwords_android_client/assets/27887974/b0707b9b-059e-4036-befa-0afd13f2bacb" width="150" />
</p>

## Feature list

### Currently supported features:
- Login
- Logout
- Deleting password
- Adding and removing password from favorites
- Pulling and showing passwords from server
- Pulling and showing favicons from Nextcloud server (Multithreaded and nonblocking)
- Editing label, username, password, url (only works with Internet connection)
- Offline caching for passwords and favicons
- A very early version of Autofill, this will be changed for sure
- Viewing favorites
- Creating notes
- Viewing shares 

### Not yet implemented, but planned:
- Editing shares and options for editing, resharing and setting expiration
- Viewing tags
- Viewing folders
- Custom fields
- Locking app and unlocking via biometrics or pin
- E2E encryption
- Adding toggle in settings to enable filling selected fields in apps, when username and password fields cannot be automatically detected
- Black list of apps for Autofilling and maybe a whitelist as well
- Adding language selection
- Adding option to toggle showing username in Autofill popup maybe making selectable choice of what detail of password should be shown

### Should be fixed:
- If request, responds with "not authorized" logout and request new login
- Block filling fields that contain string "search"

### Not yet implemented and not planned for now:
- Using multiple accounts
- Sorting passwords by security
- Viewing revisions of passwords
- Theming based on Nextcloud color

### How do I edit/use/compile this project?
Just open the project in Android Studio, the rest should be self explanatory or at the very least googleable.

## Disclaimer
The author of this repository is not liable for any damages resulting from the use of this application.
