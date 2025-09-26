package com.example.buddychat.utils.touch_sensors;

import android.util.Log;

import com.example.buddychat.utils.behavior.Emotions;

// =======================================================================
// HeadTouchSensors
// =======================================================================
// ToDo: Probably should rework this to just be TouchSensors, then do the body ones in here too.
public final class HeadTouchSensors {
    private static final String TAG = "[DPU_HeadTouchSensors]";
    private HeadTouchSensors() {} // no instances

    // -----------------------------------------------------------------------
    // Cooldown guard
    // -----------------------------------------------------------------------
    private static final    long COOLDOWN_MS   = 3_000L;  // 3s global lockout
    private static volatile long cooldownUntil =     0L;  // elapsedRealtime millis

    private static boolean check() {
        final long now = android.os.SystemClock.elapsedRealtime();
        final boolean condition = now > cooldownUntil;

        if (condition) cooldownUntil = now + COOLDOWN_MS; // update the cooldown
        return condition;
    }

    // -----------------------------------------------------------------------
    // Receive data from SensorListener
    // -----------------------------------------------------------------------
    // ToDo: Use logs to figure out which one is which
    public static void onHeadTouchSample(boolean touch1, boolean touch2, boolean touch3) {
        boolean touched = touch1 | touch2 | touch3;
        if (touched) {
            Log.i(TAG, String.format("%s Head Touched! (1=%s, 2=%s, 3=%s)", TAG, touch1, touch2, touch3));

            // Trying to avoid setting the emotion constantly
            if (check()) Emotions.setMood("LOVE", 1_000L);
        }
    }

}
