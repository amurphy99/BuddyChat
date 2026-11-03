package com.example.buddychat.utils.sensors;

import android.os.RemoteException;
import android.util.Log;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// Buddy SDK
import com.bfr.buddy.usb.shared.BodySensorData;
import com.bfr.buddy.usb.shared.HeadSensorData;
import com.bfr.buddy.usb.shared.IUsbAidlCbListener;
import com.bfr.buddy.usb.shared.IUsbCommadRsp;
import com.bfr.buddy.usb.shared.MotorHeadData;
import com.bfr.buddy.usb.shared.MotorMotionData;
import com.bfr.buddy.usb.shared.VocalData;
import com.bfr.buddysdk.BuddySDK;

// From this project
import com.example.buddychat.utils.audio_triangulation.AudioTracking;

// ====================================================================
// SensorListener
// ====================================================================
public final class SensorListener {
    private static final String TAG = "[DPU_SensorListener]";
    private SensorListener() {} // no instances

    // Feature gates (audio/head)
    private static final AtomicBoolean audioEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean touchEnabled = new AtomicBoolean(false);

    // Most recent reading from each source (written from the USB callback thread)
    private static final Map<String, Reading> latest = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Setup Sensors
    // -----------------------------------------------------------------------
    /** Enable sensor module (call after SDK launch). */
    public static void setupSensors() {
        BuddySDK.USB.enableSensorModule(true, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { Log.d(TAG, String.format("%s Enabled Sensors ",             TAG   )); }
            @Override public void onFailed (String s) { Log.e(TAG, String.format("%s Failed to Enable sensors: %s", TAG, s)); }
        });
    }

    // -----------------------------------------------------------------------
    // Feature toggles
    // -----------------------------------------------------------------------
    public static void setAudioTrackingEnabled(boolean enable) { audioEnabled.set(enable); }
    public static boolean isAudioTrackingEnabled() { return audioEnabled.get(); }

    public static void setTouchSensorsEnabled(boolean enable) { touchEnabled.set(enable); }
    public static boolean isTouchSensorsEnabled() { return touchEnabled.get(); }

    // -----------------------------------------------------------------------
    // Subscribe to USB sensor readings (page 48 of SDK user guide)
    // -----------------------------------------------------------------------
    // Keep every reading for telemetry; pass touch and sound readings on when enabled
    public static final IUsbAidlCbListener usbCallback = new IUsbAidlCbListener.Stub() {
        @Override public void ReceiveMotorMotionData(MotorMotionData d) throws RemoteException { keep("motor_motion", d); }
        @Override public void ReceiveMotorHeadData  (MotorHeadData   d) throws RemoteException { keep("motor_head",   d); }

        // Body touch sensors
        @Override public void ReceiveBodySensorData (BodySensorData  d) throws RemoteException {
            keep("body", d);
            if (touchEnabled.get()) { TouchSensors.onTouchSample(d.sensor1Touch, d.sensor2Touch, d.sensor3Touch, "BODY"); }
        }

        // Head touch sensors
        @Override public void ReceiveHeadSensorData (HeadSensorData  d) throws RemoteException {
            keep("head", d);
            if (touchEnabled.get()) { TouchSensors.onTouchSample(d.firstTouchSensor, d.secondTouchSensor, d.thirdTouchSensor, "HEAD"); }
        }

        // ToDo: if we were REALLY serious, we could combine this with body/head sensor data and turn/rotate buddy to improve the accuracy...
        // Push into the audio tracking buffer
        @Override public void ReceivedVocalData(VocalData d) throws RemoteException {
            keep("vocal", d);
            if (audioEnabled.get()) { AudioTracking.onVocalSample(d.SoundSourceLocalisation); }
        }
    };

    // Toggle the callback
    public static void EnableUsbCallback () { Log.d(TAG, String.format("%s Enabling USB callback",  TAG)); BuddySDK.USB.registerCb  (usbCallback); }
    public static void DisableUsbCallback() { Log.d(TAG, String.format("%s Disabling USB callback", TAG)); BuddySDK.USB.unRegisterCb(usbCallback); }


    // --------------------------------------------------------------------------------
    // Latest Readings
    // --------------------------------------------------------------------------------
    // One source's most recent SDK reading, and how many readings that source has sent
    public static final class Reading {
        public final Object data;  // SDK reading object (plain public fields: numbers, flags, strings, arrays)
        public final long   seq;   // Readings received from this source so far

        // Pair a reading with its count
        Reading(Object data, long seq) { this.data = data; this.seq = seq; }
    }

    // Copy the most recent reading from each source, sorted by source name
    public static Map<String, Reading> latestReadings() { return new TreeMap<>(latest); }

    // Record one reading as its source's latest
    private static void keep(String source, Object data) {
        latest.compute(source, (key, previous) -> new Reading(data, (previous == null) ? 1 : previous.seq + 1));
    }
}
