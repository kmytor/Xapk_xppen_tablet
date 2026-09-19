package com.xppen.inputmapper.daemon;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * High-performance autonomous Input Daemon Server written in pure Java.
 * Runs directly under Dalvik / app_process with UID 2000 (Shell) without external Kotlin dependencies.
 */
public class InputDaemonServer {
    public static final int SERVER_PORT = 18742;
    public static final byte CMD_PING = 0x00;
    public static final byte CMD_MOTION_EVENT = 0x01;
    public static final byte CMD_KEY_EVENT = 0x02;
    public static final byte CMD_WAKEUP = 0x03;
    public static final byte CMD_STOP = (byte) 0xFF;

    public static void main(String[] args) {
        System.out.println("InputDaemonServer Java starting on port " + SERVER_PORT);
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "input");
            if (binder == null) {
                System.err.println("Fatal: input service binder is null");
                return;
            }

            Class<?> iInputManagerStubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Method asInterfaceMethod = iInputManagerStubClass.getMethod("asInterface", IBinder.class);
            Object iInputManager = asInterfaceMethod.invoke(null, binder);

            Class<?> iInputManagerClass = Class.forName("android.hardware.input.IInputManager");
            Method injectMethod = iInputManagerClass.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT, 50, InetAddress.getByName("127.0.0.1"));
            serverSocket.setReuseAddress(true);
            System.out.println("InputDaemonServer Java ready on 127.0.0.1:" + SERVER_PORT);

            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    handleClient(client, iInputManager, injectMethod);
                } catch (Exception e) {
                    System.err.println("Client error: " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
    }

    private static void handleClient(Socket socket, Object iInputManager, Method injectMethod) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[0].toolType = MotionEvent.TOOL_TYPE_STYLUS;

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].size = 1f;

        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            while (true) {
                byte cmd = input.readByte();
                if (cmd == CMD_PING) {
                    output.writeByte(0x01);
                    output.flush();
                } else if (cmd == CMD_MOTION_EVENT) {
                    long downTime = input.readLong();
                    long eventTime = input.readLong();
                    int action = input.readInt();
                    float x = input.readFloat();
                    float y = input.readFloat();
                    float pressure = input.readFloat();
                    int buttonState = input.readInt();
                    int deviceId = input.readInt();

                    coords[0].x = x;
                    coords[0].y = y;
                    coords[0].pressure = pressure;

                    // If pressure is 0 and action is DOWN/MOVE, ensure at least minimal pressure for stylus tip
                    if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) && coords[0].pressure <= 0f) {
                        coords[0].pressure = 0.05f;
                    }

                    MotionEvent motionEvent = MotionEvent.obtain(
                        downTime, eventTime, action, 1,
                        properties, coords, 0, buttonState,
                        1.0f, 1.0f, deviceId, 0,
                        InputDevice.SOURCE_STYLUS, 0
                    );

                    try {
                        injectMethod.invoke(iInputManager, motionEvent, 0);
                    } finally {
                        motionEvent.recycle();
                    }
                } else if (cmd == CMD_KEY_EVENT) {
                    long downTime = input.readLong();
                    long eventTime = input.readLong();
                    int action = input.readInt();
                    int keyCode = input.readInt();
                    int metaState = input.readInt();

                    KeyEvent keyEvent = new KeyEvent(
                        downTime, eventTime, action, keyCode, 0, metaState,
                        android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                        KeyEvent.FLAG_FROM_SYSTEM
                    );
                    injectMethod.invoke(iInputManager, keyEvent, 0);
                } else if (cmd == CMD_WAKEUP) {
                    wakeScreen();
                } else if (cmd == CMD_STOP) {
                    socket.close();
                    System.exit(0);
                }
            }
        } catch (java.io.EOFException ignored) {
        } catch (Exception e) {
            System.err.println("Client handler exception: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Wakes the screen when the pen comes into proximity.
     * Tries, in order: direct PowerManager goToSleep/setInteractive via reflection,
     * then a KEYCODE_WAKEUP key event through the input manager. Requires shell/system UID.
     */
    private static void wakeScreen() {
        try {
            // Method 1: PowerManager.wakeUp(time) via reflection (needs shell or system UID)
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "power");
            if (binder != null) {
                long now = android.os.SystemClock.uptimeMillis();
                Class<?> iPowerManagerClass = Class.forName("android.os.IPowerManager");
                // Recent Android: wakeUp(long time, int reason, String details)
                try {
                    Class<?> stub = Class.forName("android.os.IPowerManager$Stub");
                    Method asInterface = stub.getMethod("asInterface", IBinder.class);
                    Object iPower = asInterface.invoke(null, binder);
                    if (iPower != null) {
                        try {
                            Method wakeUp = iPowerManagerClass.getMethod("wakeUp", long.class, int.class, String.class);
                            wakeUp.invoke(iPower, now, 2, "xppen_input_mapper"); // reason 2 = WAKE_REASON_PEN
                            System.out.println("InputDaemonServer: screen woken via IPowerManager.wakeUp");
                            return;
                        } catch (NoSuchMethodException ns) {
                            // Older signature: wakeUp(long)
                            try {
                                Method wakeUp2 = iPowerManagerClass.getMethod("wakeUp", long.class);
                                wakeUp2.invoke(iPower, now);
                                System.out.println("InputDaemonServer: screen woken via IPowerManager.wakeUp(long)");
                                return;
                            } catch (NoSuchMethodException ns2) {
                                // ignore, fall through to key event
                            }
                        }
                    }
                } catch (Exception e) {
                    // fall through to key event
                }
            }
        } catch (Throwable t) {
            // external binder attempt failed, fall through
        }

        // Method 2: Inject a KEYCODE_WAKEUP key event to wake the screen.
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getServiceMethod.invoke(null, "input");
            if (binder == null) return;
            Class<?> iInputManagerStubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Method asInterfaceMethod = iInputManagerStubClass.getMethod("asInterface", IBinder.class);
            Object iInputManager = asInterfaceMethod.invoke(null, binder);
            Class<?> iInputManagerClass = Class.forName("android.hardware.input.IInputManager");
            Method injectMethod = iInputManagerClass.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            long now = android.os.SystemClock.uptimeMillis();
            KeyEvent wakeup = new KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_WAKEUP, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
            );
            injectMethod.invoke(iInputManager, wakeup, 0);
            KeyEvent wakeupUp = new KeyEvent(
                now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_WAKEUP, 0, 0,
                android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM
            );
            injectMethod.invoke(iInputManager, wakeupUp, 0);
            System.out.println("InputDaemonServer: screen woken via KEYCODE_WAKEUP");
        } catch (Throwable t) {
            System.err.println("InputDaemonServer: screen wake failed: " + t.getMessage());
        }
    }
}
