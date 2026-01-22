package com.example.buddychat;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;

// Buddy SDK
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.BuddyActivity;
import com.bfr.buddy.ui.shared.FacialExpression;

// Speech System
import com.example.buddychat.chat.StatusController;
import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.network.api.TokenManager;
import com.example.buddychat.network.api.ProfileManager;

// Buddy Features
import com.example.buddychat.tts.SetupTTS;
import com.example.buddychat.utils.motors.HeadMotors;
import com.example.buddychat.utils.sensors.SensorListener;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.utils.behavior.BehaviorTasks;

// BuddySDK.Speech wrappers
import com.example.buddychat.stt.STTCallbacks;
import com.example.buddychat.stt.BuddySTT;


// ================================================================================
// Main Activity of the app; runs on startup
// ================================================================================
// In the official examples when running on the BuddyRobot, we need to
// overlay the interface of this app on top of the core application. In
// the examples they have code setup to relay clicks from our app UI to
// whatever is below it.
public class MainActivity extends BuddyActivity {
    private static final String TAG = "[DPU_Main]";

    // UI References
    private TextView textUserInfo;   // Username (currently hidden)
    private Button   buttonStartEnd; // Start or end the chat/backend websocket connection

    // WebSocket related ToDo: IDK if this was needed anymore...
    private STTCallbacks sttCallbacks;

    // ================================================================================
    // Startup code (should any of this go in onSDKReady() instead?)
    // ================================================================================
    // Login as a way to check our status (i.e. if the login works we know the app is working)
    @Override protected void onCreate(Bundle savedInstanceState) {
        // Setup the app & layout
        super.onCreate(savedInstanceState);
        Log.i(TAG, String.format("%s <==================== onCreate ====================>", TAG));

        // Guarantee a clean slate for StatusController every time the app opens
        StatusController.forceReset();

        // Setup UI
        setContentView(R.layout.activity_main);
        initializeUI();
        wireButtons();

        // Initial login in app startup
        NetworkUtils.pingHealth();  // Test the API
        TokenManager.initialLogin(ProfileManager::fetchProfile);

        // WebSocket & STT callback objects (we pass the STT callback some things here like UI references, etc.)
        sttCallbacks  = new STTCallbacks(ChatSocketManager::sendString);

        // Register for updates about the chat status from StatusController
        StatusController.setListener(new StatusController.StateListener() {
            @Override public void onStateChange(final boolean isActive) {
                updateButton(isActive);
            }
        });

    }

    // --------------------------------------------------------------------------------
    // Called when the BuddyRobot SDK is ready
    // --------------------------------------------------------------------------------
    @Override public void onSDKReady() {
        Log.i(TAG, "--------------------------- Buddy SDK ready ---------------------------");

        // Transfer the touch information to BuddyCore in the background
        BuddySDK.UI.setViewAsFace(findViewById(R.id.view_face));

        // Setup STT & TTS
        BuddySTT.init(this, sttCallbacks);
        SetupTTS.loadTTS();

        // Setup USB sensor listeners (AudioTracking is enabled after the first time Buddy talks)
        SensorListener.setupSensors();
        SensorListener.EnableUsbCallback();
        SensorListener.setTouchSensorsEnabled(true);

        // Check the motor status ToDo: Not sure if they need enabling here
        HeadMotors.logHeadMotorStatus();

        // Set Buddy's behavior to "Sleep" mode until the chat is started
        BehaviorTasks.startSleepTask(); // ToDo: What order should these be in?
        Emotions.setMood(FacialExpression.TIRED);
    }

    // ================================================================================
    // System Behavior (Pause, Resume, Destroy)
    // ================================================================================
    /** Only release our own stuff inside it, nothing SDK related... */
    @Override public void onDestroy() {
        Log.i(TAG, String.format("%s <==================== onDestroy ====================>", TAG));

        // Stop background timers
        TokenManager.stopTokenRefresher();

        // Unregister the UI listener so we don't try to update a dead screen
        StatusController.setListener(null);

        // Make sure Buddy stops talking/listening
        StatusController.stop(); // Safe to call even if already stopped

        // Clean up sensors ToDo: Audio would go here if it was enabled
        SensorListener.setTouchSensorsEnabled(false);
        SensorListener.DisableUsbCallback();

        // Clean up motors ToDo: Would call stop from RotateBody here if it was enabled
        HeadMotors.stopAll();

        // The super method gets called LAST so we can clean up our own resources first
        super.onDestroy();
    }

    // ================================================================================
    // Other Helper Methods
    // ================================================================================
    /** Toggles the chat state. We rely on StatusController to track if we are running or not.
     * Note: We do NOT update the button text here manually. We wait for the StatusController to tell us the state actually changed. */
    public void toggleChat() {
        // Robot is running -> Tell it to Stop; Robot is sleeping -> Tell it to Start
        if (StatusController.isActive()) { StatusController.stop (); }
        else                             { StatusController.start(); }
    }

    // --------------------------------------------------------------------------------
    // UI Elements
    // --------------------------------------------------------------------------------
    /** Initialize UI element references */
    private void initializeUI() {
        textUserInfo   = findViewById(R.id.textUserInfo  );
        buttonStartEnd = findViewById(R.id.buttonStartEnd);
    }

    /** Set button listeners */
    private void wireButtons() {
        // Start or end the chat/backend websocket connection
        buttonStartEnd.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s StartEnd Button pressed", TAG));
            toggleChat();
        });
    }

    /** Updates button appearance based on state. */
    private void updateButton(final boolean isActive) {
        // StatusController runs on bg thread, so we must wrap in runOnUiThread
        runOnUiThread(() -> {
            if (isActive) {
                // If Active -> Button should allow stopping
                buttonStartEnd.setText(R.string.end_chat);
                buttonStartEnd.setBackgroundColor(getColor(R.color.purple_700));
            } else {
                // If Inactive -> Button should allow starting
                buttonStartEnd.setText(R.string.start_chat);
                buttonStartEnd.setBackgroundColor(getColor(R.color.teal_200));
            }
        });
    }


}
