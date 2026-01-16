package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Log;

import com.example.buddychat.stt.BuddySTT;
import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.utils.UiUtils;

// ================================================================================
// StatusController
// ================================================================================
/** StatusController <br>
 * Control the chat starting and ending. Includes processes like STT, TTS, the
 * WebSocket connection, and the beginning/ending behaviors (sleep/wake).
 */
public final class StatusController {
    private static final String TAG = "[DPU_StatusController]";
    private StatusController() {} // no instances

    // Simple thread-safe flag: Is the robot currently in an active chat session?
    // true = Awake, Talking, WS Connected; false = Asleep, Idle, WS Disconnected
    private static final AtomicBoolean isChatActive = new AtomicBoolean(false);

    // --------------------------------------------------------------------------------
    // Public API (start & stop)
    // --------------------------------------------------------------------------------
    /** Called by MainActivity (Button Press) or touch sensors.
     * Start "stage 1" -- initializes TTS & STT + WebSocket connection. If WS is succeeds it triggers start "stage 2." */
    public static void start() {
        if (isChatActive.get()) { Log.w(TAG, String.format("%s Start called, but chat is already active. Ignoring.", TAG)); return; }
        Log.i(TAG, String.format("%s Starting Chat Sequence (Stage 1)...", TAG));

        // 1. Enable all of the "basic processes"
        BuddyTTS.start();  // LoadTTS is already done in MainActivity, so that is always ready (just calling it for fun)
        BuddySTT.start();  // Starting STT here

        // 2. Connect to WebSocket (async) ToDo: I don't think i setup 'showError()' yet...
        ChatSocketManager.connect(); // The SocketManager will call 'startSuccess()' if it works, or 'showError()' if it fails.
    }

    /** Stops the chat cleanly (UI button press or "stop chat" voice command). */
    public static void stop() { stopInternal("User requested stop"); }

    // --------------------------------------------------------------------------------
    // Callbacks (Called by ChatSocketManager)
    // --------------------------------------------------------------------------------
    /** Stage 2: WebSocket is connected. The robot is now "Online". Wake up the robot and say hello. */
    public static void startSuccess() {
        Log.i(TAG, "WebSocket Connected. Entering Stage 2 (Wake Up).");
        isChatActive.set(true);
        playBeginning(); // Play "Wake Up" behavior
    }

    /** Called if the connection fails (after retries). Log error & shutdown. */
    public static void showError(String errorMsg) {
        Log.e(TAG, String.format("%s Chat failed to start: %s", TAG, errorMsg));
        UiUtils.showToast("Connection Error: " + errorMsg);
        stopInternal("System Error");
    }


    // --------------------------------------------------------------------------------
    // Internal Logic  ToDo: Not sure how to handle STT and TTS
    // --------------------------------------------------------------------------------
    private static void stopInternal(String reason) {
        Log.i(TAG, String.format("%s Stopping Chat. Reason: %s", TAG, reason));

        // 1. Check if we were actually awake
        boolean wasAwake = isChatActive.get();
        isChatActive.set(false); // Mark as offline immediately

        // 2. If we were awake, be polite before dying. If we weren't awake (e.g., error during startup), just ensure the sleep pose is held.
        if (wasAwake) { playEnding(); }
        else          { Log.d(TAG, String.format("%s Robot was not active, skipping goodbye animation.", TAG)); }

        // 3. Kill the Network
        ChatSocketManager.endChat(); // Sends "end_chat" JSON and closes socket

        // 4. Kill the Sensors
        //BuddySTT.stop();
        //BuddyTTS.stop(); // Silence pending speech
    }


    // --------------------------------------------------------------------------------
    // Behavior Utilities (for start/end)
    // --------------------------------------------------------------------------------
    /** Say final message and start sleep animation. */
    private static void playBeginning() {
        Log.d(TAG, String.format("%s >>> Playing beginning behavior <<<", TAG));

        // 2. Audio: Say Hello
        //BuddyTTS.speak("Hello! I am ready to chat.");
    }

    /** Say final message and start sleep animation. */
    private static void playEnding() {
        Log.d(TAG, String.format("%s >>> Playing ending behavior <<<", TAG));
    }



}
