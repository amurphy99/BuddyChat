package com.example.buddychat.network;

import com.example.buddychat.BuildConfig;
import okhttp3.HttpUrl;


// ================================================================================
// Reusable Backend WebSocket Endpoints
// ================================================================================
public final class BackendURLs {
    private static final String TAG = "[DPU_BackendURLs]";

    // Endpoint base URL: "sandbox2.cognibot.org" | "cognibot.org"
    // (reused for the main chat client endpoint + the new video/navigation endpoint)
    private static final String host = "sandbox1.cognibot.org";

    // --------------------------------------------------------------------------------
    // Get the WebSocket URL used for the main chat client connection
    // --------------------------------------------------------------------------------
    public static HttpUrl getWebSocketURL(String accessToken) {
        // If running through a local Docker instance of the backend, we need to change:
        // (1) the 'scheme' from https to http, and (2) add the 8000 port after the host.
        if ("1".equals(BuildConfig.TEST_LOCAL)) {
            return new HttpUrl.Builder().scheme("http").host("10.0.2.2").port(8000).addPathSegments("ws/chat/")
                    .addQueryParameter("token",  accessToken )
                    .addQueryParameter("source", "buddyrobot")
                    .build();
        }

        // For cloud server connection we just need to use https
        else {
            return new HttpUrl.Builder().scheme("https").host(host).addPathSegments("ws/chat/")
                    .addQueryParameter("token",  accessToken )
                    .addQueryParameter("source", "buddyrobot")
                    .build();
        }
    }

    // --------------------------------------------------------------------------------
    // Get the WebSocket URL for the new video + navigation endpoint
    // --------------------------------------------------------------------------------
    // TODO...


}
