package com.example.buddychat.network.api;

import android.util.Log;

import com.example.buddychat.network.NetworkUtils;
import com.example.buddychat.network.model.Profile;
import com.example.buddychat.utils.UiUtils;

// ================================================================================
// Fetch profile information from the backend API
// ================================================================================
// Relies on the `TokenManager` package for the authentication token
// ToDo: To update the actual text UI, we need way to pass a callback in fetch profile that has a reference to that UI element
public final class ProfileManager {
    private static final String TAG  = "[DPU_Profile]";

    /// Fetch the user's profile information (can only do so once we have logged in)
    public static void fetchProfile() {
        Log.d(TAG, String.format("%s Attempting to fetch user profile...", TAG));

        // Get the authorization token from TokenManager
        final String authToken = TokenManager.getAccessToken();

        // Use the NetworkUtils code
        NetworkUtils.fetchProfile(authToken, new NetworkUtils.ProfileCallback() {
            @Override public void onSuccess(Profile p  ) { onProfileSuccess(p); }
            @Override public void onError  (Throwable t) { onProfileError  (t); }
        });
    }

    // --------------------------------------------------------------------------------
    // Profile Callbacks
    // --------------------------------------------------------------------------------
    /// Flash a Toast message once successfully retrieving the profile
    private static void onProfileSuccess(Profile p) {
        Log.i(TAG, String.format("%s Profile fetch success! Welcome, %s %s | %s", TAG, p.plwd.first_name, p.plwd.last_name, p.plwd.username));
        UiUtils.showToast(String.format("Welcome %s", p.plwd.username));
    }

    private static void onProfileError  (Throwable t) {
        Log.e(TAG, String.format("%s Profile fetch failed: %s", TAG, t.getMessage()));
        UiUtils.showToast(String.format("%s Profile fetch failed: %s", TAG, t.getMessage()));
    }

}
