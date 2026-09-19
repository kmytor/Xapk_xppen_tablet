# Project Status: XP-Pen Input Mapper for Android

## ✅ Completed
- Project structure created in `C:\Users\kmyto\.openclaw\workspace\XPPenInputMapper`
- Basic Android app with:
  - MainActivity and UI layout
  - USB input service for detecting and communicating with XP-Pen tablet
  - HID report parser (placeholder - needs calibration for your specific device)
  - Motion event injector (placeholder - requires permission setup)
  - Gradle build files
  - Manifest with required permissions
  - README and build instructions

## 🔧 Next Steps for You
1. **Install Android Studio** (you mentioned you already did - great!)
2. **Open the project** in Android Studio:
   - File -> Open -> select the `XPPenInputMapper` folder
3. **Build the APK**:
   - Click Build -> Build Bundle(s) / APK -> Build APK(s)
   - Wait for the build to complete (may take a few minutes on first build)
4. **Install on your Xiaomi Pad Pro**:
   - Enable Developer Options and USB Debugging on your tablet
   - Connect via USB to your PC
   - In Android Studio: Click the Run button (green triangle) and select your device
   - OR manually: `adb install app/build/outputs/apk/debug/app-debug.apk`
5. **Grant the critical permission** (via ADB - as you requested):
   ```
   adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
   ```
   - If this fails (some devices restrict this), we may need to:
     - Use the AccessibilityService fallback (less ideal for pressure)
     - Or explore making the app a system app (requires root/custom recovery)
6. **Configure and test**:
   - Launch the app on your tablet
   - Connect your XP-Pen Deco WL via USB or Bluetooth
   - Watch the status for device detection
   - Use in your target apps (Krita, Parsec, Sunshine)

## 📝 Important Notes About the Current Implementation
- **HID Parser**: The `HidReportParser.java` currently uses a guessed report format. To get accurate pressure, X/Y, and button data, we need to:
  1. Use your `Code_Tool (2).exe` on a Windows PC to get the HID descriptor of your tablet
  2. OR use Linux tools like `hidrd-convert` if you have access to a Linux system
  3. Update the parser in `HidReportParser.java` to match the actual byte layout
- **Input Injection**: The `MotionEventInjector.java` is currently a placeholder that logs data. To actually inject events:
  - The `INJECT_EVENTS` permission (if granted) allows use of `android.hardware.input.InputManager`
  - Alternatively, we can implement a fallback using `sendevent` via shell (requires the app to have shell access, which can be granted via ADB once)
  - The AccessibilityService option is included but may not transmit pressure values accurately
- **Pressure Curve**: We've noted your request for an adjustable pressure curve. This can be added in the settings once the basic parsing and injection are working.

## 🛠️ How to Get Your Tablet's HID Descriptor (Recommended)
Since you have the `Code_Tool (2).exe`:
1. Connect your XP-Pen Deco WL to a Windows PC
2. Run the Code_Tool
3. Look for an option to "Get Report Descriptor" or "HID Information"
4. Save the descriptor (it will be a series of hex bytes)
5. Share it with me (or describe the format) and I'll update the parser

Alternatively, if you're comfortable with Linux:
- Install `hid-tools`: `sudo apt-get install hid-tools`
- Run: `hidrd-convert -o report descriptor /dev/hidrawX` (find the correct hidraw device)

## 🎯 Immediate Action Items
1. Open the project in Android Studio and try to build it (let me know if you encounter any errors)
2. If you can get the HID descriptor from your tablet using the Code_Tool, share it here
3. Test the basic USB detection (we can add logging to see if the device is recognized)

Your XP-Pen Input Mapper is taking shape! Once we have the correct HID format, the pressure and button mapping will work accurately with your specific Deco WL model.

Let me know how the build goes and if you can extract the HID descriptor! 🎩