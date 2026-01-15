package com.example.buddychat.network.ws;

import com.example.buddychat.R;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import com.example.buddychat.chat.ChatHub;


// ================================================================================
// Handles the WebSocket responses
// ================================================================================
// This is a helper class for ChatSocketManager -- implements all of the WebSocketListener methods
// UI updates, logs, start/end button
// ToDo: UI helper class needs to be made for UI updates/Toast
public class ChatUiCallbacks extends WebSocketListener {
    private static final String TAG  = "[DPU_ChatListener]";

    // UI references that will be modified
    private final TextView statusView;
    private final Button   startEndBtn;

    // Let MainActivity know whether chat is running (true/false)
    private final Consumer<Boolean> runningStateSink;

    // Handler to hop onto UI thread.
    private final Handler ui = new Handler(Looper.getMainLooper());

    public ChatUiCallbacks(TextView statusView, Button startEndBtn, Consumer<Boolean> runningStateSink) {
        this.statusView        = statusView;
        this.startEndBtn       = startEndBtn;
        this.runningStateSink  = runningStateSink;
    }


    // --------------------------------------------------------------------------------
    // ChatListener
    // --------------------------------------------------------------------------------
    @Override public void onOpen(@NonNull WebSocket ws, @NonNull Response res) {
        // Tell ChatHub we succeeded
        ChatHub.wsState.set(true);

        runningStateSink.accept(true);  // tells MainActivity
        Log.d(TAG, String.format("%s WebSocket successfully opened, response: %s", TAG, res));

        // ToDo: Replace with calls to the UI helper class I will make
        ui.post(() -> {
            startEndBtn.setText(R.string.end_chat);
            Toast.makeText(startEndBtn.getContext(), "Chat started", Toast.LENGTH_SHORT).show();
        });
    }

    @Override public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
        MessageHandler.onMessage(text);
    }

    @Override public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
        Log.d(TAG, String.format("%s Closing: %s", TAG, reason));
        ws.close(code, reason);
    }

    @Override public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
        // ToDo: Later I think I would call like a function here, so set it to true, and ChatHub checks if everything worked
        ChatHub.wsState    .set(false); // tell ChatHub we have disconnected
        ChatHub.cancelStart.set(true ); // tell ChatHub to cancel startup (if we were starting)

        runningStateSink.accept(false);  // tells MainActivity
        Log.d(TAG, String.format("%s WebSocket closed: %s", TAG, reason));

        // ToDo: Replace with calls to the UI helper class I will make
        ui.post(() -> {
            startEndBtn.setText(R.string.start_chat);
            Toast.makeText(startEndBtn.getContext(), "Chat ended", Toast.LENGTH_SHORT).show();
        });
    }

    @Override public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, Response res) {
        // ToDo: tell ChatHub to cancel
        ChatHub.wsState    .set(false); // tell ChatHub we have disconnected
        ChatHub.cancelStart.set(true ); // tell ChatHub to cancel startup (if we were starting)

        // ToDo: Replace with calls to the UI helper class I will make
        ui.post(() -> {
            String wsError = String.format("WS error: %s", t.getMessage());
            Toast.makeText(startEndBtn.getContext(), wsError, Toast.LENGTH_LONG).show();
            Log.d(TAG, wsError);
        });
    }


}
