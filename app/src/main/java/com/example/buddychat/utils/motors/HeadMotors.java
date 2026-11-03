package com.example.buddychat.utils.motors;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;

// Buddy SDK
import com.bfr.buddy.usb.shared.IUsbCommadRsp;
import com.bfr.buddysdk.BuddySDK;

// =======================================================================
// Unified head motor control: YES (pitch) and NO (yaw)
// =======================================================================
// YES motor => angles: (-45, 45),  speed: (-49.2, 49.2)
//  NO motor => angles: ()
public final class HeadMotors {
    private static final String TAG = "[DPU_HeadMotors]";
    private static final String RES  = "RESET";
    private static final String SEQ  = "SEQUENCE";
    private static final String LOOK = "LOOK";

    // -----------------------------------------------------------------------
    // Motion Parameters (angles are in degrees, speed in degrees/sec)
    // -----------------------------------------------------------------------
    private static final float SPEED       = 10f;  // Different ranges for yes/no
    private static final long  SETTLE_MS   =  5L;  // Short pause between steps
    private static final int   MAX_RETRIES =   2;

    // YES angles (pitch)
    private static final float YES_DOWN    = -15f;
    private static final float YES_UP      = + 5f;
    private static final float HOME        =   0f;  // HOME is 0 for both

    // NO angles (yaw)
    private static final float NO_LEFT     =  -10f;
    private static final float NO_RIGHT    =  +10f;

    // Look-command angles (guide limits: pitch -35 to 45, yaw -90 to 90; BuddySDK guide 2.4, pp. 33-34)
    private static final float LOOK_PITCH_DEG = 15f;  // Up is positive
    private static final float LOOK_YAW_DEG   = 30f;  // Right is positive

    // -----------------------------------------------------------------------
    // Motor Thread (serialize all commands)
    // -----------------------------------------------------------------------
    private static final HandlerThread motorThread = new HandlerThread("MotorBus");
    private static final Handler       motor;
    static { motorThread.start(); motor = new Handler(motorThread.getLooper()); }

    // -----------------------------------------------------------------------
    // Running States
    // -----------------------------------------------------------------------
    public static volatile boolean emergencyStopped = false;
    public static volatile boolean running          = false;

    // Cooldown guard
    private static final    long COOLDOWN_MS   = 3_000L;  // 3s global lockout
    private static volatile long cooldownUntil =     0L;  // elapsedRealtime millis

    // Not really in use yet... can be used for tracking separate states for each motor...
    private enum Motor { YES, NO }

    // =======================================================================
    // Functional Adapters to SDK (avoids repeat code for YES and NO motors)
    // =======================================================================
    // Functions are equivalent for each, just mapped to different SDK calls
    private interface Enabler      { void   call(boolean enable,           IUsbCommadRsp cb); }
    private interface Commander    { void   call(float speed, float angle, IUsbCommadRsp cb); }
    private interface StatusGetter { String call(); }
    private interface PosGetter    { float  call(); }

    private static final class Ops {
        final String       label;
        final String       moveFinished;
        final Enabler      enable;
        final Commander    command;
        final StatusGetter status;
        final PosGetter    position;

        Ops(String label, String moveFinished, Enabler enable, Commander command, StatusGetter status, PosGetter position) {
            this.label = label; this.moveFinished = moveFinished; this.enable = enable; this.command = command; this.status = status; this.position = position;
        }
    }

    private static final Ops YES_OPS = new Ops(
            "YES", "YES_MOVE_FINISHED",
            (enable, cb) -> BuddySDK.USB.enableYesMove(enable, cb),
            (speed, angle, cb) -> BuddySDK.USB.buddySayYes(speed, angle, cb),
            () -> BuddySDK.Actuators.getYesStatus(),
            () -> BuddySDK.Actuators.getYesPosition()
    );

