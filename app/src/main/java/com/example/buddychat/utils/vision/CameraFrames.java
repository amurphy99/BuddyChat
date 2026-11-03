/* Grab a frame from Buddy's wide-angle camera as a small JPEG.
--------------------------------------------------------------------------------
`com.example.buddychat.utils.vision.CameraFrames`

Reads the camera through BuddySDK, which keeps the wide-angle camera started by
default (BuddySDK guide 2.4, pp. 62 and 73). The SDK frame is 1024x768; it is
scaled down to at most MAX_WIDTH_PX wide before encoding. Each capture is a
blocking SDK call, so callers run it on a worker thread.

*/
package com.example.buddychat.utils.vision;

import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;

// Buddy SDK
import com.bfr.buddysdk.BuddySDK;


// ================================================================================
// Camera Frames
// ================================================================================
// Capture, scale, and JPEG-encode one camera frame
public final class CameraFrames {
    private static final String TAG = "[DPU_CameraFrames]";
    private CameraFrames() {}  // Static-only class

    // Encoded frame size and quality
    private static final int MAX_WIDTH_PX = 640;  // Frames are scaled down to this width, keeping their shape
    private static final int JPEG_QUALITY =  70;  // Android JPEG quality (0-100)


    // --------------------------------------------------------------------------------
    // Frame
    // --------------------------------------------------------------------------------
    // One encoded camera frame and its size in pixels
    public static final class Frame {
        // Encoded bytes, and the pixel size they were encoded at
        public final byte[] jpeg;
        public final int    width;
        public final int    height;

        // Pair the JPEG bytes with the size they were encoded at
        Frame(byte[] jpeg, int width, int height) {
            this.jpeg   = jpeg;
            this.width  = width;
            this.height = height;
        }
    }


    // --------------------------------------------------------------------------------
    // Capture
    // --------------------------------------------------------------------------------
    // Grab the current frame; throws IllegalStateException when the SDK has none to give
    public static Frame capture() {
        /*
        getGrandAngleFrame() throws IllegalStateException("CAMERA NOT STARTED") when the
        camera is off, and returns null when the Vision service call fails (BuddySDK
        2.4.4). Both reach the caller as IllegalStateException.
        */
        final Bitmap full = BuddySDK.Vision.getGrandAngleFrame();
        if (full == null) throw new IllegalStateException("SDK returned no camera frame");

        // Scale down to MAX_WIDTH_PX wide, keeping the frame's shape
        final float  scale  = Math.min(1f, (float) MAX_WIDTH_PX / full.getWidth());
        final Bitmap scaled = Bitmap.createScaledBitmap(full, Math.round(full.getWidth() * scale), Math.round(full.getHeight() * scale), true);

        // Encode as JPEG
        final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) throw new IllegalStateException("JPEG encoding failed");
        return new Frame(jpeg.toByteArray(), scaled.getWidth(), scaled.getHeight());
    }
}
