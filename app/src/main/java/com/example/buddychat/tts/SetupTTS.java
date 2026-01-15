package com.example.buddychat.tts;

import android.util.Log;

import com.bfr.buddysdk.BuddySDK;

// ================================================================================
// Setup / Utility for the BuddySDK speech module
// ================================================================================
public final class SetupTTS {
    private static final String TAG = "[DPU_SetupTTS]";

    // --------------------------------------------------------------------------------
    // This loads the speech module from the SDK
    // --------------------------------------------------------------------------------
    // We are just going to do this once in onCreate to make sure the speaker is set
    public static void loadTTS() {
        BuddySDK.Speech.loadReadSpeaker();
        BuddySDK.Speech.setSpeakerVoice("kate");
        Log.d(TAG, String.format("%s ReadSpeaker loaded, speaker = kate", TAG));
    }

    // --------------------------------------------------------------------------------
    // Ready checks -- If the SDK class itself is missing, or the service isn't bound yet, return false
    // --------------------------------------------------------------------------------
    /** Check if the class is present (it won't be on simulators, only on the BuddyRobot itself). Need to wrap it in try-except to avoid crashing locally. */
    public static boolean isReady() {
        try                         { return BuddySDK.Speech != null && BuddySDK.Speech.isReadyToSpeak(); }
        catch (RuntimeException ex) { Log.w(TAG, String.format("%s BuddyTTS not ready: %s", TAG, ex.getMessage())); return false; }
    }
    public static boolean notReady() { return !isReady(); }


    // --------------------------------------------------------------------------------
    // Settings for the speech module -- not using right now
    // --------------------------------------------------------------------------------
    public static void setPitch (int pitch ) { BuddySDK.Speech.setSpeakerPitch (pitch ); }
    public static void setSpeed (int speed ) { BuddySDK.Speech.setSpeakerSpeed (speed ); }
    public static void setVolume(int volume) { BuddySDK.Speech.setSpeakerVolume(volume); }
}
