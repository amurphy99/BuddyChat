package com.example.buddychat.stt;

import android.annotation.SuppressLint;
import android.util.Log;

// App code
import com.bfr.buddy.ui.shared.FacialExpression;
import com.example.buddychat.chat.StatusController;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.utils.behavior.UserIntent;
import com.example.buddychat.utils.audio_triangulation.AudioTracking;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets.Bucket;

// ================================================================================
// Handles Recognized Speech Events
// ================================================================================
public final class STTCallbacks  {
    private static final String TAG  = "[DPU_STTCallbacks]";

    // Initialization
    private final UtteranceCallback utteranceCallback;
    public STTCallbacks(UtteranceCallback utteranceCallback) { this.utteranceCallback = utteranceCallback; }

    // --------------------------------------------------------------------------------
    // Methods
    // --------------------------------------------------------------------------------
    public void onError(String e) { Log.e(TAG, String.format("%s error: %s", TAG, e)); }

    public void onText(String utterance, float confidence, String rule) {
        Log.i(TAG, String.format("%s Utt: %s (conf: %.3f, rule: %s)", TAG, utterance, confidence, rule));
        BuddySTT.pause(); // ToDo: Testing out pausing/resuming STT to avoid double messages

        // 1. Operations that need to happen on detection of a user utterance
        final boolean chatEnded = onUserUtterance(utterance);

        // 2. If the robot isn't officially "Active", ignore the speech.
        if (!StatusController.isActive()) {
            Log.w(TAG, String.format("%s Ignored speech (Chat is not active)", TAG));
            return;
        }

        // 3. Did the user ask to end the chat?
        if (chatEnded) {
            Log.w(TAG, String.format("%s User intent detected: END CHAT", TAG));
            StatusController.stop(); // StatusController handles the shutdown
            return;
        }

        // 4. Send the message over the WebSocket
        utteranceCallback.sendString(utterance);
    }


    // --------------------------------------------------------------------------------
    // Check for flags indicating we need to do stuff (emotions, ending the chat, etc.)
    // --------------------------------------------------------------------------------
    /** Method that runs when a user's utterance is detected. */
    private boolean onUserUtterance(String utterance) {
        // Set Buddy's face to "THINKING" while we wait for the backend
        Emotions.setMood(FacialExpression.THINKING);

        // Audio Triangulation
        //final String audLog = audioTriangulation();

        // User intent detection (for ending the chat)
        final boolean userEndChat = UserIntent.isEndChat(utterance);
        final String userLog = String.format("EndChat=%s", userEndChat);

        // Log and return -- ToDo: Normally we would combine the audio string with the user intent string...
        Log.d(TAG, String.format("%s onUserUtt: %s", TAG, userLog));

        return userEndChat;
    }


    // ToDo: All audio tracking disabled for the demo
    @SuppressLint("DefaultLocale") // This isn't needed if you just log the string right away
    private String audioTriangulation() {
        final float  averageAngle = AudioTracking.getRecentAngle();
        final Bucket bucket       = AngleBuckets.classify(averageAngle);
        final String angleLabel   = AngleBuckets.label(bucket);
        return String.format("(AudioLoc=%s, Angle=%.1f)", angleLabel, averageAngle);

    }



}
