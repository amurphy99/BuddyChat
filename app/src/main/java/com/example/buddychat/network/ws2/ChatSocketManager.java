package com.example.buddychat.network.ws2;

import android.util.Log;

import okhttp3.*;

import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.BackendURLs;

import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.chat.StatusController;

// ================================================================================
// WebSocket Manager
// ================================================================================
/// Connect, disconnect, send...
/// Refers to a separate ChatListener utility for handling messages
public final class ChatSocketManager {
    private static final String TAG  = "[DPU_ChatSocketManager]";

    // Control these objects as a part of the chat
    private static WebSocket             SOCKET;
    private static final OkHttpClient    CLIENT   = NetworkUtils.CLIENT;  // reused
    private static final ChatUICallbacks LISTENER = new ChatUICallbacks();

    // ================================================================================
    // Connect to the WebSocket (login first)
    // ================================================================================
    public static void connect() {
        // Use hardcoded username/password information to login
        NetworkUtils.login(new NetworkUtils.AuthCallback() {
            // 1) onSuccess of logging in, use the auth token to connect to the WebSocket
            @Override public void onSuccess(String accessToken) {
                // 1a) Build WebSocket address using the access token
                HttpUrl url = BackendURLs.getWebSocketURL(accessToken);
                Log.d(TAG, String.format("%s Connecting to: %s", TAG, url.toString()));

                // 1b) Connect to it and pass the listener
                Request req = new Request.Builder().url(url).build();
                ChatSocketManager.SOCKET = CLIENT.newWebSocket(req, ChatSocketManager.LISTENER);   // async open

                // 1c) Tell StatusController that we succeeded
                StatusController.startSuccess();
            }

            // 2) On login failure, retry or just cancel processes
            @Override public void onError(Throwable t) {
                Log.e(TAG, String.format("%s Login failed", TAG));
                Emotions.setMood("Angry",   3_000L); // ToDo: with no toast, set expression to communicate

                // Tell StatusController that we failed
                StatusController.stop();
            }
        });

    }

    // ================================================================================
    // WebSocket Utility
    // ================================================================================
    /** Send JSON the WebSocket (called from the main thread) */
    public void sendJson(String json) { if (SOCKET != null) SOCKET.send(json); }

    /** Send a string the WebSocket (called from the main thread) (auto-format as JSON) */
    public void sendString(String text) {
        String stringJson = String.format("{\"type\": \"transcription\", \"data\": \"%s\"}", text);
        sendJson(stringJson);
    }

    /** End the chat */
    public void endChat() {
        if (SOCKET != null) {
            SOCKET.send(String.format("{\"type\":\"end_chat\", \"data\":%s}", System.currentTimeMillis()));
            SOCKET.close(1000, "user ended");
            SOCKET = null;
        }
    }

}
