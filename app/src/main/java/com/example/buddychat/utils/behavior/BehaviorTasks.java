package com.example.buddychat.utils.behavior;

import android.util.Log;
import androidx.annotation.Nullable;

// Buddy SDK
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.services.companion.Task;
import com.bfr.buddysdk.services.companion.TaskCallback;

import com.example.buddychat.utils.ThreadUtils;

// ================================================================================
// Controller for the BuddySDKs "BehaviorInstructions" (BI) Tasks
// ================================================================================
public final class BehaviorTasks {
    private static final String TAG = "[DPU_BehaviorTasks]";
    private BehaviorTasks() {} // no instances

    // Running State
    private static Task     currentTask = null;
    private static Behavior currentMode = null;

    // ToDo: Not sure if this is required anymore, depends on if `Task` gets automatically set to null after completion
    public static volatile boolean isRunning = false;

    // --------------------------------------------------------------------------------
    // Configuration
    // --------------------------------------------------------------------------------
    // We trust the Robot's internal configuration:
    // "Sleep" should be configured to LOOP.
    // "Yawn"  should be configured to PLAY ONCE.
    public enum Behavior {
        SLEEP("Sleep"),
        WAKE ("Yawn");

        final String sdkName;
        Behavior(String name) { this.sdkName = name; }
    }

    // --------------------------------------------------------------------------------
    // Public API
    // --------------------------------------------------------------------------------
    /** Start the Wake Up behavior. Fires callback when animation FINISHES. */
    public static void startWakeUpTask(Runnable onWakeUpComplete) {
        startBehavior(Behavior.WAKE, onWakeUpComplete);
    }

    /** Start the Sleep behavior. Loops forever (no callback). */
    public static void startSleepTask() {
        startBehavior(Behavior.SLEEP, null);
    }

    /** Stops whatever task the robot is currently doing. */
    public static synchronized void stopCurrentTask() {
        Log.d(TAG, String.format("%s Stopping BI (Current state: nullTask=%s, isRunning=%s, biMode=%s)", TAG, (currentTask == null), isRunning, currentMode.sdkName));
        if (currentTask != null) {
            try                 { currentTask.stop(); }
            catch (Exception e) { Log.e(TAG, String.format("%s Error stopping task: %s", TAG, e.getMessage())); }
            currentTask = null;
            currentMode = null;
        }
    }

    // --------------------------------------------------------------------------------
    // Internal Logic
    // --------------------------------------------------------------------------------
    private static synchronized void startBehavior(Behavior mode, @Nullable Runnable onSuccess) {
        // Always stop the previous behavior before trying to start a new one
        stopCurrentTask();

        // Set the current mode
        Log.i(TAG, String.format("%s Starting Behavior: %s", TAG, mode.sdkName));
        currentMode = mode;

        // Create the task & start it
        try {
            currentTask = BuddySDK.Companion.createBICategoryTask(mode.sdkName, null, null, true);
            currentTask.start(newTaskCallback(onSuccess, mode));
        }
        // Even if start fails, try to run the callback so the app flow continues
        catch (Exception e) {
            Log.e(TAG, String.format("%s Failed to start behavior: %s", TAG, e.getMessage()));
            ThreadUtils.runOnUiThread(onSuccess);
        }
    }

    // ================================================================================
    // Generate new TaskCallbacks
    // ================================================================================
    // ToDo: onCancel might be what happens when you call .stop()... so set the task to null there?
    // ToDo: isSleeping needs to change differently depending on which Task this is a Callback for...
    private static TaskCallback newTaskCallback(@Nullable Runnable onSuccessCb, Behavior mode) {
        return new TaskCallback() {
            @Override public void onStarted           (        ) { logStarted(mode   ); }
            @Override public void onSuccess           (String s) { logSuccess(mode, s); ThreadUtils.runOnUiThread(onSuccessCb); }
            @Override public void onCancel            (        ) { logCancel (mode   ); }
            @Override public void onError             (String s) { logError  (mode, s); }
            @Override public void onIntermediateResult(String s) { logInter  (mode, s); }
        };
    }

    // Helpers
    private static void logStarted(Behavior mode          ) { Log.d(TAG, String.format("%s %s | onStarted()",                  TAG, mode.sdkName   )); isRunning = true;  }
    private static void logSuccess(Behavior mode, String s) { Log.i(TAG, String.format("%s %s | onSuccess() -> %s",            TAG, mode.sdkName, s)); isRunning = false; }
    private static void logCancel (Behavior mode          ) { Log.i(TAG, String.format("%s %s | onCancel()",                   TAG, mode.sdkName   )); isRunning = false; }
    private static void logError  (Behavior mode, String s) { Log.e(TAG, String.format("%s %s | onError() -> %s",              TAG, mode.sdkName, s)); isRunning = false; }
    private static void logInter  (Behavior mode, String s) { Log.d(TAG, String.format("%s %s | onIntermediateResult() -> %s", TAG, mode.sdkName, s)); }

}
