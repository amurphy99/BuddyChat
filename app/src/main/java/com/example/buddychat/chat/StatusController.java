package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;
import android.util.Log;

import com.example.buddychat.stt.BuddySTT;
import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.network.ws.ChatSocketManager;

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

    // ToDo: Notes for how to finish this
    /**
     * Bottom Line: it seems like all of this code can be a lot simpler than I have been making it unfortunately...
     * I think I will for sure remove all of the extra interface/UI stuff, it is just clunky. So I will do that toast class idea,
     * and then start removing the extra. We really just need to slim down all of the code for this app.
     */
    private static void idk() {}


    // --------------------------------------------------------------------------------
    // Start
    // --------------------------------------------------------------------------------
    /** Start "stage 1" -- initializes TTS & STT + WebSocket connection. If WS is succeeds it triggers start "stage 2." */
    public static void start() {
        Log.i(TAG, String.format("%s Start called", TAG));

        // Enable all of the "basic processes" ToDo: (does this include the face and behaviors? maybe not...)
        BuddyTTS.start();            // LoadTTS is already done in MainActivity, so that is always ready (just calling it for fun)
        BuddySTT.start();            // Starting STT here
        ChatSocketManager.connect(); // ChatSocketManager
    }

    /** Start "stage 2" -- called after a successful startup by ChatSocketManager. Sets status and plays startup behavior.*/
    public static void startSuccess() {
        chatStatus.set(true); // ToDo: Maybe should guard for already true?
        playBeginning();
    }

    // --------------------------------------------------------------------------------
    // Stop
    // --------------------------------------------------------------------------------
    /**
     * Then second, we will have an cancelChat that needs to cascade to all processes
     * - cancel chat won't do the cute things because when it is called, buddy should still be asleep
     * - so like if chat == false, buddy should still be asleep and we should stop everything
     * - if chat == true, we still cancel everything, but we say goodbye and set the behavior to sleep
     * - disconnect the chat/ws
     * - turn off/pause STT and TTS
     * - play sleep animation
     * <br>
     * so stop and cancel are in a single function, we just decide if we should say goodbye or change the behavior based
     * on whether or not a chat session was active when it was called.
     */
    public static void stop() {
        // Cancel everything regardless of the chat state
        Log.i(TAG, String.format("%s Stop called", TAG));
        cancel();

        // Play the cute animations--Only if we were already chatting (if called due to a startup failure, we are still asleep)
        if (chatStatus.get()) { playEnding(); }

        // Change the status
        chatStatus.set(false);
    }

    /** Disconnect from Chat/WebSocket (if connected) & stop listening. */
    private static void cancel() {
        // ToDo: What were the other things in the background?
        // ToDo: Might need to guard for already false--like if this gets called back to back
        Log.d(TAG, String.format("%s Cancel called", TAG));

        // Pause STT
        // Pause TTS
        // Disconnect from WS/End the chat


    }

    // --------------------------------------------------------------------------------
    // Behavior Utilities (for start/end)
    // --------------------------------------------------------------------------------
    /** Say final message and start sleep animation. */
    private static void playBeginning() {
        Log.d(TAG, String.format("%s Playing beginning behavior", TAG));
    }

    /** Say final message and start sleep animation. */
    private static void playEnding() {
        Log.d(TAG, String.format("%s Playing ending behavior", TAG));
    }





}
