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

// ====================================================================
// Wrapper class around BuddySDK.Speech for Text-to-Speech
// ====================================================================
public final class BuddyTTS {
    private static final String TAG = "[DPU_BuddyTTS]";
    private BuddyTTS() {} // static-only class

    private static boolean enabled = false;

    // set by ChatHub.onStart(cancel). When cancel.get() == true, we should not speak.
    private static volatile AtomicBoolean cancelRef = null;

    // --------------------------------------------------------------------
    // Initialization & Utility
    // --------------------------------------------------------------------
    public static void start() { SetupTTS.loadTTS(); }
    public static void stop () { try { BuddySDK.Speech.stopSpeaking(); } catch (Throwable ignored) {} }

    // ToDo: Basically we need to de-couple this from toggle
    // ToDo: And this first message for hello how are you goes in the ChatHub; when all services start successfully, then we say this...
    public static void startTTS() {

    }

    /** Stop current utterance if there is one */
    public static void toggle() {
        if (enabled && BuddySDK.Speech.isSpeaking()) { stop(); }
        enabled = !enabled;
        Log.w(TAG, enabled ? "TTS Enabled." : "TTS Disabled");
        if (enabled) {speak("Hello, how are you today?");}
    }

    // ====================================================================
    // Text-to-Speech
    // ====================================================================
    // if AudioTracking ever comes back, it needs to be disabled while buddy speaks...
    public static void speak(String text) {
        if (SetupTTS.notReady()) { return; } // Ready checks

        // ToDo: "THINKING" while waiting for backend, then "NEUTRAL" to speak. If we receive emotions from the backend this will have to change...
        //Emotions.setMood(FacialExpression.NEUTRAL);

        // Use BuddySDK Speech
        BuddySDK.Speech.startSpeaking(text, new ITTSCallback.Stub() {
            @Override public void onSuccess(String s) { speechCompleted(s); }
            @Override public void onPause  ()         { }
            @Override public void onResume ()         { }
            @Override public void onError  (String s) { speechCompleted(s); }
        });
    }

    // --------------------------------------------------------------------
    // Overload for use only at the end of a chat
    // --------------------------------------------------------------------
    // ToDo: This code could be combined with the above, but the only difference is the SPEAK_HAPPY thing
    public static void speak(String text, @Nullable Runnable onSuccessCb) {
        if (SetupTTS.notReady()) { return; } // Ready checks
        BuddySDK.Speech.startSpeaking(text, LabialExpression.SPEAK_HAPPY, new ITTSCallback.Stub() {
            @Override public void onSuccess(String s) { speechCompleted(s); runCb(onSuccessCb); }
            @Override public void onPause  ()         { }
            @Override public void onResume ()         { }
            @Override public void onError  (String s) { speechCompleted(s); runCb(onSuccessCb); }
        });
    }

    // --------------------------------------------------------------------
    // Shared Helpers
    // --------------------------------------------------------------------
    private static void speechCompleted(String s) {
        Log.d(TAG, String.format("%s TTS Speech completed: %s", TAG, s));
    }

    private static void runCb(@Nullable Runnable cb) {
        if (cb != null) ChatController.mainExecutor().execute(cb);
    }

    // ====================================================================
    // Link to CHatHub
    // ====================================================================
    public static void registerWithHub(ChatHub hub) { hub.addListener(LISTENER); }

    // Listener adapter
    // ToDo: This isn't exactly right yet, start is different
    // ToDo: How would I make it so that buddy greeting people is the very last thing it does?
    // ToDo: I guess I would put that in ChatHub...
    private static final ChatHub.Listener LISTENER = new ChatHub.Listener() {
        @Override public boolean onStart(AtomicBoolean cancel) {
            cancelRef = cancel; enabled = true; start();
            return true;
        }

        // Cooperative cancel has already been set in ChatHub
        @Override public void onStop() { enabled = false; stop(); cancelRef = null; }
    };

}
