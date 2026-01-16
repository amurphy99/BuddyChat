package com.example.buddychat.network.ws;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.*;
import org.json.JSONObject;

import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.BackendURLs;

import com.example.buddychat.network.api.TokenManager;

import com.example.buddychat.chat.StatusController;

// ================================================================================
// WebSocket Manager
// ================================================================================
/// Connect, disconnect, send...
/// Refers to a separate ChatListener utility for handling messages
public final class ChatSocketManager {
    private static final String TAG  = "[DPU_ChatSocketManager]";

    // The single active connection
    private static WebSocket SOCKET;
    private static final OkHttpClient CLIENT = NetworkUtils.CLIENT;

    // We keep track of the listener so we can notify it of logic changes if needed
    private static ChatUICallbacks listenerInstance;

    // ================================================================================
    // Connect to the WebSocket
    // ================================================================================
    public static void connect() { connect(3); }

    private static void connect(final int retriesRemaining) {
        final String authToken = TokenManager.getAccessToken();

        // 1. Safety Check: Don't try to connect if we don't have a token yet
        if (authToken == null || authToken.isEmpty()) {
            Log.e(TAG, String.format("%s Cannot connect: Token is missing. Retrying login...", TAG));
            TokenManager.initialLogin(()->{}); // trigger a login instead
            return;
        }

        // 2. Build WebSocket address using the access token
        HttpUrl url = BackendURLs.getWebSocketURL(authToken);
        Log.d(TAG, String.format("%s Connecting to: %s (Retries left: %d)", TAG, url, retriesRemaining));

        // 3. Create the Request
        Request req = new Request.Builder().url(url).build();

        // 4. Instantiate the Listener with the retry count
        // We pass 'retriesRemaining' to the listener so it knows if it should retry on failure
        listenerInstance = new ChatUICallbacks(retriesRemaining);

        // 5. Open the connection
        // Note: We do NOT call StatusController.startSuccess() here.
        // We let listenerInstance.onOpen() do that when it actually connects.
        SOCKET = CLIENT.newWebSocket(req, listenerInstance);
    }

    // --------------------------------------------------------------------------------
    // Retry the connection
    // --------------------------------------------------------------------------------
    // Helper to trigger a retry from the Listener
    public static void triggerRetry(final int retriesLeft) {
        if (retriesLeft > 0) {
            Log.d(TAG, "Retrying connection in 3 seconds...");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { connect(retriesLeft - 1); }
            }, 3000);
        } else {
            Log.e(TAG, "All connection retries failed.");
            StatusController.showError("Connection Failed");
            StatusController.stop();
        }
    }

    // ================================================================================
    // WebSocket Utility
    // ================================================================================
    /** Send JSON to the WebSocket safely */
    public static void sendJson(String json) {
        if (SOCKET != null) { SOCKET.send(json); }
        else { Log.w(TAG, "Attempted to send message, but Socket is null."); }
    }

    /** Send a string (automatically escapes quotes/special chars) */
    public static void sendString(String text) {
        // Use JSONObject to safely format the string
        try {
            JSONObject json = new JSONObject();
            json.put("type", "transcription");
            json.put("data", text);
            sendJson(json.toString());
        } catch (Exception e) { Log.e(TAG, "Failed to format JSON", e); }
    }

    /** End the chat and clean up variables (sends a logic message first). */
    public static void endChat() {
        if (SOCKET != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "end_chat");
                json.put("data", System.currentTimeMillis());
                SOCKET.send(json.toString());

            } catch (Exception ignored) { }

            // Close code 1000 = Normal Closure
            SOCKET.close(1000, "user ended"); SOCKET = null;
        }
    }

}
