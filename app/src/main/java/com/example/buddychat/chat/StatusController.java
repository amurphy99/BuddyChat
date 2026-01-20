package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Log;

import com.bfr.buddy.ui.shared.FacialExpression;
import com.example.buddychat.stt.BuddySTT;
import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.utils.UiUtils;
import com.example.buddychat.utils.behavior.BehaviorTasks;
import com.example.buddychat.utils.behavior.Emotions;

// ================================================================================
// StatusController -- ToDo: Need to find all classes that used ChatHub and rework
// ================================================================================
/** StatusController <br>
 * Control the chat starting and ending. Includes processes like STT, TTS, the
 * WebSocket connection, and the beginning/ending behaviors (sleep/wake).
 */
public final class StatusController {
    private static final String TAG = "[DPU_StatusController]";
    private StatusController() {} // no instances

    // Thread-safe flag: true = Awake, Talking, WS Connected; false = Asleep, Idle, WS Disconnected
    private static final AtomicBoolean isChatActive = new AtomicBoolean(false);

    public static boolean isActive() { return isChatActive.get(); }

    // --------------------------------------------------------------------------------
    // Public API (start & stop)
    // --------------------------------------------------------------------------------
    /** Called by MainActivity (Button Press) or `utils.sensors.TouchSensors`.
     * Start "stage 1" -- initializes TTS & STT + WebSocket connection. If WS is succeeds it triggers start "stage 2." */
    public static void start() {
        if (isChatActive.get()) { Log.w(TAG, String.format("%s Start called, but chat is already active. Ignoring.", TAG)); return; }
        Log.i(TAG, String.format("%s Starting Chat Sequence (Stage 1)...", TAG));

        // 1. Enable all of the "basic processes"
        BuddyTTS.start();  // LoadTTS is already done in MainActivity, so that is always ready (just calling it for fun)
        BuddySTT.start();  // Starting STT here

        // 2. Connect to WebSocket (async)
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
        UiUtils.showToast("Chat Connected!");
        updateState(true);
        playBeginning(); // Play "Wake Up" behavior
    }

    /** Called if the connection fails (after retries). Log error & shutdown. */
    public static void showError(String errorMsg) {
        Log.e(TAG, String.format("%s Chat failed to start: %s", TAG, errorMsg));
        UiUtils.showToast("Connection Error: " + errorMsg);
        stopInternal("System Error");
    }


    // --------------------------------------------------------------------------------
    // Internal Logic
    // --------------------------------------------------------------------------------
    private static void stopInternal(String reason) {
        Log.i(TAG, String.format("%s Stopping Chat. Reason: %s", TAG, reason));

        // 1. Check if we were actually awake
        boolean wasAwake = isChatActive.get();
        updateState(false); // Mark as offline immediately
        UiUtils.showToast("Chat ended, Goodbye!");

        // 2. Kill the Network -- ToDo: Do I need to guard for if the chat wasn't active?
        ChatSocketManager.endChat(); // Sends "end_chat" JSON and closes socket

        // 3. If we were awake, be polite before dying. If we weren't awake (e.g., error during startup), just ensure the sleep pose is held.
        if (wasAwake) { playEnding(); }
        else          { Log.d(TAG, String.format("%s Robot was not active, skipping goodbye animation.", TAG)); }
    }


    // --------------------------------------------------------------------------------
    // Behavior Utilities (for start/end)
    // --------------------------------------------------------------------------------
    /** Say final message and start sleep animation. */
    private static void playBeginning() {
        Log.d(TAG, String.format("%s >>> Playing beginning behavior <<<", TAG));

        // Wake Buddy up from the "SLEEP" BI
        BehaviorTasks.startWakeUpTask(() -> {
            Emotions.setMood(FacialExpression.HAPPY, 2_000L);

            // Say Hello -- ToDo: Should I use "speak happy" here?
            // ToDo: Where does the STT get started back up?
            //BuddyTTS.speak("Hello! I am ready to chat.");

            BuddySTT.start();
        });
    }

    /** Say final message and start sleep animation. */
    // ToDo: Not sure how to handle STT and TTS (need to wait until here?)
    private static void playEnding() {
        Log.d(TAG, String.format("%s >>> Playing ending behavior <<<", TAG));

        // Say "goodbye" before doing the sleep animation
        BuddyTTS.speak("Okay, thank you for talking today!", () -> {
            Emotions.setMood(FacialExpression.TIRED);

            BuddySTT.pause();

            // ToDo: Toggle STT+TTS off
            Log.i(TAG, String.format("%s Chat ended; STT & TTS paused.", TAG));

            BehaviorTasks.startSleepTask();
        });


    }


    // ================================================================================
    // Chat Status Listeners (used in MainActivity for UI updates)
    // ================================================================================
    // Define interface for the UI to listen to
    public interface StateListener {
        void onStateChange(boolean isActive);
    }

    private static StateListener uiListener;

    // Allow MainActivity to register itself
    public static void setListener(StateListener listener) {
        uiListener = listener;
    }

    // Update the internal helpers to notify the listener
    private static void updateState(boolean active) {
        isChatActive.set(active);
        if (uiListener != null) { uiListener.onStateChange(active); }
    }


}
