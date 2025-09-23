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

    // Running state
    public static volatile boolean isRunning = false;

    // Callback for our task (might sometimes have "onIntermediateResult" if specified in task creation)
    // ToDo: onCancel might be what happens when you call .stop()... so set the task to null there?
    // ToDo: isSleeping needs to change differently depending on which Task this is a Callback for...
    private static final TaskCallback biTaskCb = new TaskCallback() {
        @Override public void onStarted(        ) { Log.d(TAG, String.format("%s onStarted()",       TAG   )); isRunning = true;  }
        @Override public void onSuccess(String s) { Log.i(TAG, String.format("%s onSuccess() -> %s", TAG, s)); isRunning = false; }
        @Override public void onCancel (        ) { Log.i(TAG, String.format("%s onCancel()",        TAG   )); isRunning = false; }
        @Override public void onError  (String s) { Log.e(TAG, String.format("%s onError() -> %s",   TAG, s)); isRunning = false; }
        @Override public void onIntermediateResult(String s) { Log.d(TAG, String.format("%s onIntermediateResult() -> %s", TAG, s)); }
    };

    // Task pointer - ToDo: Would it be better to keep two tasks (one for Sleep, one for WakeUp) instead of re-creating them?
    private static Task biTask = null;

    // -----------------------------------------------------------------------
    // Initialize / Start the "SLEEP" Task
    // -----------------------------------------------------------------------
    // Set the current task to "Sleep" & start it
    public static void startSleepTask() {
        if (biTask != null) { biTask.stop(); }
        biTask = BuddySDK.Companion.createBICategoryTask("Sleep");
        biTask.start(biTaskCb);
    }

    // Set the current task to "WakeUp" & start it
    public static void startWakeUpTask() {
        if (biTask != null) { biTask.stop(); }
        biTask = BuddySDK.Companion.createBICategoryTask("WakeUp");
        biTask.start(biTaskCb);
    }

    // Cancel any current tasks
    public static void stopCurrentTask() {
        if (biTask != null) { biTask.stop(); biTask = null; }
        isRunning = false;
    }

}
