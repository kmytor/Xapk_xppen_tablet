package com.xppen.inputmapper.input

import android.util.Log

/**
 * Parses HID reports from XP-Pen tablets.
 * Supports standard XP-Pen pen reports (Report ID 0x07) and falls back to
 * other common formats, logging raw data for debugging.
 */
class HidReportParser {

    companion object {
        private const val TAG = "UsbInputService"

        // Physical maximum coordinates reported by XP-Pen Deco LW.
        // Centralized here so they can be adjusted to the real HID descriptor
        // without touching multiple files.
        const val MAX_X = 50800
        const val MAX_Y = 30480
        const val MAX_PRESSURE = 16383
    }

    /**
     * Parsed data from a HID report.
     */
    data class ParsedData(
        val isValid: Boolean,
        val x: Int,         // Raw X coordinate
        val y: Int,         // Raw Y coordinate
        val pressure: Int,  // Raw pressure
        val buttons: Int,   // Bitmask: bit1=button1, bit2=button2
        val tipSwitch: Int, // 1 if drawing/touching, 0 if hovering
        val inProximity: Boolean // true if pen is in range
    )

    /**
     * Parses a HID report byte array.
     */
     fun parseReport(report: ByteArray, productId: Int): ParsedData {
        if (report.isEmpty()) {
            return ParsedData(false, 0, 0, 0, 0, 0, false)
        }

        // Log the raw bytes in hex format for diagnostic purposes
        val hexString = report.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw HID Report (Len=${report.size}): $hexString")

        var x = 0
        var y = 0
        var pressure = 0
        var tipSwitch = 0
        var buttons = 0
        var inProximity = false
        var isReportParsed = false

        // XP-Pen standard Pen report - Report ID 0x07 (UC-Logic v1 pen layout,
        // confirmed against the Linux hid-uclogic kernel driver):
        //   byte 0: Report ID (0x07)
        //   byte 1: status
        //     bit 0 (0x01): Tip Switch
        //     bit 1 (0x02): Barrel Switch (button 2)
        //     bit 2 (0x04): Tablet Pick (button 1)
        //     bit 6 (0x40): In Range
        //   byte 2-3: X (lean16)
        //   byte 4-5: Y (lean16)
        //   byte 6-7: Tip Pressure (lean16, low 13 bits)
        if (report[0].toInt() == 0x07 && report.size >= 8) {
            val status = report[1].toInt() and 0xFF
            // Check if it's a battery or status report (status starts with 0xF0)
            if ((status and 0xF0) == 0xF0) {
                Log.d(TAG, "Skipping battery/status report (0x07): status=${String.format("%02X", status)}")
                isReportParsed = false
            } else {
                tipSwitch = status and 0x01

                // Buttons: bit1 = barrel switch, bit2 = tablet pick
                buttons = status and 0x06

                // X and Y coordinates (16-bit little-endian starting at byte 2)
                x = ((report[3].toInt() and 0xFF) shl 8) or (report[2].toInt() and 0xFF)
                y = ((report[5].toInt() and 0xFF) shl 8) or (report[4].toInt() and 0xFF)

                // Pressure (14-bit little-endian starting at byte 6)
                val rawPressureVal = ((report[7].toInt() and 0xFF) shl 8) or (report[6].toInt() and 0xFF)
                pressure = rawPressureVal and 0x3FFF

                // In-range bit lives in the status byte as bit 6 (0x40), exactly like
                // the kernel driver. Some models also set bit 13 (0x2000) of the
                // pressure word; treat either as "in range".
                val inRangeStatus = (status and 0x40) != 0
                val inRangePressure = (rawPressureVal and 0x2000) != 0
                inProximity = inRangeStatus || inRangePressure || tipSwitch == 1 || pressure > 0
                isReportParsed = true
            }
        } 
        // UGEE v2 pen report (used by wireless XP-Pen tablets such as the
        // Deco LW when connected via the USB dongle). Report ID 0x02, layout
        // confirmed against the uclogic_rdesc_ugee_v2_pen_template in the Linux
        // hid-uclogic kernel driver:
        //   byte 0: Report ID (0x02)
        //   byte 1: status
        //     bit 0 (0x01): Tip Switch
        //     bit 1 (0x02): Barrel Switch (button 2)
        //     bit 2 (0x04): Tablet Pick (button 1)
        //     bit 5 (0x20): In Range   <-- NOTE: bit 5 for UGEE v2, not bit 6
        //   byte 2-3: X (lean16)
        //   byte 4-5: Y (lean16)
        //   byte 6-7: Tip Pressure (lean16, low 13 bits)
        //   byte 8: X Tilt (optional)
        //   byte 9: Y Tilt (optional)
        else if (report[0].toInt() == 0x02 && report.size >= 8) {
            val status = report[1].toInt() and 0xFF
            // Check if it's a battery/status or wireless reconnection report
            // (status starts with 0xF0; the driver treats 0x02 F8 ... as a
            // wireless reconnection event, not pen coordinates)
            if ((status and 0xF0) == 0xF0) {
                Log.d(TAG, "Skipping battery/reconnect report (0x02): status=${String.format("%02X", status)}")
                isReportParsed = false
            } else {
                tipSwitch = status and 0x01

                // Buttons: bit1 = barrel switch, bit2 = tablet pick
                buttons = status and 0x06

                // X and Y coordinates (16-bit little-endian starting at byte 2)
                x = ((report[3].toInt() and 0xFF) shl 8) or (report[2].toInt() and 0xFF)
                y = ((report[5].toInt() and 0xFF) shl 8) or (report[4].toInt() and 0xFF)

                // Pressure (14-bit little-endian starting at byte 6)
                val rawPressureVal = ((report[7].toInt() and 0xFF) shl 8) or (report[6].toInt() and 0xFF)
                pressure = rawPressureVal and 0x3FFF

                // In-range for UGEE v2 is bit 5 (0x20), per the kernel template.
                // Some units also set bit 6 (0x40) or bit 13 (0x2000) of the
                // pressure word; treat any of them as "in range".
                val inRangeStatus = (status and 0x20) != 0 || (status and 0x40) != 0
                val inRangePressure = (rawPressureVal and 0x2000) != 0
                inProximity = inRangeStatus || inRangePressure || tipSwitch == 1 || pressure > 0
                isReportParsed = true
            }
        }
        // UC-Logic v2 high-resolution pen report - Report ID 0x08 (used by
        // newer XP-Pen tablets with tilt, e.g. Deco LW). Confirmed against the
        // Linux hid-uclogic kernel driver:
        //   byte 0: Report ID (0x08)
        //   byte 1: status (tip=0x01, barrel=0x02, pick=0x04, inrange=0x40)
        //   byte 2-4: X (24-bit LE)
        //   byte 5-7: Y (24-bit LE)
        //   byte 8-9: Tip Pressure (16-bit)
        //   byte 10-11: X/Y Tilt (8-bit each)
        else if (report[0].toInt() == 0x08 && report.size >= 10) {
            val status = report[1].toInt() and 0xFF
            if ((status and 0xF0) == 0xF0) {
                Log.d(TAG, "Skipping battery/status report (0x08): status=${String.format("%02X", status)}")
                isReportParsed = false
            } else {
                tipSwitch = status and 0x01
                buttons = status and 0x06

                // X and Y: 24-bit little-endian
                x = ((report[4].toInt() and 0xFF) shl 16) or
                    ((report[3].toInt() and 0xFF) shl 8) or
                    (report[2].toInt() and 0xFF)
                y = ((report[7].toInt() and 0xFF) shl 16) or
                    ((report[6].toInt() and 0xFF) shl 8) or
                    (report[5].toInt() and 0xFF)

                // Pressure: 16-bit little-endian at bytes 8-9
                pressure = ((report[9].toInt() and 0xFF) shl 8) or (report[8].toInt() and 0xFF)

                val inRangeStatus = (status and 0x40) != 0
                inProximity = inRangeStatus || tipSwitch == 1 || pressure > 0
                isReportParsed = true
            }
        }
        // Fallback placeholder parser
        else if (report.size >= 6) {
            val buttonsAndTip = report[0].toInt() and 0xFF
            tipSwitch = buttonsAndTip and 0x01
            buttons = (buttonsAndTip shr 1) and 0x06
            x = ((report[2].toInt() and 0xFF) shl 8) or (report[1].toInt() and 0xFF)
            y = ((report[4].toInt() and 0xFF) shl 8) or (report[3].toInt() and 0xFF)
            pressure = report[5].toInt() and 0xFF
            inProximity = (x > 0 || y > 0 || pressure > 0 || tipSwitch > 0)
            isReportParsed = true
        }

        // We validate that we parsed the report structure successfully
        val isValid = isReportParsed

        if (isValid) {
            Log.d(TAG, "Parsed data successfully: X=$x, Y=$y, Pressure=$pressure, Tip=$tipSwitch, Buttons=$buttons, Proximity=$inProximity")
        } else {
            Log.d(TAG, "Unrecognized or idle report frame skipped")
        }

        return ParsedData(isValid, x, y, pressure, buttons, tipSwitch, inProximity)
    }
}