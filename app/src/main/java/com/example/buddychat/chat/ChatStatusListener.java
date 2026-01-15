package com.example.buddychat.chat;

import java.util.concurrent.atomic.AtomicBoolean;

// Used by services to stay in sync together
public interface ChatStatusListener {

    /** Start your subsystem (spawn threads, open sockets, etc.).
     *  Return true if you kicked off successfully (non-blocking).
     *  Use cancel.get() inside loops/work to bail when stopping. */
    boolean onStart(AtomicBoolean cancel);

    /** Stop your subsystem (cooperative). Should be idempotent. */
    void onStop();

}
