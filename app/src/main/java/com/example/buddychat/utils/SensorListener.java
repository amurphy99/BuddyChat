package com.example.buddychat.utils;

import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

// BuddySDK imports
import com.bfr.buddy.usb.shared.IUsbAidlCbListener;
import com.bfr.buddy.usb.shared.IUsbCommadRsp;

// Sensor reading types
import com.bfr.buddy.usb.shared.BodySensorData;
import com.bfr.buddy.usb.shared.HeadSensorData;
import com.bfr.buddy.usb.shared.MotorHeadData;
import com.bfr.buddy.usb.shared.MotorMotionData;
import com.bfr.buddy.usb.shared.VocalData;

import com.bfr.buddysdk.BuddySDK;
import com.example.buddychat.utils.audio_triangulation.AudioTracking;

// ====================================================================
// SensorListener
// ====================================================================
public final class SensorListener {
    private static final String TAG = "[DPU_SensorListener]";
    private SensorListener() {} // no instances

    // Feature gates (audio/head)
    private static final AtomicBoolean audioEnabled = new AtomicBoolean(false);
    private static final AtomicBoolean headEnabled  = new AtomicBoolean(false);

    // -----------------------------------------------------------------------
    // Setup Sensors
    // -----------------------------------------------------------------------
    /** Enable sensor module (call after SDK launch). */
    public static void setupSensors() {
        BuddySDK.USB.enableSensorModule(true, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { Log.i(TAG, "-------------- Enabled Sensors --------------");  }
            @Override public void onFailed (String s) { Log.w(TAG, "Failed to Enable sensors:" + s); }
        });
    }

    // Feature toggles
    public static void setAudioTrackingEnabled(boolean enable) { audioEnabled.set(enable); }
    public static boolean isAudioTrackingEnabled() { return audioEnabled.get(); }

    public static void setHeadSensorsEnabled(boolean enable) { headEnabled.set(enable); }
    public static boolean isHeadSensorsEnabled() { return headEnabled.get(); }

    // -----------------------------------------------------------------------
    // Subscribe to USB sensor readings (page 48 of SDK user guide)
    // -----------------------------------------------------------------------
    // Only using the VocalData reading
    public static final IUsbAidlCbListener usbCallback = new IUsbAidlCbListener.Stub() {
        @Override public void ReceiveMotorMotionData(MotorMotionData d) throws RemoteException { }
        @Override public void ReceiveMotorHeadData  (MotorHeadData   d) throws RemoteException { }
        @Override public void ReceiveBodySensorData (BodySensorData  d) throws RemoteException { }

        // Head touch sensors
        @Override public void ReceiveHeadSensorData (HeadSensorData  d) throws RemoteException {

        }

        // Push into the audio tracking buffer
        @Override public void ReceivedVocalData(VocalData d) throws RemoteException {
            if (audioEnabled.get()) { AudioTracking.onVocalSample(d.SoundSourceLocalisation); }
        }
    };

    // Toggle the callback
    public static void EnableUsbCallback () { Log.d(TAG, String.format("%s Enabling USB callback",  TAG)); BuddySDK.USB.registerCb  (usbCallback); }
    public static void DisableUsbCallback() { Log.d(TAG, String.format("%s Disabling USB callback", TAG)); BuddySDK.USB.unRegisterCb(usbCallback); }

}
