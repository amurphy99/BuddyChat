package com.example.buddychat.network;

import com.example.buddychat.BuildConfig;

import okhttp3.HttpUrl;


public final class BackendURLs {
    private static final String TAG = "[DPU_BackendURLs]";

    // --------------------------------------------------------------------------------
    // WebSocket URL
    // --------------------------------------------------------------------------------
    // URL of the WebSocket server
    public static HttpUrl getWebSocketURL(String accessToken) {
        // For local Docker container
        if (BuildConfig.TEST_LOCAL == "1") {
            return new HttpUrl.Builder().scheme("http").host("10.0.2.2").port(8000).addPathSegments("ws/chat/")
                    .addQueryParameter("token",  accessToken)
                    .addQueryParameter("source", "buddyrobot")
                    .build();
        }

        // For cloud server connection
        else {
            String host = "cognibot.org"; // sandbox.cognibot.org | cognibot.org
            return new HttpUrl.Builder().scheme("https").host(host).addPathSegments("ws/chat/")
                    .addQueryParameter("token",  accessToken)
                    .addQueryParameter("source", "buddyrobot")
                    .build();
        }


    }


}
