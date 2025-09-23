package com.example.buddychat.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

import com.bfr.buddy.usb.shared.IUsbCommadRsp;
import com.bfr.buddysdk.BuddySDK;

// =======================================================================
// Rotate the body of the robot
// =======================================================================
public final class RotateBody {
    private static final String TAG = "[DPU_RotateBody]";
    private RotateBody() {}  // no instances

    // -----------------------------------------------------------------------
    // Motor Thread (serialize all commands)
    // -----------------------------------------------------------------------
    private static final HandlerThread driveThread = new HandlerThread("DriveBus");
    private static final Handler drive;
    static { driveThread.start(); drive = new Handler(driveThread.getLooper()); }

    // -----------------------------------------------------------------------
    // Running states
    // -----------------------------------------------------------------------
    public static boolean emergencyStopped = false;
    public static boolean running          = false;

    // Cooldown guard
    private static final    long COOLDOWN_MS   = 3_000L;  // 3s global lockout
    private static volatile long cooldownUntil =     0L;  // elapsedRealtime millis

    // =======================================================================
    // Rotate Buddy a set number of degrees at a set speed
    // =======================================================================
    // Speed  : in deg/s (>0 to go forward, <0 to go backward)
    // Degree : degree of rotation
    public static void rotate(float speed, float degrees) {
        if (motorPreCheck(speed, degrees)) return;
        Log.i(TAG, String.format("%s Attempting to rotate Buddy %.3f degrees at %.3f speed", TAG, degrees, speed));
        running = true;

        // Enable the motors; onSuccess => start the movement; onSuccess => disable the wheels
        drive.post(() -> {
            BuddySDK.USB.enableWheels(true, new IUsbCommadRsp.Stub() {
                @Override public void onSuccess(String s) throws RemoteException {
                    Log.d(TAG, String.format("%s ENABLE wheels for rotation SUCCESS: %s", TAG, s));
                    issueRotation(speed, degrees);
                }
                @Override public void onFailed(String s) throws RemoteException {
                    Log.w(TAG, String.format("%s ENABLE wheels for rotation FAIL: %s", TAG, s));
                    running = false;
                }
            });
        });
    }

    /** Issue a rotation command to Buddy; disable the wheels again on completion. */
    private static void issueRotation(float speed, float degree) {
        BuddySDK.USB.rotateBuddy(speed, degree, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { BuddySDK.USB.enableWheels(false, new LogCb("DISABLE wheels onSuccess")); running = false; }
            @Override public void onFailed (String s) { BuddySDK.USB.enableWheels(false, new LogCb("DISABLE wheels onFailed" )); running = false; }
        });
    }

    // -----------------------------------------------------------------------
    // Utility Functions
    // -----------------------------------------------------------------------
    // Run some checks before we proceed with motor commands
    private static boolean motorPreCheck(float speed, float degrees) {
        // Emergency stop & timeout check
        final long now = android.os.SystemClock.elapsedRealtime();
        final boolean inTimeout = (now < cooldownUntil);
        final boolean condition = emergencyStopped || inTimeout;

        // Only log if the check was failed; if it succeeded, set the cooldown timer
        if (condition) { Log.w(TAG, String.format("%s Rotation ignored (speed=%.1f, deg=%.1f) (eStop=%s, inTimeout=%s)", TAG, speed, degrees, emergencyStopped, inTimeout)); }
        else           { cooldownUntil = now + COOLDOWN_MS;  }
        return condition;
    }

    // Logging helper for toggling wheels on/off
    private static final class LogCb extends IUsbCommadRsp.Stub {
        private final String what;
        LogCb(String what) { this.what = what; }
        @Override public void onSuccess(String s) { Log.d(TAG, String.format("%s %s OK:   %s", TAG, what, s)); }
        @Override public void onFailed (String s) { Log.w(TAG, String.format("%s %s FAIL: %s", TAG, what, s)); }
    }

    // -----------------------------------------------------------------------
    // Emergency Stop & Disable Wheels
    // -----------------------------------------------------------------------
    /** Emergency stop for the motors (Stop motors AND disable wheels..?). */
    public static void emergencyStopMotors() {
        emergencyStopped = true;

        // Use the SDKs built-in emergency stop
        BuddySDK.USB.emergencyStopMotors(new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { Log.i(TAG, String.format("%s StopMotors success: %s", TAG, s)); }
            @Override public void onFailed (String s) { Log.w(TAG, String.format("%s StopMotors failed: %s",  TAG, s)); }
        });

        // Disable wheels too, just to be sure
        BuddySDK.USB.enableWheels(false, new LogCb("DISABLE wheels on StopMotors" ));
    }

    /** Clean shutdown for app exit. ToDo: Maybe would go in like onDestroy, etc., IDK... */
    public static void shutdown() {
        drive.post(() -> BuddySDK.USB.enableWheels(false, new LogCb("DISABLE wheels (shutdown)")));
        driveThread.quitSafely();
    }

}
