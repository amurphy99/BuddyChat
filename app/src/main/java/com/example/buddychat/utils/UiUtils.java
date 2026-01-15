package com.example.buddychat.utils;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.buddychat.MainApplication;

// ================================================================================
// Utility to help static classes access & make updates to the UI
// ================================================================================
public class UiUtils {
    private static final String TAG  = "[DPU_UiUtils]";

    // Helper to show short toasts from ANY thread safely
    public static void showToast(final String message) {
        // This runs on the Main UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainApplication.getAppContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }




}

