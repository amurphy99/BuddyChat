package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Log;

import com.example.buddychat.stt.BuddySTT;
import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.utils.UiUtils;

/** StatusController <br>
 * This only handles 3 main services + the beginning/ending behaviors. <br>
 * STT, TTS, & WebSocket control.
 * Other stuff that might be happening should just be handled by MainActivity.
 * <br>
 * <br>
 * ToDo: Okay for when I come back to this again after months, I'll explain where we are at...
 * So I had notes from last time in "ChatHub" saying that basically all of that fancy shit I was
 * doing was pointless. And I think those notes are right. I started implementing what they
 * described here and it seems like it makes a lot of sense to me. So I also remade some of the
 * stuff from the "ws" package in a new "ws2" version. The new versions are all final classes, so I
 * removed any UI references or Toast calls (these are planned for another helper class I need to
 * make). So everything should actually work smoothly from here. I think the main stuff to do is to
 * go through everything and make sure I am aware of all of the things that I have to actually start
 * up/pause/shutdown and make sure they are all covered here. <br>
 * Overall, I think this all tracks because it honestly works really well right now already, so I
 * don't see why I would even need something fancy. At most here we might want to add: <br>
 * - Some sort of retries thing for the login
 * - New Toast class thing
 * - The variable status thing--so not a boolean but a custom class for like off, starting_up, on
 *
 */
public final class StatusController {
    private static final String TAG = "[DPU_StatusController]";
    private StatusController() {} // no instances

    // ToDo: I guess maybe we should do the enumerate thing...
    public static AtomicBoolean chatStatus = new AtomicBoolean(false);




    // Simple thread-safe flag: Is the robot currently in an active chat session?
    // true = Awake, Talking, WS Connected
    // false = Asleep, Idle, WS Disconnected
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
        BuddyTTS.start();            // LoadTTS is already done in MainActivity, so that is always ready (just calling it for fun)
        BuddySTT.start();            // Starting STT here

        // 2. Connect to WebSocket (async) ToDo: I don't think i setup 'showError()' yet...
        ChatSocketManager.connect(); // The SocketManager will call 'startSuccess()' if it works, or 'showError()' if it fails.
    }

    /** Called by MainActivity (Button Press) or Voice Command ("Goodbye").
     * Stops the chat cleanly. */
    public static void stop() {
        stopInternal("User requested stop");
    }

    // --------------------------------------------------------------------------------
    // Callbacks (Called by ChatSocketManager)
    // --------------------------------------------------------------------------------

    /** * Stage 2: WebSocket is connected. The robot is now "Online".
     * Wake up the robot and say hello.
     */
    public static void startSuccess() {
        Log.i(TAG, "WebSocket Connected. Entering Stage 2 (Wake Up).");

        isChatActive.set(true);

        // 1. Visual: Play Wake Up Animation
        playBeginning();

        // 2. Audio: Say Hello
        //BuddyTTS.speak("Hello! I am ready to chat.");
    }

    /**
     * Called if the connection fails (after retries).
     */
    public static void showError(String errorMsg) {
        Log.e(TAG, "Chat failed to start: " + errorMsg);
        UiUtils.showToast("Connection Error: " + errorMsg);

        // Ensure everything is shut down safely
        stopInternal("System Error");
    }

    // --------------------------------------------------------------------------------
    // Internal Logic
    // --------------------------------------------------------------------------------
    // ToDo: Cancel current utterance?
    private static void stopInternal(String reason) {
        Log.i(TAG, "Stopping Chat. Reason: " + reason);

        // 1. Check if we were actually awake
        boolean wasAwake = isChatActive.get();
        isChatActive.set(false); // Mark as offline immediately

        // 2. If we were awake, be polite before dying. If we weren't awake (e.g., error during startup), just ensure the sleep pose is held.
        if (wasAwake) { playEnding(); }
        else          { Log.d(TAG, "Robot was not active, skipping goodbye animation."); }

        // 3. Kill the Network
        ChatSocketManager.endChat(); // Sends "end_chat" JSON and closes socket

        // 4. Kill the Sensors
        //BuddySTT.stop();
        BuddyTTS.stop(); // Silence pending speech
    }

    // --------------------------------------------------------------------------------
    // Behavior Utilities (for start/end)
    // --------------------------------------------------------------------------------
    /** Say final message and start sleep animation. */
    private static void playBeginning() {
        Log.d(TAG, String.format("%s >>> Playing beginning behavior <<<", TAG));
    }

    /** Say final message and start sleep animation. */
    private static void playEnding() {
        Log.d(TAG, String.format("%s >>> Playing ending behavior <<<", TAG));
    }



}
