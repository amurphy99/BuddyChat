/* Stream camera frames and sensor readings to the backend, and take head look commands.
--------------------------------------------------------------------------------
`com.example.buddychat.network.media.MediaSocketManager`

Optional, switched by the Video button during a chat (`notes/buddy-media-protocol.md`).
A get_control_state probe on the chat socket returns the chat connection's id;
the media socket opens with it, and media_start returns the session id that
every image and telemetry message carries. Each tick then sends the latest
sensor readings and one JPEG. The backend sends get_media_state every second and
each one is answered with the current state; after 3 seconds without any backend
command the stream stops itself. Camera and head failures are logged, and the
stream keeps going.

Every field lives on one worker thread: button presses, socket callbacks, and
the stream loop are all posted to it.

*/
package com.example.buddychat.network.media;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONException;
import org.json.JSONObject;

// From this project
import com.example.buddychat.chat.StatusController;
import com.example.buddychat.network.BackendURLs;
import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.api.TokenManager;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.utils.UiUtils;
import com.example.buddychat.utils.motors.HeadMotors;
import com.example.buddychat.utils.sensors.SensorListener;
import com.example.buddychat.utils.vision.CameraFrames;


// ================================================================================
// Media Socket Manager
// ================================================================================
// Own the media socket and the stream loop that feeds it
public final class MediaSocketManager {
    private static final String TAG = "[DPU_MediaSocketManager]";
    private MediaSocketManager() {}  // Static-only class

    // Prefix of this class's command ids (MessageHandler routes the chat-socket probe's ack here)
    public static final String ID_PREFIX = "video-";

    // Stream timing and limits
    private static final long TICK_MS              =         200L;  // One frame and one telemetry message per tick
    private static final long HEARTBEAT_TIMEOUT_MS =       3_000L;  // Stop after this long without a backend command
    private static final long FPS_WINDOW_MS        =       1_000L;  // Window for the measured frame rate
    private static final long MAX_QUEUE_BYTES      = 512 * 1_024L;  // Skip a frame while this much is still unsent

    // Where the switch is (shown on the button)
    public enum State { OFF, STARTING, ON }

    // Worker thread that owns every field below
    private static final HandlerThread thread = new HandlerThread("MediaStream");
    private static final Handler       worker;
    static { thread.start(); worker = new Handler(thread.getLooper()); }

    // The stream loop, posted to the worker once per tick
    private static final Runnable TICK = MediaSocketManager::tick;

    // Switch state (volatile because the screen reads it)
    private static volatile State state = State.OFF;

    // Connection (socket and session are set only while STARTING or ON)
    @Nullable private static WebSocket socket;
    @Nullable private static String    sessionId;      // From the media_start ack
    private static int                 commandCount;   // Makes each command id unique
    private static long                lastCommandMs;  // Last backend command, on the elapsedRealtime clock

    // Counters for the state report (replaced at each media_start)
    private static Stats stats = new Stats();

    // Screen to notify when the state changes (set on the main thread, read here)
    @Nullable private static volatile Runnable listener;


    // --------------------------------------------------------------------------------
    // Button and Chat End
    // --------------------------------------------------------------------------------
    // Start the stream (the chat socket's probe answer arrives in onAck)
    public static void start() { worker.post(MediaSocketManager::startNow); }

    // Stop the stream and close the media socket (runs in every shutdown, from any thread)
    public static void stop() { worker.post(() -> stopNow(null, null)); }

    // Report the switch's state, and register the screen to notify (null unregisters it)
    public static State state()                              { return state;    }
    public static void  setListener(@Nullable Runnable value) { listener = value; }

    // Ask the chat socket for the chat connection's id
    private static void startNow() {
        if (state != State.OFF) return;
        if (!StatusController.isActive()) { UiUtils.showToast("Start the chat before turning on Video"); return; }
        setState(State.STARTING);
        if (!ChatSocketManager.sendCommand(ID_PREFIX + (++commandCount), "get_control_state", null)) { stopNow("the chat socket is closed", null); }
    }

    // Read the chat connection's id from the probe's command_ack (MessageHandler drops a malformed one)
    public static void onAck(JSONObject data) throws JSONException {
        final String connectionId = data.getJSONObject("session").getString("primary_connection_id");
        worker.post(() -> openSocket(connectionId));
    }

