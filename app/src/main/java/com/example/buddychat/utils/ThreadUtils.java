package com.example.buddychat.utils;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;

// ================================================================================
// Utility class to provide threading/execution to other classes.
// ================================================================================
public final class ThreadUtils {
    private static final String TAG = "[DPU_ThreadUtils]";
    private ThreadUtils() {} // Static-only class

    // Handler for posting a given Runnable
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    // The Executor for passing to libraries/callbacks that require an Executor interface
    public static final Executor MAIN_EXECUTOR = MAIN_HANDLER::post;

    /** [Helper] Run a Runnable on the UI thread safely.  */
    public static void runOnUiThread(Runnable action) {
        if (action == null) return;
        MAIN_HANDLER.post(action);
    }

}
