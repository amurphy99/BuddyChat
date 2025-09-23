package com.example.buddychat.utils.audio_triangulation;

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

// ====================================================================
// AudioTracking
// ====================================================================
/** AudioTracking
 * <br>
 * (Right now there are two methods for this, but we probably will settle on using method #2 if it
 * works well in testing.)
 * <br><br>
 * We use a queue to help smooth the sensor reading out due to its' instability. When we make a
 * call to actually rotate buddy and use the queues value, we take the average and then clear the
 * queue. We should (probably) only call the function to rotate Buddy on STT detection, because
 * with that we know that the user was just speaking for at least a short period before the
 * detection event.
 * <br><br>
 * There are four important methods: <ul>
 *   <li> setupSensors()       => Sets up the sensor module for the SDK
 *   <li> EnableUsbCallback()  => Registers a CB with the sensor module (reads sensor data)
 *   <li> DisableUsbCallback() => Unregisters the CB (no more data will be read)
 *   <li> rotateTowardsAudio() => Takes the average angle from the recent queue, rotates Buddy that direction
 * </ul>
 */
public class AudioTracking {
    private static final String TAG = "[DPU_AudioTracking]";

    // Queue of recent values for the LocationAngle
    private static final AngleBuffer angleBuf = AngleBuffer.defaultAudio(/*capacity*/ 30);

    // Public access to the current angle. Calculates the rolling average and clears the queue.
    public static float getRecentAngle() { return angleBuf.averageCircularAndClear(); }

    // -----------------------------------------------------------------------
    // Setup Sensors
    // -----------------------------------------------------------------------
    // Needs to be called after the SDK launches
    public static void setupSensors() {
        BuddySDK.USB.enableSensorModule(true, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { Log.i(TAG, "-------------- Enabled Sensors --------------");  }
            @Override public void onFailed (String s) { Log.w(TAG, "Failed to Enable sensors:" + s); }
        });
    }

    // -----------------------------------------------------------------------
    // Callback subscribes to USB sensor readings
    // -----------------------------------------------------------------------
    public static final IUsbAidlCbListener usbCallback = new IUsbAidlCbListener.Stub() {
        @Override public void ReceiveMotorMotionData(MotorMotionData motorMotionData) throws RemoteException { }
        @Override public void ReceiveMotorHeadData  (MotorHeadData   motorHeadData  ) throws RemoteException { }
        @Override public void ReceiveHeadSensorData (HeadSensorData  headSensorData ) throws RemoteException { }
        @Override public void ReceiveBodySensorData (BodySensorData  bodySensorData ) throws RemoteException { }

        @Override public void ReceivedVocalData(VocalData vocalData) throws RemoteException {
            angleBuf.push(vocalData.SoundSourceLocalisation);
        }
    };

    // Toggle the callback
    public static void EnableUsbCallback () { Log.d(TAG, String.format("%s Enabling USB callback",  TAG)); BuddySDK.USB.registerCb  (usbCallback); }
    public static void DisableUsbCallback() { Log.d(TAG, String.format("%s Disabling USB callback", TAG)); BuddySDK.USB.unRegisterCb(usbCallback); }

}
