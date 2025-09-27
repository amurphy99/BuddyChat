package com.example.buddychat.utils.behavior;

import android.util.Log;

// Buddy SDK
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.services.companion.Task;
import com.bfr.buddysdk.services.companion.TaskCallback;

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

    // Callback for our task (might sometimes have "onIntermediateResult" if specified in task creation)
    // ToDo: onCancel might be what happens when you call .stop()... so set the task to null there?
    // ToDo: isSleeping needs to change differently depending on which Task this is a Callback for...
    private static final TaskCallback biTaskCb = new TaskCallback() {
        @Override public void onStarted(        ) { Log.d(TAG, String.format("%s %s | onStarted()",       TAG, biMode   )); isRunning = true;  }
        @Override public void onSuccess(String s) { Log.i(TAG, String.format("%s %s | onSuccess() -> %s", TAG, biMode, s)); isRunning = false; }
        @Override public void onCancel (        ) { Log.i(TAG, String.format("%s %s | onCancel()",        TAG, biMode   )); isRunning = false; }
        @Override public void onError  (String s) { Log.e(TAG, String.format("%s %s | onError() -> %s",   TAG, biMode, s)); isRunning = false; }
        @Override public void onIntermediateResult(String s) { Log.d(TAG, String.format("%s %s | onIntermediateResult() -> %s", TAG, biMode, s)); }
    };

    // Task pointer - ToDo: Would it be better to keep two tasks (one for Sleep, one for WakeUp) instead of re-creating them?
    private static Task biTask = null;

    // -----------------------------------------------------------------------
    // Initialize / Start the "SLEEP" Task
    // -----------------------------------------------------------------------
    // ToDo: Replace these with a single function that takes an argument: "Sleep" | "WakeUp"
    // Set the current task to "Sleep" & start it
    public static void startSleepTask() {
        if (biTask != null) { biTask.stop(); }
        biMode = SLEEP;
        biTask = BuddySDK.Companion.createBICategoryTask(SLEEP, null, null, true);
        biTask.start(biTaskCb);
    }

    // Set the current task to "WakeUp" & start it
    public static void startWakeUpTask() {
        if (biTask != null) { biTask.stop(); }
        biMode = WAKE;
        biTask = BuddySDK.Companion.createBICategoryTask(WAKE, null, null, true);
        biTask.start(biTaskCb);
    }

    // Cancel any current tasks
    public static void stopCurrentTask() {
        if (biTask != null) { biTask.stop(); biTask = null; }
        isRunning = false; biMode = "";
    }

    // Toggle Sleep/WakeUp
    public static void toggleSleepWakeUp() {
        Log.i(TAG, String.format("%s Toggling BI (Current state: nullTask=%s, isRunning=%s, biMode=%s)", TAG, (biTask == null), isRunning, biMode));
        if ((biMode.equals("Wake" )) || (biTask == null) || (biMode.isEmpty())) { startSleepTask(); return; }
        if ( biMode.equals("Sleep")) { startWakeUpTask(); }
    }

}
