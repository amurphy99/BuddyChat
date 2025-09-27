package com.example.buddychat.utils.touch_sensors;

import android.util.Log;

import com.example.buddychat.chat.ChatStatus;
import com.example.buddychat.chat.ChatController;
import com.example.buddychat.utils.behavior.Emotions;

// =======================================================================
// HeadTouchSensors
// =======================================================================
// ToDo: Probably should rework this to just be TouchSensors, then do the body ones in here too.
public final class HeadTouchSensors {
    private static final String TAG = "[DPU_HeadTouchSensors]";
    private HeadTouchSensors() {} // no instances

    // -----------------------------------------------------------------------
    // Cooldown Guard (trying to avoid setting the emotion constantly)
    // -----------------------------------------------------------------------
    private static final    long COOLDOWN_MS   = 3_000L;  // 3s global lockout
    private static volatile long cooldownUntil =     0L;  // elapsedRealtime millis

    /** Returns TRUE if we are in timeout and should NOT do anything; FALSE if we are clear to act. */
    private static boolean check() {
        final long now = android.os.SystemClock.elapsedRealtime();
        final boolean inTimeout = now < cooldownUntil;

        if (!inTimeout) cooldownUntil = now + COOLDOWN_MS; // update the cooldown timer
        return inTimeout;
    }

    // -----------------------------------------------------------------------
    // Receive data from SensorListener
    // -----------------------------------------------------------------------
    // ToDo: Use logs to figure out which motor is which (left, center, right)
    public static void onHeadTouchSample(boolean touch1, boolean touch2, boolean touch3) {
        boolean touched = touch1 | touch2 | touch3;
        if (touched) {
            // Check status of things
            final boolean awakeStatus = ChatStatus.isRunning();
            final boolean inTimeout   = check();
            Log.i(TAG, String.format("%s Head Touched! (isAwake=%s, inTimeout=%s) (Motors: 1=%s, 2=%s, 3=%s)", TAG, awakeStatus, inTimeout, touch1, touch2, touch3));

            // Do nothing if we are still timed out
            if (inTimeout) return;

            // If Buddy is awake set the "LOVE" expression; if Buddy is sleeping/the chat is off, then we need to start it
            if (awakeStatus) { Emotions.setMood("LOVE", 1_000L); }
            else { ChatController.start(); }
        }
    }

}
