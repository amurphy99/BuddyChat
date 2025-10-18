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
