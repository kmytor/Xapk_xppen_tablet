# Next Steps for XP-Pen Input Mapper

## Current State
You have a compilable Android project that:
- Detects USB devices with XP-Pen vendor ID (0x28bd)
- Has placeholders for HID parsing and event injection
- Includes all necessary permissions in the manifest
- Is ready to be built and installed

## Immediate Actions

### 1. Build and Install
**In Android Studio:**
- Open the project: File -> Open -> select `XPPenInputMapper` folder
- Wait for Gradle sync to complete
- Build -> Build Bundle(s) / APK -> Build APK(s)
- Once built, click "Locate" to find the APK or use:
  ```
  adb install app/build/outputs/apk/debug/app-debug.apk
  ```

### 2. Grant INJECT_EVENTS Permission (as requested)
After installing, run:
```
adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
```
**Important Note**: On many devices, `INJECT_EVENTS` is a privileged permission that cannot be granted via `pm grant` to third-party apps. If you get an error like:
```
Operation not allowed: java.lang.SecurityException: Permission inject_events is not changeable
```
Then we have alternatives:

#### Alternative A: Use AccessibilityService (included)
The app already includes an AccessibilityService. To enable:
1. Install the app
2. Go to Settings -> Accessibility -> XP-Pen Input Mapper -> Toggle ON
3. The service can simulate touch events (pressure may be limited)

#### Alternative B: Root Access (if your device is rooted)
If rooted, you can grant the permission in a root shell:
```
adb shell
su
pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
```

#### Alternative C: Make App System App (advanced)
Requires custom recovery or root to move APK to `/system/priv-app/`

### 3. Test Basic Functionality
After installing and granting permissions (or enabling accessibility):
1. Launch the app from your launcher
2. Connect your XP-Pen Deco WL via USB-C
3. Watch the status text for "Device connected" messages
4. Open the app's logcat in Android Studio to see parsed data

### 4. Get Your Tablet's HID Descriptor (Critical for Accuracy)
To make pressure and buttons work correctly, we need to parse the actual HID report format.

**Using your Code_Tool (2).exe:**
1. Connect tablet to a Windows PC
2. Run Code_Tool
3. Look for "Get Report Descriptor", "HID Information", or similar
4. Save the output (will look like: `05 0d 09 02 a1 01 09 20 ...`)

**Or using Linux (if available):**
```
sudo apt-get install hid-tools
hidrd-convert -o rdesc report /dev/hidrawX  # Find correct hidraw device
```

Once you have the descriptor (a string of hex bytes), share it here and I'll update `HidReportParser.java` to correctly extract:
- X position (0-max)
- Y position (0-max)
- Pressure (0-255 or 0-1023)
- Button states (stylus buttons, tablet buttons)
- Tip switch (in-contact detection)

### 5. Configure for Mouse vs Pen Mode
The app will need to distinguish between:
- **Pen/Stylus mode**: Injects `MotionEvent` with pressure and tilt (for drawing apps like Krita)
- **Mouse mode**: Injects mouse movement and clicks (for Parsec/Sunshine remote desktop)

We'll add a setting to switch between these modes, which will change how we interpret and inject the data.

## What I'll Do Next
Once you provide:
1. Build success/failure notes
2. HID descriptor from your tablet (or confirmation to use the Code_Tool)
3. Which permission approach you want to try (ADB grant, accessibility, or root)

I will:
- Update `HidReportParser.java` with the correct bit layout for your Deco WL
- Implement actual event injection in `MotionEventInjector.java` using:
  - `InputManager` (if INJECT_EVENTS works)
  - `sendevent` via shell (as fallback)
  - AccessibilityService gestures (as last resort)
- Add UI elements for:
  - Mode selection (Mouse/Pen)
  - Button mapping configuration
  - Pressure curve adjustment (as requested)
  - USB/Bluetooth toggling
- Add per-app profile support (for Krita vs Parsec etc.)

## Your Tablet Size Note
You mentioned: "tamaño fisico de la tablet es de 25,5 mm ancho x 15,4 de alto"
This seems quite small for a drawing tablet (that's ~2.5cm x 1.5cm). Did you mean:
- 25.5 cm x 15.4 cm? (more reasonable for active area)
- Or is this the sensor size? 
- Or perhaps it's a different measurement?

Please confirm the active area dimensions so we can correctly scale coordinates to your screen.

## Ready When You Are
Let me know:
1. How the build went in Android Studio
2. If you can get the HID descriptor using your Code_Tool
3. Which permission path you prefer to try first
4. Confirmation on the tablet size

Then we'll get those pressure-sensitive strokes flowing in Krita and smooth cursor movement in Parsec! 🎩