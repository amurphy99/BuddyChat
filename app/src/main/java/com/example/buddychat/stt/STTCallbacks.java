package com.example.buddychat.stt;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

// App code
import com.bfr.buddy.ui.shared.FacialExpression;
import com.example.buddychat.utils.Emotions;
import com.example.buddychat.utils.audio_triangulation.AudioTracking;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets;
import com.example.buddychat.utils.audio_triangulation.AngleBuckets.Bucket;

// =======================================================================
// Handles Recognized Speech Events
// =======================================================================
// ToDo: There are going to be some temporary elements here
public class STTCallbacks implements STTListener {
    private static final String TAG  = "[DPU_STTCallback]";

    // UI references that will be modified
    private final TextView          sttView;
    private final TextView          testView1;
    private final UtteranceCallback utteranceCallback;

    // Handler to hop onto UI thread.
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Initialization
    public STTCallbacks(TextView sttView, TextView testView1, UtteranceCallback utteranceCallback) {
        this.sttView = sttView;
        this.testView1 = testView1;
        this.utteranceCallback = utteranceCallback;
    }

    // -----------------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------------
    @SuppressLint("DefaultLocale")
    @Override public void onText(String utterance, float confidence, String rule) {
        // Set Buddy's face to "THINKING" while we wait for the backend
        Emotions.setMood(FacialExpression.THINKING);

        // ToDo: Audio triangulation
        float  averageAngle = AudioTracking.getRecentAngle();
        Bucket bucket       = AngleBuckets.classify(averageAngle);
        String angleLabel   = AngleBuckets.label(bucket);
        String logMsg = String.format("Audio Source: %s (angle=%.1f)", angleLabel, averageAngle);

        Log.i(TAG, String.format("%s Recent Average %s", TAG, logMsg));


        // Do some stuff on the UI thread
        ui.post(() -> {
            // Send the message over the WebSocket
            utteranceCallback.sendString(utterance);

            // Logging the message
            Log.i(TAG, String.format("%s Utt: %s (conf: %.3f, rule: %s)", TAG, utterance, confidence, rule));
            sttView  .setText(String.format("User (%.3f): %s", (confidence/1_000), utterance));

            testView1.setText(logMsg);
        });
    }

    @Override public void onError(String e) { Log.e(TAG,  " error: " + e); }

}
