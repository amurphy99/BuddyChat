package com.example.buddychat.chat;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import android.util.Log;

// =======================================================================
// ChatHub
// =======================================================================
// This is how classes will know if the chat is running or not
public final class ChatHub {
    private static final String TAG = "[DPU_ChatHub]";

    // Listener that systems will implement to communicate with the overall chat status
    public interface Listener {
        /** Start your subsystem (spawn threads, open sockets, etc.).
         *  Return true if you kicked off successfully (non-blocking).
         *  Use cancel.get() inside loops/work to bail when stopping. */
        boolean onStart(AtomicBoolean cancel);

        /** Stop your subsystem (cooperative). Should be idempotent. */
        void onStop();
    }

    // -----------------------------------------------------------------------
    // Controlling Elements
    // -----------------------------------------------------------------------
    private static final ChatHub INSTANCE = new ChatHub();
    public static ChatHub get() { return INSTANCE; }

    private final CopyOnWriteArrayList<Listener>  listeners = new CopyOnWriteArrayList<>();
    private final AtomicReference     <ChatState> state     = new AtomicReference<>(ChatState.OFF);
    private final AtomicBoolean                   cancel    = new AtomicBoolean(true);   // true means "stop requested"
    private final ExecutorService                 serial    = Executors.newSingleThreadExecutor(); // serialize start/stop

    private ChatHub() {} // no instances

    // Check the state of the chat
    public ChatState state() { return state.get(); }
    public void addListener   (Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove     (l); }

    // -----------------------------------------------------------------------
    // Start Everything
    // -----------------------------------------------------------------------
    /** Public API: try to start everything. If any fails, auto-stop all. */
    public void startAll() {
        serial.execute(() -> {
            if (!state.compareAndSet(ChatState.OFF, ChatState.STARTING)) return;

            // Allow work to run
            cancel.set(false); boolean allOk = true;

            // Notify all listeners to start their processes
            for (Listener l : listeners) {
                try                 { if (!l.onStart(cancel)) { allOk = false; break; } }
                catch (Throwable t) {                           allOk = false; break;   }
            }

            // Roll back cleanly (i.e. if one process fails, cancel the others too)
            if (!allOk) { stopAllInternal(); Log.e(TAG, String.format("%s Some process failed; canceling chat start",      TAG)); }
            else { state.set(ChatState.ON);  Log.i(TAG, String.format("%s All processes successful; chat started cleanly", TAG)); }
        });
    }

    // -----------------------------------------------------------------------
    // Stop Everything
    // -----------------------------------------------------------------------
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
        for (Listener l : listeners) { try { l.onStop(); } catch (Throwable ignored) {} }
        state.set(ChatState.OFF);
    }

}
