# Building and Installing XP-Pen Input Mapper

## Prerequisites
- Android Studio installed (Arctic Fox or newer recommended)
- Android SDK Platform 34 installed
- USB debugging enabled on your Android device
- USB cable to connect device to PC (for initial install and ADB commands)

## Steps

1. **Open the project in Android Studio**
   - File -> Open -> select the `XPPenInputMapper` directory

2. **Build the APK**
   - Click Build -> Build Bundle(s) / APK -> Build APK(s)
   - Or use the Build menu -> Make Project
   - The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`

3. **Install the APK on your device**
   - Via Android Studio: Click the Run button (green triangle) and select your device
   - Or via command line:
     ```
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

4. **Grant the INJECT_EVENTS permission**
   - This permission is required to inject input events that other apps (like Krita, Parsec) will receive.
   - Note: `INJECT_EVENTS` is a special permission that may not be grantable via PM on non-system apps.
   - However, on many devices, you can try:
     ```
     adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
     ```
   - If that fails, you may need to:
     - Make the app a system app (requires root or custom recovery)
     - Use root and grant the permission via `pm grant` in a root shell
     - Alternatively, use the AccessibilityService as a fallback (see below)

5. **Enable Accessibility Service (optional fallback)**
   - If you cannot get `INJECT_EVENTS`, the app includes an AccessibilityService that can simulate touch events (though pressure sensitivity may be limited).
   - Go to Settings -> Accessibility -> XP-Pen Input Mapper -> Turn on

6. **Connect your XP-Pen Deco WL**
   - Plug in via USB-C or pair via Bluetooth
   - The app should detect the device and start processing reports

7. **Configure the app**
   - Launch the app from your launcher
   - Use the settings to:
     - Switch between USB and Bluetooth mode
     - Configure button mappings (what each button does)
     - Choose between mouse mode and pen/stylus mode
     - Adjust pressure curve (if implemented)
     - Set per-app profiles

## Troubleshooting

- **Device not detected**: 
  - For USB: Ensure the device is not already claimed by another driver (some Android devices may automatically claim USB HID devices). You may need to use `adb usb` to reset the USB stack.
  - For Bluetooth: Make sure the device is paired in Android Bluetooth settings first.

- **No input in apps**:
  - Check the app's status in the UI to see if it's receiving reports.
  - Verify that the INJECT_EVENTS permission is granted (Settings -> Apps -> XP-Pen Input Mapper -> Permissions).
  - Try enabling the AccessibilityService as an alternative input method.

- **Pressure not working**:
  - The current HID parser is a placeholder. You may need to adjust the parser based on the actual report descriptor of your specific tablet model.
  - To get the descriptor, you can use the `Code_Tool (2).exe` provided with your tablet on a Windows PC, or use Linux tools like `hidrd-convert` or `hid-descriptor` from the `hid-tools` package.

## Next Steps for Development

If you want to modify the app:

1. **Improve HID parsing**: 
   - Look at the HID descriptor from your tablet (using the Code_Tool or Linux tools) and update `HidReportParser.java` to correctly extract X, Y, pressure, and buttons.

2. **Improve input injection**:
   - If you have root, consider using `sendevent` via `Runtime.getRuntime().exec()`.
   - If you can make the app a system app (by placing it in `/system/priv-app`), the `INJECT_EVENTS` permission will work.
   - Explore using the `android.hardware.input.InputManager` API if available.

3. **Add Bluetooth HID support**:
   - Implement a Bluetooth service that connects to the tablet's HID over GATT profile and listens for notifications.

4. **Add pressure curve adjustment**:
   - Allow the user to map the raw pressure value (0-255) to a curve for finer control in drawing apps.

5. **Add per-app profiles**:
   - Store different configurations for different apps (Krita, Parsec, etc.) and switch automatically based on the foreground app.

## Notes

- This app is intended for educational and personal use. 
- Modifying input injection can interfere with normal device use. Use the "Stop" button in the app or disable the service if you experience issues.
- The developer is not responsible for any damage or unexpected behavior.

For questions or contributions, please refer to the project README.