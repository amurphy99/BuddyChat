/* Build every backend URL the app uses, for the cloud or a local backend.
--------------------------------------------------------------------------------
`com.example.buddychat.network.BackendURLs`

The only place backend hosts, ports, schemes, and paths are defined. REST calls
(`NetworkUtils`) and the chat socket (`ChatSocketManager`) both build their URLs
here, so they always reach the same backend. Setting the `testlocal=1` Gradle
property switches every URL to a backend in Docker on the development machine.

*/
package com.example.buddychat.network;

import okhttp3.HttpUrl;

// From this project
import com.example.buddychat.BuildConfig;


// ================================================================================
// Backend URLs
// ================================================================================
// Build REST and WebSocket URLs for whichever backend this build targets
public final class BackendURLs {
    private static final String TAG = "[DPU_BackendURLs]";
    private BackendURLs() {}  // Static-only class

    // Cloud backend: "sandbox1.cognibot.org" | "sandbox2.cognibot.org" | "cognibot.org"
    private static final String CLOUD_HOST = "sandbox1.cognibot.org";

    // Local backend in Docker (10.0.2.2 is the emulator's alias for the development machine)
    private static final String LOCAL_HOST = "10.0.2.2";
    private static final int    LOCAL_PORT = 8000;

    // Whether this build targets the local backend (`testlocal=1`)
    private static final boolean IS_LOCAL = "1".equals(BuildConfig.TEST_LOCAL);


    // --------------------------------------------------------------------------------
    // REST Endpoints
    // --------------------------------------------------------------------------------
    // REST API endpoints (the trailing slash is part of each backend route)
    public static HttpUrl getHealthURL () { return baseURL().addPathSegments("api/health/" ).build(); }
    public static HttpUrl getTokenURL  () { return baseURL().addPathSegments("api/token/"  ).build(); }
    public static HttpUrl getProfileURL() { return baseURL().addPathSegments("api/profile/").build(); }


    // --------------------------------------------------------------------------------
    // WebSocket Endpoints
    // --------------------------------------------------------------------------------
    // Main chat socket (OkHttp switches http/https to ws/wss when it opens the socket)
    public static HttpUrl getWebSocketURL(String accessToken) {
        return baseURL()
            .addPathSegments("ws/chat/")
            .addQueryParameter("token",  accessToken )
            .addQueryParameter("source", "buddyrobot")
            .build();
    }

    // Video and navigation socket
    // TODO...


    // --------------------------------------------------------------------------------
    // Shared Base
    // --------------------------------------------------------------------------------
    // Start a URL at the target backend (plain HTTP and port 8000 locally, HTTPS in the cloud)
    private static HttpUrl.Builder baseURL() {
        if (IS_LOCAL) { return new HttpUrl.Builder().scheme("http" ).host(LOCAL_HOST).port(LOCAL_PORT); }
        else          { return new HttpUrl.Builder().scheme("https").host(CLOUD_HOST);                  }
    }
}
