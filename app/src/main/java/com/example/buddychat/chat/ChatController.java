package com.example.buddychat.chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

// =======================================================================
// Chat Controller
// =======================================================================
public final class ChatController {
    private static final String TAG = "[DPU_ChatController]";
    private ChatController() {}

    // Main Thread
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Executor MAIN_EXECUTOR = MAIN::post;

    // Helper for classes that need to execute things
    public static Executor mainExecutor() { return MAIN_EXECUTOR; }

    // Actions wired by the Activity
    private static volatile WeakReference<Runnable> startChatAction = new WeakReference<>(null);
    private static volatile WeakReference<Runnable>   endChatAction = new WeakReference<>(null);

    public static void setStartChatAction(Runnable action) { startChatAction = new WeakReference<>(action); }
    public static void   setEndChatAction(Runnable action) {   endChatAction = new WeakReference<>(action); }

    public static void clearChatActions(Runnable startAction, Runnable endAction) {
        if (startChatAction.get() == startAction) startChatAction = new WeakReference<>(null);
        if (  endChatAction.get() ==   endAction)   endChatAction = new WeakReference<>(null);
    }

    // -----------------------------------------------------------------------
    // Public Access
    // -----------------------------------------------------------------------
    public static void start() { runIfAllowed(startChatAction, "StartChat"); }
    public static void end()   { runIfAllowed(  endChatAction, "EndChat"  ); }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------
    private static void runIfAllowed(WeakReference<Runnable> ref, String what) {
        Runnable r = ref.get();
        if (r == null) { Log.w(TAG, String.format("%s No action registered for: %s", TAG, what)); return; }

        // Run it on the main thread
        MAIN.post(() -> {
            try                 { r.run(); Log.i(TAG, String.format("%s Dispatched: %s",        TAG, what)); }
            catch (Throwable t) {          Log.e(TAG, String.format("%s Error running %s: %s ", TAG, what, t.getMessage())); }
        });
    }

}
