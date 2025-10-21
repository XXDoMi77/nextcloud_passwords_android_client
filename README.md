# Nextcloud Passwords for Android

An open-source Android client for the [Nextcloud Passwords](https://apps.nextcloud.com/apps/passwords) application.

<a href='https://play.google.com/store/apps/details?id=com.dominikdomotor.nextcloudpasswords' target="_blank" rel="noopener noreferrer">
  <img alt='Jetzt bei Google Play' src='https://play.google.com/intl/en_us/badges/images/badges/en_badge_web_generic.png' width='240px'/>
</a>


---


## About the Project

This project aims to be a fully-featured, secure, and user-friendly password manager for Android that syncs with your self-hosted Nextcloud instance. It leverages the Android Autofill framework for seamless integration with other apps and browsers.

#### Current Status: **Stable Preview**
This application is currently in a stable preview state. It is functional for daily use, but as it is under active development, please use it with care. Feedback and contributions are always welcome!

---

## Screenshots

<p float="left">
  <img alt="view" src="https://github.com/user-attachments/assets/708b88f2-34ce-42ab-adb3-fede4f2a0c81" width="150" />
  <img alt="night_mode" src="https://github.com/user-attachments/assets/c2f82626-18fe-4e6b-a493-1aaa43956b36" width="150" />
  <img alt="settings" src="https://github.com/user-attachments/assets/e05e392e-4805-4053-9df1-a257c621f537" width="150" />
  <img alt="autofill" src="https://github.com/user-attachments/assets/b113cd5c-21b3-4e40-991f-c344b3458608" width="150" />
  <img alt="autofill_example" src="https://github.com/user-attachments/assets/9d54b15f-6ffe-473e-82ad-eab8ae140005" width="150" />
  <img alt="search" src="https://github.com/user-attachments/assets/c0908273-0925-4b5f-8112-b7467bf08254" width="150" />
  <img alt="edit" src="https://github.com/user-attachments/assets/45f7f1cc-9009-428b-ab2e-7a30e99d91a1" width="150" />
  <img alt="create" src="https://github.com/user-attachments/assets/018984a7-95c3-4340-94d3-0152b49dd909" width="150" />
</p>

---

## Features

### Current Features
- **Secure Login & Sync:** Connects to your Nextcloud instance to sync passwords.
- **Full Password Management:** Create, view, edit, and delete passwords.
- **Offline Caching:** Access your passwords and favicons even without an internet connection.
- **Android Autofill:** Provides password suggestions in other apps and browsers using a hint-based detection system.
- **Password Generation:** Create strong, random passwords with customizable parameters.
- **View Shares:** See who a password has been shared with.
- **Note Creation:** Securely create and attach notes to your passwords.

### Roadmap (Planned Features)
- Reimplement Favorites: Add support for marking favorite passwords after the recent network manager refactor.
- View & Edit Notes: Implement the ability to view and edit existing notes.
- Full management of shares (editing, resharing, setting expiration).
- Support for folders and tags.
- App lock with Biometrics or a PIN code.
- End-to-End Encryption (E2E).
- App blacklisting/whitelisting for the Autofill service.
- In-app language selection.

---

## Building from Source

To build and run this project yourself, follow these simple steps:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/XXDoMi77/nextcloud_passwords_android_client.git
    ```
2.  **Open in Android Studio:**
    Open the cloned project directory in the latest stable version of Android Studio.
3.  **Sync Gradle:**
    Allow Android Studio to automatically download and sync the required Gradle dependencies.
4.  **Build & Run:**
    Build and run the app on an emulator or a physical Android device.

---

## Disclaimer

This application is provided "as is" and without warranty of any kind. The author is not liable for any damages resulting from the use of this software. Please use it at your own risk.