    private static final Ops NO_OPS = new Ops(
            "NO", "NO_MOVE_FINISHED",
            (enable, cb) -> BuddySDK.USB.enableNoMove(enable, cb),
            (speed, angle, cb) -> BuddySDK.USB.buddySayNo(speed, angle, cb),
            () -> BuddySDK.Actuators.getNoStatus(),
            () -> BuddySDK.Actuators.getNoPosition()
    );

    // =======================================================================
    // Public: Status Helpers (check page 32 of the SDK guide for codes)
    // =======================================================================
    // "DISABLE => Motor is disabled
    // "STOP"   => Motor is enabled
    // "SET"    => Motor is moving
    // "NONE"   => Default (what does that mean? maybe if the board isn't responding?)
    public static void logHeadMotorStatus() {
        final String y = "YES status=" + YES_OPS.status.call() + ", pos=" + YES_OPS.position.call();
        final String n =  "NO status=" +  NO_OPS.status.call() + ", pos=" +  NO_OPS.position.call();
        Log.d(TAG, y + " | " + n);
    }

    public static void stopAll() {
        emergencyStopped = true;
        motor.post(() -> {
            YES_OPS.enable.call(false, new LogCb("YES disable"));
            NO_OPS .enable.call(false, new LogCb("NO disable"));
            running = false;
        });
    }

    // =======================================================================
    // Public: YES (nod) / NO (shake) and resets
    // =======================================================================
    // Nod   sequence: DOWN => UP    => HOME  (was: { YES_DOWN, YES_UP, HOME })
    // Shake sequence: LEFT => RIGHT => HOME
    public static void nodYes () { runSequence(Motor.YES, YES_OPS, new float[]{ YES_DOWN,            HOME }); }
    public static void shakeNo() { runSequence(Motor.NO,   NO_OPS, new float[]{  NO_LEFT,  NO_RIGHT, HOME }); }

    // Reset the motors to 0 degrees
    public static void resetYes() { resetToHome(Motor.YES, YES_OPS); }
    public static void resetNo () { resetToHome(Motor.NO,   NO_OPS); }


    // --------------------------------------------------------------------------------
    // Look Commands
    // --------------------------------------------------------------------------------
    // Directions the backend can request over the media socket
    public enum Look { UP, DOWN, LEFT, RIGHT, CENTER }

    // Turn the head one way, or straighten it (pitch, then yaw); false if a move is running
    public static boolean look(Look direction) {
        switch (direction) {
            case UP     : return lookAt(YES_OPS,  LOOK_PITCH_DEG, null);
            case DOWN   : return lookAt(YES_OPS, -LOOK_PITCH_DEG, null);
            case LEFT   : return lookAt( NO_OPS, -LOOK_YAW_DEG,   null);
            case RIGHT  : return lookAt( NO_OPS,  LOOK_YAW_DEG,   null);
            case CENTER : return lookAt(YES_OPS,  HOME,           () -> lookAt(NO_OPS, HOME, null));
            default     : throw new IllegalStateException("Unhandled look direction: " + direction);
        }
    }

