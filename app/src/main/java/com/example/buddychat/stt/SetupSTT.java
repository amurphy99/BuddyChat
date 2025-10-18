package com.example.buddychat.stt;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// ================================================================================
// Setup for Built-in Speech-to-Text -- only called once, during initialization
// ================================================================================
public final class SetupSTT {
    private static final String TAG = "[DPU_SetupSTT]";

    // Microphone permissions
    private static final int      REQ_PERM  = 9001;
    private static final String[] MIC_PERMS = { Manifest.permission.RECORD_AUDIO };

    // --------------------------------------------------------------------------------
    // Permission Helpers -- used once during initialization
    // --------------------------------------------------------------------------------
    public static boolean notMicPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
    }

    public static void requestMicPermission(Context context) {
        Log.i(TAG, String.format("%s Requesting microphone permission...", TAG));
        if (!(context instanceof Activity)) return;  // caller must be an Activity
        ActivityCompat.requestPermissions((Activity) context, MIC_PERMS, REQ_PERM);
    }

}
