package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;

// Class that everything can access to check if the chat is running
public final class ChatStatus {
    private ChatStatus() {} // no instances

    private static final AtomicBoolean isChatRunning = new AtomicBoolean(false);

    public static void setIsRunning(boolean enable) { isChatRunning.set(enable); }
    public static boolean isRunning() { return isChatRunning.get(); }
}
