package com.example.buddychat.network.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.buddychat.network.NetworkUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// ================================================================================
// Fetch & maintain authorization token from the backend API
// ================================================================================
// ToDo: Maybe use synchronized here on the first call? Or somehow freeze the state...
public final class TokenManager {
    private static final String TAG  = "[DPU_TokenManager]";

    private static volatile String          authToken;
    private static ScheduledExecutorService scheduler;

    // Safety buffer: If token lasts 15 mins, refresh at 14 mins
    private static final long REFRESH_INTERVAL_MINUTES = 14;

    // --------------------------------------------------------------------------------
    // Public Access
    // --------------------------------------------------------------------------------
    /// Get the current access token
    public static String getAccessToken() {
        if (authToken == null) {
            Log.e(TAG, String.format("%s WARNING: Token requested before login completed!", TAG));
            return ""; // return empty string to prevent NullPointerExceptions elsewhere
        }
        return authToken;
    }

    /// Public wrapper method; set to try 3 times
    public static void initialLogin(final Runnable onLoginSuccessAction) {
        Log.d(TAG, String.format("%s Starting initial login sequence...", TAG));
        login(3, true, onLoginSuccessAction); // true = this is the initial login on app startup
    }

    /// Cleanup (Call in onDestroy)
    public static void stopTokenRefresher() {
        if (scheduler != null && !scheduler.isShutdown()) { scheduler.shutdownNow(); scheduler = null; }
    }

    // --------------------------------------------------------------------------------
    // Login & Callbacks
    // --------------------------------------------------------------------------------
    /// Attempt to fetch token; retry if it fails
    private static void login(final int retries, final boolean isInitialSetup, final Runnable onSuccessAction) {
        Log.d(TAG, String.format("%s Attempting login...", TAG));
        NetworkUtils.login(new NetworkUtils.AuthCallback() {
            @Override public void onSuccess(String    accessToken) { onLoginSuccess(accessToken, isInitialSetup, onSuccessAction); }
            @Override public void onError  (Throwable t          ) { onLoginError  (t,  retries, isInitialSetup, onSuccessAction); }
        });
    }

    /// Successful login callback: Set the token & start the refresh timer
    private static void onLoginSuccess(String accessToken, final boolean isInitialSetup, final Runnable onSuccessAction) {
        authToken = accessToken;
        // Only start the refresh timer if this was the initial login attempt
        if (isInitialSetup) {
            Log.d(TAG, String.format("%s Initial login successful. Starting refresh timer & executing next step...", TAG));
            startTokenRefresher();
            if (onSuccessAction != null) { onSuccessAction.run(); }
        }
        else { Log.d(TAG, String.format("%s Token background refresh successful.", TAG)); }
    }

    /// Failed login Callback: Try again
    private static void onLoginError(Throwable t, int retries, final boolean isInitialSetup, final Runnable onSuccessAction) {
        Log.w(TAG, String.format("%s Login failed: %s (%d retries remaining)", TAG, t.getMessage(), retries));
        handleRetry(retries, isInitialSetup, onSuccessAction);
    }

    /// Handle retries with a small delay (2 seconds) so we don't spam the network
    private static void handleRetry(final int retries, final boolean isInitialSetup, final Runnable onSuccessAction) {
        if (retries > 0) {
            Log.i(TAG, String.format("%s Retrying in 2 seconds... (%d retries remaining)", TAG, retries));
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { login(retries - 1, isInitialSetup, onSuccessAction); } }, 2000); // 2000ms delay
        } else {
            Log.e(TAG, String.format("%s All login attempts failed.", TAG));
            // ToDo: Notify the UI that the Robot is offline?
        }
    }

    // --------------------------------------------------------------------------------
    // Token Refresh / Timer Logic
    // --------------------------------------------------------------------------------
    /// Set a timer to refresh the token before it expires
    private static void startTokenRefresher() {
        // Prevent starting multiple timers if one is already running
        stopTokenRefresher();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Runnable that triggers the background refresh
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override public void run() { performRefresh(); }
        }, REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /// Refresh Logic (no onSuccessAction)
    private static void performRefresh() {
        Log.d(TAG, String.format("%s Timer triggered: Refreshing token now...", TAG));
        login(3, false, ()->{}); // false = this is NOT initial setup, so don't restart the timer
    }

}
