package com.vectras.vm.serial;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UsbSerialHelper {
    private static String action(Context context) {
        return context.getPackageName() + ".USB_SERIAL_PERMISSION";
    }

    public static List<UsbSerialDriver> findAllDrivers(Context context) {
        UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return new ArrayList<>();
        try {
            return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        }
        catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String getLabel(UsbSerialDriver driver) {
        UsbDevice device = driver.getDevice();
        String name = null;
        try {
            name = device.getProductName();
        }
        catch (Exception ignored) {}
        if (name == null || name.trim().isEmpty()) name = driver.getClass().getSimpleName().replace("SerialDriver", "");
        return String.format(Locale.ENGLISH, "%s (%04x:%04x)", name.trim(), device.getVendorId(), device.getProductId());
    }

    /** Shows the Android USB permission dialog and waits. Not for the UI thread. */
    public static boolean requestPermissionBlocking(Context context, UsbDevice device, long timeoutMs) {
        UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return false;
        if (usbManager.hasPermission(device)) return true;

        final Context app = context.getApplicationContext();
        final String action = action(app);
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] granted = {false};
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!action.equals(intent.getAction())) return;
                granted[0] = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                latch.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(receiver, filter);
        try {
            Intent intent = new Intent(action);
            intent.setPackage(app.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            usbManager.requestPermission(device, PendingIntent.getBroadcast(app, 0, intent, flags));
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            try {
                app.unregisterReceiver(receiver);
            }
            catch (Exception ignored) {}
        }
        return granted[0] || usbManager.hasPermission(device);
    }
}
