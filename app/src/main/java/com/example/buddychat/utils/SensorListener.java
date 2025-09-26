package com.example.buddychat.utils;

import android.os.RemoteException;
import android.util.Log;

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
import com.example.buddychat.utils.audio_triangulation.AngleBuffer;

// ====================================================================
// AudioTracking
// ====================================================================
/** AudioTracking
 * <br><br>
 * We use a queue to help smooth the sensor reading out due to its' instability. When we make a
 * call to actually rotate buddy and use the queues value, we take the average and then clear the
 * queue. We should (probably) only call the function to rotate Buddy on STT detection, because
 * with that we know that the user was just speaking for at least a short period before the
 * detection event.
 * <br><br>
 * There are three important methods: <ul>
 *   <li> setupSensors()       => Sets up the sensor module for the SDK
 *   <li> EnableUsbCallback()  => Registers a CB with the sensor module (reads sensor data)
 *   <li> DisableUsbCallback() => Unregisters the CB (no more data will be read)
 * </ul>
 */
public final class SensorListener {
    private static final String TAG = "[DPU_SensorListener]";
    private SensorListener() {} // no instances


    // -----------------------------------------------------------------------
    // Audio Handling
    // -----------------------------------------------------------------------
    // Boolean for handling whether or not we should be tracking the audio readings
    public static volatile boolean trackAudio = true;

    // Rolling buffer of mic localization values (thread-safe)
    private static final AngleBuffer angleBuf = AngleBuffer.defaultAudio(/*capacity*/ 20);

    // Public access to the current angle. Calculates the circular mean of recent angles and clears the buffer.
    public static float getRecentAngle() { return angleBuf.averageCircularAndClear(); }

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

        //
        @Override public void ReceivedVocalData(VocalData d) throws RemoteException {
            angleBuf.push(d.SoundSourceLocalisation);
        }
    };

    // Toggle the callback
    public static void EnableUsbCallback () { Log.d(TAG, String.format("%s Enabling USB callback",  TAG)); BuddySDK.USB.registerCb  (usbCallback); }
    public static void DisableUsbCallback() { Log.d(TAG, String.format("%s Disabling USB callback", TAG)); BuddySDK.USB.unRegisterCb(usbCallback); }

}
