package com.example.buddychat.utils.sensors;

import android.util.Log;

import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.chat.StatusController;

// ================================================================================
// TouchSensors
// ================================================================================
/** TouchSensors <br>
 * Start the chat ("wake" Buddy up) by touching him and triggering the sensors.
 * When the chat is already on, simply triggers the "LOVE" mood setting for 1 second. */
public final class TouchSensors {
    private static final String TAG = "[DPU_TouchSensors]";
    private TouchSensors() {} // no instances

    // --------------------------------------------------------------------------------
    // Cooldown Guard (trying to avoid setting the emotion constantly)
    // --------------------------------------------------------------------------------
    private static final    long COOLDOWN_MS   = 3_000L;  // 3s global lockout
    private static volatile long cooldownUntil =     0L;  // elapsedRealtime millis

    /** Returns TRUE if we are in timeout and should NOT do anything; FALSE if we are clear to act. */
    private static boolean check() {
        final long now = android.os.SystemClock.elapsedRealtime();
        final boolean inTimeout = now < cooldownUntil;

        if (!inTimeout) cooldownUntil = now + COOLDOWN_MS; // update the cooldown timer
        return inTimeout;
    }

    // --------------------------------------------------------------------------------
    // Receive data from SensorListener
    // --------------------------------------------------------------------------------
    // ToDo: Use logs to figure out which motor number is which (i.e. left, center, right)
    public static void onTouchSample(boolean touch1, boolean touch2, boolean touch3, String source) {
        boolean touched = touch1 | touch2 | touch3;
        if (touched) {
            // Check status of things
            final boolean awakeStatus = StatusController.isActive(); // use StatusController.java to check on the chat status
            final boolean inTimeout   = check();
            Log.i(TAG, String.format("%s %s Touched! (isAwake=%s, inTimeout=%s) (Motors: 1=%s, 2=%s, 3=%s)",
                    TAG, source, awakeStatus, inTimeout, touch1, touch2, touch3));

            // Do nothing if we are still timed out
            if (inTimeout) return;

            // If Buddy is awake set the "LOVE" expression; if Buddy is sleeping/the chat is off, then we need to start it
            if (awakeStatus) { Emotions.setMood("LOVE", 1_000L); }
            else { StatusController.start(); }
        }
    }

}
