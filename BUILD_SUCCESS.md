# Build Successful! 🎉

The XP-Pen Input Mapper app has been successfully built.

## 📱 Installation Steps

1. **Locate the APK:**
   - The debug APK is at: `app/build/outputs/apk/debug/app-debug.apk`

2. **Install on your Xiaomi Pad Pro:**
   - Enable **Developer options** and **USB debugging** on your tablet
   - Connect the tablet to your PC via USB
   - Install the APK using Android Studio (Run button) or via command line:
     ```
     adb install app/build/outputs/apk/debug/app-debug.apk
     ```

3. **Grant the required permission (as requested):**
   After installing, run:
   ```
   adb shell pm grant com.xppen.inputmapper android.permission.INJECT_EVENTS
   ```
   Note: If this fails due to device restrictions, you can use the AccessibilityService fallback (see below).

## ⚙️ Post-Installation Setup

### Option A: Using INJECT_EVENTS (Preferred)
- If the `adb shell pm grant` command succeeded, the app can inject input events directly.
- Launch the app and connect your XP-Pen Deco LW via USB-C to test.

### Option B: AccessibilityService Fallback (If INJECT_EVENTS grant fails)
1. Install the app
2. Go to **Settings → Accessibility → XP-Pen Input Mapper** → Toggle ON
3. The service can simulate touch events (pressure sensitivity may be limited, but basic function will work)

## 🔍 Next Critical Step: Get Your Tablet's HID Descriptor

To make pressure sensitivity and button mapping work accurately, we need the **HID report descriptor** from your specific XP-Pen Deco LW tablet.

You have the `Code_Tool (2).exe` - this is the easiest way to get it:

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

## 📤 What to Do Next

Please:
1. Install the app on your Xiaomi Pad Pro
2. Try to grant the INJECT_EVENTS permission via ADB
3. Use your Code_Tool on a Windows PC to get the HID descriptor and **share it here**
4. Let me know if you encounter any issues with installation or permission granting

Once I have the HID descriptor, I'll update the `HidReportParser.kt` to correctly extract:
- X position (0 to maxX from your tablet)
- Y position (0 to maxY from your tablet)
- Pressure (0-255 or whatever range your tablet reports)
- Button states (both stylus buttons + tablet buttons)
- Tip switch (in-contact detection)

And then we'll be much closer to having pressure-sensitive strokes in Krita and smooth cursor control in Parsec!

🎩 *Ready for your HID descriptor whenever you are!*