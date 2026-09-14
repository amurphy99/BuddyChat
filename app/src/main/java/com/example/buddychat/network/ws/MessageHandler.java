package com.example.buddychat.network.ws;

import android.util.Log;

import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.utils.behavior.BehaviorTasks;
import com.example.buddychat.utils.behavior.Emotions;

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
            JSONObject obj = new JSONObject(raw);
            Object rawType = obj.opt("type");
            if (!(rawType instanceof String)) { Log.w(TAG, "Ignoring WebSocket message without a string type."); return; }

            // Move through the different communication types
            switch ((String) rawType) {
                case "robot_action":     onRobotAction    (obj); break;
                case "llm_response":     onLLMResponse    (obj); break;
                case "message":          onTranscript     (obj); break;
                case "command_ack":      onCommandAck     (obj); break;
                case "stream_status":    onStreamStatus   (obj); break;
                case "recording_status": onRecordingStatus(obj); break;
                case "chat_ended":       onChatEnded      (   ); break;
                default:
                    Log.d(TAG, String.format("%s Ignoring unsupported message type: %s", TAG, rawType));
            }

        } catch (JSONException e) { Log.e(TAG, String.format("%s Bad JSON: %s", TAG, e.getMessage())); }
    }

    // --------------------------------------------------------------------------------
    // Individual Message Types
    // --------------------------------------------------------------------------------
    /** Handle "llm_response" data from the backend (an utterance from the LLM). */
    private static void onLLMResponse(JSONObject obj) {
        final Object rawBody = obj.opt("data");
        if (!(rawBody instanceof String) || ((String) rawBody).trim().isEmpty()) { 
            Log.w(TAG, "Ignoring llm_response without non-empty string data.");
            return;
        }

        final String body = (String) rawBody;
        final String time = obj.optString("time", "");
        final Object responseId = obj.opt("responseId");
        Log.i(TAG, String.format("%s %s (responseId=%s): %s", TAG, time, responseId, body));

        // The matching "message" frame is transcript-only. Speak exactly once, here.
        BuddyTTS.speak(body);
    }

    // --------------------------------------------------------------------------------
    // [onRobotAction] Handle the uniform emotion/animation action envelope
    // --------------------------------------------------------------------------------
    private static void onRobotAction(JSONObject obj) {
        final JSONObject data = obj.optJSONObject("data");
        if (data == null) { Log.w(TAG, "Ignoring robot_action without object data."); return; }

        final Object rawKind  = data.opt("kind" );
        final Object rawValue = data.opt("value");
        if (!(rawKind instanceof String) || !(rawValue instanceof String)
                || ((String) rawValue).trim().isEmpty()) {
            Log.w(TAG, "Ignoring malformed robot_action.");
            return;
        }

        final String kind = ((String) rawKind).trim();
        final String value = ((String) rawValue).trim();
        final long durationMs = data.optLong("duration_ms", 3_000L);
        Log.i(TAG, String.format("%s robot_action: %s=%s (%d ms)", TAG, kind, value, durationMs));

        // Emotions maps labels case-insensitively and falls back to NEUTRAL
        // Animation values are Buddy-owned BI category names, so play them as sent.
        if      ("emotion"  .equals(kind)) { Emotions.setMood(value, durationMs); }
        else if ("animation".equals(kind)) { BehaviorTasks.startAnimation(value); }
        else { Log.w(TAG, String.format("%s Ignoring unknown robot_action kind: %s", TAG, kind)); }
    }

    // --------------------------------------------------------------------------------
    // [onTranscript] Backend sends copies of the full transcript for display ONLY
    // --------------------------------------------------------------------------------
    // This format never gets spoken, otherwise assistant text would be duplicated.
    private static void onTranscript(JSONObject obj) {
        final JSONObject data = obj.optJSONObject("data");
        if (data == null) { Log.w(TAG, "Ignoring message without object data."); return; }
        Log.d(TAG, String.format("%s Transcript message received for role=%s", TAG, data.optString("role", "unknown")));
    }

    // --------------------------------------------------------------------------------
    // [onCommandAck] Receives command acknowledgements from the backend
    // --------------------------------------------------------------------------------
    /** Buddy currently sends no commands, but we still parse and log any command failures safely. */
    private static void onCommandAck(JSONObject obj) {
        final JSONObject data = obj.optJSONObject("data");
        if (data == null) { Log.w(TAG, "Ignoring command_ack without object data."); return; }

        final String id   = data.optString("id", "unknown");
        final String name = data.isNull("name") ? "unknown" : data.optString("name", "unknown");

        if (data.optBoolean("ok", false)) {
            Log.d(TAG, String.format("%s Command accepted: %s (id=%s), state=%s",
                    TAG, name, id, data.optJSONObject("state")));
        } else {
            Log.w(TAG, String.format("%s Command rejected: %s (id=%s): %s, state=%s",
                    TAG, name, id, data.optString("message", "unknown reason"),
                    data.optJSONObject("state")));
        }
    }

    // --------------------------------------------------------------------------------
    // [onStreamStatus] This would be for if the backend paused the stream
    // --------------------------------------------------------------------------------
    /** Buddy does not currently support this, but we would pause via this if it was... */
    private static void onStreamStatus(JSONObject obj) {
        final JSONObject data = obj.optJSONObject("data");
        Log.d(TAG, String.format("%s Backend stream status: %s", TAG, data == null ? "unknown" : data.optString("status", "unknown")));
    }

    // --------------------------------------------------------------------------------
    // [onRecordingStatus] Backend admin has enabled/disabled audio saving
    // --------------------------------------------------------------------------------
    /** Buddy does not currently support audio streaming anyways, so this doesn't apply yet. */
    private static void onRecordingStatus(JSONObject obj) {
        final JSONObject data = obj.optJSONObject("data");
        Log.d(TAG, String.format("%s Backend recording enabled: %s", TAG,
                data == null ? "unknown" : data.opt("enabled")));
    }

    // --------------------------------------------------------------------------------
    // [onChatEnded] Chat was ended from the backend
    // --------------------------------------------------------------------------------
    /** The goodbye llm_response is sent first. The backend closes normally after this frame. */
    private static void onChatEnded() {
        Log.i(TAG, "Backend reported that the chat has ended.");
    }
}

