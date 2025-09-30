package com.example.buddychat;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import java.util.Locale;

// Buddy SDK
import com.bfr.buddysdk.BuddySDK;
import com.bfr.buddysdk.BuddyActivity;

import com.bfr.buddy.ui.shared.FacialExpression;

// Speech System
import com.example.buddychat.network.LoginAndProfile;
import com.example.buddychat.network.model.AuthListener;
import com.example.buddychat.network.ws.ChatSocketManager;
import com.example.buddychat.network.ws.ChatUiCallbacks;
import com.example.buddychat.chat.ChatController;
import com.example.buddychat.chat.ChatStatus;

// Buddy Features
import com.example.buddychat.utils.motors.RotateBody;
import com.example.buddychat.utils.SensorListener;
import com.example.buddychat.utils.behavior.Emotions;
import com.example.buddychat.utils.motors.HeadMotors;
import com.example.buddychat.utils.behavior.BehaviorTasks;

// BuddySDK.Speech wrappers
import com.example.buddychat.stt.STTCallbacks;
import com.example.buddychat.tts.BuddyTTS;
import com.example.buddychat.stt.BuddySTT;
import com.example.buddychat.stt.BuddySTT.Engine;

// =======================================================================
// Main Activity of the app; runs on startup
// =======================================================================
// In the official examples when running on the BuddyRobot, we need to
// overlay the interface of this app on top of the core application. In
// the examples they have code setup to relay clicks from our app UI to
// whatever is below it.
public class MainActivity extends BuddyActivity {
    private static final String TAG = "[DPU_Main]";

    // UI References
    private TextView textUserInfo;   // Username (currently hidden)
    private TextView userView;       // Display user's most recent message
    private TextView botView;        // Display Buddy's most recent message
    private Button   buttonStartEnd; // Start or end the chat/backend websocket connection

    // UI Elements for Development
    private Button   buttonTester1;  // [Development] Trigger features to be tested
    private Button   buttonTester2;  // [Development] Emergency stop any motors/movements
    private Button   buttonTester3;  // [Development] Trigger features to be tested
    private Button   buttonTester4;  // [Development] Trigger features to be tested
    private Button   buttonTester5;  // [Development] Trigger features to be tested
    private TextView testView1;

    // WebSocket related
    private volatile String            authToken;
    private          boolean           isRunning = false;
    private final    ChatSocketManager chat      = new ChatSocketManager();
    private          ChatUiCallbacks   chatCallbacks;
    private          STTCallbacks      sttCallbacks;

    // So classes can call these
    private final Runnable startChatAction = this::startChat;
    private final Runnable   endChatAction = this::endChat;

    // =======================================================================
    // Startup code
    // =======================================================================
    // I don't know if maybe all of this should just go into the onSDKReady() function...
    @Override protected void onCreate(Bundle savedInstanceState) {
        // Setup the app & layout
        super.onCreate(savedInstanceState);
        Log.i(TAG, String.format("%s <==================== onCreate ====================>", TAG));

        // Setup UI
        setContentView(R.layout.activity_main);
        initializeUI(); wireButtons();

        // WebSocket & STT callback objects (we pass the STT callback some things here like UI references, etc.)
        chatCallbacks = new ChatUiCallbacks(botView, buttonStartEnd, running -> isRunning = running);
        sttCallbacks  = new STTCallbacks(userView, testView1, chat::sendString);

        // Login, set auth tokens, and fetch the profile. ToDo: Could also use this in the future to set profile information...
        final LoginAndProfile loginAndProfile = new LoginAndProfile(textUserInfo, botView);
        loginAndProfile.doLoginAndProfile(new AuthListener() {
            @Override public void onSuccess(String    token) { authToken = token; }
            @Override public void onError  (Throwable t    ) { Log.e(TAG, "Login failed"); }
        });
    }

    // =======================================================================
    // Called when the BuddyRobot SDK is ready
    // =======================================================================
    @Override public void onSDKReady() {
        Log.i(TAG, "-------------- Buddy SDK ready --------------");

        // Transfer the touch information to BuddyCore in the background
        BuddySDK.UI.setViewAsFace(findViewById(R.id.view_face));

        // Setup STT & TTS
        BuddyTTS.init(getApplicationContext());
        BuddySTT.init(this, Locale.ENGLISH, Engine.GOOGLE, true);

        // Setup USB sensor listeners (AudioTracking is enabled after the first time Buddy talks)
        SensorListener.setupSensors(); SensorListener.EnableUsbCallback();
        SensorListener.setTouchSensorsEnabled(true);

        // Set the ChatControllers methods
        ChatController.setStartChatAction(startChatAction);
        ChatController.setEndChatAction  (  endChatAction);

        // Set Buddy's behavior to "Sleep" mode until the chat is started
        BehaviorTasks.startSleepTask();
        Emotions.setMood(FacialExpression.TIRED);
    }

    // -----------------------------------------------------------------------
    // App Behavior
    // -----------------------------------------------------------------------
    // ToDo: Ignoring these methods until not having them causes an error!
    // ToDo: If we set onDestroy back up, only release our own stuff inside it, nothing SDK related
    // ToDo: MAYBE onDestroy() we should make sure to exit some of our threads for the motor movement?
    // ToDo: Disable USB callbacks would be called here, in like onDestroy().
    // The wheels example project had onStop and onDestroy disable the wheels...
    //@Override public void onPause  () { super.onPause  (); Log.i(TAG, String.format("%s <========== onPause ==========>",   TAG));}
    //@Override public void onResume () { super.onResume (); Log.i(TAG, String.format("%s <========== onResume ==========>",  TAG));}
    @Override public void onDestroy() {
        Log.i(TAG, String.format("%s <========== onDestroy ==========>", TAG));
        ChatController.clearChatActions(startChatAction, endChatAction);
        super.onDestroy();
    }

