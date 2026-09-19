# XP-Pen Input Mapper for Android
Custom APK to enable pressure sensitivity, button mapping, and orientation support for XP-Pen Deco WL/LW tablets on Android devices.

## Features
- USB / wireless-dongle HID communication with XP-Pen Deco WL/LW (via USB OTG)
- Pressure sensitivity detection and injection
- Configurable button mapping (stylus buttons + tablet buttons)
- Switch between mouse mode and pen/stylus mode
- Adjustable pressure curve for sensitivity tuning
- Automatic orientation detection and coordinate transformation
- Per-app profiles (Krita, Parsec, Sunshine, etc.)
- Direct input injection via a shell-UID daemon (autonomous), with Shizuku and AccessibilityService as fallbacks
- Wake-on-stylus: turns the screen on when the pen comes into proximity

> **Important — Bluetooth:** On Android, the Deco LW only works over a USB cable or the included 2.4 GHz wireless dongle (which the app detects as USB via OTG). Android cannot pair the Deco LW over native Bluetooth (XP-Pen only supports direct Bluetooth on Windows/macOS). The Bluetooth button in the UI has no effect on Android, by hardware design.

## Setup
1. Install Android Studio and build the APK
2. Install the APK on your Android device
3. Start the app and launch the autonomous engine (or grant INJECT_EVENTS via ADB when possible)
4. Launch the app and configure your preferences
5. Connect your XP-Pen Deco WL/LW via USB cable or dongle
6. Use in your favorite apps (Krita, Parsec, Sunshine, etc.)

## Permissions Required
- android.permission.INJECT_EVENTS (granted via ADB when possible; the autonomous daemon avoids this)
- android.permission.USB_PERMISSION
- android.permission.FOREGROUND_SERVICE
- android.permission.BIND_ACCESSIBILITY_SERVICE (optional fallback)

## Development
Built with Android Studio, targeting API 34 (Android 14), min API 24 (Android 7.0).
