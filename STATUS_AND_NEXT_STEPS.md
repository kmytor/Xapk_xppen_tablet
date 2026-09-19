# Current Status and Next Steps

## ✅ What We Have So Far
- Android Studio project created with package `com.xppen.inputmapper`
- Minimum SDK: API 21 (Android 5.0), Target SDK: API 34
- MainActivity with USB/Bluetooth scan buttons and status display
- UsbInputService that:
  - Detects XP-Pen devices (vendor ID 0x28bd)
  - Requests USB permission
  - Opens connection and claims interface
  - Reads interrupt IN endpoint in a background thread
- HidReportParser (placeholder) - needs your tablet's specific HID descriptor
- MotionEventInjector (placeholder) - attempts to inject via InputManager if INJECT_EVENTS permission is granted
- All necessary permissions in AndroidManifest.xml (including INJECT_EVENTS)
- Basic UI layout and string resources
- Fixed build errors:
  - Added namespace to app/build.gradle.kts
  - Added android.useAndroidX=true to gradle.properties
  - Added missing theme and accessibility service config resources

## 📏 Your Tablet Specs
You confirmed: XP-Pen Deco LW active area = 10" × 6" = 279mm × 152mm
This is essential for scaling raw tablet coordinates to screen coordinates.

## 🔑 What We Need From You
To make pressure sensitivity and button mapping work accurately, we need the **HID report descriptor** from your specific XP-Pen Deco LW tablet.

### How to Get the HID Descriptor
You have the `Code_Tool (2).exe` that came with your tablet. Here's how to use it:

1. **Connect your XP-Pen Deco LW to a Windows PC** (via USB)
2. **Run `Code_Tool (2).exe`**
3. **Look for an option to get the HID descriptor** - it might be labeled as:
   - "Get Report Descriptor"
   - "HID Information"
   - "Descriptor View"
   - "Device Information"
   - Or similar in the interface
4. **Save or copy the output** - it will look like a series of hex bytes, e.g.:
   ```
   05 0d 09 02 a1 01 09 20 a1 02 15 00 26 ff 7f 75 09 95 01 81 02 95 01 81 03 75 08 95 01 81 03 ...
   ```
   (This is just an example - your actual descriptor will be different)

### Alternative if you prefer Linux
If you have access to a Linux system, you can use:
```bash
sudo apt-get install hid-tools
hidrd-convert -o rdesc report /dev/hidrawX
```
(You'll need to find the correct hidraw device, usually under `/dev/hidraw*`)

## 🛠️ Next Steps for You

### 1. Build and Install the App
- In Android Studio: **File → Open** → select the `XPPenInputMapper` folder
- Wait for Gradle sync to complete
- **Build → Build Bundle(s) / APKs → Build APK(s)**
- Install on your Xiaomi Pad Pro:
  - Enable Developer Options and USB Debugging on tablet
  - Connect via USB to PC
  - Click the Run button (green triangle) in Android Studio and select your device
  - OR manually: `adb install app/build/outputs/apk/debug/app-debug.apk`

### 2. Grant the INJECT_EVENTS Permission (as requested)
After installing, run:
```
adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
```
Note: On some devices, this permission cannot be granted via `pm grant` to third-party apps. If you get an error, we have fallbacks:
- The app includes an AccessibilityService (less ideal for pressure but functional)
- Or we can explore making the app a system app (requires root/custom recovery)

### 3. Get and Share the HID Descriptor
Use your Code_Tool on a Windows PC to get the descriptor and share it here.

### 4. Test Basic Detection
Once installed:
- Launch the app on your tablet
- Connect your XP-Pen Deco LW via USB-C
- Watch the status for "Device connected" messages
- Open Logcat in Android Studio (View → Tool Windows → Logcat) and filter by `XPPenInputMapper` to see parsed data (even if inaccurate at first)

## 🔧 What I Will Do Once I Have the HID Descriptor
1. **Update `HidReportParser.kt`** to correctly extract:
   - X position (0 to maxX from your tablet)
   - Y position (0 to maxY from your tablet)
   - Pressure (0-255 or whatever range your tablet reports)
   - Button states (both stylus buttons + tablet buttons)
   - Tip switch (in-contact detection)

2. **Improve coordinate scaling** using your active area (279mm × 152mm) and screen dimensions

3. **Enhance `MotionEventInjector.kt`** to:
   - Properly use InputManager for injection (if permission granted)
   - Add support for switching between pen/stylus mode (for Krita, etc.) and mouse mode (for Parsec/Sunshine)
   - Implement your requested adjustable pressure curve
   - Add per-app profile support (different configs for Krita vs Parsec, etc.)

4. **Add USB/Bluetooth toggling** in the UI

## 📝 Important Notes
- The current HidReportParser is a placeholder that assumes a common 6-byte format. It will likely not work correctly with your tablet until we update it with the real descriptor.
- The MotionEventInjector currently logs data but attempts real injection if INJECT_EVENTS is granted.
- We chose Kotlin as you requested during setup.
- The project targets API 21 min, API 34 max for broad compatibility.

## ⏳ Waiting for Your HID Descriptor
Please:
1. Use your Code_Tool (2).exe on a Windows PC to get the HID descriptor of your XP-Pen Deco LW
2. Share the descriptor here (as a string of hex bytes, space-separated or contiguous)
3. Let me know if you encounter any issues building or installing the app

Once I have the descriptor, I'll update the parser and we'll be much closer to having pressure-sensitive strokes in Krita and smooth cursor control in Parsec!

🎩 *Ready when you are - just share that HID descriptor!*