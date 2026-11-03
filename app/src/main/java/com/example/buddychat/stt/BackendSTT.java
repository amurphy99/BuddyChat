/* Stream the microphone to the backend for speech recognition, in place of Buddy's STT.
--------------------------------------------------------------------------------
`com.example.buddychat.stt.BackendSTT`

Optional, switched by the Backend ASR button during a chat
(`notes/backend-buddy-media.md` §2). Turning it on reads the backend's
input-mode revision, stops Buddy's STT, switches the backend to `backend` input
mode, and then records 100 ms chunks of 16 kHz mono PCM onto the chat socket.
Turning it off stops recording, switches the backend back to `sdk`, and restarts
Buddy's STT. A new chat always starts in `sdk` mode, so ending the chat only
stops recording.

State changes run on the main thread; recording runs on its own thread. Audio
keeps streaming while Buddy speaks (there is no echo suppression).

*/
package com.example.buddychat.stt;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

// From this project
import com.example.buddychat.chat.StatusController;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.utils.ThreadUtils;
import com.example.buddychat.utils.UiUtils;


// ================================================================================
// Backend STT
// ================================================================================
// Switch the chat between Buddy's STT and streamed microphone audio
public final class BackendSTT {
    private static final String TAG = "[DPU_BackendSTT]";
    private BackendSTT() {}  // Static-only class

    // Audio format the backend expects (mono, signed 16-bit little-endian; `notes/client-protocol.md` §2.6)
    public  static final int SAMPLE_RATE_HZ  = 16_000;
    public  static final int BITS_PER_SAMPLE =     16;
    private static final int CHUNK_BYTES     =  3_200;  // 100 ms of audio per audio_data frame

    // Prefix of this class's command ids (MessageHandler routes their acks here)
    public static final String ID_PREFIX = "audio-";

    // Where the switch is (shown on the button)
    public enum State { OFF, STARTING, ON, STOPPING }

    // Switch state (volatile because STTCallbacks reads it from the SDK's thread)
    private static volatile State state = State.OFF;

    // Switch progress (main thread only)
    private static boolean sdkStopped;    // Buddy's STT was stopped here and must be restarted
    private static long    revision;      // Backend input-mode revision from the latest ack
    private static int     commandCount;  // Makes each command id unique

    // Recorder thread (null while not recording)
    @Nullable private static HandlerThread recorderThread;

    // Ends the recorder loop (read on the recorder thread)
    private static volatile boolean recording;

    // Screen to notify when the state changes (null after MainActivity is destroyed)
    @Nullable private static Runnable listener;


    // --------------------------------------------------------------------------------
    // Button and Chat End
    // --------------------------------------------------------------------------------
    // Start switching to backend STT (the next steps run in continueSwitch)
    public static void turnOn() {
        if (state != State.OFF) return;
        if (!StatusController.isActive()) { UiUtils.showToast("Start the chat before turning on Backend ASR"); return; }
        setState(State.STARTING);
        send("get_control_state", null);
    }

    // Start switching back to Buddy's STT (finishes in continueSwitch)
    public static void turnOff() {
        if (state != State.ON) return;
        stopRecording();
        setState(State.STOPPING);
        send("set_input_mode", modeBody("sdk"));
    }

    // Stop recording when the chat ends (runs in every shutdown, from any thread)
    public static void reset() { ThreadUtils.runOnUiThread(() -> finish(null)); }

    // Report the switch's state, and register the screen to notify (null unregisters it)
    public static State state()                              { return state;    }
    public static void  setListener(@Nullable Runnable value) { listener = value; }


    // --------------------------------------------------------------------------------
    // Backend Answers
    // --------------------------------------------------------------------------------
    // Read one of this class's command_acks (MessageHandler drops a malformed one)
    public static void onAck(JSONObject data) throws JSONException {
        final String  name            = data.getString ("name");
        final boolean ok              = data.getBoolean("ok"  );
        final Object  message         = data.get       ("message");
        final long    currentRevision = data.getJSONObject("state").getLong("input_mode_revision");
        ThreadUtils.runOnUiThread(() -> continueSwitch(name, ok, message, currentRevision));
    }

