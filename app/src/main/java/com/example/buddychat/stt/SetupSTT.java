package com.example.buddychat.stt;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bfr.buddysdk.services.speech.STTTask;
import com.bfr.buddysdk.BuddySDK;

import java.util.Locale;

// ================================================================================
// Setup for Built-in Speech-to-Text
// ================================================================================
// Only called once, from 'BuddySTT', during initialization.
// ToDo: Should maybe show Toast if the task initialization fails...
// ToDo: Add retries where we use Cerence if Google isn't available (?)
public final class SetupSTT {
    private static final String TAG = "[DPU_SetupSTT]";

    // Microphone permissions
    private static final int      REQ_PERM  = 9001;
    private static final String[] MIC_PERMS = { Manifest.permission.RECORD_AUDIO };

    // --------------------------------------------------------------------------------
    // Permission Helpers -- used once during initialization
    // --------------------------------------------------------------------------------
    private static boolean notMicPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
    }

    private static void requestMicPermission(Context context) {
        Log.i(TAG, String.format("%s Requesting microphone permission...", TAG));
        if (!(context instanceof Activity)) return;  // caller must be an Activity
        ActivityCompat.requestPermissions((Activity) context, MIC_PERMS, REQ_PERM);
    }

    public static void checkMicPermission(Context context) {
        if (SetupSTT.notMicPermission(context)) { SetupSTT.requestMicPermission(context); }
    }

    // ================================================================================
    // STT Task Initialization
    // ================================================================================
    // Public choices
    public enum Engine { GOOGLE, CERENCE_FREE, CERENCE_FCF }

    // Parameters
    private static final Locale LOCALE = Locale.ENGLISH;
    private static final Engine ENGINE = Engine.GOOGLE;

    private static String fcf() { return LOCALE == Locale.ENGLISH ? "audio_en.fcf" : "audio_fr.fcf"; }

    // --------------------------------------------------------------------------------
    // Initialization -- Called once in MainActivity.onCreate
    // --------------------------------------------------------------------------------
    // ToDo: Add retry logic to use Cerence if Google fails
    public static STTTask initializeSTTTask(Context context) {
        // Check microphone permission
        if (SetupSTT.notMicPermission(context)) { SetupSTT.requestMicPermission(context); }

        // Initialize empty task object
        STTTask task = null;

        // Guard for BuddyRobot hardware
        try {
            switch (ENGINE) {
                case GOOGLE       : task = BuddySDK.Speech.createGoogleSTTTask(LOCALE); break;
                case CERENCE_FREE : task = BuddySDK.Speech.createCerenceFreeSpeechTask(LOCALE); break;
                case CERENCE_FCF  : task = BuddySDK.Speech.createCerenceTaskFromAssets(LOCALE, fcf(), context.getAssets()); break;
            }

            if (task == null) { return task; }

            // Success, finish initializing
            task.initialize();
            Log.i(TAG, String.format("%s Buddy STT initialised with: %s", TAG, ENGINE));
            return task;
        }

        // Not on a Buddy robot / some other failure
        catch (Throwable t) { Log.w(TAG, String.format("%s Buddy STT unavailable: %s", TAG, t)); return task; }
    }

}
