package com.vectras.vm.serial;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connects the virtual machine's COM1 to a USB serial adapter (Vectras PRO).
 *
 * QEMU exposes COM1 as a TCP server on localhost (see ComSettings.qemuParams).
 * This bridge connects to it and copies bytes both ways between the VM and the
 * USB adapter, using the line settings chosen in the app.
 */
public class ComBridge {
    private static final String TAG = "ComBridge";
    private static final int GIVE_UP_MS = 120000;

    public enum State { STOPPED, WAITING_VM, WAITING_ADAPTER, PERMISSION_DENIED, CONNECTED }

    private static ComBridge instance;

    private final Context context;
    private volatile boolean running;
    private volatile State state = State.STOPPED;
    private volatile String adapterLabel = "";
    private final AtomicLong toDevice = new AtomicLong();
    private final AtomicLong fromDevice = new AtomicLong();
    private Thread thread;

    private ComBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ComBridge get(Context context) {
        if (instance == null) instance = new ComBridge(context);
        return instance;
    }

    public State getState() {
        return state;
    }

    public String getAdapterLabel() {
        return adapterLabel;
    }

    public long getBytesToDevice() {
        return toDevice.get();
    }

    public long getBytesFromDevice() {
        return fromDevice.get();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        toDevice.set(0);
        fromDevice.set(0);
        thread = new Thread(this::loop, "ComBridge");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        state = State.STOPPED;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            running = false;
        }
    }

    private void loop() {
        long lastVmSeen = System.currentTimeMillis();
        while (running) {
            // 1. USB adapter
            List<UsbSerialDriver> drivers = UsbSerialHelper.findAllDrivers(context);
            if (drivers.isEmpty()) {
                state = State.WAITING_ADAPTER;
                sleep(1000);
                continue;
            }
            UsbSerialDriver driver = drivers.get(0);
            if (!UsbSerialHelper.requestPermissionBlocking(context, driver.getDevice(), 60000)) {
                state = State.PERMISSION_DENIED;
                sleep(3000);
                continue;
            }

            // 2. Virtual machine COM1
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", ComSettings.TCP_PORT), 1000);
                socket.setTcpNoDelay(true);
            }
            catch (IOException e) {
                closeQuietly(socket);
                state = State.WAITING_VM;
                if (System.currentTimeMillis() - lastVmSeen > GIVE_UP_MS) break;
                sleep(1000);
                continue;
            }
            lastVmSeen = System.currentTimeMillis();

            // 3. Copy bytes both ways until one side goes away
            UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
            UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
            if (connection == null) {
                closeQuietly(socket);
                state = State.PERMISSION_DENIED;
                sleep(2000);
                continue;
            }
            UsbSerialPort port = driver.getPorts().get(0);
            SerialInputOutputManager io = null;
            try {
                port.open(connection);
                port.setParameters(ComSettings.getBaudRate(context), ComSettings.getDataBits(context),
                    ComSettings.getStopBits(context), ComSettings.getParity(context));
                try {
                    port.setDTR(true);
                    port.setRTS(true);
                }
                catch (Exception ignored) {}

                final OutputStream toVm = socket.getOutputStream();
                final InputStream fromVm = socket.getInputStream();
                final Socket finalSocket = socket;
                io = new SerialInputOutputManager(port, new SerialInputOutputManager.Listener() {
                    @Override
                    public void onNewData(byte[] data) {
                        try {
                            toVm.write(data);
                            toVm.flush();
                            fromDevice.addAndGet(data.length);
                        }
                        catch (IOException e) {
                            closeQuietly(finalSocket);
                        }
                    }

                    @Override
                    public void onRunError(Exception e) {
                        closeQuietly(finalSocket);
                    }
                });
                io.start();
                adapterLabel = UsbSerialHelper.getLabel(driver);
                state = State.CONNECTED;

                byte[] buf = new byte[4096];
                int n;
                while (running && (n = fromVm.read(buf)) >= 0) {
                    if (n == 0) continue;
                    byte[] chunk = new byte[n];
                    System.arraycopy(buf, 0, chunk, 0, n);
                    port.write(chunk, 2000);
                    toDevice.addAndGet(n);
                }
            }
            catch (Exception e) {
                Log.w(TAG, "bridge closed: " + e);
            }
            finally {
                if (io != null) io.stop();
                try {
                    port.close();
                }
                catch (Exception ignored) {}
                closeQuietly(socket);
            }
            lastVmSeen = System.currentTimeMillis();
            sleep(500);
        }
        running = false;
        state = State.STOPPED;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        }
        catch (Exception ignored) {}
    }
}
