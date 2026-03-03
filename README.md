# ThunderPass

## Overview
ThunderPass is an Android application that facilitates BLE exchange similar to StreetPass, allowing users to connect and share information with one another.

### Features
- BLE scanning and advertising.
- Encounter storage using Room database.
- Basic Compose UI screens.

## Setup
1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project to generate the APK.

## Permissions
Make sure to handle the following permissions in your app:
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION` (if necessary)