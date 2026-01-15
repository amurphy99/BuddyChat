package com.example.buddychat.stt;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

// App code
import com.bfr.buddy.ui.shared.FacialExpression;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.utils.behavior.UserIntent;
import com.example.buddychat.utils.audio_triangulation.AudioTracking;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets.Bucket;

import com.example.buddychat.chat.ChatStatus;
import com.example.buddychat.chat.ChatController;

// ================================================================================
// Handles Recognized Speech Events
// ================================================================================
public final class STTCallbacks  {
    private static final String TAG  = "[DPU_STTCallbacks]";

    // Handler to hop onto UI thread
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Initialization
    private final UtteranceCallback utteranceCallback;
    public STTCallbacks(UtteranceCallback utteranceCallback) { this.utteranceCallback = utteranceCallback; }

    // --------------------------------------------------------------------------------
    // Methods
    // --------------------------------------------------------------------------------
    public void onError(String e) { Log.e(TAG, String.format("%s error: %s", TAG, e)); }

    @SuppressLint("DefaultLocale")
    public void onText(String utterance, float confidence, String rule) {
        // Operations that need to happen on detection of a user utterance
        final boolean chatEnded   = onUserUtterance(utterance);
        final boolean awakeStatus = ChatStatus.isRunning();

        // Do some stuff on the UI thread
        ui.post(() -> {
            // Send the message over the WebSocket
            utteranceCallback.sendString(utterance);

            // ToDo: IDK how this interaction is going to work at all
            if (chatEnded && awakeStatus) { ChatController.end(); }

            // Logging the message
            Log.i(TAG, String.format("%s Utt: %s (conf: %.3f, rule: %s)", TAG, utterance, confidence, rule));
        });
    }


    // --------------------------------------------------------------------------------
    // Other Helpers
    // --------------------------------------------------------------------------------
    /** Method that runs when a user's utterance is detected. */
    private boolean onUserUtterance(String utterance) {
        // Set Buddy's face to "THINKING" while we wait for the backend
        Emotions.setMood(FacialExpression.THINKING);

        // Audio Triangulation -- ToDo: All audio tracking disabled for the demo
        //final float  averageAngle = AudioTracking.getRecentAngle();
        //final Bucket bucket       = AngleBuckets.classify(averageAngle);
        //final String angleLabel   = AngleBuckets.label(bucket);
        //final String audLog = String.format("(AudioLoc=%s, Angle=%.1f)", angleLabel, averageAngle);

        // User intent detection (for ending the chat) -- ToDo: Here is where I think we would actually end the chat
        final boolean userEndChat = UserIntent.isEndChat(utterance);
        final String userLog = String.format("EndChat=%s", userEndChat);

        // Log and return -- ToDo: Normally we would combine the audio string with the user intent string...
        Log.d(TAG, String.format("%s onUserUtt: %s", TAG, userLog));

        return userEndChat;
    }

}
