package com.example.buddychat.network.ws;

import com.example.buddychat.BuildConfig;

import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import okhttp3.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.buddychat.chat.ChatHub;
import com.example.buddychat.chat.ChatStatusListener;
import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.BackendURLs;

// ================================================================================
// WebSocket Manager
// ================================================================================
/// Connect, disconnect, send...
/// Refers to a separate ChatListener utility for handling messages
public class ChatSocketManager {
    private static final String TAG  = "[DPU_ChatWS]";
    private static final OkHttpClient CLIENT = NetworkUtils.CLIENT;  // reused

    private WebSocket    socket;
    private ChatUiCallbacks listener;



    // ToDo: Right now I am still going to use an init for the UI stuff, but I need to move away from that
    public ChatSocketManager(TextView statusView, Button startEndBtn, Consumer<Boolean> runningStateSink) {
        listener = new ChatUiCallbacks(statusView, startEndBtn, runningStateSink);
    }









    // ================================================================================
    // Connect to the WebSocket (login first)
    // ================================================================================
    /** Connect to the WebSocket (login first) <br>
     *
     * Login
     *   onSuccess => connect to WS  (the onSuccess/onFailure functionality is already in this object)
     *      onSuccess => return True
     *      onFailure => return False
     *   onFailure => return False
     */
    public boolean connect_2() {
        // Use hardcoded username/password information to login
        NetworkUtils.login(new NetworkUtils.AuthCallback() {
            // --------------------------------------------------------------------------------
            // onSuccess of logging in, use the auth token to connect to the WebSocket
            // --------------------------------------------------------------------------------
            @Override public void onSuccess(String accessToken) {
                // Build WebSocket address using the access token
                HttpUrl url = BackendURLs.getWebSocketURL(accessToken);
                Log.d(TAG, String.format("%s Connecting to: %s", TAG, url.toString()));

                // Connect to it and pass the listener
                Request req = new Request.Builder().url(url).build();
                socket = CLIENT.newWebSocket(req, ChatSocketManager.this.listener);   // async open
            }

            // --------------------------------------------------------------------------------
            // On login failure, retry or just cancel processes
            // --------------------------------------------------------------------------------
            @Override public void onError(Throwable t) {
                Log.e(TAG, String.format("%s Login failed", TAG));
            }
        });

        return true;
    }



    public void disconnect() { }



    // --------------------------------------------------------------------------------
    // Socket control functions
    // --------------------------------------------------------------------------------
    public void connect(@NonNull String accessToken) {
        this.endChat();

        // URL of the WebSocket server
        HttpUrl url = BackendURLs.getWebSocketURL(accessToken);

        // Connect
        Log.d("WS", String.format("Connecting to: %s", url.toString()));
        Request req = new Request.Builder().url(url).build();
        socket = CLIENT.newWebSocket(req, this.listener);   // async open
    }


    // --------------------------------------------------------------------------------
    // Connect override for when you don't have a token
    // --------------------------------------------------------------------------------
    // ToDo: Need to somehow make this async return true or false so that ChatHub knows if we were
    // ToDo: Actually successful or not.
    // ToDo: Make new separate code for the connection so we have access to a onSuccess or onFailure for connecting only
    public CompletableFuture<Boolean> connect() {
        var result = new CompletableFuture<Boolean>();

        // Try to get a login token
        NetworkUtils.login(new NetworkUtils.AuthCallback() {
            @Override public void onSuccess(String accessToken) {
                // Get the WebSocket address (including the auth token)
                HttpUrl url = BackendURLs.getWebSocketURL(accessToken);
                Log.d("WS", String.format("Connecting to: %s", url.toString()));

                // Connect to it and pass the listener
                Request req = new Request.Builder().url(url).build();
                socket = CLIENT.newWebSocket(req, ChatSocketManager.this.listener);   // async open
            }

            @Override public void onError(Throwable t) {
                Log.e(TAG, String.format("%s Login failed", TAG));
                result.complete(false);
            }
        });

        return result;
    }


    // ================================================================================
    // WebSocket Utility
    // ================================================================================
    /** Send JSON the WebSocket (called from the main thread) */
    public void sendJson(String json) { if (socket != null) socket.send(json); }

    /** Send a string the WebSocket (called from the main thread) (auto-format as JSON) */
    public void sendString(String text) {
        String stringJson = String.format("{\"type\": \"transcription\", \"data\": \"%s\"}", text);
        sendJson(stringJson);
    }

    /** End the chat */
    public void endChat() {
        if (socket != null) {
            socket.send(String.format("{\"type\":\"end_chat\", \"data\":%s}", System.currentTimeMillis()));
            socket.close(1000, "user ended");
            socket = null;
        }
    }


    // ================================================================================
    // Link to ChatHub
    // ================================================================================
    // set by ChatHub.onStart(cancel). When cancel.get() == true, we should not speak.
    private static volatile AtomicBoolean cancelRef = null;

    // Listener Adapter -- ToDo: Need to make these static (and the whole class...)
    public  void registerWithHub(ChatHub hub) { hub.addListener(LISTENER); }
    private final ChatStatusListener LISTENER = new ChatStatusListener() {
        @Override public boolean onStart(AtomicBoolean cancel) { cancelRef = cancel; return connect_2(); }
        @Override public void    onStop () { disconnect(); cancelRef = null; }
    };





}
