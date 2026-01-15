package com.example.buddychat.network.model;

// Allows us to do onSuccess and onFailure stuff
public interface AuthListener {
    void onSuccess(String    token);
    void onError  (Throwable t    );
}