    // Take the next step of the switch
    private static void continueSwitch(String name, boolean ok, Object message, long currentRevision) {
        if ((state != State.STARTING) && (state != State.STOPPING)) return;  // An answer that arrived after the chat ended
        revision = currentRevision;
        if (!ok) { finish(String.format("%s was rejected (%s)", name, message)); return; }

        // 1) Revision read: stop Buddy's STT, then switch the backend to `backend` mode
        if ((state == State.STARTING) && name.equals("get_control_state")) {
            BuddySTT.stop();
            sdkStopped = true;
            send("set_input_mode", modeBody("backend"));
        }

        // 2) Backend is listening: start streaming
        else if ((state == State.STARTING) && name.equals("set_input_mode")) {
            startRecording();
            setState(State.ON);
        }

        // 3) Backend is back in `sdk` mode: restart Buddy's STT
        else if ((state == State.STOPPING) && name.equals("set_input_mode")) {
            finish(null);
        }
    }

    // Return to Buddy's STT, and tell the person at the robot why when the switch failed
    private static void finish(@Nullable String error) {
        // 1) Release the microphone and restart Buddy's STT if the chat is still running
        stopRecording();
        if (sdkStopped && StatusController.isActive()) { BuddySTT.start(); }
        sdkStopped = false;
        setState(State.OFF);

        // 2) Report a failure (Logcat is not visible during a session)
        if (error == null) return;
        Log.w(TAG, String.format("%s Backend ASR stopped: %s", TAG, error));
        UiUtils.showToast("Backend ASR stopped: " + error);
    }


    // --------------------------------------------------------------------------------
    // Commands
    // --------------------------------------------------------------------------------
    // Send one command on the chat socket; a closed socket ends the switch here
    private static void send(String name, @Nullable JSONObject body) {
        if (!ChatSocketManager.sendCommand(ID_PREFIX + (++commandCount), name, body)) { finish("the chat socket is closed"); }
    }

    // Body of a set_input_mode command, naming the revision it expects to replace
    private static JSONObject modeBody(String mode) {
        // put() throws only for a non-finite number, so a JSONException here is a bug
        try                     { return new JSONObject().put("mode", mode).put("expected_revision", revision); }
        catch (JSONException e) { throw new IllegalStateException(e);                                         }
    }


    // ================================================================================
    // Recording
    // ================================================================================
    // Start the recorder thread
    private static void startRecording() {
        recording      = true;
        recorderThread = new HandlerThread("BackendSTT");
        recorderThread.start();
        new Handler(recorderThread.getLooper()).post(BackendSTT::record);
    }

    // End the recorder loop (it releases the microphone after its current chunk)
    private static void stopRecording() {
        recording = false;
        if (recorderThread != null) { recorderThread.quitSafely(); recorderThread = null; }
    }

    // Record and send 100 ms chunks until stopped (runs on the recorder thread)
    @SuppressLint("MissingPermission")  // The Backend ASR button checks the permission before turnOn
    private static void record() {
        // 1) Open the microphone in the backend's format
        final int         bufferBytes = Math.max(CHUNK_BYTES * 4, AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
        final AudioRecord microphone  = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
        if (microphone.getState() != AudioRecord.STATE_INITIALIZED) {
            microphone.release();
            recordingFailed("the microphone could not be opened");
            return;
        }

        // 2) Start recording (a microphone held elsewhere may fail here, or may record silence)
        microphone.startRecording();
        if (microphone.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            microphone.release();
            recordingFailed("the microphone is in use");
            return;
        }

        // 3) Send each full chunk (read() blocks until it fills; PCM bytes are little-endian)
        final byte[] chunk = new byte[CHUNK_BYTES];
        while (recording) {
            final int count = microphone.read(chunk, 0, CHUNK_BYTES);
            if (count <= 0)                                 { recordingFailed("microphone read failed (" + count + ")"); break; }
            if (!ChatSocketManager.sendAudio(chunk, count)) { recordingFailed("the chat socket stopped taking audio");     break; }
        }

        // 4) Release the microphone
        microphone.stop();
        microphone.release();
    }

    // Report a recording failure and switch back to Buddy's STT (called on the recorder thread)
    private static void recordingFailed(String error) {
        recording = false;
        ThreadUtils.runOnUiThread(() -> {
            if (state != State.ON) return;  // Already switching off
            Log.w(TAG, String.format("%s Recording failed: %s", TAG, error));
            UiUtils.showToast("Backend ASR: " + error);
            turnOff();
        });
    }


    // --------------------------------------------------------------------------------
    // Screen Updates
    // --------------------------------------------------------------------------------
    // Record the new state and tell the screen
    private static void setState(State next) {
        state = next;
        if (listener != null) listener.run();
    }
}
