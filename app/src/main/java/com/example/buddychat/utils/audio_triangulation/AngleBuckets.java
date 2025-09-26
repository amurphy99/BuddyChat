package com.example.buddychat.utils.audio_triangulation;

// =======================================================================
// Angle Localization Buckets
// =======================================================================
/** Bucketization for audio localization angles around a chosen "center" (default ~180 degrees). */
public final class AngleBuckets {
    private static final String TAG = "[DPU_AngleBuckets]";

    // -----------------------------------------------------------------------
    // Buckets (mapped to motor target angles)
    // -----------------------------------------------------------------------
    public enum Bucket {
        MEDIUM_LEFT  (-10f, "Medium-Left" ),
        SLIGHT_LEFT  (- 5f, "Slight-Left" ),
        CENTER       (+ 0f, "Center"      ),
        SLIGHT_RIGHT (+ 5f, "Slight-Right"),
        MEDIUM_RIGHT (+10f, "Medium-Right");

        /** Yaw (horizontal axis) target in degrees relative to HOME (negative = left). */
        public final float  yawDegrees;
        public final String label;
        Bucket(float yawDegrees, String label) { this.yawDegrees = yawDegrees; this.label = label; }
    }

    // -----------------------------------------------------------------------
    // Bucket the angles
    // -----------------------------------------------------------------------
    /** Wrap any angle into [-180, 180) */
    public static float wrapTo180(float deg) {
        float x = (deg + 540f) % 360f; // -> [0,360)
        return x - 180f;               // -> [-180,180)
    }

    /** Classify an absolute angle (0...360) into a bucket, using center=180, deadband=10, slight=30. */
    public static Bucket classify(float absoluteAngleDeg) {
        return classify(absoluteAngleDeg, /*center*/180f, /*deadband*/20f, /*slight*/50f);
    }

    /**
     * Classify with custom thresholds.
     * @param absoluteAngleDeg absolute angle from the mic (0..360)
     * @param centerDeg        where "front" is (e.g., 180)
     * @param deadbandDeg      +/- range considered CENTER (e.g., 10)
     * @param slightDeg        +/- bound for SLIGHT vs MEDIUM (e.g., 30)
     */
    public static Bucket classify(float absoluteAngleDeg, float centerDeg, float deadbandDeg, float slightDeg) {
        // Signed deviation from center, wrap-safe
        float d  = wrapTo180(absoluteAngleDeg - centerDeg);
        float ad = Math.abs(d);

        // Decide by magnitude and sign
        if      (ad <= deadbandDeg) { return Bucket.CENTER; }
        else if (ad <=   slightDeg) { return (d < 0) ? Bucket.SLIGHT_LEFT : Bucket.SLIGHT_RIGHT; }
        else                        { return (d < 0) ? Bucket.MEDIUM_LEFT : Bucket.MEDIUM_RIGHT; }
    }

    /** Convert a bucket to a String. */
    public static String label(Bucket b) { return b.label; }

    /** Convert a bucket to a suggested horizontal-axis (yaw) target (absolute) around HOME=0. */
    public static float suggestedYawTarget(Bucket b) { return b.yawDegrees; }

}
