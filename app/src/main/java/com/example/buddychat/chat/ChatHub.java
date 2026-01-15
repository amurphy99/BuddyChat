package com.example.buddychat.chat;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import android.util.Log;

// ================================================================================
// ChatHub
// ================================================================================
/** This is how classes will know if the chat is running or not. <br>
 * When we start the chat, each service is toggled on here. If they all return True we proceed as
 * usual. If any fail (returning false), we cancel all services. <br>
 * ToDo: Maybe add Toast here so I can say which service failed?
 * ToDo: STTCallbacks needs to be integrated somehow
 */
public final class ChatHub {
    private static final String TAG = "[DPU_ChatHub]";
    private ChatHub() {} // no instances

    // --------------------------------------------------------------------------------
    // Controlling Elements
    // --------------------------------------------------------------------------------
    private static final ChatHub INSTANCE = new ChatHub();
    public static ChatHub get() { return INSTANCE; }

    private final CopyOnWriteArrayList<ChatStatusListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference     <ChatState>          state     = new AtomicReference<>(ChatState.OFF);
    private final AtomicBoolean                            cancel    = new AtomicBoolean(true);   // true means "stop requested"
    private final ExecutorService                          serial    = Executors.newSingleThreadExecutor(); // serialize start/stop

    // Check the state of the chat
    public ChatState state() { return state.get(); }
    public void addListener   (ChatStatusListener l) { listeners.addIfAbsent(l); }
    public void removeListener(ChatStatusListener l) { listeners.remove     (l); }


    // ToDo: ---- testing ----
    // These would be 1 for each sub process
    public static AtomicBoolean wsState = new AtomicBoolean(false);

    // This will be for everyone (should we cancel?)
    public static AtomicBoolean cancelStart = new AtomicBoolean(false);

    // ToDo: Notes for how to finish this
    /** So how would it work?
     * I actually think that i should split it into two parts kind of
     * So part one is init some of the stuff, we don't want to actually enable everything like wheels though
     * because that takes like extra energy.
     * So when we want to start the chat, we call two functions, first we do the STT TTS stuff
     * Then we do the WS one, if that fails, we cancel the first part. If the first part fails, we don't do the second one
     * So i guess I need to check if those have like onSuccess or onFailure, or if they are instant or not...
     * <br>
     * 3 Functions: start, cancel, end
     * <br>
     * Okay so: we call startChat
     * - We start/enable all of the "basic processes"
     * - If that works, we move on to the Login/WebSocket
     * - If that works, we do the final stuff -> wake up, say hello, start listening, set status to True
     * <br>
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
     * <br>
     * Bottom Line: it seems like all of this code can be a lot simpler than I have been making it unfortunately...
     * I think I will for sure remove all of the extra interface/UI stuff, it is just clunky. So I will do that toast class idea,
     * and then start removing the extra. We really just need to slim down all of the code for this app.
     */
    public static void shutdown() {

    }


    // --------------------------------------------------------------------------------
    // Start Everything
    // --------------------------------------------------------------------------------
    /** Try to start all of the chat-related services. If any fails, auto-stop all. Services:
     * <br> - BuddyTTS
     * <br> - BuddySTT
     * <br> - ChatSocketManager
     * */
    public void startAll() {
        serial.execute(() -> {
            // 1) Make sure we aren't already starting up
            if (!state.compareAndSet(ChatState.OFF, ChatState.STARTING)) return;
            cancel.set(false); boolean allOk = true; // Allow work to run

            // 2) Notify all listeners to start their processes
            for (ChatStatusListener l : listeners) {
                try                 { if (!l.onStart(cancel)) { allOk = false; break; } }
                catch (Throwable t) {                           allOk = false; break;   }
            }

            // 3a) If any one service fails to startup => roll back cleanly (i.e. if one process fails, cancel the others too)
            if (!allOk) { stopAllInternal(); Log.e(TAG, String.format("%s Some process failed; canceling chat start", TAG)); return; }

            // 3b) If all services started successfully
            state.set(ChatState.ON);
            Log.i(TAG, String.format("%s All processes successful; chat started cleanly", TAG));

            // ToDo: The behavior task stuff and opening message needs to be setup here
            // ToDo: Actually since this is like the serial thing, we should do the wakeup and speak in here too?
            // "Hello, how are you today?"
        });
    }

    // --------------------------------------------------------------------------------
    // Stop Everything
    // --------------------------------------------------------------------------------
    /** Public API: stop everything. Safe to call multiple times. */
    public void stopAll() { serial.execute(this::stopAllInternal); }
    private void stopAllInternal() {
        // Check if already off...
        ChatState s = state.get(); if (s == ChatState.OFF) return;
        Log.d(TAG, String.format("%s Stopping all chat-related processes...", TAG));

        // Tell everyone to bail
        state.set(ChatState.STOPPING);
        cancel.set(true);

        // Ask all listeners to stop
        for (ChatStatusListener l : listeners) { try { l.onStop(); } catch (Throwable ignored) {} }
        state.set(ChatState.OFF);
    }

}
