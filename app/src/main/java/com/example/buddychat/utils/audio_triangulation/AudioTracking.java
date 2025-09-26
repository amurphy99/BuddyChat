package com.example.buddychat.utils.audio_triangulation;

/** Rolling audio localization angle smoothing.
 * <br><br>
 * We use a queue to help smooth the sensor reading out due to its' instability. When we make a
 * call to actually rotate buddy and use the queues value, we take the average and then clear the
 * queue. We should (probably) only call the function to rotate Buddy on STT detection, because
 * with that we know that the user was just speaking for at least a short period before the
 * detection event.
 */
public final class AudioTracking {
    private AudioTracking() {}

    // Rolling buffer of mic localization values (thread-safe)
    private static final AngleBuffer angleBuf = AngleBuffer.defaultAudio(/*capacity*/ 20);

    /** Called by SensorListener when a new vocal sample arrives. */
    public static void onVocalSample(float absoluteAngleDeg) { angleBuf.push(absoluteAngleDeg); }

    /** Compute circular mean of recent angles and clear the buffer. */
    public static float getRecentAngle() { return angleBuf.averageCircularAndClear(); }

}
