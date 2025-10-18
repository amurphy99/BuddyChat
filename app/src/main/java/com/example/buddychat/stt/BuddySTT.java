package com.example.buddychat.stt;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import com.bfr.buddy.speech.shared.ISTTCallback;
import com.bfr.buddy.speech.shared.STTResult;
import com.bfr.buddy.speech.shared.STTResultsData;
import com.bfr.buddysdk.services.speech.STTTask;
import com.bfr.buddysdk.BuddySDK;

import com.example.buddychat.chat.ChatHub;
import com.example.buddychat.chat.ChatStatusListener;

// ================================================================================
// Wrapper class around BuddySDK.Speech for Speech-to-Text
// ================================================================================
// On initialization we call SetupSTT to get an STTTask object from the SDK that we can use.
// Start, pause, and stop use that task as expected.
public final class BuddySTT {
    private static final String TAG = "[DPU_BuddySTT]";
    private BuddySTT() {} // Static-only class

    private static final boolean LISTEN_CONTINUOUS = true;

    private static STTTask task;
    private static STTCallbacks sttCallbacks;

    // --------------------------------------------------------------------------------
    // Initialization -- Called once in MainActivity.onCreate
    // --------------------------------------------------------------------------------
    /** Sets up microphone permissions, STTCallbacks, and the BuddySDK STTTask. */
    public static void init(Context context, STTCallbacks callbacks) {
        SetupSTT.checkMicPermission(context);
        sttCallbacks = callbacks;
        task = SetupSTT.initializeSTTTask(context);
    }

    // Check if the task (1) was initialized and (2) if the task is ready
    private static boolean ready() { if (task == null) { return false; } return task.isRunning(); }

    // --------------------------------------------------------------------------------
    // Speech-to-Text Usage
    // --------------------------------------------------------------------------------
    public  static void    pause() { if (ready()) task.pause(); Log.d(TAG, String.format("%s STT paused",  TAG)); }
    private static void    stop () { if (ready()) task.stop (); Log.d(TAG, String.format("%s STT stopped", TAG)); }
    private static boolean start() {
        if (!ready()) { Log.e(TAG, String.format("%s STT start FAILURE (not available)", TAG)); return false; }

        // Start the STTTask using the callbacks object we were initialized with
        task.start(LISTEN_CONTINUOUS, new ISTTCallback.Stub() {
            @Override public void onSuccess(STTResultsData res) {
                if (!res.getResults().isEmpty()) {
                    STTResult r = res.getResults().get(0);
                    sttCallbacks.onText(r.getUtterance(), r.getConfidence(), r.getRule());
                }
            }
            @Override public void onError(String e) { sttCallbacks.onError(e); }
        });

        // Return that our startup was a success
        Log.d(TAG, String.format("%s STT start SUCCESS", TAG));
        return true;
    }


    // ================================================================================
    // Link to ChatHub
    // ================================================================================
    // set by ChatHub.onStart(cancel). When cancel.get() == true, we should not speak.
    private static volatile AtomicBoolean cancelRef = null;

    // Listener Adapter
    public static void registerWithHub(ChatHub hub) { hub.addListener(LISTENER); }
    private static final ChatStatusListener LISTENER = new ChatStatusListener() {
        @Override public boolean onStart(AtomicBoolean cancel) { cancelRef = cancel; return start(); }
        @Override public void    onStop () { stop(); cancelRef = null; }
    };

}