    // Open the media socket for this chat connection (its onOpen sends media_start)
    private static void openSocket(String connectionId) {
        if (state != State.STARTING) return;  // Turned off while waiting for the probe
        final Request request = new Request.Builder()
            .url(BackendURLs.getMediaWebSocketURL(TokenManager.getAccessToken(), connectionId))
            .build();
        socket = NetworkUtils.CLIENT.newWebSocket(request, new Listener());
    }


    // --------------------------------------------------------------------------------
    // Backend Messages
    // --------------------------------------------------------------------------------
    // Ask the backend for a media session (a failed send is reported by onClosing or onFailure)
    private static void sendMediaStart() {
        socket.send(MediaFrames.command(ID_PREFIX + (++commandCount), "media_start", MediaFrames.object("protocol_version", 1)));
    }

    // Handle one text frame (a malformed or unknown frame is logged and dropped)
    private static void onText(String text) {
        try {
            final JSONObject obj  = new JSONObject(text);
            final String     type = obj.getString("type");
            switch (type) {
                case "command_ack" : onMediaStartAck(obj.getJSONObject("data")); break;
                case "command"     : onCommand      (obj.getJSONObject("data")); break;
                default            : Log.d(TAG, String.format("%s Ignoring media message type: %s", TAG, type));
            }
        }
        catch (JSONException e) { Log.w(TAG, String.format("%s Dropped a malformed media frame: %s", TAG, e.getMessage())); }
    }

    // Start streaming once the backend accepts media_start (Buddy sends no other media command)
    private static void onMediaStartAck(JSONObject data) throws JSONException {
        if (!data.getBoolean("ok")) { stopNow("media_start was rejected (" + data.get("message") + ")", null); return; }
        sessionId     = data.getJSONObject("state").getString("media_session_id");
        stats         = new Stats();
        lastCommandMs = SystemClock.elapsedRealtime();
        setState(State.ON);
        worker.post(TICK);
    }

    // Answer one backend command; every command also counts as the heartbeat
    private static void onCommand(JSONObject data) throws JSONException {
        final String id   = data.getString("id"  );
        final String name = data.getString("name");
        lastCommandMs = SystemClock.elapsedRealtime();

        // get_media_state is the heartbeat; move_head turns the head
        final String error;
        switch (name) {
            case "get_media_state" : error = null;                                 break;
            case "move_head"       : error = moveHead(data.getJSONObject("data")); break;
            default                : error = "Unknown command: " + name;
        }
        socket.send(MediaFrames.ack(id, name, error, stateReport()));
    }

