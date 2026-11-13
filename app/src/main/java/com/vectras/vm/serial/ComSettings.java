package com.vectras.vm.serial;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.hoho.android.usbserial.driver.UsbSerialPort;

/** COM port settings (Vectras PRO). The guest sees a standard COM1 (16550 UART). */
public class ComSettings {
    public static final String KEY_ENABLED = "proComEnabled";
    public static final String KEY_BAUD = "proComBaud";
    public static final String KEY_DATA_BITS = "proComDataBits";
    public static final String KEY_PARITY = "proComParity";
    public static final String KEY_STOP_BITS = "proComStopBits";
    public static final String KEY_FAST_DISK = "proFastDisk";
    public static final int TCP_PORT = 45550;

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static boolean isFastDisk(Context context) {
        return prefs(context).getBoolean(KEY_FAST_DISK, false);
    }

    public static int getBaudRate(Context context) {
        return parse(prefs(context).getString(KEY_BAUD, "9600"), 9600);
    }

    public static int getDataBits(Context context) {
        return parse(prefs(context).getString(KEY_DATA_BITS, "8"), 8);
    }

    public static int getParity(Context context) {
        switch (prefs(context).getString(KEY_PARITY, "none")) {
            case "odd": return UsbSerialPort.PARITY_ODD;
            case "even": return UsbSerialPort.PARITY_EVEN;
            case "mark": return UsbSerialPort.PARITY_MARK;
            case "space": return UsbSerialPort.PARITY_SPACE;
        }
        return UsbSerialPort.PARITY_NONE;
    }

    public static int getStopBits(Context context) {
        switch (prefs(context).getString(KEY_STOP_BITS, "1")) {
            case "1.5": return UsbSerialPort.STOPBITS_1_5;
            case "2": return UsbSerialPort.STOPBITS_2;
        }
        return UsbSerialPort.STOPBITS_1;
    }

    /** QEMU arguments that give the guest a COM1 connected to the bridge. */
    public static String qemuParams() {
        return " -serial tcp:127.0.0.1:" + TCP_PORT + ",server=on,wait=off ";
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (Exception e) {
            return fallback;
        }
    }
}
