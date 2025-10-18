package com.example.buddychat.tts;

import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;
import androidx.annotation.Nullable;

import com.bfr.buddy.speech.shared.ITTSCallback;
import com.bfr.buddy.ui.shared.FacialExpression;
import com.bfr.buddy.ui.shared.LabialExpression;
import com.bfr.buddysdk.BuddySDK;

import com.example.buddychat.chat.ChatController;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.chat.ChatHub;
import com.example.buddychat.chat.ChatStatusListener;

// ================================================================================
// Wrapper class around BuddySDK.Speech for Text-to-Speech
// ================================================================================
// SetupTTS makes sure everything is loaded on app start
// ToDo: I might not actually care about 'cancelRef' here...
public final class BuddyTTS {
    private static final String TAG = "[DPU_BuddyTTS]";
    private BuddyTTS() {} // static-only class

    // Control
    public static boolean start() { return SetupTTS.isReady(); }
    public static void    stop () { try { BuddySDK.Speech.stopSpeaking(); } catch (Throwable ignored) {} }

    // ================================================================================
    // Text-to-Speech (you have the option to do different
    // ================================================================================
    public static void speak(String text, @Nullable LabialExpression iExpression, @Nullable Runnable onSuccessCb) {
        if (SetupTTS.notReady()) { return; } // Ready checks
        if (iExpression == null) { iExpression = LabialExpression.SPEAK_NEUTRAL; }

        BuddySDK.Speech.startSpeaking(text, iExpression, new ITTSCallback.Stub() {
            @Override public void onSuccess(String s) { speechCompleted(s); runCb(onSuccessCb); }
            @Override public void onPause  ()         { }
            @Override public void onResume ()         { }
            @Override public void onError  (String s) { speechCompleted(s); runCb(onSuccessCb); }
        });
    }

    // Overloads for 'speak' (don't require all arguments)
    public static void speak(String text) { speak(text, LabialExpression.SPEAK_NEUTRAL, null); }
    public static void speak(String text, @Nullable Runnable onSuccessCb) { speak(text, LabialExpression.SPEAK_NEUTRAL, onSuccessCb); }

    // Shared Helpers (log on speech completion & execute a callback on success)
    private static void speechCompleted(String s) { Log.d(TAG, String.format("%s TTS Speech completed: %s", TAG, s)); }
    private static void runCb(@Nullable Runnable cb) { if (cb != null) ChatController.mainExecutor().execute(cb); }


    // ================================================================================
    // Link to ChatHub
    // ================================================================================
    // set by ChatHub.onStart(cancel). When cancel.get() == true, we should not speak.
    private static volatile AtomicBoolean cancelRef = null;

    // Listener Adapter
    public static void registerWithHub(ChatHub hub) { hub.addListener(LISTENER); }
    private static final ChatStatusListener LISTENER = new ChatStatusListener() {
        @Override public boolean onStart(AtomicBoolean cancel) { cancelRef = cancel; return start(); }
        @Override public void    onStop (                    ) { stop(); cancelRef = null; }
    };

}
