# SkyDrive X for Android

[English](README.md) | [中文](README_zh.md)

![Version](https://img.shields.io/badge/version-2.2.0-blue)
![Platform](https://img.shields.io/badge/platform-Android-brightgreen)
![License](https://img.shields.io/badge/license-MIT-green)
![Language](https://img.shields.io/badge/language-Kotlin-orange)
![Stars](https://img.shields.io/github/stars/lurenjia534/SkyDrive-X-for-Android)
![Issues](https://img.shields.io/github/issues/lurenjia534/SkyDrive-X-for-Android)
![Last Commit](https://img.shields.io/github/last-commit/lurenjia534/SkyDrive-X-for-Android)
![Lines of code](https://img.shields.io/tokei/lines/github/lurenjia534/SkyDrive-X-for-Android)

## Currently, the project only supports self-building


```bash
git clone https://github.com/lurenjia534/SkyDrive-X-for-Android
```

Find the following in the project's `AndroidManifest.xml`

```
<activity android:name="com.microsoft.identity.client.BrowserTabActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:host="com.lurenjia534.nextonedrivev2"
                    android:path="/<YOUR_BASE64_ENCODED_PACKAGE_SIGNATURE>"
                    android:scheme="msauth" />
            </intent-filter>
        </activity>
```

Replace `<YOUR_BASE64_ENCODED_PACKAGE_SIGNATURE>` with the Base64 encoded hash value of your application's signing certificate

### Next step

```json
{
  "client_id": "<YOUR_PACKAGE_NAME>",
  "redirect_uri": "msauth://com.lurenjia534.nextonedrivev2/<YOUR_BASE64_URL_ENCODED_PACKAGE_SIGNATURE>",
  "broker_redirect_uri_registered": true
}

```

Where `<YOUR_PACKAGE_NAME>` is your client ID 
Replace `<YOUR_BASE64_URL_ENCODED_PACKAGE_SIGNATURE>` with the Base64 URL-safe encoded hash value of your application's signing certificate


### Next step you need to configure your own Azure application

## Project Introduction

SkyDrive X is an OneDrive client application designed specifically for Android. It provides rich cloud file management features, supports multi-account management, and adopts the modern Material 3 design style, bringing users a smooth and intuitive cloud storage experience.

## Screenshot display

![1](image/1.png)
![2](image/2.png)
![3](image/3.png)
![4](image/4.png)
![5](image/5.png)

## Features

### Multi-account Management
- Support for adding multiple OneDrive accounts
- Automatic and manual account token refresh
- Account information management and editing

### File Management
- Browse files and folder hierarchy
- Create new folders
- Upload files (supports various file types)
- Upload photos (supports multiple selection)
- Download files to local storage
- Move files/folders
- Copy files/folders (supports renaming)
- Delete files/folders

### Sharing and Collaboration
- Create sharing links for files/folders
- Various sharing permission settings (read-only/editable)
- Sharing scope control (anonymous/within organization)
- Copy or directly share links

### Cloud Drive Information
- View storage space usage
- Account quota information
- Remaining space display

### User Experience
- Material 3 design language
- Dark mode support
- Smooth animations and transition effects
- Intuitive file type icons

## Tech Stack

- **Programming Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture Pattern**: MVVM
- **Dependency Injection**: Hilt
- **Asynchronous Processing**: Kotlin Coroutines + Flow
- **State Management**: LiveData + StateFlow
- **Network Requests**: Retrofit
- **Permission Management**: Android Dynamic Permissions
- **Design Specification**: Material 3

## System Requirements

- Android 12.0 (API level 31) or higher
- Network connection required
- Storage permission required (for file uploads and downloads)
- Notification permission required (for upload progress notifications)

## User Guide

### Adding an Account
1. After launching the app, click the "+" button in the bottom right corner
2. Enter an account name (for distinguishing different accounts within the app)
3. After clicking confirm, you will be redirected to the Microsoft login page
4. Login to your Microsoft account and authorize the app to access OneDrive

### Browsing and Managing Files
- Click on an account card to enter that account's cloud drive
- Click on a folder to enter that folder
- Click on the file action icon (⋮) to open the file operation menu
- Use the bottom navigation bar to switch between "File List" and "My Information" pages

### Uploading Files
1. On the file list page, click the "+" button in the bottom right corner
2. Select "Upload Photo" or "Upload File"
3. Choose the file you want to upload from your device
4. View the upload progress bar

### Sharing Files
1. In the file list, click on the action menu to the right of the file
2. Select the "Share" option
3. Choose sharing permissions and scope
4. Copy or share the generated link

## Customization and Settings

- Toggle dark mode on the "Settings" page
- Customize account names on the account details page

## Contribution Guidelines

Pull Requests or Issues are welcome to help improve the project. Please follow these steps:

1. Fork the project
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Create a Pull Request

## License

This project is licensed under the Apache License Version 2.0 - see the [LICENSE](LICENSE) file for details

## Contact

GitHub: [lurenjia534](https://github.com/lurenjia534)

---

*Note: This application is a third-party unofficial OneDrive client, not an official Microsoft product.*