    // Enable one motor and move it to an angle (onDone runs when that move ends)
    private static boolean lookAt(Ops ops, float angle, @Nullable Runnable onDone) {
        // 1) One head move at a time
        if (running || emergencyStopped) {
            Log.w(TAG, String.format("%s %s LOOK ignored (running=%s, eStop=%s)", TAG, ops.label, running, emergencyStopped));
            return false;
        }

        // 2) Nothing to move when the motor is already there
        if (Math.abs(ops.position.call() - angle) < 1f) {
            if (onDone != null) onDone.run();
            return true;
        }

        // 3) Enable the motor, then move it
        running = true;
        motor.post(() -> ops.enable.call(true, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String s) { issueMove   (ops, LOOK, angle, 0, onDone); }
            @Override public void onFailed (String s) { enableFailed(ops, LOOK, s); running = false; }
        }));
        return true;
    }

    // Current head angles in degrees, for the media state report
    public static float pitchDeg() { return YES_OPS.position.call(); }
    public static float yawDeg  () { return  NO_OPS.position.call(); }

    // =======================================================================
    // Core Reset (shared)
    // =======================================================================
    // Enable the motors; onSuccess, set the position to 0; disable motor after finished
    private static void resetToHome(Motor m, Ops ops) {
        if (motorPreCheck(ops, true)) return;  // Run some checks before we proceed

        // Checks passed; run the command
        running = true;
        motor.post(() -> { final String before = ops.status.call();
            ops.enable.call(true, new IUsbCommadRsp.Stub() {
                @Override public void onSuccess(String s) { enableSuccess(ops, RES, before); issueMove(ops, RES, HOME, 0, null); }
                @Override public void onFailed (String s) { enableFailed (ops, RES, s     ); running = false; }
            });
        });
    }

    // --------------------------------------------------------------------------------
    // Single Move
    // --------------------------------------------------------------------------------
    // Move one enabled motor to an angle, retrying a failed attempt, then disable the motor
    private static void issueMove(
        Ops                ops,         // Motor being moved (YES or NO)
        String             forCommand,  // RESET or LOOK, for logs
        float              angle,       // Target angle in degrees
        int                retries,     // Failed attempts so far for this move
        @Nullable Runnable onDone       // Runs after the move ends and the motor is disabled
    ) {
        /*
        The SDK answers "OK" when the move starts, "NOK" when it is refused, and
        "YES_MOVE_FINISHED" / "NO_MOVE_FINISHED" when it arrives. The speed's sign picks
        the direction: positive looks up (YES) or right (NO) (BuddySDK guide 2.4, pp. 33-34).
        */
        if (emergencyStopped) { running = false; return; }
        final float speed = (angle >= ops.position.call()) ? SPEED : -SPEED;

        // Disable the motor when the move finishes; retry a refused move
        ops.command.call(speed, angle, new IUsbCommadRsp.Stub() {
            @Override public void onSuccess(String result) {
                if      (result.equals(ops.moveFinished)) { moveSuccess(ops, forCommand, angle, result); endMove(ops, forCommand, onDone); }
                else if (result.equals("NOK"))            { onFailed(result); }
            }
            @Override public void onFailed(String err) {
                moveFailed(ops, forCommand, angle, retries, err);
                if ((retries < MAX_RETRIES) && !emergencyStopped) { motor.postDelayed(() -> issueMove(ops, forCommand, angle, retries + 1, onDone), SETTLE_MS); }
                else                                              { endMove(ops, forCommand, onDone); }
            }
        });
    }

    // Free the head for the next move, disable this motor, and continue with onDone
    private static void endMove(Ops ops, String forCommand, @Nullable Runnable onDone) {
        running = false;
        ops.enable.call(false, new LogCb(ops.label + " DISABLE after " + forCommand));
        if (onDone != null) onDone.run();
    }

    // =======================================================================
    // Core Sequences (shared)
    // =======================================================================
    // Enable the motors; onSuccess callback, issue the sequence
    private static void runSequence(Motor m, Ops ops, float[] angles) {
        if (motorPreCheck(ops, false)) return; // Run some checks before we proceed
        running = true;

        // Checks passed; run the sequence
        motor.post(() -> { final String before = ops.status.call();
            ops.enable.call(true, new IUsbCommadRsp.Stub() {
                @Override public void onSuccess(String s) { enableSuccess(ops, SEQ, before); startStep(ops, angles, 0, 0); }
                @Override public void onFailed (String s) { enableFailed (ops, SEQ, s     ); running = false; }
            });
        });
    }

    // -----------------------------------------------------------------------
    // Run a step of the move sequence
    // -----------------------------------------------------------------------
    /** Chain through angles[idx...]; retries on a single step if it fails. */
    private static void startStep(Ops ops, float[] angles, int idx, int retries) {
        if (emergencyStopped) { running = false; return; }

        // Check if we have finished the last step; disable motor and return if so
        if (idx >= angles.length) {
            running = false;
            Log.d(TAG, String.format("%s %s move %s complete.", TAG, ops.label, SEQ));
            ops.enable.call(false, new LogCb(ops.label + " disable after sequence")); return;
        }

        // Proceed with the move command
        float angle = angles[idx];
        ops.command.call(SPEED, angle, new IUsbCommadRsp.Stub() {

            // Check the status code (refer to above documentation) & start the next move
            @Override public void onSuccess(String success) {
                if (success.equals(ops.moveFinished)) {
                    moveSuccess(ops, SEQ, angle, success);
                    motor.postDelayed(() -> startStep(ops, angles, idx + 1, 0), SETTLE_MS);
                }
            }

            // On fail, retry until we hit the limit; then disable
            @Override public void onFailed(String err) {
                moveFailed(ops, SEQ, angle, retries, err);
                if (retries < MAX_RETRIES && !emergencyStopped) { motor.postDelayed(() -> startStep(ops, angles, idx, retries + 1), SETTLE_MS); }
                else { running = false; ops.enable.call(false, new LogCb(ops.label + " disable after failure")); }
            }

        });
    }


    // =======================================================================
    // Misc. Helpers
    // =======================================================================
    // Logging helper for enable/disable
    private static final class LogCb extends IUsbCommadRsp.Stub {
        private final String what;
        LogCb(String what) { this.what = what; }
        @Override public void onSuccess(String s) { Log.d(TAG, String.format("%s %s OK:   %s", TAG, what, s)); }
        @Override public void onFailed (String s) { Log.w(TAG, String.format("%s %s FAIL: %s", TAG, what, s)); }
    }

    // Run some checks before we proceed with motor commands (already running/already HOME)
    private static boolean motorPreCheck(Ops ops, boolean startAngle) {
        // 1) Basic conditions (true => it failed the check)
        boolean condition = (running || emergencyStopped);

        // 2) Timeout check -- ToDo: maybe add logging for this...
        final long now = android.os.SystemClock.elapsedRealtime();
        condition = condition || (now < cooldownUntil);

        // 3) Don't reset to HOME if already there...
        final float pos = ops.position.call();
        if (startAngle) condition = condition || (Math.abs(pos - HOME) < 1e-3);

        // Only log if the check was failed; if it succeeded, set the cooldown timer
        if (condition) { Log.w(TAG, String.format("%s %s %s ignored (running=%s, eStop=%s, pos=%.1f)", TAG, ops.label, (startAngle ? "RESET" : "SEQUENCE"), running, emergencyStopped, pos)); }
        else           { cooldownUntil = now + COOLDOWN_MS;  }
        return condition;
    }

    // -----------------------------------------------------------------------
    // Helpers for onSuccess & onFailed
    // -----------------------------------------------------------------------
    // Logging for enable onSuccess or onFailed
    private static void enableSuccess(Ops ops, String forCommand, String before) {
        Log.d(TAG, String.format("%s %s ENABLE for %s ok (was=%s, now=%s)", TAG, ops.label, forCommand, before, ops.status.call()));
    }
    private static void enableFailed(Ops ops, String forCommand, String err) {
        Log.w(TAG, String.format("%s %s ENABLE for %s failed: %s", TAG, ops.label, forCommand, err));
    }

    // Logging for move command success
    private static void moveSuccess(Ops ops, String forCommand, float angle, String success) {
        Log.d(TAG, String.format("%s %s MOVE for %s ok (angle=%5.1f, resp=%s)", TAG, ops.label, forCommand, angle, success));
    }
    private static void moveFailed(Ops ops, String forCommand, float angle, int retries, String err) {
        Log.w(TAG, String.format("%s %s MOVE for %s failed [retry %s/%s] (angle=%.1f, err=%s)", TAG, ops.label, forCommand, retries, MAX_RETRIES, angle, err));
    }

}
