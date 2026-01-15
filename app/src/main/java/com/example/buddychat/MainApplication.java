package com.example.buddychat;
import com.bfr.buddysdk.BuddyApplication;

import android.content.Context;

public class MainApplication extends BuddyApplication {

    // Static variable to hold the context
    private static MainApplication instance;

    @Override public void onCreate() {
        super.onCreate();

        // Save the instance so it can be accessed later
        instance = this;
    }

    // Public static method to get the context from anywhere
    public static Context getAppContext() { return instance.getApplicationContext(); }

}
