/* Build the JSON and binary messages Buddy sends on the media socket.
--------------------------------------------------------------------------------
`com.example.buddychat.network.media.MediaFrames`

Shapes follow `notes/buddy-media-protocol.md`. Telemetry copies every public
field of each SDK sensor reading, so the backend sees whatever the SDK reports
without a hand-written list. Images use the BMV1 binary layout.

*/
package com.example.buddychat.network.media;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// From this project
import com.example.buddychat.utils.sensors.SensorListener;
import com.example.buddychat.utils.vision.CameraFrames;


// ================================================================================
// Media Frames
// ================================================================================
// Build media-socket messages (MediaSocketManager holds all of the state)
final class MediaFrames {
    private static final String TAG = "[DPU_MediaFrames]";
    private MediaFrames() {}  // Static-only class

    // First four bytes of every image message
    private static final byte[] IMAGE_MAGIC = { 'B', 'M', 'V', '1' };


    // --------------------------------------------------------------------------------
    // Commands and Answers
    // --------------------------------------------------------------------------------
    // Command from Buddy to the backend (media_start is the only one)
    static String command(String id, String name, JSONObject body) {
        return object("type", "command", "data", object("id", id, "name", name, "data", body)).toString();
    }

    // Answer to one backend command, carrying the current media state
    static String ack(
        String           id,     // The command's id
        String           name,   // The command's name
        @Nullable String error,  // Why the command was refused, or null when it was accepted
        JSONObject       state   // Current media state
    ) {
        return object("type", "command_ack", "data", object(
            "id", id, "name", name, "ok", error == null, "message", error, "state", state
        )).toString();
    }


    // --------------------------------------------------------------------------------
    // Telemetry and Images
    // --------------------------------------------------------------------------------
    // Latest reading from every sensor source, with every field the SDK provides
    static String telemetry(String sessionId, Map<String, SensorListener.Reading> readings) {
        final JSONArray samples = new JSONArray();
        for (Map.Entry<String, SensorListener.Reading> reading : readings.entrySet()) {
            samples.put(object("source", reading.getKey(), "seq", reading.getValue().seq, "values", fields(reading.getValue().data)));
        }
        return object("type", "telemetry", "data", object("media_session_id", sessionId, "samples", samples)).toString();
    }

    // One camera frame: "BMV1", the header's byte length (big-endian), the JSON header, then the JPEG
    static ByteString image(String sessionId, long seq, CameraFrames.Frame frame) {
        // 1) JSON header (orientation is unknown, so it is null)
        final byte[] header = object(
            "media_session_id", sessionId,
            "seq",              seq,
            "encoding",         "jpeg",
            "width",            frame.width,
            "height",           frame.height,
            "rotation_deg",     null,
            "mirrored",         null
        ).toString().getBytes(StandardCharsets.UTF_8);

        // 2) Binary layout (ByteBuffer writes the length big-endian)
        final ByteBuffer message = ByteBuffer.allocate(IMAGE_MAGIC.length + Integer.BYTES + header.length + frame.jpeg.length);
        message.put(IMAGE_MAGIC).putInt(header.length).put(header).put(frame.jpeg);
        return ByteString.of(message.array());
    }


    // --------------------------------------------------------------------------------
    // JSON Helpers
    // --------------------------------------------------------------------------------
    // Build an object from alternating keys and values (a null value is written as JSON null)
    static JSONObject object(Object... pairs) {
        final JSONObject object = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) { put(object, (String) pairs[i], pairs[i + 1]); }
        return object;
    }

    // Every public field of one SDK reading (static fields such as Parcelable.CREATOR are skipped)
    private static JSONObject fields(Object reading) {
        final JSONObject values = new JSONObject();
        for (Field field : reading.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) { put(values, field.getName(), JSONObject.wrap(read(field, reading))); }
        }
        return values;
    }

    // Add one value; put() throws only for NaN or infinity, which no value here is expected to be
    private static void put(JSONObject object, String key, @Nullable Object value) {
        try                     { object.put(key, (value == null) ? JSONObject.NULL : value); }
        catch (JSONException e) { throw new IllegalStateException(e);                         }
    }

    // Read one public field (always allowed, so IllegalAccessException is a bug)
    private static Object read(Field field, Object owner) {
        try                              { return field.get(owner);             }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