    // Start a head look from a move_head body; null when it started, otherwise why it did not
    @Nullable private static String moveHead(JSONObject body) throws JSONException {
        final String          direction = body.getString("direction");
        final HeadMotors.Look look;
        try                                { look = HeadMotors.Look.valueOf(direction.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return "Unknown direction: " + direction;                       }
        return HeadMotors.look(look) ? null : "Head is busy";
    }


    // ================================================================================
    // Stream Loop
    // ================================================================================
    // One tick: check the heartbeat, send the sensor readings, then send one camera frame
    private static void tick() {
        final long startMs = SystemClock.elapsedRealtime();

        // 1) Stop when the backend has gone quiet
        if (startMs - lastCommandMs > HEARTBEAT_TIMEOUT_MS) { stopNow("no heartbeat from the backend", null); return; }

        // 2) Latest reading from every sensor (a failed send is reported by onClosing or onFailure)
        socket.send(MediaFrames.telemetry(sessionId, SensorListener.latestReadings()));

        // 3) One camera frame, skipped while the socket is still sending older data
        if (socket.queueSize() > MAX_QUEUE_BYTES) { dropFrame("the network is behind"); }
        else                                      { sendFrame();                         }

        // 4) Measured frame rate over the last window
        if (startMs - stats.windowStartMs >= FPS_WINDOW_MS) {
            stats.measuredFps     = (stats.framesSent - stats.windowStartSent) * 1_000.0 / (startMs - stats.windowStartMs);
            stats.windowStartMs   = startMs;
            stats.windowStartSent = stats.framesSent;
        }

        // 5) Next tick, keeping the cadence when the capture was quick
        worker.postDelayed(TICK, Math.max(0L, TICK_MS - (SystemClock.elapsedRealtime() - startMs)));
    }

    // Capture and send one frame (an SDK camera failure only drops this frame)
    private static void sendFrame() {
        final CameraFrames.Frame frame;
        try                             { frame = CameraFrames.capture(); }
        catch (IllegalStateException e) { dropFrame(String.valueOf(e.getMessage())); return; }

        // Send it, and record its size for the state report
        if (!socket.send(MediaFrames.image(sessionId, ++stats.frameSeq, frame))) { dropFrame("the media socket is closing"); return; }
        stats.framesSent++;
        stats.width  = frame.width;
        stats.height = frame.height;
    }

    // Count a skipped frame, logging its reason once until a different reason comes up
    private static void dropFrame(String reason) {
        stats.framesDropped++;
        if (reason.equals(stats.lastError)) return;
        stats.lastError = reason;
        Log.w(TAG, String.format("%s Frame dropped: %s", TAG, reason));
    }

    // Current media state, sent with every command answer (null where a value is unknown)
    private static JSONObject stateReport() {
        final double targetFps = 1_000.0 / TICK_MS;
        return MediaFrames.object(
            "media_session_id", sessionId,
            "video", MediaFrames.object(
                "requested_fps",  targetFps,
                "effective_fps",  targetFps,
                "measured_fps",   stats.measuredFps,
                "width",          stats.width,
                "height",         stats.height,
                "frames_sent",    stats.framesSent,
                "frames_dropped", stats.framesDropped
            ),
            "head", MediaFrames.object(
                "available", !HeadMotors.emergencyStopped,
                "busy",      HeadMotors.running,
                "pitch_deg", HeadMotors.pitchDeg(),
                "yaw_deg",   HeadMotors.yawDeg()
            ),
            "last_error", stats.lastError
        );
    }

    // Counters and measurements for the state report
    private static final class Stats {
        long              frameSeq;                                         // Frames sent, as numbered in the image header
        long              framesSent;                                       // Frames handed to the socket
        long              framesDropped;                                    // Frames skipped (camera error or backed-up socket)
        long              windowStartMs = SystemClock.elapsedRealtime();    // Start of the frame-rate window
        long              windowStartSent;                                  // framesSent at the window's start
        @Nullable Double  measuredFps;                                      // Null until the first window ends
        @Nullable Integer width;                                            // Null until the first frame
        @Nullable Integer height;
        @Nullable String  lastError;                                        // Most recent dropped-frame reason
    }


    // --------------------------------------------------------------------------------
    // Socket Callbacks and Stop
    // --------------------------------------------------------------------------------
    // Hand each OkHttp callback to the worker, ignoring a socket that has since been closed
    private static final class Listener extends WebSocketListener {
        @Override public void onOpen   (@NonNull WebSocket ws, @NonNull Response response) { worker.post(() -> { if (ws == socket) sendMediaStart(); }); }
        @Override public void onMessage(@NonNull WebSocket ws, @NonNull String   text    ) { worker.post(() -> { if (ws == socket) onText(text);     }); }

        // The backend closes the media socket when the chat ends or is replaced (code 4004)
        @Override public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
            worker.post(() -> { if (ws == socket) stopNow(String.format(Locale.ROOT, "the media socket closed (%d %s)", code, reason), null); });
        }

        // A dropped connection or a refused handshake
        @Override public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
            worker.post(() -> { if (ws == socket) stopNow("the media socket failed", t); });
        }
    }

    // Stop the loop and close the socket, and tell the person at the robot why when it failed
    private static void stopNow(@Nullable String error, @Nullable Throwable cause) {
        if (state == State.OFF) return;

        // 1) Stop the loop and close the socket (its later callbacks are ignored)
        worker.removeCallbacks(TICK);
        if (socket != null) { socket.close(1000, "video off"); socket = null; }  // Normal WebSocket closure
        sessionId = null;
        setState(State.OFF);

        // 2) Report a failure (Logcat is not visible during a session)
        if (error == null) return;
        Log.w(TAG, String.format("%s Video stopped: %s", TAG, error), cause);
        UiUtils.showToast("Video stopped: " + error);
    }

    // Record the new state and tell the screen
    private static void setState(State next) {
        state = next;
        final Runnable current = listener;  // Read once (the screen can unregister from the main thread)
        if (current != null) current.run();
    }
}
