package com.example.buddychat.utils.touch_sensors;

import android.util.Log;

import com.example.buddychat.utils.behavior.Emotions;

// ToDo: Probably should rework this to just be TouchSensors, then do the body ones in here too.
public final class HeadTouchSensors {
    private static final String TAG = "[DPU_HeadTouchSensors]";
    private HeadTouchSensors() {} // no instances

    // ToDo: Use logs to figure out which one is which
    public static void onHeadTouchSample(boolean touch1, boolean touch2, boolean touch3) {
        boolean touched = touch1 | touch2 | touch3;
        if (touched) {
            Log.i(TAG, String.format("%s Head Touched! (1=%s, 2=%s, 3=%s)", TAG, touch1, touch2, touch3));
            Emotions.setMood("LOVE", 1_000L);
        }
    }

}