    // =======================================================================
    // Methods for starting/ending the chat
    // =======================================================================
    /** Toggle WebSocket connection & control "SLEEP" BehaviorInstruction (BI) */
    public void toggleChat() {
        if (!isRunning) { startChat(); } else { endChat(); }
        Toast.makeText(this, (isRunning ? "Chat connected; STT & TTS started.": "Chat ended; STT & TTS paused."), Toast.LENGTH_LONG).show();
    }

    /** Start Chat. Two parts: 1) Wake up from "SLEEP" BI; 2) Connect to WebSocket. */
    public void startChat() { if (isRunning) { return; }
        ChatStatus.setIsRunning(true);  // change the chat status

        // Make sure our token is set - ToDo: Could also check the refresh/timeout here (might need to...)
        // ... ToDo: Could repeat the login call here

        // 1) Wake Buddy up from the "SLEEP" BI
        BehaviorTasks.stopCurrentTask();
        BehaviorTasks.startWakeUpTask(() -> {
            Emotions.setMood(FacialExpression.HAPPY, 2_000L);

            // 2) Connect to the backend through the WebSocket & toggle STT+TTS on
            chat.connect(authToken, chatCallbacks);
            BuddyTTS.toggle(); BuddySTT.toggle(sttCallbacks);
            Log.i(TAG, String.format("%s Chat connected; STT & TTS started.", TAG));
        });
    }

    /** End Chat. */
    public void endChat() { if (!isRunning) { return; }
        // 1) End the chat through the WebSocket & change the chat status
        chat.endChat();
        ChatStatus.setIsRunning(false);

        // 2) Speak one final message
        BehaviorTasks.stopCurrentTask();
        BuddyTTS.speak("Okay, thank you for talking today!", () -> {

            // 3) Toggle STT+TTS off
            BuddyTTS.toggle(); BuddySTT.toggle(sttCallbacks);
            Log.i(TAG, String.format("%s Chat ended; STT & TTS paused.", TAG));

            // 4) Start sleep mode -- ToDo: Maybe set its face to "TIRED" onStarted...
            BehaviorTasks.startSleepTask();
        });
    }

    // =======================================================================
    // UI Elements
    // =======================================================================
    /** Initialize UI element references */
    private void initializeUI() {
        textUserInfo   = findViewById(R.id.textUserInfo  );
        userView       = findViewById(R.id.userView      );
        botView        = findViewById(R.id.botView       );
        buttonStartEnd = findViewById(R.id.buttonStartEnd);

        buttonTester1  = findViewById(R.id.buttonTester1 );
        buttonTester2  = findViewById(R.id.buttonTester2 );
        buttonTester3  = findViewById(R.id.buttonTester3 );
        buttonTester4  = findViewById(R.id.buttonTester4 );
        buttonTester5  = findViewById(R.id.buttonTester5 );
        testView1      = findViewById(R.id.testView1     );
    }

    /** Set button listeners */
    private void wireButtons() {
        // Start or end the chat/backend websocket connection
        buttonStartEnd.setOnClickListener(v -> { Log.w(TAG, String.format("%s StartEnd Button pressed", TAG)); toggleChat(); });

        // -----------------------------------------------------------------------
        // Testing Buttons
        // -----------------------------------------------------------------------
        // Testing Button #1: Trigger "YES" nod
        buttonTester1.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s Testing Button #1 pressed.", TAG));
            Emotions.setMood(FacialExpression.SURPRISED, 2_000L);

            HeadMotors.logHeadMotorStatus();
            HeadMotors.nodYes();
        });

        // Testing Button #3: Reset head motor positions (X, Y)
        // ToDo: I actually don't know if its okay to have them both running at hte same time?
        // ToDo: The second one probably fails instantly because of the cooldown feature...
        // ToDo: So something would need to be added here, maybe like the ability to "queue" movements up?
        // ToDo: Also could just add a check within here and click it twice: if posX != 0: resetX(); if posY !=0: resetY();
        buttonTester3.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s Testing Button #3 pressed.", TAG));
            Emotions.setMood(FacialExpression.SAD, 2_000L);

            HeadMotors.logHeadMotorStatus();
            HeadMotors.resetYes();
            HeadMotors.resetNo ();
        });

        // Testing Button #4:
        buttonTester4.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s Testing Button #4 pressed. (toggle Sleep behavior)", TAG));

            if (BehaviorTasks.isRunning) { BehaviorTasks.stopCurrentTask(); }
            else                         { BehaviorTasks.startSleepTask (); }
        });

        // Testing Button #5: ToDo: Testing for the BehaviorInstructions for Sleep and WakeUp... no idea what these will do
        buttonTester5.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s Testing Button #5 pressed. (toggle Wake behavior)", TAG));

            if (BehaviorTasks.isRunning) { BehaviorTasks.stopCurrentTask(); }
            else                         { BehaviorTasks.startWakeUpTask(null); }
        });

        // -----------------------------------------------------------------------
        // Emergency Stop Motors
        // -----------------------------------------------------------------------
        // Testing Button #2: Emergency stop any motors/movements
        buttonTester2.setOnClickListener(v -> {
            Log.w(TAG, String.format("%s !!! Emergency Stop Button Activated !!! -------", TAG));
            RotateBody.emergencyStopMotors();
            HeadMotors.stopAll();
        });
    }

}
