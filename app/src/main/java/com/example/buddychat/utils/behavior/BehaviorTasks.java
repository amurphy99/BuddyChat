package com.example.buddychat.utils.behavior;

import android.util.Log;

// Buddy SDK
import androidx.annotation.Nullable;

import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.services.companion.Task;
import com.bfr.buddysdk.services.companion.TaskCallback;
import com.example.buddychat.chat.ChatController;

// =======================================================================
// Controller for the BuddySDKs "BehaviorInstructions" Tasks
// =======================================================================
public final class BehaviorTasks {
    private static final String TAG = "[DPU_BehaviorTasks]";
    private BehaviorTasks() {} // no instances

    // Task names
    private static final String SLEEP = "Sleep";
    private static final String WAKE  = "Yawn";

    // Running state
    public static volatile boolean isRunning = false;
    public static volatile String  biMode    = "";

    // Task pointer - ToDo: Would it be better to keep two tasks (one for Sleep, one for WakeUp) instead of re-creating them?
    private static Task biTask = null;

    // -----------------------------------------------------------------------
    // Initialize / Start the "SLEEP" Task
    // -----------------------------------------------------------------------
    // ToDo: Replace these with a single function that takes an argument: "Sleep" | "WakeUp"
    // Set the current task to "Sleep" & start it
    public static void startSleepTask() {
        if (biTask != null) { stopCurrentTask(); }
        biMode = SLEEP;
        biTask = BuddySDK.Companion.createBICategoryTask(SLEEP, null, null, true);
        biTask.start(newTaskCallback(null));
    }

    // Set the current task to "WakeUp" & start it
    public static void startWakeUpTask(@Nullable Runnable onSuccess) {
        if (biTask != null) { stopCurrentTask(); }
        biMode = WAKE;
        biTask = BuddySDK.Companion.createBICategoryTask(WAKE, null, null, true);
        biTask.start(newTaskCallback(onSuccess));
    }

    // Cancel any current tasks
    public static void stopCurrentTask() {
        Log.d(TAG, String.format("%s Stopping BI (Current state: nullTask=%s, isRunning=%s, biMode=%s)", TAG, (biTask == null), isRunning, biMode));
        if (biTask != null) { biTask.stop(); biTask = null; }
        isRunning = false; biMode = "";
    }

    // =======================================================================
    // Generate new TaskCallbacks
    // =======================================================================
    // ToDo: onCancel might be what happens when you call .stop()... so set the task to null there?
    // ToDo: isSleeping needs to change differently depending on which Task this is a Callback for...
    private static TaskCallback newTaskCallback(@Nullable Runnable onSuccessCb) {
        return new TaskCallback() {
            @Override public void onStarted(        ) { logStarted( ); }
            @Override public void onSuccess(String s) { logSuccess(s); if (onSuccessCb != null) ChatController.mainExecutor().execute(onSuccessCb); }
            @Override public void onCancel (        ) { logCancel ( ); }
            @Override public void onError  (String s) { logError  (s); }
            @Override public void onIntermediateResult(String s) { logInter(s); }
        };
    }

    // Helpers
    private static void logStarted(        ) { Log.d(TAG, String.format("%s %s | onStarted()",                  TAG, biMode   )); isRunning = true;  }
    private static void logSuccess(String s) { Log.i(TAG, String.format("%s %s | onSuccess() -> %s",            TAG, biMode, s)); isRunning = false; }
    private static void logCancel (        ) { Log.i(TAG, String.format("%s %s | onCancel()",                   TAG, biMode   )); isRunning = false; }
    private static void logError  (String s) { Log.e(TAG, String.format("%s %s | onError() -> %s",              TAG, biMode, s)); isRunning = false; }
    private static void logInter  (String s) { Log.d(TAG, String.format("%s %s | onIntermediateResult() -> %s", TAG, biMode, s)); }

}
