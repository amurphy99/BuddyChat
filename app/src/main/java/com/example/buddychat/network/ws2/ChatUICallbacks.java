package com.example.buddychat.network.ws2;


import android.util.Log;
import androidx.annotation.NonNull;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import com.example.buddychat.chat.ChatHub;
import com.example.buddychat.network.ws.MessageHandler;
import com.example.buddychat.chat.StatusController;

// ================================================================================
// Handles the WebSocket responses
// ================================================================================
// This is a helper class for ChatSocketManager -- implements all of the WebSocketListener methods
// UI updates, logs, start/end button
// ToDo: UI helper class needs to be made for UI updates/Toast
public final class ChatUICallbacks extends WebSocketListener {
    private static final String TAG  = "[DPU_ChatListener]";

    // Handler to hop onto UI thread.
    //private final Handler ui = new Handler(Looper.getMainLooper());

    // --------------------------------------------------------------------------------
    // ChatListener
    // --------------------------------------------------------------------------------
    @Override public void onOpen(@NonNull WebSocket ws, @NonNull Response res) {
        // Tell StatusController we succeeded
        Log.d(TAG, String.format("%s WebSocket successfully opened, response: %s", TAG, res));
        StatusController.chatStatus.set(true);
    }

    @Override public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
        MessageHandler.onMessage(text);
    }

    @Override public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
        Log.d(TAG, String.format("%s Closing: %s", TAG, reason));
        ws.close(code, reason);
    }

    @Override public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
        Log.d(TAG, String.format("%s WebSocket closed: %s", TAG, reason));
    }

    @Override public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, Response res) {
        // Tell StatusController we failed (and to cancel)
        Log.d(TAG, String.format("%s WS error: %s", TAG, t.getMessage()));
        StatusController.stop();
    }


}
