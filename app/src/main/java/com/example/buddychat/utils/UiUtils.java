package com.example.buddychat.utils;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import com.example.buddychat.MainApplication;

// ================================================================================
// Utility to help static classes access & make updates to the UI
// ================================================================================
public class UiUtils {
    private static final String TAG  = "[DPU_UiUtils]";

    // ToDo: Might want to make a helper to set UI element text? Like the hidden userInfo one or the start/end button
    // textUserInfo.setText(String.format("%s %s | %s", p.plwd.first_name, p.plwd.last_name, p.plwd.username));

    // Helper to show short toasts from ANY thread safely
    public static void showToast(final String message) {
        // This runs on the Main UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainApplication.getAppContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --------------------------------------------------------------------------------
    // Safe Text Setters -- Safely update a TextView from any thread
    // --------------------------------------------------------------------------------
    /// Use when there is a String variable.
    public static void updateText(final TextView view, final String text) {
        if (view == null) return; // Safety check
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { view.setText(text); }
        });
    }

    /// Use when there is a Resource ID (e.g., R.string.welcome_message).
    public static void updateText(final TextView view, final int stringResId) {
        if (view == null) return; // Safety check
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { view.setText(stringResId); }
        });
    }




}

