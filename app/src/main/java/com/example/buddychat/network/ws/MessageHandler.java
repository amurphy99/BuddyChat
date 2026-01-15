package com.example.buddychat.network.ws;

import android.util.Log;

import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.utils.behavior.IntentDetector;

import org.json.JSONException;
import org.json.JSONObject;

// ================================================================================
// Handle different types of WS messages
// ================================================================================
public final class MessageHandler {
    private static final String TAG = "[DPU_MessageHandler]";

    // --------------------------------------------------------------------------------
    // Public Message Handler
    // --------------------------------------------------------------------------------
    public static void onMessage(String raw) {
        try {
            // Process the data we received & act accordingly
            JSONObject obj  = new JSONObject(raw);
            String type     = obj.optString("type", "");

            switch (type) {
                case "llm_response" : onLLMResponse(obj); break;
                case "affect"       : onAffect     (obj); break;
                case "expression"   : onExpression (obj); break;
            }

        } catch (JSONException e) { Log.e(TAG, String.format("%s Bad JSON: %s", TAG, e.getMessage())); }
    }

    // --------------------------------------------------------------------------------
    // Individual Message Types
    // --------------------------------------------------------------------------------
    /** Handle "llm_response" data from the backend (an utterance from the LLM). */
    private static void onLLMResponse(JSONObject obj) {
        // Parse and log the message
        final String body = obj.optString("data", "(empty)");
        final String time = obj.optString("time", "");
        Log.i(TAG, String.format("%s %s: %s", TAG, time, body));

        // Return early if the message is empty
        if (body.equals("(empty)")) { return; }

        // Check the LLMs utterance for action cues (e.g. nod yes for "of course", "sure", etc...)
        IntentDetector.IntentDetection(body);

        // Speak the response
        BuddyTTS.speak(body);
    }

    /** Handle "affect" data from the backend (valence+arousal emotion values for the face). */
    private static void onAffect(JSONObject obj) {
        final float valence = (float) obj.optDouble("valence", 0.5);
        final float arousal = (float) obj.optDouble("arousal", 0.5);
        Emotions.setMood("NEUTRAL"); // Buddy's expression must be "NEUTRAL" for these values
        Emotions.setPositivityEnergy(valence, arousal);
    }

    /** Handle "expression" data */
    private static void onExpression(JSONObject obj) {
        final String rawExpression = obj.optString("expression", "NEUTRAL");
        Emotions.setMood(rawExpression, 1_000L);
    }

}
